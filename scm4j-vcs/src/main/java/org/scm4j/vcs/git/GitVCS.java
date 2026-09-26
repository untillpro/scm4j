package org.scm4j.vcs.git;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedRunnable;
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
import org.scm4j.vcs.api.*;
import org.scm4j.vcs.api.exceptions.*;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;

import java.io.*;
import java.net.*;
import java.net.Proxy.Type;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;

public class GitVCS implements IVCS {

	public static final String GIT_VCS_TYPE_STRING = "git";
	private static final String REFS_REMOTES_ORIGIN = Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/";
	private static final String REFS_HEADS = Constants.R_HEADS;
	private static final String REFS_TAGS = Constants.R_TAGS;
	private CredentialsProvider credentials;
	private final IVCSRepositoryWorkspace repo;
	private String defaultBranchName;
	private BiConsumer<String, Throwable> retryStatusReporter = (operation, failure) -> {};

	public CredentialsProvider getCredentials() {
		return credentials;
	}

	public GitVCS(IVCSRepositoryWorkspace repo) {
		this.repo = repo;
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
		boolean repoInited;
		try (Repository gitRepo = new FileRepositoryBuilder()
				.setGitDir(new File(checkoutDir, ".git"))
				.build()) {
			repoInited = gitRepo.getObjectDatabase().exists();
		}
		if (!repoInited) {
			Git
					.cloneRepository()
					.setDirectory(checkoutDir)
					.setURI(repo.getRepoUrl())
					.setCredentialsProvider(credentials)
					.setNoCheckout(noCheckout)
					.call()
					.close();
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
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			git
					.pull()
					.setCredentialsProvider(credentials)
					.call();

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
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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
				.call();
	}

	@Override
	public VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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

	@Override
	public String getFileContent(String branchName, String fileRelativePath, String revision) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk revWalk = new RevWalk(gitRepo);
			 TreeWalk treeWalk = new TreeWalk(gitRepo)) {

			pullAndFetch(git);

			ObjectId revisionCommitId = gitRepo.resolve(revision == null ? REFS_HEADS + getRealBranchName(branchName) : revision);
			if (revision == null && revisionCommitId == null) {
				throw new EVCSBranchNotFound(getRepoUrl(), getRealBranchName(branchName));
			}

			RevCommit commit = revWalk.parseCommit(revisionCommitId);
			RevTree tree = commit.getTree();
			treeWalk.addTree(tree);
			treeWalk.setRecursive(true);
			treeWalk.setFilter(PathFilter.create(fileRelativePath));
			if (!treeWalk.next()) {
				throw new EVCSFileNotFound(getRepoUrl(), getRealBranchName(branchName), fileRelativePath, revision);
			}
			ObjectId objectId = treeWalk.getObjectId(0);

			ObjectLoader loader = gitRepo.open(objectId);
			InputStream in = loader.openStream();
			String res = IOUtils.toString(in, StandardCharsets.UTF_8);

			if (revision != null) {
				// need to prevent "checkout conflict with files" exception on scm4j-releaser testTagExistsOnExecute() test
				git
						.reset()
						.setMode(ResetType.HARD)
						.call();
			}
			return res;
		} catch(EVCSFileNotFound | EVCSBranchNotFound e) {
			throw e;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList) {
		if (vcsChangeList.isEmpty()) {
			return null;
		}
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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
		CheckoutCommand cmd = git.checkout();

		pullAndFetch(git);

		if (revision == null) {
			cmd
					.setStartPoint("origin/" + bn)
					.setCreateBranch(gitRepo.exactRef(REFS_HEADS + bn) == null)
					.setUpstreamMode(SetupUpstreamMode.TRACK)
					.setName(bn)
					.call();

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
		runWithTransportRetry("Git pull", () -> git
					.pull()
					.setCredentialsProvider(credentials)
					.call());

		// remove local branches and tags which are not exists on remote
		// See https://github.com/scm4j/scm4j-releaser/issues/59
		// if executed first then version is considered as modified. So have uncommited change: 19.5-SNAPSHOT -> 18.5-SNAPSHOT
		fetch(git,
				new RefSpec("+refs/heads/*:refs/heads/*"),
				new RefSpec("+refs/tags/*:refs/tags/*"));
	}

	private void fetch(Git git, RefSpec... refSpecs) throws GitAPIException {
		runWithTransportRetry("Git fetch", () -> git
				.fetch()
				.setRefSpecs(refSpecs)
				.setRemoveDeletedRefs(true)
				.setCredentialsProvider(credentials)
				.call());
	}

	private void runWithTransportRetry(String operation, CheckedRunnable command) throws GitAPIException {
		try {
			Failsafe.with(RetryPolicy.builder()
					.handleIf(GitVCS::isTransientTransportFailure)
					.withBackoff(500, 2000, ChronoUnit.MILLIS)
					.withJitter(.25)
					.withMaxRetries(10)
					.onRetryScheduled(event -> retryStatusReporter.accept(operation, event.getLastException()))
					.build()).run(command);
		} catch (FailsafeException e) {
			if (e.getCause() instanceof GitAPIException) {
				throw (GitAPIException) e.getCause();
			}
			throw e;
		}
	}

	private static boolean isTransientTransportFailure(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SocketException || cause instanceof EOFException) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String dstBranchName) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk walk = new RevWalk(gitRepo)) {

			// https://stackoverflow.com/questions/34025577/jgit-how-to-show-changed-files-in-merge-commit

			String srcBN = getRealBranchName(srcBranchName);
			String dstBN = getRealBranchName(dstBranchName);

			RevCommit destHeadCommit = walk.parseCommit(git.getRepository().resolve("remotes/origin/" + dstBN));

			ObjectReader reader = gitRepo.newObjectReader();

			checkout(git, gitRepo, dstBranchName, null);

			git
					.merge()
					.include(gitRepo.findRef("origin/" + srcBN))
					.setCommit(false)
					.call();

			CanonicalTreeParser srcTreeIter = new CanonicalTreeParser();
			srcTreeIter.reset(reader, destHeadCommit.getTree());

			List<DiffEntry> diffs = git
					.diff()
					.setOldTree(srcTreeIter)
					.call();

			List<VCSDiffEntry> res = new ArrayList<>();
			for (DiffEntry diffEntry : diffs) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (DiffFormatter formatter = new DiffFormatter(baos)) {
					formatter.setRepository(git.getRepository());
					formatter.format(diffEntry);
				}
				VCSDiffEntry vcsEntry = new VCSDiffEntry(
						diffEntry.getPath(diffEntry.getChangeType() == ChangeType.ADD ? Side.NEW : Side.OLD),
						gitChangeTypeToVCSChangeType(diffEntry.getChangeType()),
						baos.toString("UTF-8"));
				res.add(vcsEntry);
			}
			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Set<String> getBranches(String path) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			pullAndFetch(git);

			Collection<Ref> refs = gitRepo.getRefDatabase().getRefsByPrefix(REFS_REMOTES_ORIGIN);
			Set<String> res = new HashSet<>();
			String bn;
			for (Ref ref : refs) {
				if (ref.isSymbolic()) {
					continue;
				}
				bn = ref.getName().replace(REFS_REMOTES_ORIGIN, "");
				if (bn.startsWith(path == null ? "" : path)) {
					res.add(bn);
				}
			}
			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<VCSCommit> log(String branchName, int limit) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			LogCommand log = git
					.log()
					.add(gitRepo.resolve(REFS_REMOTES_ORIGIN + getRealBranchName(branchName)));

			if (limit > 0) {
				log.setMaxCount(limit);
			}

			Iterable<RevCommit> commits = log.call();

			List<VCSCommit> res = new ArrayList<>();
			for (RevCommit commit : commits) {
				res.add(getVCSCommit(commit));
			}

			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getVCSTypeString() {
		return GIT_VCS_TYPE_STRING;
	}

	@Override
	public VCSCommit removeFile(String branchName, String filePath, String commitMessage) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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

	public List<VCSCommit> getCommitsRange(String branchName, String startRevision, String endRevision) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, branchName, null);

			String bn = getRealBranchName(branchName);

			ObjectId startCommit = startRevision == null ?
					getInitialCommit(gitRepo, bn).getId() :
					ObjectId.fromString(startRevision);

			ObjectId endCommit = endRevision == null ?
					gitRepo.exactRef(REFS_HEADS + bn).getObjectId() :
					ObjectId.fromString(endRevision);

			Iterable<RevCommit> commits;
			commits = git
					.log()
					.addRange(startCommit, endCommit)
					.call();

			List<VCSCommit> res = new ArrayList<>();
			for (RevCommit commit : commits) {
				VCSCommit vcsCommit = getVCSCommit(commit);
				res.add(vcsCommit);
			}

			Collections.reverse(res);
			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private RevCommit getInitialCommit(Repository gitRepo, String branchName) throws Exception {
		try (RevWalk rw = new RevWalk(gitRepo)) {
			Ref ref = gitRepo.exactRef(REFS_HEADS + branchName);
			ObjectId headCommitId = ref.getObjectId();
			RevCommit root = rw.parseCommit(headCommitId);
			rw.markStart(root);
			rw.sort(RevSort.REVERSE);
			return rw.next();
		}
	}

	@Override
	public List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction,
										   int limit, String repositoryRelativePath) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			checkout(git, gitRepo, branchName, null);
			String bn = getRealBranchName(branchName);

			List<VCSCommit> res = new ArrayList<>();
			RevCommit traversalStart;
			if (direction == WalkDirection.ASC) {
				ObjectId headCommitId = gitRepo.exactRef(REFS_REMOTES_ORIGIN + bn).getObjectId();
				traversalStart = rw.parseCommit(headCommitId);
				if (startRevision != null) {
					RevCommit cursor = rw.parseCommit(ObjectId.fromString(startRevision));
					for (RevCommit parent : cursor.getParents()) {
						rw.markUninteresting(parent);
					}
				}
			} else {
				ObjectId traversalStartId = startRevision == null ?
						gitRepo.exactRef(REFS_REMOTES_ORIGIN + bn).getObjectId() :
						ObjectId.fromString(startRevision);
				traversalStart = rw.parseCommit(traversalStartId);
			}

			if (repositoryRelativePath != null && !repositoryRelativePath.isEmpty()) {
				rw.setTreeFilter(AndTreeFilter.create(
						PathFilter.create(repositoryRelativePath), TreeFilter.ANY_DIFF));
			}
			rw.markStart(traversalStart);

			RevCommit commit = rw.next();
			while (commit != null) {
				VCSCommit vcsCommit = getVCSCommit(commit);
				res.add(vcsCommit);
				if (direction == WalkDirection.DESC && limit > 0 && res.size() >= limit) {
					break;
				}
				commit = rw.next();
			}

			if (direction == WalkDirection.ASC) {
				Collections.reverse(res);
			}
			if (limit > 0 && res.size() > limit) {
				res = res.subList(0, limit);
			}

			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VCSCommit getHeadCommit (String branchName) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			checkout(git, gitRepo, null, null);

			Ref ref = gitRepo.exactRef(REFS_REMOTES_ORIGIN + getRealBranchName(branchName));
			if (ref == null) {
				return null;
			}
			ObjectId commitId = ref.getObjectId();
			RevCommit revCommit = rw.parseCommit( commitId );
			return getVCSCommit(revCommit);
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return "GitVCS [url=" + repo.getRepoUrl() + "]";
	}

	@Override
	public Boolean fileExists(String branchName, String filePath) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository()) {

			checkout(git, gitRepo, branchName, null);

			return new File(wc.getFolder(), filePath).exists();
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			pullAndFetch(git);
			Collection<Ref> tagRefs = gitRepo.getRefDatabase().getRefsByPrefix(REFS_TAGS);
	        List<VCSTag> res = new ArrayList<>();
	        RevCommit revCommit;
	        for (Ref ref : tagRefs) {
	        	ObjectId relatedCommitObjectId = ref.getPeeledObjectId() == null ? ref.getObjectId() : ref.getPeeledObjectId();
	        	revCommit = rw.parseCommit(relatedCommitObjectId);
	        	VCSCommit relatedCommit = getVCSCommit(revCommit);
	        	RevObject revObject = rw.parseAny(ref.getObjectId());
	        	VCSTag tag;
	        	if (revObject instanceof RevTag) {
	        		RevTag revTag = (RevTag) revObject;
	        		tag = new VCSTag(revTag.getTagName(), revTag.getFullMessage(), revTag.getTaggerIdent().getName(), relatedCommit);
	        	} else  {
	        		// tag is unannotated
	        		tag = new VCSTag(ref.getName().replace(REFS_TAGS, ""), null, null, relatedCommit);
	        	}
	        	res.add(tag);
	        }
	        return res;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void removeTag(String tagName) {
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
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
		try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy();
			 Git git = getLocalGit(wc);
			 Repository gitRepo = git.getRepository();
			 RevWalk rw = new RevWalk(gitRepo)) {

			pullAndFetch(git);

			List<VCSTag> res = new ArrayList<>();

			// getAllRefsByPeeledObject does not work. Does not return newely created tag
			Collection<Ref> tagRefs = gitRepo.getRefDatabase().getRefsByPrefix(REFS_TAGS);

			RevCommit revCommit;
			for (Ref ref : tagRefs) {
				ObjectId relatedCommitObjectId = ref.getPeeledObjectId() == null ? ref.getObjectId() : ref.getPeeledObjectId();
	        	revCommit = rw.parseCommit(relatedCommitObjectId);
				if (revCommit.getName().equals(revision)) {
					VCSCommit relatedCommit = getVCSCommit(revCommit);
					RevObject revObject = rw.parseAny(ref.getObjectId());
					if (revObject instanceof RevTag) {
						RevTag revTag = (RevTag) revObject;
						res.add(new VCSTag(revTag.getTagName(), revTag.getFullMessage(), revTag.getTaggerIdent().getName(), relatedCommit));
					} else {
						res.add(new VCSTag(ref.getName().replace(REFS_TAGS, ""), null, null, relatedCommit));
					}
				}
			}

			return res;
		} catch (GitAPIException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


}
