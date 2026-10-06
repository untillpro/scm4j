package org.scm4j.vcs.svn;

import org.junit.After;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.verification.VerificationMode;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSChangeType;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;
import org.scm4j.vcs.api.abstracttest.VCSAbstractTest;
import org.scm4j.vcs.api.exceptions.EVCSBranchNotFound;
import org.scm4j.vcs.api.exceptions.EVCSException;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.anyCollectionOf;
import static org.mockito.Mockito.*;

public class SVNVCSTest extends VCSAbstractTest {

	private static final String FOLDER_STRUCT_CREATED_COMMIT_MESSAGE = "trunk/ and branches/ created";
	private final RuntimeException testSvnRevertException = new RuntimeException("test exeption on svn revert");
	private final SVNException testSVNException = new SVNException(SVNErrorMessage.create(SVNErrorCode.ATOMIC_INIT_FAILURE, "test svn exception"));
	private final IOException testCommonException = new IOException("test exception");
	private SVNVCS svn;
	private SVNRepository svnRepo;private SVNWCClient mockedSVNRevertClient;
	private String reportedRetryOperation;
	private Throwable reportedRetryFailure;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		svnRepo = SVNVCSUtils.createRepository(new File(URI.create(repoUrl)));
		SVNVCSUtils.createFolderStructure(svn, FOLDER_STRUCT_CREATED_COMMIT_MESSAGE);
	}

	@After
	public void tearDown() throws IOException {
		svnRepo.closeSession();
	}

	@Override
	public String getVCSTypeString() {
		return "svn";
	}

	@Override
	protected IVCS getVCS(IVCSRepositoryWorkspace mockedVCSRepo) {
		// nulls as user and pwd because we using file repository, not server
		svn = spy(new SVNVCS(mockedVCSRepo, null, null));
		return svn;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void setMakeFailureOnVCSReset(Boolean doMakeFailure) {
		if (doMakeFailure) {
			mockedSVNRevertClient = spy(svn.getRevertClient((DefaultSVNOptions) svn.getOptions()));
			doReturn(mockedSVNRevertClient).when(svn).getRevertClient(any(DefaultSVNOptions.class));
			try {
				doThrow(testSvnRevertException).when(mockedSVNRevertClient)
						.doRevert(any(File[].class), any(SVNDepth.class),
								isNull(Collection.class));
			} catch (SVNException e) {
				throw new RuntimeException(e);
			}
		} else {
			if (mockedSVNRevertClient != null) {
				doCallRealMethod().when(svn).getRevertClient((DefaultSVNOptions) svn.getOptions());
				mockedSVNRevertClient = null;
			}
		}
	}

	@Test
	public void testSVNVCSCreation() {
		IVCSRepositoryWorkspace mockedWS = mock(IVCSRepositoryWorkspace.class);
		doReturn("wrong_protocol://www.ru").when(mockedWS).getRepoUrl();
		try {
			new SVNVCS(mockedWS, "", "");
			fail();
		} catch (EVCSException e) {
			assertTrue(e.getCause() instanceof SVNException);
			assertTrue(e.getMessage().contains(e.getCause().getMessage()));
		}
	}

	@Test
	public void testCredentials() throws Exception {
		testAuth(new SVNVCS(localVCSRepo, null, null), null, null);
		testAuth(new SVNVCS(localVCSRepo, "user", "pass"), "user", "pass");
		vcs.setCredentials(null, null);
		testAuth(svn, null, null);
		vcs.setCredentials("user", "pass");
		testAuth(svn, "user", "pass");
	}
	
	private void testAuth(SVNVCS svn, String user, String pass) throws Exception {
		SVNRepository repo = svn.getSVNRepository();
		SVNAuthentication auth = repo.getAuthenticationManager().getFirstAuthentication("svn.simple", "",
				svn.getTrunkSVNUrl());
		assertTrue(auth instanceof SVNPasswordAuthentication);
		SVNPasswordAuthentication pAuth = (SVNPasswordAuthentication) auth;
		assertEquals(pAuth.getUserName(), user);
		if (pass == null) {
			assertTrue(pAuth.getPasswordValue().length == 0);
		} else {
			assertEquals(new String(pAuth.getPasswordValue()), pass);
		}
	}

	@Test
	public void testSVNExceptions() throws SVNException {
		doThrow(testSVNException).when(svn).getBranchUrl(anyString());
		doThrow(testSVNException).when(svn).listEntries(anyString());
		doThrow(testSVNException).when(svn).getBranchFirstCommit(anyString());
		doThrow(testSVNException).when(svn).getDirHeadLogEntry(anyString());
		testSVNException(() -> svn.createBranch("", "", ""));
		testSVNException(() -> svn.deleteBranch("", ""));
		testSVNException(() -> svn.merge("", "", ""));
		testSVNException(() -> svn.setFileContent("", "", "", ""));
		testSVNException(() -> svn.getBranchesDiff("", ""));
		testSVNException(() -> svn.getBranches(""));
		testSVNException(() -> svn.log("", 0));
		testSVNException(() -> svn.removeFile("", "", ""));
		testSVNException(() -> svn.getCommitsRange("", null, WalkDirection.ASC, 0));
		testSVNException(() -> svn.getCommitsRange("", null, WalkDirection.ASC, 0, "folder"));
		testSVNException(() -> svn.getCommitsRange("", null, ""));
		testSVNException(() -> svn.getHeadCommit(""));
		testSVNException(() -> svn.createTag("", "", "", ""));
		testSVNException(() -> svn.checkout("", "", ""));
		testSVNException(() -> svn.sparseCheckout("", "", "", "../folder"));
	}

	@Test
	public void testSparseCheckoutCollapsesExistingWorkingCopy() {
		vcsTestDataGen.setFileContent(null, SPARSE_FILE, LINE_1, "selected component added");
		vcsTestDataGen.setFileContent(null, SPARSE_SIBLING_FILE, LINE_2, "sibling component added");

		File checkoutDir = new File(TEST_BASE_DIR, "sparse-existing-full-checkout");
		svn.checkout(null, checkoutDir.getPath(), null);
		assertTrue(new File(checkoutDir, SPARSE_FILE).isFile());
		assertTrue(new File(checkoutDir, SPARSE_SIBLING_FILE).isFile());

		svn.sparseCheckout(null, checkoutDir.getPath(), null, SPARSE_DIRECTORY);

		assertTrue(new File(checkoutDir, SPARSE_FILE).isFile());
		assertFalse(new File(checkoutDir, SPARSE_SIBLING_FILE).exists());

		svn.sparseCheckout(null, checkoutDir.getPath(), null, "components/sibling");

		assertFalse(new File(checkoutDir, SPARSE_FILE).exists());
		assertTrue(new File(checkoutDir, SPARSE_SIBLING_FILE).isFile());
	}

	@Test
	public void testCommitsRangeRejectsNonRelativePaths() {
		// History filtering must remain below the selected branch for Unix, Windows drive,
		// UNC, and parent-traversal forms instead of passing them to the SVN repository.
		assertInvalidHistoryPath("/tags");
		assertInvalidHistoryPath("C:\\tags");
		assertInvalidHistoryPath("\\\\server\\tags");
		assertInvalidHistoryPath("../tags");
		assertInvalidHistoryPath("components/../tags");
	}

	private void assertInvalidHistoryPath(String path) {
		try {
			vcs.getCommitsRange(null, null, WalkDirection.ASC, 0, path);
			fail("Expected an invalid repository-relative path to be rejected: " + path);
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("repositoryRelativePath"));
		}
	}

	@Test
	public void testCommonExceptions() throws IOException {
		IVCSRepositoryWorkspace mockedRepo = mock(IVCSRepositoryWorkspace.class);
		svn.setRepo(mockedRepo);
		doThrow(testCommonException).when(mockedRepo).getVCSLockedWorkingCopy();
		testCommonException(() -> svn.setFileContent("", "", "", ""));
		testCommonException(() -> svn.merge("", "", ""));
		testCommonException(() -> svn.getBranchesDiff("", ""));
	}

	private void testSVNException(Runnable toTest) {
		testException(toTest, testSVNException);
	}

	private void testException(Runnable toTest, Exception expectedCause) {
		try {
			toTest.run();
			fail();
		} catch (Exception e) {
			checkException(e, expectedCause);
		}
	}

	private void testCommonException(Runnable toTest) {
		testException(toTest, testCommonException);
	}

	@Test
	public void testVCSTypeString() {
		assertEquals(vcs.getVCSTypeString(), SVNVCS.SVN_VCS_TYPE_STRING);
	}

	@Test
	public void testDefaultChangeTypeToVCSType() throws IllegalAccessException {
		for (Field f : SVNStatusType.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
				if (!f.get(null).equals(SVNStatusType.STATUS_ADDED)
						&& !f.get(null).equals(SVNStatusType.STATUS_DELETED)
						&& !f.get(null).equals(SVNStatusType.STATUS_MODIFIED)) {
					assertEquals(svn.SVNChangeTypeToVCSChangeType((SVNStatusType) f.get(null)),
							VCSChangeType.UNKNOWN);
				}
			}
		}
	}

	@Test
	public void testCreateBranchExceptions() throws Exception {
		SVNClientManager mockedManager = spy(svn.getClientManager());
		SVNCopyClient mockedCopyClient = mock(SVNCopyClient.class);
		svn.setClientManager(mockedManager);
		doReturn(mockedCopyClient).when(mockedManager).getCopyClient();
		doThrow(testSVNException).when(mockedCopyClient).doCopy(any(SVNCopySource[].class), any(SVNURL.class),
				anyBoolean(), anyBoolean(), anyBoolean(), anyString(), any(SVNProperties.class));
		try {
			vcs.createBranch("", "", "");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@Override
	public void testMergeConflictWCCorruption() throws Exception {
		super.testMergeConflictWCCorruption();
		super.resetMocks();
		setMakeFailureOnVCSReset(false);
		SVNWCClient mockedWCClient = mock(SVNWCClient.class);
		doReturn(mockedWCClient).when(svn).getRevertClient(any(DefaultSVNOptions.class));
		doThrow(testSVNException).when(mockedWCClient).doRevert(any(File[].class), any(SVNDepth.class),
				Matchers.<Collection<String>>any());
		vcs.merge(NEW_BRANCH, null, MERGE_COMMIT_MESSAGE);
		assertTrue(mockedLWC.getCorrupted());

		SVNClientManager mockedManager = spy(svn.getClientManager());
		SVNDiffClient mockedDiffClient = spy(mockedManager.getDiffClient());
		svn.setClientManager(mockedManager);
		doReturn(mockedDiffClient).when(mockedManager).getDiffClient();
		doThrow(testSVNException).when(mockedDiffClient).doMerge(any(SVNURL.class), any(SVNRevision.class),
				anyCollectionOf(SVNRevisionRange.class), any(File.class), any(SVNDepth.class),
				anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
		try {
			vcs.merge(NEW_BRANCH, null, MERGE_COMMIT_MESSAGE);
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@Test
	public void testIsWorkingCopyInitedExceptions() throws Exception {
		SVNStatusClient mockedStatus = mock(SVNStatusClient.class);
		svn.setClientManager(spy(svn.getClientManager()));
		SVNClientManager manager = svn.getClientManager();
		doReturn(mockedStatus).when(manager).getStatusClient();
		doThrow(testSVNException).when(mockedStatus).doStatus(any(File.class), anyBoolean());
		try {
			svn.isWorkingCopyInited(null);
			fail();
		} catch (EVCSException e) {
			assertTrue(e.getCause() instanceof SVNException);
		}
	}

	@Test
	public void testProxy() throws Exception {
		vcs.setProxy("host", 123, "user", "pass");
		ISVNProxyManager manager = svn.getSVNRepository().getAuthenticationManager().getProxyManager(svn.getTrunkSVNUrl());
		assertEquals(manager.getProxyHost(), "host");
		assertEquals(manager.getProxyPassword(), "pass");
		assertEquals(manager.getProxyPort(), 123);
		assertEquals(manager.getProxyUserName(), "user");
	}
	
	@Test
	public void testGetFileContentExceptions() throws Exception {
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		doThrow(testSVNException).when(mockedRepo).getFile(anyString(), anyLong(), any(SVNProperties.class), any(OutputStream.class));
		try {
			vcs.getFileContent("", "", "");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}

		SVNRepository repo = svn.getSVNRepository();
		svn.setSVNRepository(null);
		try {
			vcs.getFileContent("", "", "");
			fail();
		} catch (RuntimeException e) {
			assertTrue(e.getCause() instanceof NullPointerException);
		}
		svn.setSVNRepository(repo);

		doCallRealMethod().when(mockedRepo).getFile(anyString(), anyLong(), any(SVNProperties.class), any(OutputStream.class));
		doThrow(testSVNException).when(mockedRepo).checkPath(anyString(), anyLong());
		try {
			vcs.getFileContent("wrong-branch", "", "");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@Test
	public void testGetFilesContentChecksBranchAtSelectedRevision() throws Exception {
		long selectedRevision = 7L;
		String branchName = "created-later";
		String branchPath = SVNVCS.BRANCHES_PATH + branchName;
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		SVNException fileNotFound = new SVNException(
				SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "not found"));
		doThrow(fileNotFound).when(mockedRepo).getFile(anyString(), eq(selectedRevision),
				any(SVNProperties.class), any(OutputStream.class));
		doReturn(SVNNodeKind.NONE).when(mockedRepo).checkPath(branchPath, selectedRevision);

		try {
			vcs.getFilesContent(branchName, Arrays.asList(FILE1_NAME), Long.toString(selectedRevision));
			fail(EVCSBranchNotFound.class.getSimpleName() + " is not thrown");
		} catch (EVCSBranchNotFound ignored) {
		}

		verify(mockedRepo).checkPath(branchPath, selectedRevision);
		verify(mockedRepo, never()).checkPath(branchPath, -1L);
	}

	@Test
	public void testGetFilesContentChecksEmptyBranchAtSelectedRevision() throws Exception {
		long selectedRevision = 7L;
		String branchName = "created-later";
		String branchPath = SVNVCS.BRANCHES_PATH + branchName;
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		doReturn(SVNNodeKind.NONE).when(mockedRepo).checkPath(branchPath, selectedRevision);

		try {
			vcs.getFilesContent(branchName, Collections.emptyList(), Long.toString(selectedRevision));
			fail(EVCSBranchNotFound.class.getSimpleName() + " is not thrown");
		} catch (EVCSBranchNotFound ignored) {
		}

		verify(mockedRepo).checkPath(branchPath, selectedRevision);
		verify(mockedRepo, never()).checkPath(branchPath, -1L);
	}

	@Test
	public void setFileContentWCCorruption() throws Exception {
		SVNCommitClient mockedCommitClient = mock(SVNCommitClient.class);
		svn.setClientManager(spy(svn.getClientManager()));
		SVNClientManager manager = svn.getClientManager();
		doReturn(mockedCommitClient).when(manager).getCommitClient();
		doThrow(testSVNException).when(mockedCommitClient).doCommit(any(File[].class),
				anyBoolean(), anyString(), any(SVNProperties.class), any(String[].class),
				anyBoolean(), anyBoolean(), any(SVNDepth.class));
		try {
			vcs.setFileContent(null, "test.txt", "", "");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
		assertTrue(mockedLWC.getCorrupted());
	}

	private void checkEVCSException(EVCSException e) {
		checkException(e, testSVNException);
	}

	private void checkException(Exception e, Exception expectedCause) {
		assertEquals(expectedCause.getClass(), e.getCause().getClass());
		if (e.getCause().getMessage() == null) {
			assertNull(expectedCause.getMessage());
		} else {
			assertTrue(e.getCause().getMessage().contains(expectedCause.getMessage()));
		}
	}

	@Test
	public void testFileExistsExceptions() throws Exception {
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		doThrow(testSVNException).when(mockedRepo).checkPath(anyString(), anyLong());
		try {
			vcs.fileExists("", "");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testListEntriesSorting() throws Exception {
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		SVNDirEntry entry1 = new SVNDirEntry(null, null, "entry1", SVNNodeKind.DIR, 0, false, 1, null, null);
		SVNDirEntry entry2 = new SVNDirEntry(null, null, "entry2", SVNNodeKind.DIR, 0, false, 2, null, null);

		doReturn(Arrays.asList(entry1, entry2)).when(mockedRepo).getDir(anyString(), anyLong(), (SVNProperties) isNull(),
				(Collection<SVNDirEntry>) isNull());

		List<String> entries = svn.listEntries("");
		assertEquals(entry1.getName(), entries.get(0));
		assertEquals(entry2.getName(), entries.get(1));
		doReturn(Arrays.asList(entry1, entry1)).when(mockedRepo).getDir(anyString(), anyLong(), any(SVNProperties.class),
				Matchers.<Collection<SVNDirEntry>>any());
		entries = svn.listEntries("");
		assertEquals(entry1.getName(), entries.get(0));
		assertEquals(entry1.getName(), entries.get(1));
	}

	@Test
	public void testRevToSVNEntryNull() throws Exception {
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		doReturn(null).when(mockedRepo).log(any(String[].class),
				any(Collection.class), anyLong(), anyLong(), anyBoolean(), anyBoolean());
		assertNull(svn.revToSVNEntry("", -1L));
	}

	@Test
	public void testSVNVCSUtilsCreation() {
		assertNotNull(new SVNVCSUtils());
	}
	
	@Test
	public void testListEntriesNone() throws Exception {
		SVNRepository mockedRepo = spy(svn.getSVNRepository());
		svn.setSVNRepository(mockedRepo);
		doReturn(SVNNodeKind.NONE).when(mockedRepo).checkPath(anyString(), anyLong());
		assertTrue(svn.listEntries(null).isEmpty()); // expecting no NPE
	}
	
	@Test
	public void testGetTagsNoTagsDir() throws SVNException {
		// Recursive namespace discovery starts at tags/. A repository without that root
		// therefore has no tags for either listing operation rather than being an error.
		svn.getClientManager()
				.getCommitClient()
				.doDelete(new SVNURL[] { SVNURL.parseURIEncoded(svn.getRepoUrl() + "/" + SVNVCS.TAGS_PATH)}, "tags/ deleted");
		assertTrue(vcs.getTags().isEmpty());
		assertTrue(vcs.getTagsOnRevision("0").isEmpty());
	}

	/*
	 * physical SVN root
	 * `-- project/                         nestedWriter and nestedReader point here
	 *     +-- trunk/payload/{ordinary.txt,namespaced.txt}
	 *     `-- tags/
	 *         +-- ordinary/                copy of trunk@ordinaryCommit
	 *         `-- release/                 namespace: added without copy history
	 *             `-- 1.0/                 copy of trunk@namespacedCommit
	 *
	 * Regression:
	 *   SVN path /project/tags/ordinary != old expected /tags/ordinary  <-- fault
	 *     -> copied tag mistaken for namespace
	 *     -> recursively log every payload directory
	 *     -> many remote requests ending in an apparent socket-read hang
	 *
	 * Assertions:
	 *   getTags()                         -> ordinary, release/1.0
	 *   getTagsOnRevision(ordinary)       -> ordinary
	 *   getTagsOnRevision(namespaced)     -> release/1.0
	 *   enumerate tags/release only; never enumerate either copied tag leaf
	 */
	@Test
	public void testTagsBelowRepositoryRoot() throws Exception {
		SVNURL projectUrl = SVNURL.parseURIEncoded(repoUrl).appendPath("project", false);
		svn.getClientManager().getCommitClient().doMkDir(
				new SVNURL[] {projectUrl}, "nested project created");
		IVCSRepositoryWorkspace nestedWorkspace = localVCSWorkspace
				.getVCSRepositoryWorkspace(projectUrl.toString());
		SVNVCS nestedWriter = new SVNVCS(nestedWorkspace, null, null);
		SVNVCS nestedReader = new SVNVCS(nestedWorkspace, null, null);
		try {
			SVNVCSUtils.createFolderStructure(nestedWriter, FOLDER_STRUCT_CREATED_COMMIT_MESSAGE);
			VCSCommit ordinaryCommit = nestedWriter.setFileContent(
					null, "payload/ordinary.txt", LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
			VCSCommit namespacedCommit = nestedWriter.setFileContent(
					null, "payload/namespaced.txt", LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
			VCSTag ordinaryTag = nestedWriter.createTag(
					null, "ordinary", TAG_MESSAGE_1, ordinaryCommit.getRevision());
			VCSTag namespacedTag = nestedWriter.createTag(
					null, "release/1.0", TAG_MESSAGE_2, namespacedCommit.getRevision());

			SVNRepository nestedRepository = spy(nestedReader.getSVNRepository());
			nestedReader.setSVNRepository(nestedRepository);

			List<VCSTag> tags = nestedReader.getTags();
			assertEquals(2, tags.size());
			assertTrue(tags.containsAll(Arrays.asList(ordinaryTag, namespacedTag)));
			assertOnlyTag(nestedReader.getTagsOnRevision(ordinaryCommit.getRevision()), ordinaryTag);
			assertOnlyTag(nestedReader.getTagsOnRevision(namespacedCommit.getRevision()), namespacedTag);

			verifyDirectoryEnumeration(nestedRepository, "tags/ordinary", never());
			verifyDirectoryEnumeration(nestedRepository, "tags/release", atLeastOnce());
			verifyDirectoryEnumeration(nestedRepository, "tags/release/1.0", never());
		} finally {
			nestedReader.getSVNRepository().closeSession();
			nestedWriter.getSVNRepository().closeSession();
		}
	}

	/*
	 * tags/broken -> expected creation entry /project/tags/broken
	 *
	 * missing case:       log contains only /project/tags/other
	 * contradictory case: exact path exists, but its change type is M instead of A
	 *
	 * Either case -> EVCSException; never recurse into tags/broken.
	 */
	@Test
	public void testTagDiscoveryRejectsInvalidCreationMetadata() throws Exception {
		String tagPath = "tags/broken";
		String repositoryTagPath = "/project/" + tagPath;

		Map<String, SVNLogEntryPath> missingCreationPath = new HashMap<>();
		missingCreationPath.put("/project/tags/other", new SVNLogEntryPath(
				"/project/tags/other", SVNLogEntryPath.TYPE_ADDED, null, -1, SVNNodeKind.DIR));
		assertInvalidTagCreationMetadata(tagPath, repositoryTagPath, missingCreationPath);

		Map<String, SVNLogEntryPath> contradictoryCreationPath = new HashMap<>();
		contradictoryCreationPath.put(repositoryTagPath, new SVNLogEntryPath(
				repositoryTagPath, SVNLogEntryPath.TYPE_MODIFIED, null, -1, SVNNodeKind.DIR));
		assertInvalidTagCreationMetadata(tagPath, repositoryTagPath, contradictoryCreationPath);
	}

	/*
	 * tags/tag1_name -> first-commit lookup
	 *
	 * old: log(0..HEAD, limit=0) -> transfer all history
	 * new: log(0..HEAD, limit=1) -> return only the creation entry
	 *
	 * The spy verifies changed paths=true, strict node history=true, and limit=1.
	 */
	@Test
	public void testTagFirstCommitLogIsBounded() throws Exception {
		vcsTestDataGen.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcsTestDataGen.createTag(null, TAG_NAME_1, TAG_MESSAGE_1, null);
		SVNRepository repository = spy(svn.getSVNRepository());
		svn.setSVNRepository(repository);

		vcs.getTags();

		verify(repository, atLeastOnce()).log(
				any(String[].class), eq(0L), eq(-1L), eq(true), eq(true), eq(1L),
				any(ISVNLogEntryHandler.class));
	}

	private void assertOnlyTag(List<VCSTag> tags, VCSTag expected) {
		assertEquals(1, tags.size());
		assertTrue(tags.contains(expected));
	}

	@SuppressWarnings("unchecked")
	private void verifyDirectoryEnumeration(SVNRepository repository, String path, VerificationMode mode)
			throws SVNException {
		verify(repository, mode).getDir(
				eq(path), eq(-1L), (SVNProperties) isNull(), (Collection<SVNDirEntry>) isNull());
	}

	@SuppressWarnings("unchecked")
	private void assertInvalidTagCreationMetadata(String tagPath, String repositoryTagPath,
			Map<String, SVNLogEntryPath> changedPaths) throws Exception {
		SVNRepository repository = mock(SVNRepository.class);
		SVNDirEntry tagDirectory = new SVNDirEntry(
				null, null, "broken", SVNNodeKind.DIR, 0, false, 1, null, null);
		SVNLogEntry firstEntry = new SVNLogEntry(changedPaths, 1, "author", new Date(), "message");

		doReturn(SVNNodeKind.DIR).when(repository).checkPath(SVNVCS.TAGS_PATH, -1L);
		doAnswer(invocation -> SVNVCS.TAGS_PATH.equals(invocation.getArguments()[0])
				? Collections.singletonList(tagDirectory) : Collections.emptyList())
				.when(repository).getDir(anyString(), anyLong(),
						(SVNProperties) isNull(), (Collection<SVNDirEntry>) isNull());
		doReturn(Collections.singletonList(firstEntry)).when(repository).log(
				any(String[].class), (Collection<SVNLogEntry>) isNull(),
				eq(0L), eq(-1L), eq(true), eq(true));
		doAnswer(invocation -> {
			((ISVNLogEntryHandler) invocation.getArguments()[6]).handleLogEntry(firstEntry);
			return 1L;
		}).when(repository).log(
				any(String[].class), eq(0L), eq(-1L), eq(true), eq(true), eq(1L),
				any(ISVNLogEntryHandler.class));
		doReturn(repositoryTagPath.substring(1)).when(repository).getRepositoryPath(tagPath);
		svn.setSVNRepository(repository);

		try {
			vcs.getTags();
			fail("Expected invalid tag creation metadata to fail discovery");
		} catch (EVCSException e) {
			assertTrue(e.getCause() instanceof SVNException);
		}
		verifyDirectoryEnumeration(repository, tagPath, never());
	}
	
	@Test
	public void testGetTagsOnRevisionExceptions() throws Exception {
		doThrow(testSVNException).when(svn).getTags(anyString());
		try {
			vcs.getTagsOnRevision("");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}
	
	@Test
	public void testGetTagsExceptions() throws Exception {
		doThrow(testSVNException).when(svn).getTags((String) isNull());
		try {
			vcs.getTags();
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@Test
	public void testRemoveTagExceptions() throws SVNException {
		SVNClientManager mockedManager = spy(svn.getClientManager());
		SVNCommitClient mockedCommitClient = mock(SVNCommitClient.class);
		svn.setClientManager(mockedManager);
		doReturn(mockedCommitClient).when(mockedManager).getCommitClient();
		doThrow(testSVNException).when(mockedCommitClient).doDelete(any(SVNURL[].class), (String) isNull());
		try {
			vcs.removeTag("");
			fail();
		} catch (EVCSException e) {
			checkEVCSException(e);
		}
	}

	@Test
	public void testTransientTransportFailureClassification() {
		assertTrue(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_DAV_SOCK_INIT)));
		assertTrue(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_DAV_CREATING_REQUEST)));
		assertTrue(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_DAV_CONN_TIMEOUT)));
		assertTrue(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_SVN_CONNECTION_CLOSED)));
		assertTrue(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_SVN_IO_ERROR)));
		assertTrue(SVNVCS.isTransientTransportFailure(new RuntimeException(new SocketException("reset"))));
		assertTrue(SVNVCS.isTransientTransportFailure(new RuntimeException(new EOFException("eof"))));

		assertFalse(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.RA_NOT_AUTHORIZED)));
		assertFalse(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.FS_NOT_FOUND)));
		assertFalse(SVNVCS.isTransientTransportFailure(svnFailure(SVNErrorCode.WC_LOCKED)));
		assertFalse(SVNVCS.isTransientTransportFailure(new IllegalStateException("permanent")));
	}

	@Test
	public void testCheckoutRetriesTransientFailureAndReportsIt() throws Exception {
		SVNUpdateClient updateClient = mockUpdateClient();
		doReturn(false).when(svn).isWorkingCopyInited(any(File.class));
		SVNException failure = svnFailure(SVNErrorCode.RA_DAV_CONN_TIMEOUT);
		doThrow(failure).doReturn(1L).when(updateClient).doCheckout(
				any(SVNURL.class), any(File.class), any(SVNRevision.class), any(SVNRevision.class),
				any(SVNDepth.class), anyBoolean());
		vcs.setRetryStatusReporter(this::captureRetry);

		vcs.checkout(null, new File(WORKSPACE_DIR, "retry-checkout").getAbsolutePath(), null);

		verify(updateClient, times(2)).doCheckout(
				any(SVNURL.class), any(File.class), any(SVNRevision.class), any(SVNRevision.class),
				any(SVNDepth.class), anyBoolean());
		assertRetryReport("SVN checkout", failure);
	}

	@Test
	public void testSwitchRetriesTransientFailureAndReportsIt() throws Exception {
		SVNUpdateClient updateClient = mockUpdateClient();
		doReturn(true).when(svn).isWorkingCopyInited(any(File.class));
		SVNException failure = svnFailure(SVNErrorCode.RA_SVN_CONNECTION_CLOSED);
		doThrow(failure).doReturn(1L).when(updateClient).doSwitch(
				any(File.class), any(SVNURL.class), any(SVNRevision.class), any(SVNRevision.class),
				any(SVNDepth.class), anyBoolean(), anyBoolean());
		vcs.setRetryStatusReporter(this::captureRetry);

		vcs.checkout(null, new File(WORKSPACE_DIR, "retry-switch").getAbsolutePath(), null);

		verify(updateClient, times(2)).doSwitch(
				any(File.class), any(SVNURL.class), any(SVNRevision.class), any(SVNRevision.class),
				any(SVNDepth.class), anyBoolean(), anyBoolean());
		assertRetryReport("SVN switch", failure);
	}

	@Test
	public void testSparseUpdateRetriesTransientFailureAndReportsIt() throws Exception {
		SVNUpdateClient updateClient = mockUpdateClient();
		doReturn(true).when(svn).isWorkingCopyInited(any(File.class));
		SVNException failure = svnFailure(SVNErrorCode.RA_SVN_IO_ERROR);
		doThrow(failure).doReturn(new long[] {1L}).when(updateClient).doUpdate(
				any(File[].class), any(SVNRevision.class), any(SVNDepth.class),
				anyBoolean(), anyBoolean(), anyBoolean());
		vcs.setRetryStatusReporter(this::captureRetry);

		vcs.sparseCheckout(null, new File(WORKSPACE_DIR, "retry-sparse-update").getAbsolutePath(), null, "dir");

		verify(updateClient, times(2)).doUpdate(
				any(File[].class), any(SVNRevision.class), any(SVNDepth.class),
				anyBoolean(), anyBoolean(), anyBoolean());
		assertRetryReport("SVN update", failure);
	}

	@Test
	public void testRemoteMutationsAreNotRetried() throws Exception {
		SVNClientManager manager = spy(svn.getClientManager());
		svn.setClientManager(manager);
		SVNCopyClient copyClient = mock(SVNCopyClient.class);
		SVNCommitClient commitClient = mock(SVNCommitClient.class);
		doReturn(copyClient).when(manager).getCopyClient();
		doReturn(commitClient).when(manager).getCommitClient();
		SVNException failure = svnFailure(SVNErrorCode.RA_DAV_CONN_TIMEOUT);
		AtomicInteger reports = new AtomicInteger();
		vcs.setRetryStatusReporter((operation, actualFailure) -> reports.incrementAndGet());

		doThrow(failure).when(copyClient).doCopy(any(SVNCopySource[].class), any(SVNURL.class),
				anyBoolean(), anyBoolean(), anyBoolean(), anyString(), any(SVNProperties.class));
		try {
			vcs.createBranch(null, "not-retried", "create");
			fail("EVCSException is not thrown");
		} catch (EVCSException actual) {
			assertSame(failure, actual.getCause());
		}
		verify(copyClient, times(1)).doCopy(any(SVNCopySource[].class), any(SVNURL.class),
				anyBoolean(), anyBoolean(), anyBoolean(), anyString(), any(SVNProperties.class));

		doThrow(failure).when(commitClient).doDelete(any(SVNURL[].class), anyString());
		try {
			vcs.deleteBranch("not-retried", "delete");
			fail("EVCSException is not thrown");
		} catch (EVCSException actual) {
			assertSame(failure, actual.getCause());
		}
		verify(commitClient, times(1)).doDelete(any(SVNURL[].class), anyString());

		doThrow(failure).when(commitClient).doCommit(any(File[].class), anyBoolean(), anyString(),
				any(SVNProperties.class), any(String[].class), anyBoolean(), anyBoolean(), any(SVNDepth.class));
		try {
			vcs.setFileContent(null, "not-retried.txt", "content", "commit");
			fail("EVCSException is not thrown");
		} catch (EVCSException actual) {
			assertSame(failure, actual.getCause());
		}
		verify(commitClient, times(1)).doCommit(any(File[].class), anyBoolean(), anyString(),
				any(SVNProperties.class), any(String[].class), anyBoolean(), anyBoolean(), any(SVNDepth.class));
		assertEquals(0, reports.get());
	}

	private SVNUpdateClient mockUpdateClient() {
		SVNClientManager manager = spy(svn.getClientManager());
		svn.setClientManager(manager);
		SVNUpdateClient updateClient = mock(SVNUpdateClient.class);
		doReturn(updateClient).when(manager).getUpdateClient();
		return updateClient;
	}

	private void captureRetry(String operation, Throwable failure) {
		reportedRetryOperation = operation;
		reportedRetryFailure = failure;
	}

	private void assertRetryReport(String operation, Throwable failure) {
		assertEquals(operation, reportedRetryOperation);
		assertSame(failure, reportedRetryFailure);
	}

	private SVNException svnFailure(SVNErrorCode errorCode) {
		return new SVNException(SVNErrorMessage.create(errorCode, "test transport failure"));
	}
}
