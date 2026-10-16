package org.scm4j.vcs.git;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.scm4j.vcs.api.*;
import org.scm4j.vcs.api.exceptions.*;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;

import java.io.*;
import java.net.*;
import java.net.Proxy.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

public class GitVCS implements IVCS {

	public static final String GIT_VCS_TYPE_STRING = "git";
	static final int TRANSPORT_TIMEOUT_SECONDS = 60;
	private static final String REFS_REMOTES_ORIGIN = Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/";
	private static final String REFS_HEADS = Constants.R_HEADS;
	private static final String REFS_TAGS = Constants.R_TAGS;
	private CredentialsProvider credentials;
	private final IVCSRepositoryWorkspace repo;
	private final IVCSLockedWorkingCopy retainedWorkingCopy;
	private String defaultBranchName;
	private BiConsumer<String, Throwable> retryStatusReporter = (operation, failure) -> {};

	public CredentialsProvider getCredentials() {
		return credentials;
	}

	public GitVCS(IVCSRepositoryWorkspace repo) {
		this(repo, null);
	}

	private GitVCS(IVCSRepositoryWorkspace repo, IVCSLockedWorkingCopy retainedWorkingCopy) {
		this.repo = repo;
		this.retainedWorkingCopy = retainedWorkingCopy;
	}

	@Override
	public IVCSMutationSession startModify() {
		try {
			GitSnapshot snapshot = getReadSnapshot();
			return repo.withRepoLock(() -> {
				IVCSLockedWorkingCopy workingCopy = repo.getVCSLockedWorkingCopy();
				try {
					initializeMutationRepository(workingCopy.getFolder(), snapshot);
					return new GitMutationSession(this, workingCopy,
							componentWorkingDirectory(workingCopy.getFolder()), snapshot.defaultBranchName);
				} catch (Exception | Error failure) {
					try (IVCSLockedWorkingCopy failedCopy = workingCopy) {
						failedCopy.setCorrupted(true);
					} catch (Exception | Error cleanupFailure) {
						failure.addSuppressed(cleanupFailure);
					}
					throw failure;
				}
			});
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	private void setCredentials(CredentialsProvider credentials) {
		this.credentials = credentials;
	}

	@Override
	public void setRetryStatusReporter(BiConsumer<String, Throwable> reporter) {
		retryStatusReporter = reporter;
	}

	private String getRealBranchName(String branchName) throws GitAPIException {
		return branchName == null ? getDefaultBranchName() : branchName;
	}

	private synchronized String getDefaultBranchName() throws GitAPIException {
		if (defaultBranchName != null) {
			return defaultBranchName;
		}

		Map<String, Ref> refs;
		try {
			refs = Git.lsRemoteRepository()
					.setRemote(repo.getRepoUrl())
					.setCredentialsProvider(credentials)
					.setTimeout(TRANSPORT_TIMEOUT_SECONDS)
					.callAsMap();
		} catch (GitAPIException e) {
			throw defaultBranchLookupFailed(e);
		}

		Ref head = refs.get(Constants.HEAD);
		if (head == null) {
			throw invalidDefaultBranch("remote HEAD is not advertised");
		}
		if (!head.isSymbolic()) {
			throw invalidDefaultBranch("remote HEAD is not symbolic");
		}

		String targetName = head.getLeaf().getName();
		Ref target = refs.get(targetName);
		if (!targetName.startsWith(REFS_HEADS) || targetName.length() == REFS_HEADS.length()
				|| target == null || target.getObjectId() == null) {
			throw invalidDefaultBranch("remote HEAD does not target an advertised branch");
		}
		defaultBranchName = targetName.substring(REFS_HEADS.length());
		return defaultBranchName;
	}

	private GitAPIException invalidDefaultBranch(String reason) {
		return defaultBranchException(": " + reason, null);
	}

	private GitAPIException defaultBranchLookupFailed(Exception cause) {
		return defaultBranchException("", cause);
	}

	private GitAPIException defaultBranchException(String detail, Exception cause) {
		return new GitAPIException("Could not determine the default branch of Git repository "
				+ repo.getRepoUrl() + detail, cause) {
			private static final long serialVersionUID = 1L;
		};
	}

	Git getLocalGit(String folder) throws Exception {
		return getLocalGit(folder, false);
	}

	private Git getLocalGit(String folder, boolean noCheckout) throws Exception {
		File checkoutDir = new File(folder);
		boolean repoInited = isGitRepositoryInitialized(checkoutDir);
		if (!repoInited) {
			Utils.runWithRetry("Git clone", () -> Git
					.cloneRepository()
					.setDirectory(checkoutDir)
					.setURI(repo.getRepoUrl())
					.setCredentialsProvider(credentials)
					.setTimeout(TRANSPORT_TIMEOUT_SECONDS)
					.setNoCheckout(noCheckout)
					.call()
					.close(), GitVCS::isTransientTransportFailure, retryStatusReporter);
		}
		if (!noCheckout) {
			try (Git git = Git.open(checkoutDir)) {
				configureJGit43LineEndings(git);
			}
		}
		Repository gitRepo = new FileRepositoryBuilder()
				.setGitDir(new File(checkoutDir, ".git"))
				.build();
		return new Git(gitRepo);
	}

	private void configureJGit43LineEndings(Git git) throws IOException, GitAPIException {
		Repository gitRepo = git.getRepository();
		StoredConfig config = gitRepo.getConfig();
		CoreConfig.EOL eol = config.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EOL, CoreConfig.EOL.NATIVE);
		if (eol != CoreConfig.EOL.NATIVE) {
			return;
		}
		ObjectId head = gitRepo.resolve(Constants.HEAD);
		// A compatibility migration must not discard a caller's tracked changes.
		// Leave native configured and retry on a later clean use of the workspace.
		if (head != null && git.status().call().hasUncommittedChanges()) {
			return;
		}

		// JGit 4.3 treated native EOL as a direct checkout on every platform.
		// Current JGit converts text to CRLF on Windows, so use LF explicitly;
		// explicit LF and CRLF settings already behave compatibly and stay intact.
		config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_EOL, CoreConfig.EOL.LF);
		config.save();
		// The initial clone has already checked out files with current JGit's
		// native behavior. Rewrite it once using the compatibility setting.
		if (head != null) {
			git.reset().setMode(ResetType.HARD).call();
		}
	}

	Git getLocalGit(IVCSLockedWorkingCopy wc) throws Exception {
		return getLocalGit(wc.getFolder().getPath());
	}

	private IVCSLockedWorkingCopy getWorkingCopy() throws IOException {
		return retainedWorkingCopy == null
				? repo.getVCSLockedWorkingCopy()
				: IVCSMutationSession.nonClosingView(retainedWorkingCopy);
	}

	private GitSnapshot getReadSnapshot() {
		try {
			if (retainedWorkingCopy != null) {
				try (Git git = getLocalGit(retainedWorkingCopy.getFolder().getPath(), true);
					 Repository repository = git.getRepository()) {
					return captureSnapshot(retainedWorkingCopy.getFolder(), repository);
				}
			}
			return (GitSnapshot) repo.getPreparedRepositorySnapshot(cacheFolder -> {
				FileUtils.forceMkdir(cacheFolder);
				boolean refresh = isGitRepositoryInitialized(cacheFolder);
				try (Git git = getLocalGit(cacheFolder.getPath(), true);
					 Repository repository = git.getRepository()) {
					StoredConfig config = repository.getConfig();
					config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME,
							"url", repo.getRepoUrl());
					config.save();
					if (refresh) {
						fetchForSparseCheckout(git);
					}
					return captureSnapshot(cacheFolder, repository);
				}
			});
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	private GitSnapshot captureSnapshot(File folder, Repository repository) throws IOException, GitAPIException {
		Map<String, ObjectId> refs = new LinkedHashMap<>();
		captureRefs(repository, REFS_REMOTES_ORIGIN, refs);
		captureRefs(repository, REFS_TAGS, refs);
		if (retainedWorkingCopy != null) {
			// A session may have created or advanced local branches since the cache was captured.
			captureRefs(repository, REFS_HEADS, refs);
		}
		return new GitSnapshot(folder, refs, getDefaultBranchName());
	}

	private boolean isGitRepositoryInitialized(File checkoutDirectory) throws IOException {
		try (Repository repository = new FileRepositoryBuilder()
				.setGitDir(new File(checkoutDirectory, Constants.DOT_GIT))
				.build()) {
			return repository.getObjectDatabase().exists();
		}
	}

	private void captureRefs(Repository repository, String prefix, Map<String, ObjectId> target)
			throws IOException {
		String targetPrefix = REFS_HEADS.equals(prefix) ? REFS_REMOTES_ORIGIN : prefix;
		for (Ref ref : repository.getRefDatabase().getRefsByPrefix(prefix)) {
			if (!ref.isSymbolic() && ref.getObjectId() != null) {
				target.put(targetPrefix + ref.getName().substring(prefix.length()), ref.getObjectId().copy());
			}
		}
	}

	private void initializeMutationRepository(File workingCopyFolder, GitSnapshot snapshot) throws Exception {
		FileUtils.cleanDirectory(workingCopyFolder);
		try (Git git = Git.init().setDirectory(workingCopyFolder).call()) {
			Repository repository = git.getRepository();
			File preparedGitDirectory = new File(snapshot.repositoryFolder, Constants.DOT_GIT);
			File alternates = new File(repository.getDirectory(), "objects/info/alternates");
			FileUtils.forceMkdir(alternates.getParentFile());
			FileUtils.writeStringToFile(alternates,
					new File(preparedGitDirectory, Constants.OBJECTS).getCanonicalPath().replace('\\', '/')
							+ "\n", StandardCharsets.UTF_8);

			StoredConfig config = repository.getConfig();
			config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, "longpaths", true);
			config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME,
					"url", repo.getRepoUrl());
			config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME,
					"fetch", "+refs/heads/*:" + REFS_REMOTES_ORIGIN + "*");
			config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_EOL, CoreConfig.EOL.LF);
			config.save();

			for (Map.Entry<String, ObjectId> ref : snapshot.refs.entrySet()) {
				RefUpdate update = repository.updateRef(ref.getKey());
				update.setNewObjectId(ref.getValue());
				update.setForceUpdate(true);
				RefUpdate.Result result = update.forceUpdate();
				if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED
						&& result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NO_CHANGE) {
					throw new IOException("Could not seed Git ref " + ref.getKey() + ": " + result);
				}
			}
		}

		String componentSubfolder = normalizeComponentSubfolder(repo.getComponentSubfolder());
		if (!componentSubfolder.isEmpty()) {
			runGitCommand(workingCopyFolder, "sparse-checkout", "init", "--cone");
			runGitCommand(workingCopyFolder, "sparse-checkout", "set", "--", componentSubfolder);
		}
		runGitCommand(workingCopyFolder, "checkout", "-B", snapshot.defaultBranchName,
				REFS_REMOTES_ORIGIN + snapshot.defaultBranchName);
	}

	private File componentWorkingDirectory(File repositoryRoot) throws IOException {
		String componentSubfolder = normalizeComponentSubfolder(repo.getComponentSubfolder());
		File componentDirectory = componentSubfolder.isEmpty()
				? repositoryRoot
				: new File(repositoryRoot, componentSubfolder.replace('/', File.separatorChar));
		String rootPath = repositoryRoot.getCanonicalPath();
		String componentPath = componentDirectory.getCanonicalPath();
		if (!componentPath.equals(rootPath) && !componentPath.startsWith(rootPath + File.separator)) {
			throw new IOException("Component subfolder escapes the Git working copy: " + componentSubfolder);
		}
		return componentDirectory;
	}

	private String normalizeComponentSubfolder(String componentSubfolder) {
		String normalized = componentSubfolder == null ? "" : componentSubfolder.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	public VCSChangeType gitChangeTypeToVCSChangeType(ChangeType changeType) {
		switch (changeType) {
		case ADD:
			return VCSChangeType.ADD;
		case DELETE:
			return VCSChangeType.DELETE;
		case MODIFY:
			return VCSChangeType.MODIFY;
		default:
			return VCSChangeType.UNKNOWN;
		}
	}

	public VCSTag createUnannotatedTag(String branchName, String tagName, String revisionToTag) {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			if (retainedWorkingCopy == null) {
				git.pull().setCredentialsProvider(credentials).call();
			}

			RevCommit commitToTag = revisionToTag == null ? null : rw.parseCommit(ObjectId.fromString(revisionToTag));

			Ref ref = git
					.tag()
					.setAnnotated(false)
					.setName(tagName)
					.setObjectId(commitToTag)
					.call();

			push(git, new RefSpec(ref.getName()));

			return new VCSTag(tagName, null, null, revisionToTag == null ? getHeadCommit(branchName)
					: getVCSCommit(commitToTag));
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void createBranch(String srcBranchName, String newBranchName, String commitMessage) {
		// note: no commit message could be attached in Git
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, srcBranchName, null);

			git
					.branchCreate()
					.setUpstreamMode(SetupUpstreamMode.TRACK)
					.setName(newBranchName)
					.call();

			RefSpec refSpec = new RefSpec().setSourceDestination(newBranchName,
					newBranchName);

			push(git, refSpec);
		} catch (RefAlreadyExistsException e) {
			throw new EVCSBranchExists(newBranchName);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteBranch(String branchName, String commitMessage) {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, null, null);

			git
					.branchDelete()
					.setBranchNames(branchName)
					.setForce(true) // avoid "not merged" exception
					.call();

			RefSpec refSpec = new RefSpec(":refs/heads/" + branchName);

			push(git, refSpec);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	void push(Git git, RefSpec refSpec) throws GitAPIException {
		PushCommand cmd = git
				.push();
		if (refSpec != null) {
			cmd.setRefSpecs(refSpec);
		} else {
			cmd.setPushAll();
		}
		cmd
				.setRemote("origin")
				.setCredentialsProvider(credentials)
				.setTimeout(TRANSPORT_TIMEOUT_SECONDS)
				.call();
		repo.invalidatePreparedRepositorySnapshot();
	}

	@Override
	public VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage) {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, dstBranchName, null);

			MergeResult mr = git
					.merge()
					.include(gitRepo.findRef("origin/" + getRealBranchName(srcBranchName)))
					.setMessage(commitMessage)
					.call();

			Boolean success =
					!mr.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING) &&
					!mr.getMergeStatus().equals(MergeResult.MergeStatus.FAILED) &&
					!mr.getMergeStatus().equals(MergeResult.MergeStatus.ABORTED) &&
					!mr.getMergeStatus().equals(MergeResult.MergeStatus.NOT_SUPPORTED);

			List<String> conflictingFiles = new ArrayList<>();
			if (!success) {
				conflictingFiles.addAll(mr.getConflicts().keySet());
				try {
					git
							.reset()
							.setMode(ResetType.HARD)
							.call();
				} catch(Exception e) {
					wc.setCorrupted(true);
				}
			} else {
				push(git, null);
			}
			return new VCSMergeResult(success, conflictingFiles);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setCredentials(String user, String password) {
		setCredentials(new UsernamePasswordCredentialsProvider(user, password));
	}

	@Override
	public void setProxy(final String host, final int port, final String proxyUser, final String proxyPassword) {
		ProxySelector.setDefault(new ProxySelector() {

			final ProxySelector delegate = ProxySelector.getDefault();

			@Override
			public List<Proxy> select(URI uri) {
				if (uri.toString().toLowerCase().contains(repo.getRepoUrl().toLowerCase())) {
					return Collections.singletonList(new Proxy(Type.HTTP, InetSocketAddress
							.createUnresolved(host, port)));
				} else {
					return delegate == null ? Collections.singletonList(Proxy.NO_PROXY)
			                : delegate.select(uri);
				}
			}

			@Override
			public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
				if (delegate != null) {
					delegate.connectFailed(uri, sa, ioe);
				}
			}
		});
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				if (super.getRequestingSite().getHostName().contains(repo.getRepoUrl()) &&
						super.getRequestingPort() == port) {
					return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
				}
				return super.getPasswordAuthentication();
			}
		});
	}

	@Override
	public String getRepoUrl() {
		return repo.getRepoUrl();
	}

	private Repository openSnapshotRepository(GitSnapshot snapshot) throws IOException {
		return new FileRepositoryBuilder()
				.setGitDir(new File(snapshot.repositoryFolder, Constants.DOT_GIT))
				.build();
	}

	private String snapshotBranchName(GitSnapshot snapshot, String branchName) {
		return branchName == null ? snapshot.defaultBranchName : branchName;
	}

	private ObjectId snapshotBranchId(GitSnapshot snapshot, String branchName) {
		return snapshot.refs.get(REFS_REMOTES_ORIGIN + snapshotBranchName(snapshot, branchName));
	}

	private ObjectId snapshotRevision(Repository repository, GitSnapshot snapshot, String branchName,
			String revision) throws IOException {
		if (revision != null) {
			return repository.resolve(revision + "^{commit}");
		}
		return snapshotBranchId(snapshot, branchName);
	}

	@Override
	public String getFileContent(String branchName, String fileRelativePath, String revision) {
		return getFilesContent(branchName, Collections.singletonList(fileRelativePath), revision)
				.get(fileRelativePath);
	}

	@Override
	public Map<String, String> getFilesContent(String branchName, List<String> filePaths,
			String revision) {
		GitSnapshot snapshot = getReadSnapshot();
		Map<String, String> fileContents = new LinkedHashMap<>();
		String realBranchName = snapshotBranchName(snapshot, branchName);
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			ObjectId commitId = snapshotRevision(repository, snapshot, branchName, revision);
			if (commitId == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), realBranchName);
			}
			RevTree tree = walk.parseCommit(commitId).getTree();
			for (String filePath : filePaths) {
				if (fileContents.containsKey(filePath)) {
					continue;
				}
				try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, tree)) {
					if (treeWalk == null) {
						throw new EVCSFileNotFound(getRepoUrl(), realBranchName, filePath, revision);
					}
					ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
					try (InputStream input = loader.openStream()) {
						fileContents.put(filePath, IOUtils.toString(input, StandardCharsets.UTF_8));
					}
				}
			}
			return fileContents;
		} catch (EVCSFileNotFound | EVCSBranchNotFound e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String dstBranchName) {
		GitSnapshot snapshot = getReadSnapshot();
		try (Repository repository = openSnapshotRepository(snapshot);
			 ObjectReader reader = repository.newObjectReader();
			 RevWalk walk = new RevWalk(repository)) {
			ObjectId source = snapshotBranchId(snapshot, srcBranchName);
			ObjectId destination = snapshotBranchId(snapshot, dstBranchName);
			if (source == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), snapshotBranchName(snapshot, srcBranchName));
			}
			if (destination == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), snapshotBranchName(snapshot, dstBranchName));
			}
			Merger merger = MergeStrategy.RESOLVE.newMerger(repository, true);
			boolean merged = merger.merge(destination, source);
			CanonicalTreeParser oldTree = new CanonicalTreeParser();
			oldTree.reset(reader, walk.parseCommit(destination).getTree());
			CanonicalTreeParser newTree = new CanonicalTreeParser();
			// A conflicting in-core merge has no synthetic result tree. In that case the
			// source tree still represents the complete branch delta without touching a
			// working tree, which matches the former checkout-based diff result.
			newTree.reset(reader, merged && merger.getResultTreeId() != null
					? merger.getResultTreeId()
					: walk.parseCommit(source).getTree());
			List<VCSDiffEntry> result = new ArrayList<>();
			try (DiffFormatter scanner = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
				scanner.setRepository(repository);
				for (DiffEntry entry : scanner.scan(oldTree, newTree)) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					try (DiffFormatter formatter = new DiffFormatter(output)) {
						formatter.setRepository(repository);
						formatter.format(entry);
					}
					result.add(new VCSDiffEntry(
							entry.getPath(entry.getChangeType() == ChangeType.ADD ? Side.NEW : Side.OLD),
							gitChangeTypeToVCSChangeType(entry.getChangeType()),
							output.toString(StandardCharsets.UTF_8.name())));
				}
			}
			return result;
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public Set<String> getBranches(String path) {
		GitSnapshot snapshot = getReadSnapshot();
		String prefix = path == null ? "" : path;
		Set<String> result = new HashSet<>();
		for (String refName : snapshot.refs.keySet()) {
			if (refName.startsWith(REFS_REMOTES_ORIGIN)) {
				String branch = refName.substring(REFS_REMOTES_ORIGIN.length());
				if (branch.startsWith(prefix)) {
					result.add(branch);
				}
			}
		}
		return result;
	}

	@Override
	public List<VCSCommit> log(String branchName, int limit) {
		GitSnapshot snapshot = getReadSnapshot();
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			ObjectId head = snapshotBranchId(snapshot, branchName);
			if (head == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), snapshotBranchName(snapshot, branchName));
			}
			walk.markStart(walk.parseCommit(head));
			List<VCSCommit> result = new ArrayList<>();
			for (RevCommit commit : walk) {
				result.add(getVCSCommit(commit));
				if (limit > 0 && result.size() >= limit) {
					break;
				}
			}
			return result;
		} catch (EVCSBranchNotFound e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public List<VCSCommit> getCommitsRange(String branchName, String startRevision,
			String endRevision) {
		GitSnapshot snapshot = getReadSnapshot();
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			ObjectId head = snapshotBranchId(snapshot, branchName);
			if (head == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), snapshotBranchName(snapshot, branchName));
			}
			ObjectId start = startRevision == null
					? getInitialCommit(repository, head).getId()
					: ObjectId.fromString(startRevision);
			ObjectId end = endRevision == null ? head : ObjectId.fromString(endRevision);
			walk.markStart(walk.parseCommit(end));
			walk.markUninteresting(walk.parseCommit(start));
			List<VCSCommit> result = new ArrayList<>();
			for (RevCommit commit : walk) {
				result.add(getVCSCommit(commit));
			}
			Collections.reverse(result);
			return result;
		} catch (EVCSBranchNotFound e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public List<VCSCommit> getCommitsRange(String branchName, String startRevision,
			WalkDirection direction, int limit, String repositoryRelativePath) {
		GitSnapshot snapshot = getReadSnapshot();
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			ObjectId head = snapshotBranchId(snapshot, branchName);
			if (head == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), snapshotBranchName(snapshot, branchName));
			}
			RevCommit traversalStart;
			if (direction == WalkDirection.ASC) {
				traversalStart = walk.parseCommit(head);
				if (startRevision != null) {
					RevCommit cursor = walk.parseCommit(ObjectId.fromString(startRevision));
					for (RevCommit parent : cursor.getParents()) {
						walk.markUninteresting(parent);
					}
				}
			} else {
				traversalStart = walk.parseCommit(startRevision == null
						? head : ObjectId.fromString(startRevision));
			}
			if (repositoryRelativePath != null && !repositoryRelativePath.isEmpty()) {
				walk.setTreeFilter(AndTreeFilter.create(
						PathFilter.create(repositoryRelativePath), TreeFilter.ANY_DIFF));
			}
			walk.markStart(traversalStart);
			List<VCSCommit> result = new ArrayList<>();
			for (RevCommit commit : walk) {
				result.add(getVCSCommit(commit));
				if (direction == WalkDirection.DESC && limit > 0 && result.size() >= limit) {
					break;
				}
			}
			if (direction == WalkDirection.ASC) {
				Collections.reverse(result);
			}
			if (limit > 0 && result.size() > limit) {
				return new ArrayList<>(result.subList(0, limit));
			}
			return result;
		} catch (EVCSBranchNotFound e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public VCSCommit getHeadCommit(String branchName) {
		GitSnapshot snapshot = getReadSnapshot();
		ObjectId head = snapshotBranchId(snapshot, branchName);
		if (head == null) {
			return null;
		}
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			return getVCSCommit(walk.parseCommit(head));
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public Boolean fileExists(String branchName, String filePath) {
		GitSnapshot snapshot = getReadSnapshot();
		ObjectId head = snapshotBranchId(snapshot, branchName);
		if (head == null) {
			return false;
		}
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			try (TreeWalk tree = TreeWalk.forPath(repository, filePath, walk.parseCommit(head).getTree())) {
				return tree != null;
			}
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	private List<VCSTag> getTags(String revision) {
		GitSnapshot snapshot = getReadSnapshot();
		try (Repository repository = openSnapshotRepository(snapshot);
			 RevWalk walk = new RevWalk(repository)) {
			List<VCSTag> result = new ArrayList<>();
			for (Map.Entry<String, ObjectId> capturedRef : snapshot.refs.entrySet()) {
				if (!capturedRef.getKey().startsWith(REFS_TAGS)) {
					continue;
				}
				RevCommit related = walk.parseCommit(capturedRef.getValue());
				if (revision != null && !revision.equals(related.getName())) {
					continue;
				}
				VCSCommit relatedCommit = getVCSCommit(related);
				RevObject object = walk.parseAny(capturedRef.getValue());
				if (object instanceof RevTag) {
					RevTag tag = (RevTag) object;
					result.add(new VCSTag(tag.getTagName(), tag.getFullMessage(),
							tag.getTaggerIdent().getName(), relatedCommit));
				} else {
					result.add(new VCSTag(capturedRef.getKey().substring(REFS_TAGS.length()),
							null, null, relatedCommit));
				}
			}
			return result;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList) {
		if (vcsChangeList.isEmpty()) {
			return null;
		}
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
				 Git git = getLocalGit(wc);
				 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, branchName, null);
			CommitCommand commitCommand = git.commit();
			StringBuilder commitMessageSB = new StringBuilder();
			for (VCSChangeListNode vcsChangeListNode : vcsChangeList) {
				String filePath = vcsChangeListNode.getFilePath();
				File file = new File(wc.getFolder(), filePath);
				if (!file.exists()) {
					FileUtils.forceMkdir(file.getParentFile());
					file.createNewFile();
					git
							.add()
							.addFilepattern(filePath)
							.call();
				}

				try (FileWriter fw = new FileWriter(file, false)) {
					fw.write(vcsChangeListNode.getContent());
				}
				commitCommand.setOnly(filePath);
				commitMessageSB.append(vcsChangeListNode.getLogMessage() + VCSChangeListNode.COMMIT_MESSAGES_SEPARATOR);
			}
			commitMessageSB.setLength(commitMessageSB.length() - VCSChangeListNode.COMMIT_MESSAGES_SEPARATOR.length());
			RevCommit newCommit = commitCommand
					.setMessage(commitMessageSB.toString())
					.call();

			String bn = getRealBranchName(branchName);
			RefSpec refSpec = new RefSpec(bn + ":" + bn);
			push(git, refSpec);
			return getVCSCommit(newCommit);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage) {
		return setFileContent(branchName, Collections.singletonList(new VCSChangeListNode(filePath, content, commitMessage)));
	}

	private void checkout(Git git, Repository gitRepo, String branchName, String revision) throws Exception {
		String bn = getRealBranchName(branchName);
		if (retainedWorkingCopy != null) {
			// JGit checkout does not preserve native cone-mode sparse checkout on branch changes.
			if (revision == null) {
				String source = gitRepo.exactRef(REFS_HEADS + bn) == null
						? REFS_REMOTES_ORIGIN + bn : REFS_HEADS + bn;
				runGitCommand(retainedWorkingCopy.getFolder(), "checkout", "-B", bn, source);
			} else {
				runGitCommand(retainedWorkingCopy.getFolder(), "checkout", "--detach", revision);
			}
			gitRepo.scanForRepoChanges();
			return;
		}
		CheckoutCommand cmd = git.checkout();

		if (retainedWorkingCopy == null) {
			pullAndFetch(git);
		}

		if (revision == null) {
			if (gitRepo.exactRef(REFS_HEADS + bn) == null) {
				cmd.setStartPoint("origin/" + bn)
						.setCreateBranch(true)
						.setUpstreamMode(SetupUpstreamMode.TRACK)
						.setName(bn)
						.call();
			} else {
				cmd.setName(bn).call();
			}

		} else {
			try (RevWalk walk = new RevWalk(gitRepo)) {
				RevCommit commit = walk.parseCommit(RevCommit.fromString(revision));
				// note: entering "detached HEAD" state here
				cmd
						.setName(commit.getName())
						.call();
			}
		}
	}

	private void pullAndFetch(Git git) throws GitAPIException, WrongRepositoryStateException,
			InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException,
			RefNotFoundException, RefNotAdvertisedException, NoHeadException, TransportException {
		if (retainedWorkingCopy != null) {
			return;
		}
		Utils.runWithRetry("Git pull", () -> git
					.pull()
					.setCredentialsProvider(credentials)
					.setTimeout(TRANSPORT_TIMEOUT_SECONDS)
					.call(), GitVCS::isTransientTransportFailure, retryStatusReporter);

		// remove local branches and tags which are not exists on remote
		// See https://github.com/scm4j/scm4j-releaser/issues/59
		// if executed first then version is considered as modified. So have uncommited change: 19.5-SNAPSHOT -> 18.5-SNAPSHOT
		fetch(git,
				new RefSpec("+refs/heads/*:refs/heads/*"),
				new RefSpec("+refs/tags/*:refs/tags/*"));
	}

	private void fetch(Git git, RefSpec... refSpecs) throws GitAPIException {
		Utils.runWithRetry("Git fetch", () -> git
				.fetch()
				.setRefSpecs(refSpecs)
				.setRemoveDeletedRefs(true)
				.setCredentialsProvider(credentials)
				.setTimeout(TRANSPORT_TIMEOUT_SECONDS)
				.call(), GitVCS::isTransientTransportFailure, retryStatusReporter);
	}

	static boolean isTransientTransportFailure(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SocketException || cause instanceof SocketTimeoutException
					|| cause instanceof EOFException) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getVCSTypeString() {
		return GIT_VCS_TYPE_STRING;
	}

	@Override
	public VCSCommit removeFile(String branchName, String filePath, String commitMessage) {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, branchName, null);

			git
					.rm()
					.addFilepattern(filePath)
					.setCached(false)
					.call();

			RevCommit res = git
					.commit()
					.setMessage(commitMessage)
					.setAll(true)
					.call();

			push(git, null);
			return getVCSCommit(res);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private VCSCommit getVCSCommit(RevCommit revCommit) {
		return new VCSCommit(revCommit.getName(), revCommit.getFullMessage(), revCommit.getAuthorIdent().getName());
	}

	private RevCommit getInitialCommit(Repository repository, ObjectId head) throws IOException {
		try (RevWalk walk = new RevWalk(repository)) {
			RevCommit root = walk.parseCommit(head);
			walk.markStart(root);
			walk.sort(RevSort.REVERSE);
			return walk.next();
		}
	}

	@Override
	public String toString() {
		return "GitVCS [url=" + repo.getRepoUrl() + "]";
	}

	@Override
	public VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			pullAndFetch(git);

			checkout(git, gitRepo, branchName, null);

			RevCommit commitToTag = revisionToTag == null ? null : rw.parseCommit(ObjectId.fromString(revisionToTag));

			Ref ref = git
					.tag()
					.setAnnotated(true)
					.setMessage(tagMessage)
					.setName(tagName)
					.setObjectId(commitToTag)
					.call();

			push(git, new RefSpec(ref.getName()));

			RevTag revTag = rw.parseTag(ref.getObjectId());
			RevCommit revCommit = rw.parseCommit(ref.getObjectId());
			VCSCommit relatedCommit = getVCSCommit(revCommit);
			return new VCSTag(revTag.getTagName(), revTag.getFullMessage(), revTag.getTaggerIdent().getName(), relatedCommit);
		} catch(RefAlreadyExistsException e) {
			throw new EVCSTagExists(e);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<VCSTag> getTags() {
		return getTags(null);
	}

	@Override
	public void removeTag(String tagName) {
		try (IVCSLockedWorkingCopy wc = getWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			pullAndFetch(git);

			git
					.tagDelete()
					.setTags(tagName)
					.call();

			push(git, new RefSpec(":refs/tags/" + tagName));

		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void checkout(String branchName, String targetPath, String revision)  {
		try (Git git = getLocalGit(targetPath);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, branchName, revision);

		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void sparseCheckout(String branchName, String targetPath, String revision,
			String repositoryRelativeDirectory) {
		try (Git git = getLocalGit(targetPath, true);
			Repository gitRepo = git.getRepository()) {
			fetchForSparseCheckout(git);
			String realBranchName = getRealBranchName(branchName);
			String selectedRevision = revision == null
					? REFS_REMOTES_ORIGIN + realBranchName
					: revision;
			ObjectId selectedCommit = gitRepo.resolve(selectedRevision + "^{commit}");
			if (selectedCommit == null) {
				throw new EVCSException("Could not resolve Git revision " + selectedRevision);
			}

			File checkoutDir = new File(targetPath);
			runGitCommand(checkoutDir, "sparse-checkout", "init", "--cone");
			runGitCommand(checkoutDir, "sparse-checkout", "set", "--", repositoryRelativeDirectory);
			if (revision == null) {
				runGitCommand(checkoutDir, "checkout", "-B", realBranchName,
						REFS_REMOTES_ORIGIN + realBranchName);
			} else {
				runGitCommand(checkoutDir, "checkout", "--detach", selectedCommit.name());
			}
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	private void fetchForSparseCheckout(Git git) throws GitAPIException {
		fetch(git,
				new RefSpec("+refs/heads/*:" + REFS_REMOTES_ORIGIN + "*"),
				new RefSpec("+refs/tags/*:refs/tags/*"));
	}

	private void runGitCommand(File workingDirectory, String... arguments) throws IOException {
		Process process = startGitProcess(workingDirectory, arguments);
		String output;
		try (InputStream input = process.getInputStream()) {
			output = IOUtils.toString(input, StandardCharsets.UTF_8);
		}
		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for native Git", e);
		}
		if (exitCode != 0) {
			throw new IOException("Native Git exited with code " + exitCode + ": " + output.trim());
		}
	}

	Process startGitProcess(File workingDirectory, String... arguments) throws IOException {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.addAll(Arrays.asList(arguments));
		return new ProcessBuilder(command)
				.directory(workingDirectory)
				.redirectErrorStream(true)
				.start();
	}

	@Override
	public List<VCSTag> getTagsOnRevision(String revision) {
		return getTags(revision);
	}

	private static final class GitSnapshot implements IVCSRepositoryWorkspace.PreparedRepositorySnapshot {
		private final File repositoryFolder;
		private final Map<String, ObjectId> refs;
		private final String defaultBranchName;

		private GitSnapshot(File repositoryFolder, Map<String, ObjectId> refs, String defaultBranchName) {
			this.repositoryFolder = repositoryFolder;
			this.refs = Collections.unmodifiableMap(new LinkedHashMap<>(refs));
			this.defaultBranchName = defaultBranchName;
		}
	}

	private static class GitMutationSession extends GitVCS implements IVCSMutationSession {
		private final IVCSLockedWorkingCopy workingCopy;
		private final File workingDirectory;
		private boolean closed;

		private GitMutationSession(GitVCS owner, IVCSLockedWorkingCopy workingCopy,
				File workingDirectory, String defaultBranchName) {
			super(owner.repo, workingCopy);
			this.workingCopy = workingCopy;
			this.workingDirectory = workingDirectory;
			((GitVCS) this).credentials = owner.credentials;
			((GitVCS) this).defaultBranchName = defaultBranchName;
			((GitVCS) this).retryStatusReporter = owner.retryStatusReporter;
		}

		@Override
		public IVCSMutationSession startModify() {
			throw new IllegalStateException("A Git mutation session cannot open another mutation session");
		}

		@Override
		public File getWorkingDirectory() {
			return workingDirectory;
		}

		@Override
		public void checkout(String branchName, String targetPath, String revision) {
			super.checkout(branchName, workingDirectory.equals(new File(targetPath))
					? workingCopy.getFolder().getPath() : targetPath, revision);
		}

		@Override
		public void setCorrupted(Boolean corrupted) {
			workingCopy.setCorrupted(corrupted);
		}

		@Override
		public synchronized void close() {
			if (!closed) {
				closed = true;
				try {
					workingCopy.close();
				} catch (Exception e) {
					throw new EVCSException(e);
				}
			}
		}
	}

}
