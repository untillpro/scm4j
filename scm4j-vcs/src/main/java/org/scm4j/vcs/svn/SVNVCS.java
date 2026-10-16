package org.scm4j.vcs.svn;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.vcs.api.*;
import org.scm4j.vcs.api.exceptions.*;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnDiffSummarize;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

public class SVNVCS implements IVCS {
	private static final int SVN_PATH_IS_NOT_WORKING_COPY_ERROR_CODE = 155007;
	private static final int SVN_ITEM_EXISTS_ERROR_CODE = 160020;
	private static final int SVN_FILE_NOT_FOUND_ERROR_CODE = 160013;
	private static final Set<SVNErrorCode> TRANSIENT_SVN_ERROR_CODES = new HashSet<>(Arrays.asList(
			SVNErrorCode.RA_DAV_SOCK_INIT,
			SVNErrorCode.RA_DAV_CREATING_REQUEST,
			SVNErrorCode.RA_DAV_CONN_TIMEOUT,
			SVNErrorCode.RA_SVN_CONNECTION_CLOSED,
			SVNErrorCode.RA_SVN_IO_ERROR));

	public static final String MASTER_PATH= "trunk/";
	public static final String BRANCHES_PATH = "branches/";
	public static final String TAGS_PATH = "tags/";
	public static final String SVN_VCS_TYPE_STRING = "svn";

	private BasicAuthenticationManager authManager;
	private SVNRepository repository;
	private final ISVNOptions options;
	private SVNClientManager clientManager;
	private SVNURL trunkSVNUrl;
	private SVNAuthentication userPassAuth;
	private IVCSRepositoryWorkspace repo;
	private final IVCSLockedWorkingCopy retainedWorkingCopy;
	private String repoUrl;
	private BiConsumer<String, Throwable> retryStatusReporter = (operation, failure) -> {};

	public void setClientManager(SVNClientManager clientManager) {
		this.clientManager = clientManager;
	}

	public SVNClientManager getClientManager() {
		return clientManager;
	}
	
	public ISVNOptions getOptions() {
		return options;
	}

	public SVNURL getTrunkSVNUrl() {
		return trunkSVNUrl;
	}

	public SVNRepository getSVNRepository() {
		return repository;
	}

	public void setSVNRepository(SVNRepository repository) {
		this.repository = repository;
	}

	public void setRepo(IVCSRepositoryWorkspace repo) {
		this.repo = repo;
	}
	
	public SVNVCS(IVCSRepositoryWorkspace repo, String user, String password) {
		this(repo, user, password, null);
	}

	private SVNVCS(IVCSRepositoryWorkspace repo, String user, String password,
			IVCSLockedWorkingCopy retainedWorkingCopy) {
		this.repo = repo;
		this.retainedWorkingCopy = retainedWorkingCopy;
		repoUrl = repo.getRepoUrl().trim();
		if (!repoUrl.endsWith("/") && !repoUrl.endsWith("\\")) {
			repoUrl += "/";
		}
        options = SVNWCUtil.createDefaultOptions(true); 
		try {
			trunkSVNUrl = SVNURL.parseURIEncoded(repo.getRepoUrl().replace("\\", "/"));
			repository = SVNRepositoryFactory.create(trunkSVNUrl);
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
		
		userPassAuth = SVNPasswordAuthentication.newInstance(user, 
				(password == null ? null : password.toCharArray()), true, trunkSVNUrl, false);
		authManager = new BasicAuthenticationManager(new SVNAuthentication[] {userPassAuth});
		repository.setAuthenticationManager(authManager);
		
		clientManager = SVNClientManager.newInstance(
				options, repository.getAuthenticationManager());
	}

	private SVNVCS(SVNVCS owner, IVCSLockedWorkingCopy retainedWorkingCopy) throws SVNException {
		this.repo = owner.repo;
		this.retainedWorkingCopy = retainedWorkingCopy;
		this.repoUrl = owner.repoUrl;
		this.options = owner.options;
		this.authManager = owner.authManager;
		this.repository = owner.openReadRepository();
		this.clientManager = SVNClientManager.newInstance(options, authManager);
		this.trunkSVNUrl = owner.trunkSVNUrl;
		this.userPassAuth = owner.userPassAuth;
		this.retryStatusReporter = owner.retryStatusReporter;
	}

	@Override
	public IVCSMutationSession startModify() {
		try {
			return repo.withRepoLock(() -> {
				IVCSLockedWorkingCopy workingCopy = repo.getVCSLockedWorkingCopy();
				try {
					FileUtils.cleanDirectory(workingCopy.getFolder());
					// Defer checkout until a procedure selects its branch; trunk need not exist.
					return new SVNMutationSession(this, workingCopy,
							componentWorkingDirectory(workingCopy.getFolder()));
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

	
	public SVNURL getBranchUrl(String branchPath) throws SVNException {
		return SVNURL.parseURIEncoded(repoUrl + getBranchName(branchPath));
	}

	@Override
	public void createBranch(String srcBranchName, String dstBranchName, String commitMessage) throws EVCSBranchExists {
		try {
			SVNURL fromUrl = getBranchUrl(srcBranchName);
			SVNURL toUrl = getBranchUrl(dstBranchName);
			SVNCopyClient copyClient = clientManager.getCopyClient();
			SVNCopySource copySource = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, fromUrl);
			copySource.setCopyContents(false);
			copyClient.doCopy(new SVNCopySource[] { copySource }, toUrl,
					false, // isMove
					true, // make parents
					true, // failWhenDstExists
					commitMessage, // commit message
					null); // SVNProperties
			repo.invalidatePreparedRepositorySnapshot();

		} catch (SVNException e) {
		if (e.getErrorMessage().getErrorCode().getCode() == SVN_ITEM_EXISTS_ERROR_CODE) {
			throw new EVCSBranchExists(dstBranchName);
		}
		throw new EVCSException(e);
		}
	}
	
	@Override
	public void deleteBranch(String branchName, String commitMessage) {
		try {
			clientManager
					.getCommitClient()
					.doDelete(new SVNURL[] { getBranchUrl(branchName) }, commitMessage);
			repo.invalidatePreparedRepositorySnapshot();
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}
	
	@Override
	public VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage) {
		SVNDiffClient diffClient = clientManager.getDiffClient();
		try (IVCSLockedWorkingCopy wc = getWorkingCopy()) {
			checkout(getBranchUrl(dstBranchName), wc.getFolder(), null);

			DefaultSVNOptions options = (DefaultSVNOptions) diffClient.getOptions();
			final List<String> conflictingFiles = new ArrayList<>();
			options.setConflictHandler(conflictDescription -> {
				conflictingFiles.add(conflictDescription.getMergeFiles().getLocalPath());
				return new SVNConflictResult(SVNConflictChoice.POSTPONE,
						conflictDescription.getMergeFiles().getResultFile());
			});

			SVNRevisionRange range = new SVNRevisionRange(SVNRevision.create(1), SVNRevision.HEAD);
			try {
				diffClient.doMerge(getBranchUrl(srcBranchName),
						SVNRevision.HEAD, Collections.singleton(range),
						wc.getFolder(), SVNDepth.UNKNOWN, true, false, false, false);

				Boolean success = conflictingFiles.isEmpty();

				if (success) {
					clientManager
							.getCommitClient()
							.doCommit(new File[] {wc.getFolder()}, false, commitMessage,
							new SVNProperties(), null, true, true, SVNDepth.INFINITY);
					repo.invalidatePreparedRepositorySnapshot();
				} else {
					try {
						SVNWCClient wcClient = getRevertClient(options);
						wcClient.doRevert(new File[] {wc.getFolder()}, SVNDepth.INFINITY, null);
					} catch (Exception e) {
						// It doesn't matter if we failed to revert. Just make the workspace corrupted.
						wc.setCorrupted(true);
					}
				}
				return new VCSMergeResult(success, conflictingFiles);
			} catch (SVNException e) {
				wc.setCorrupted(true);
				throw e;
			}
		} catch (SVNException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	SVNWCClient getRevertClient(DefaultSVNOptions options) {
		return new SVNWCClient(authManager, options);
	}

	private void checkout(SVNURL sourceUrl, File destPath, String revision) throws SVNException {
		if (retainedWorkingCopy != null && retainedWorkingCopy.getFolder().equals(destPath)) {
			SVNRevision selected = revision == null ? SVNRevision.HEAD : SVNRevision.parse(revision);
			checkoutSparse(sourceUrl, destPath, selected, repo.getComponentSubfolder());
			return;
		}
		SVNUpdateClient updateClient = clientManager.getUpdateClient();
		updateClient.setIgnoreExternals(false);
		SVNRevision svnRevision = revision == null ? SVNRevision.HEAD : SVNRevision.parse(revision);
		if (isWorkingCopyInited(destPath)) {
			runWithTransportRetry("SVN switch", () -> updateClient.doSwitch(
					destPath, sourceUrl, svnRevision, svnRevision, SVNDepth.INFINITY, false, false));
		} else {
			runWithTransportRetry("SVN checkout", () -> updateClient.doCheckout(
					sourceUrl, destPath, svnRevision, svnRevision, SVNDepth.UNKNOWN, false));
		}
	}

	private void checkoutSparse(SVNURL sourceUrl, File target, SVNRevision revision,
			String repositoryRelativeDirectory) throws SVNException {
		SVNUpdateClient updateClient = clientManager.getUpdateClient();
		updateClient.setIgnoreExternals(false);
		String component = normalizeComponentSubfolder(repositoryRelativeDirectory);
		if (component.isEmpty()) {
			if (isWorkingCopyInited(target)) {
				runWithTransportRetry("SVN switch", () -> updateClient.doSwitch(
						target, sourceUrl, revision, revision, SVNDepth.INFINITY, false, false));
			} else {
				runWithTransportRetry("SVN checkout", () -> updateClient.doCheckout(
						sourceUrl, target, revision, revision, SVNDepth.INFINITY, false));
			}
			return;
		}
		if (isWorkingCopyInited(target)) {
			// Only fixed-scope sessions preserve sparse depths and local builder edits.
			boolean sessionTarget = retainedWorkingCopy != null && retainedWorkingCopy.getFolder().equals(target);
			runWithTransportRetry("SVN switch", () -> updateClient.doSwitch(
					target, sourceUrl, revision, revision, sessionTarget ? SVNDepth.UNKNOWN : SVNDepth.EMPTY,
					true, !sessionTarget, false));
		} else {
			runWithTransportRetry("SVN checkout", () -> updateClient.doCheckout(
					sourceUrl, target, revision, revision, SVNDepth.EMPTY, false));
		}
		runWithTransportRetry("SVN update", () -> updateClient.doUpdate(
				new File[] {new File(target, component.replace('/', File.separatorChar))},
				revision, SVNDepth.INFINITY, false, true, true));
	}

	private IVCSLockedWorkingCopy getWorkingCopy() throws IOException {
		return retainedWorkingCopy == null
				? repo.getVCSLockedWorkingCopy()
				: IVCSMutationSession.nonClosingView(retainedWorkingCopy);
	}

	private SVNSnapshot getPreparedSnapshot() {
		try {
			return (SVNSnapshot) repo.getPreparedRepositorySnapshot(ignoredPreparedFolder -> {
				SVNRepository readRepository = openReadRepository();
				try {
					return new SVNSnapshot(readRepository.getLatestRevision());
				} finally {
					readRepository.closeSession();
				}
			});
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		}
	}

	private long getReadRevision(SVNRepository readRepository) throws SVNException {
		return retainedWorkingCopy == null
				? getPreparedSnapshot().revision
				: readRepository.getLatestRevision();
	}

	SVNRepository openReadRepository() throws SVNException {
		SVNRepository readRepository = SVNRepositoryFactory.create(trunkSVNUrl);
		readRepository.setAuthenticationManager(authManager);
		return readRepository;
	}

	private File componentWorkingDirectory(File repositoryRoot) throws IOException {
		String component = normalizeComponentSubfolder(repo.getComponentSubfolder());
		File workingDirectory = component.isEmpty()
				? repositoryRoot
				: new File(repositoryRoot, component.replace('/', File.separatorChar));
		String rootPath = repositoryRoot.getCanonicalPath();
		String componentPath = workingDirectory.getCanonicalPath();
		if (!componentPath.equals(rootPath) && !componentPath.startsWith(rootPath + File.separator)) {
			throw new IOException("Component subfolder escapes the SVN working copy: " + component);
		}
		return workingDirectory;
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

	private void runWithTransportRetry(String operation, Utils.CheckedRunnable<SVNException> command)
			throws SVNException {
		Utils.runWithRetry(operation, command, SVNVCS::isTransientTransportFailure, retryStatusReporter);
	}

	static boolean isTransientTransportFailure(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SocketException || cause instanceof EOFException) {
				return true;
			}
			if (cause instanceof SVNException && hasTransientErrorCode(((SVNException) cause).getErrorMessage())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTransientErrorCode(SVNErrorMessage errorMessage) {
		for (SVNErrorMessage current = errorMessage; current != null; current = current.getChildErrorMessage()) {
			if (TRANSIENT_SVN_ERROR_CODES.contains(current.getErrorCode())) {
				return true;
			}
		}
		return false;
	}

	public boolean isWorkingCopyInited(File destPath) {
		try {
			clientManager.getStatusClient().doStatus(destPath, false);
			return true;
		} catch (SVNException e) {
			if (e.getErrorMessage().getErrorCode().getCode() == SVN_PATH_IS_NOT_WORKING_COPY_ERROR_CODE) {
				return false;
			}
			throw new EVCSException(e);
		}
	}

	@Override
	public void setCredentials(String user, String password) {
		userPassAuth = SVNPasswordAuthentication.newInstance(user, password == null ? null : password.toCharArray(),
				true, trunkSVNUrl, false);
		authManager.setAuthentications(new SVNAuthentication[] {userPassAuth});
		clientManager = SVNClientManager.newInstance(
				options, repository.getAuthenticationManager());
	}

	@Override
	public void setProxy(String host, int port, String proxyUser, String proxyPassword) {
		authManager.setProxy(host, port, proxyUser, proxyPassword.toCharArray());
	}

	@Override
	public void setRetryStatusReporter(BiConsumer<String, Throwable> reporter) {
		retryStatusReporter = reporter;
	}


	private String getBranchName(String branchName) {
		return branchName == null ? MASTER_PATH : BRANCHES_PATH + branchName;
	}
	
	@Override
	public VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList) {
		if (vcsChangeList.isEmpty()) {
			return null;
		}
		try (IVCSLockedWorkingCopy wc = getWorkingCopy()) {
			checkout(getBranchUrl(branchName), wc.getFolder(), null);
			StringBuilder commitMessageSB = new StringBuilder();
			Set<File> commitPaths = new LinkedHashSet<>();
			for (VCSChangeListNode vcsChangeListNode : vcsChangeList) {
				String filePath = vcsChangeListNode.getFilePath();
				File file = new File(wc.getFolder(), filePath);
				Boolean needToAdd = !file.exists();
				if (needToAdd) {
					FileUtils.forceMkdir(file.getParentFile());
					file.createNewFile();
				}
	
				try (FileWriter writer = new FileWriter(file)) {
					writer.write(vcsChangeListNode.getContent());
				}

				if (needToAdd) {
					clientManager
							.getWCClient()
							.doAdd(file,
									true /* force, avoiding "file is already under version control" exception */,
									false, false, SVNDepth.EMPTY, false, true);
				}
				commitPaths.add(file);
				// Added parents are required, but their other children must not be committed.
				for (File parent = file.getParentFile(); parent != null && !parent.equals(wc.getFolder());
						parent = parent.getParentFile()) {
					if (clientManager.getStatusClient().doStatus(parent, false).getNodeStatus()
							!= SVNStatusType.STATUS_ADDED) {
						break;
					}
					commitPaths.add(parent);
				}
				commitMessageSB.append(vcsChangeListNode.getLogMessage() + VCSChangeListNode.COMMIT_MESSAGES_SEPARATOR);
			}

			commitMessageSB.setLength(commitMessageSB.length() - VCSChangeListNode.COMMIT_MESSAGES_SEPARATOR.length());
			String commitMessage = commitMessageSB.toString();
			try {
				SVNCommitInfo newCommit = clientManager
						.getCommitClient()
						.doCommit(commitPaths.toArray(new File[0]), false, commitMessage,
								new SVNProperties(), null, false, false, SVNDepth.EMPTY);
				repo.invalidatePreparedRepositorySnapshot();
				return newCommit == SVNCommitInfo.NULL ? VCSCommit.EMPTY :
					new VCSCommit(Long.toString(newCommit.getNewRevision()), commitMessage, newCommit.getAuthor());
			} catch (SVNException e) {
				wc.setCorrupted(true);
				throw e;
			}
		} catch (SVNException e) {
			throw new EVCSException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage) {
		return setFileContent(branchName, Collections.singletonList(new VCSChangeListNode(filePath, content, commitMessage)));
	}

	@Override
	public String getRepoUrl() {
		return repo.getRepoUrl();
	}

	@Override
	public String getFileContent(String branchName, String filePath, String revision) {
		return getFilesContent(branchName, Collections.singletonList(filePath), revision).get(filePath);
	}

	@Override
	public Map<String, String> getFilesContent(String branchName, List<String> filePaths,
			String revision) {
		String branchPath = getBranchName(branchName);
		Map<String, String> result = new LinkedHashMap<>();
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long selectedRevision = revision == null || revision.isEmpty() || "-1".equals(revision)
					? getReadRevision(readRepository)
					: Long.parseLong(revision);
			if (filePaths.isEmpty()
					&& readRepository.checkPath(branchPath, selectedRevision) == SVNNodeKind.NONE) {
				throw new EVCSBranchNotFound(getRepoUrl(), branchPath);
			}
			for (String filePath : filePaths) {
				if (result.containsKey(filePath)) {
					continue;
				}
				String repositoryPath = new File(branchPath, filePath).getPath().replace("\\", "/");
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				try {
					readRepository.getFile(repositoryPath, selectedRevision, new SVNProperties(), output);
					result.put(filePath, output.toString(StandardCharsets.UTF_8.name()));
				} catch (SVNException e) {
					if (e.getErrorMessage().getErrorCode().getCode() == SVN_FILE_NOT_FOUND_ERROR_CODE) {
						if (readRepository.checkPath(branchPath, selectedRevision) == SVNNodeKind.NONE) {
							throw new EVCSBranchNotFound(getRepoUrl(), branchPath);
						}
						throw new EVCSFileNotFound(getRepoUrl(), branchPath, filePath, revision);
					}
					throw e;
				}
			}
			return result;
		} catch (EVCSException e) {
			throw e;
		} catch (Exception e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String dstBranchName) {
		SVNRepository readRepository = null;
		SvnOperationFactory operations = new SvnOperationFactory();
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			operations.setAuthenticationManager(authManager);
			SVNRevision revision = SVNRevision.create(repositoryRevision);
			SvnDiffSummarize summarize = operations.createDiffSummarize();
			summarize.setSources(
					SvnTarget.fromURL(getBranchUrl(dstBranchName), revision),
					SvnTarget.fromURL(getBranchUrl(srcBranchName), revision));
			List<VCSDiffEntry> summarized = new ArrayList<>();
			summarize.setReceiver((target, status) -> {
				if (!status.getPath().isEmpty()) {
					summarized.add(new VCSDiffEntry(status.getPath(),
							SVNChangeTypeToVCSChangeType(status.getModificationType()), null));
				}
			});
			summarize.run();
			if (summarized.isEmpty()) {
				return new ArrayList<>();
			}

			// Diff branch roots so added/deleted paths need not exist on both sides.
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			SvnDiff diff = operations.createDiff();
			diff.setSources(SvnTarget.fromURL(getBranchUrl(dstBranchName), revision),
					SvnTarget.fromURL(getBranchUrl(srcBranchName), revision));
			diff.setOutput(output);
			diff.setRelativeToDirectory(new File("").getAbsoluteFile());
			diff.run();
			Map<String, String> patches = new HashMap<>();
			for (String patch : output.toString(StandardCharsets.UTF_8.name()).split("(?m)(?=^Index: )")) {
				int lineEnd = patch.indexOf('\n');
				if (patch.startsWith("Index: ") && lineEnd >= 0) {
					patches.put(patch.substring("Index: ".length(), lineEnd).replaceFirst("\\r$", "").replace('\\', '/'), patch);
				}
			}
			List<VCSDiffEntry> result = new ArrayList<>();
			for (VCSDiffEntry entry : summarized) {
				result.add(new VCSDiffEntry(entry.getFilePath(), entry.getChangeType(),
						patches.getOrDefault(entry.getFilePath().replace('\\', '/'), "")));
			}
			return result;
		} catch (Exception e) {
			throw new EVCSException(e);
		} finally {
			operations.dispose();
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public Set<String> getBranches(String path) {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			List<String> entries = listEntries(readRepository,
					BRANCHES_PATH + (path == null ? "" : path), repositoryRevision);
			Set<String> branches = new HashSet<>(entries);
			if (readRepository.checkPath(MASTER_PATH, repositoryRevision) == SVNNodeKind.DIR
					&& (path == null || MASTER_PATH.startsWith(path))) {
				branches.add(MASTER_PATH.replace("/", ""));
			}
			Set<String> result = new HashSet<>();
			for (String branch : branches) {
				result.add(StringUtils.removeStart(branch, BRANCHES_PATH));
			}
			return result;
		} catch (SVNException e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<String> listEntries(SVNRepository readRepository, String path, long revision)
			throws SVNException {
		List<String> result = new ArrayList<>();
		if (path == null) {
			return result;
		}
		String normalized = path.trim();
		int lastSlash = normalized.lastIndexOf('/');
		String parent = lastSlash > 0 ? normalized.substring(0, lastSlash) : normalized;
		String prefix = lastSlash > 0 ? normalized.substring(lastSlash + 1) : "";
		Collection<SVNDirEntry> entries = readRepository.getDir(
				parent, revision, null, (Collection<SVNDirEntry>) null);
		List<SVNDirEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingLong(SVNDirEntry::getRevision));
		for (SVNDirEntry entry : sorted) {
			if (entry.getKind() == SVNNodeKind.DIR && entry.getName().startsWith(prefix)) {
				result.add((normalized.isEmpty() ? "" : StringUtils.appendIfMissing(parent, "/"))
						+ entry.getName());
			}
		}
		return result;
	}

	@Override
	public List<VCSCommit> log(String branchName, int limit) {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			List<VCSCommit> result = new ArrayList<>();
			readRepository.log(new String[] {getBranchName(branchName)}, repositoryRevision, 0L,
					true, true, limit, entry -> result.add(svnLogEntryToVCSCommit(entry)));
			return result;
		} catch (SVNException e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public List<VCSCommit> getCommitsRange(String branchName, String startRevision,
			WalkDirection direction, int limit, String repositoryRelativePath) {
		String historyPath = getHistoryPath(branchName, repositoryRelativePath);
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			long first = getDirFirstCommit(readRepository, getBranchName(branchName), repositoryRevision)
					.getRevision();
			long start = startRevision == null
					? (direction == WalkDirection.ASC ? first : repositoryRevision)
					: Long.parseLong(startRevision);
			long end = direction == WalkDirection.ASC ? repositoryRevision : first;
			List<VCSCommit> result = new ArrayList<>();
			readRepository.log(new String[] {historyPath}, start, end, true, true, limit,
					entry -> result.add(svnLogEntryToVCSCommit(entry)));
			return result;
		} catch (SVNException e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public List<VCSCommit> getCommitsRange(String branchName, String startRevision,
			String endRevision) {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			long start = startRevision == null
					? getDirFirstCommit(readRepository, getBranchName(branchName), repositoryRevision).getRevision()
					: Long.parseLong(startRevision);
			long end = endRevision == null ? repositoryRevision : Long.parseLong(endRevision);
			List<VCSCommit> result = new ArrayList<>();
			readRepository.log(new String[] {getBranchName(branchName)}, start, end, true, true, 0,
					entry -> result.add(svnLogEntryToVCSCommit(entry)));
			return result;
		} catch (SVNException e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public VCSCommit getHeadCommit(String branchName) {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			SVNLogEntry entry = getDirHeadLogEntry(
					readRepository, getBranchName(branchName), repositoryRevision);
			return entry == null ? null : svnLogEntryToVCSCommit(entry);
		} catch (SVNException e) {
			if (e.getErrorMessage().getErrorCode().getCode() == SVN_FILE_NOT_FOUND_ERROR_CODE) {
				return null;
			}
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@Override
	public Boolean fileExists(String branchName, String filePath) {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			return readRepository.checkPath(new File(getBranchName(branchName), filePath)
					.getPath().replace("\\", "/"), repositoryRevision) == SVNNodeKind.FILE;
		} catch (SVNException e) {
			throw new EVCSException(e);
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	List<VCSTag> getTags(String onRevision) throws SVNException {
		SVNRepository readRepository = null;
		try {
			readRepository = openReadRepository();
			long repositoryRevision = getReadRevision(readRepository);
			List<VCSTag> result = new ArrayList<>();
			if (readRepository.checkPath(TAGS_PATH, repositoryRevision) == SVNNodeKind.NONE) {
				return result;
			}
			collectTags(readRepository, TAGS_PATH,
					onRevision == null ? null : Long.parseLong(onRevision), repositoryRevision, result);
			return result;
		} finally {
			if (readRepository != null) {
				readRepository.closeSession();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void collectTags(SVNRepository readRepository, String directory, Long onRevision,
			long snapshotRevision, List<VCSTag> tags) throws SVNException {
		Collection<SVNDirEntry> entries = readRepository.getDir(
				directory, snapshotRevision, null, (Collection<SVNDirEntry>) null);
		for (SVNDirEntry entry : entries) {
			if (entry.getKind() != SVNNodeKind.DIR) {
				continue;
			}
			String tagPath = StringUtils.appendIfMissing(directory, "/") + entry.getName();
			SVNLogEntry tagEntry = getDirFirstCommit(readRepository, tagPath, snapshotRevision);
			SVNLogEntryPath creation = getCreationEntry(readRepository, tagEntry, tagPath);
			if (creation.getCopyPath() == null) {
				collectTags(readRepository, tagPath, onRevision, snapshotRevision, tags);
				continue;
			}
			long copiedRevision = creation.getCopyRevision();
			if (onRevision == null || copiedRevision == onRevision) {
				SVNProperties properties = readRepository.getRevisionProperties(copiedRevision, null);
				tags.add(new VCSTag(StringUtils.removeStart(tagPath, TAGS_PATH), tagEntry.getMessage(),
						tagEntry.getAuthor(), new VCSCommit(Long.toString(copiedRevision),
						properties.getStringValue(SVNRevisionProperty.LOG),
						properties.getStringValue(SVNRevisionProperty.AUTHOR))));
			}
		}
	}

	private SVNLogEntry getDirFirstCommit(SVNRepository readRepository, String directory,
			long revision) throws SVNException {
		final SVNLogEntry[] first = new SVNLogEntry[1];
		readRepository.log(new String[] {directory}, 0, revision, true, true, 1,
				entry -> first[0] = entry);
		if (first[0] == null) {
			throw invalidCreationMetadata(directory);
		}
		return first[0];
	}

	private SVNLogEntry getDirHeadLogEntry(SVNRepository readRepository, String directory,
			long revision) throws SVNException {
		@SuppressWarnings("unchecked")
		Collection<SVNLogEntry> entries = readRepository.log(
				new String[] {directory}, null, revision, 0, true, true);
		return entries.isEmpty() ? null : entries.iterator().next();
	}

	private SVNLogEntryPath getCreationEntry(SVNRepository readRepository, SVNLogEntry tagEntry,
			String tagPath) throws SVNException {
		String repositoryTagPath = readRepository.getRepositoryPath(tagPath);
		return getCreationEntry(tagEntry, tagPath, repositoryTagPath);
	}
	
	
	SVNLogEntry getBranchFirstCommit(final String branchPath) throws SVNException {
		return getDirFirstCommit(repository, getBranchName(branchPath), -1L);
	}
	
	public VCSChangeType SVNChangeTypeToVCSChangeType(SVNStatusType modificationType) {
		if (SVNStatusType.STATUS_ADDED.equals(modificationType)) {
			return VCSChangeType.ADD;
		} else if (SVNStatusType.STATUS_DELETED.equals(modificationType)) {
			return VCSChangeType.DELETE;
		} else if (SVNStatusType.STATUS_MODIFIED.equals(modificationType)) {
			return VCSChangeType.MODIFY;
		} else {
			return VCSChangeType.UNKNOWN;
		}
	}

	
	List<String> listEntries(String path) throws SVNException {
		return listEntries(repository, path, -1L);
	}
	
	@Override
	public String getVCSTypeString() {
		return SVN_VCS_TYPE_STRING;
	}

	@Override
	public VCSCommit removeFile(String branchName, String filePath, String commitMessage) {
		try {
			SVNCommitInfo res = clientManager
					.getCommitClient()
					.doDelete(new SVNURL[] {getBranchUrl(branchName).appendPath(filePath, true)}, commitMessage);
			repo.invalidatePreparedRepositorySnapshot();
			return new VCSCommit(Long.toString(res.getNewRevision()), commitMessage, res.getAuthor());
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}
	
	private String getHistoryPath(String branchName, String repositoryRelativePath) {
		String branchPath = getBranchName(branchName);
		if (repositoryRelativePath == null || repositoryRelativePath.isEmpty()) {
			return branchPath;
		}

		String relativePath = repositoryRelativePath.replace("\\", "/");
		boolean hasDrivePrefix = relativePath.length() >= 2
				&& Character.isLetter(relativePath.charAt(0)) && relativePath.charAt(1) == ':';
		if (relativePath.startsWith("/") || hasDrivePrefix) {
			throw new IllegalArgumentException("repositoryRelativePath must not be absolute");
		}
		for (String pathSegment : relativePath.split("/")) {
			if (pathSegment.equals("..")) {
				throw new IllegalArgumentException("repositoryRelativePath must not contain parent traversal");
			}
		}
		return StringUtils.appendIfMissing(branchPath, "/") + relativePath;
	}
	
	private VCSCommit svnLogEntryToVCSCommit(SVNLogEntry logEntry) {
		return new VCSCommit(Long.toString(logEntry.getRevision()), logEntry.getMessage(),
				logEntry.getAuthor());
	}

	
	SVNLogEntry getDirHeadLogEntry(String dir) throws SVNException {
		return getDirHeadLogEntry(repository, dir, -1L);
	}

	@Override
	public String toString() {
		return "SVNVCS [url=" + repo.getRepoUrl() + "]";
	}

	
	@Override
	public VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists {
		try {
			SVNURL srcURL = getBranchUrl(branchName);
			SVNURL dstURL = SVNURL.parseURIEncoded(repoUrl + TAGS_PATH + tagName);
			SVNLogEntry copyFromEntry = revToSVNEntry(getBranchName(branchName),
					revisionToTag == null ? SVNRevision.HEAD.getNumber() : Long.parseLong(revisionToTag));
			SVNCopySource copySource = revisionToTag == null ?
					new SVNCopySource(SVNRevision.HEAD, SVNRevision.create(copyFromEntry.getRevision()), srcURL) :
					new SVNCopySource(SVNRevision.parse(revisionToTag), SVNRevision.parse(revisionToTag), srcURL);

			clientManager.getCopyClient().doCopy(new SVNCopySource[] {copySource}, dstURL,
			        false, true, true, tagMessage, null);
			repo.invalidatePreparedRepositorySnapshot();

			SVNDirEntry entry = repository.info(TAGS_PATH + tagName, -1);

			return new VCSTag(tagName, tagMessage, entry.getAuthor(), svnLogEntryToVCSCommit(copyFromEntry));
		} catch (SVNException e) {
			if (e.getErrorMessage().getErrorCode().getCode() == SVN_ITEM_EXISTS_ERROR_CODE) {
				throw new EVCSTagExists(e);
			} 
			throw new EVCSException(e);
		}
	}
	
	SVNLogEntry revToSVNEntry(String branchName, Long rev) throws SVNException {
		SVNDirEntry info = repository.info(branchName, rev);
		@SuppressWarnings("unchecked")
		Collection<SVNLogEntry> entries = repository.log(new String[] {branchName}, null, info.getRevision(), info.getRevision(), true, true);
		if (entries != null) {
			return entries.iterator().next();
		}
		return null;
	}
	
	@Override
	public List<VCSTag> getTags() {
		try {
			return getTags(null);
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public void removeTag(String tagName) {
		try {
			clientManager
					.getCommitClient()
					.doDelete(new SVNURL[] { SVNURL.parseURIEncoded(repoUrl + TAGS_PATH + tagName) }, null);
			repo.invalidatePreparedRepositorySnapshot();
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public void checkout(String branchName, String targetPath, String revision) {
		try {
			checkout(getBranchUrl(branchName), new File(targetPath), revision);
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}

	@Override
	public void sparseCheckout(String branchName, String targetPath, String revision,
			String repositoryRelativeDirectory) {
		try {
			SVNRevision svnRevision = revision == null ? SVNRevision.HEAD : SVNRevision.parse(revision);
			checkoutSparse(getBranchUrl(branchName), new File(targetPath), svnRevision,
					repositoryRelativeDirectory);
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}
	
	private SVNLogEntryPath getCreationEntry(SVNLogEntry tagEntry, String tagPath,
			String repositoryTagPath) throws SVNException {
		String absoluteTagPath = "/" + StringUtils.removeStart(repositoryTagPath, "/");
		if (tagEntry.getChangedPaths() == null) {
			throw invalidCreationMetadata(tagPath);
		}
		SVNLogEntryPath creationEntry = tagEntry.getChangedPaths().get(absoluteTagPath);
		if (creationEntry == null || !absoluteTagPath.equals(creationEntry.getPath())
				|| creationEntry.getType() != SVNLogEntryPath.TYPE_ADDED) {
			throw invalidCreationMetadata(tagPath);
		}
		boolean hasCopyPath = creationEntry.getCopyPath() != null;
		boolean hasCopyRevision = creationEntry.getCopyRevision() >= 0;
		if (hasCopyPath != hasCopyRevision) {
			throw invalidCreationMetadata(tagPath);
		}
		return creationEntry;
	}

	private SVNException invalidCreationMetadata(String path) {
		return new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN,
				"Missing or inconsistent creation metadata for SVN path ''{0}''", path));
	}

	@Override
	public List<VCSTag> getTagsOnRevision(String revision) {
		try {
			return getTags(revision);
		} catch (SVNException e) {
			throw new EVCSException(e);
		}
	}

	private static final class SVNSnapshot implements IVCSRepositoryWorkspace.PreparedRepositorySnapshot {
		private final long revision;

		private SVNSnapshot(long revision) {
			this.revision = revision;
		}
	}

	private static class SVNMutationSession extends SVNVCS implements IVCSMutationSession {
		private final IVCSLockedWorkingCopy workingCopy;
		private final File workingDirectory;
		private boolean closed;

		private SVNMutationSession(SVNVCS owner, IVCSLockedWorkingCopy workingCopy,
				File workingDirectory) throws SVNException {
			super(owner, workingCopy);
			this.workingCopy = workingCopy;
			this.workingDirectory = workingDirectory;
		}

		@Override
		public IVCSMutationSession startModify() {
			throw new IllegalStateException("An SVN mutation session cannot open another mutation session");
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
				} finally {
					getClientManager().dispose();
					getSVNRepository().closeSession();
				}
			}
		}
	}
}
