package org.scm4j.vcs.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSChangeType;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.abstracttest.VCSAbstractTest;
import org.scm4j.vcs.api.exceptions.EVCSException;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

public class GitVCSTest extends VCSAbstractTest {
	private Repository localGitRepo;
	private ProxySelector proxySelectorBackup;
	private final RuntimeException testGitResetException = new RuntimeException("test exception on git.reset()");
	private GitVCS git;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		Git fileGitRepo = GitVCSUtils.createRepository(new File(REPO_DIR, repoName));
		localGitRepo = fileGitRepo.getRepository();
		proxySelectorBackup = ProxySelector.getDefault();
		ProxySelector.setDefault(null);
		git = (GitVCS) vcs;
	}

	@After
	public void tearDown() throws IOException {
		localGitRepo.close();
	    FileUtils.deleteDirectory(localGitRepo.getDirectory());
		ProxySelector.setDefault(proxySelectorBackup);
	}

	@Override
	protected IVCS getVCS(IVCSRepositoryWorkspace mockedVCSRepo) {
		return Mockito.spy(new GitVCS(mockedVCSRepo));
	}

	@Override
	protected void setMakeFailureOnVCSReset(Boolean doMakeFailure) throws Exception {
		Git mockedGit;
		if (doMakeFailure) {
			mockedGit = Mockito.spy(((GitVCS) vcs).getLocalGit(mockedLWC));
			Mockito.doReturn(mockedGit).when(((GitVCS) vcs)).getLocalGit(mockedLWC);
			Mockito.doThrow(testGitResetException).when(mockedGit).reset();
		} else {
			Mockito.doCallRealMethod().when(((GitVCS) vcs)).getLocalGit(mockedLWC);
			mockedGit = null;
		}
	}

	@Override
	protected String getVCSTypeString() {
		return GitVCS.GIT_VCS_TYPE_STRING;
	}

	@Test
	public void testSetCredentials() {
		vcs.setCredentials("user", "password");
		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password p = new CredentialItem.Password();
		assertTrue(git.getCredentials().get(null, u, p));
		assertEquals(u.getValue(), "user");
		assertEquals(new String(p.getValue()), "password");
	}

	@Test
	public void testProxyAuth() throws Exception {
		PasswordAuthentication initialAuth = Authenticator.requestPasswordAuthentication(InetAddress.getByName("localhost"),
				123, "http", "", "");
		IVCS vcs = new GitVCS(localVCSWorkspace.getVCSRepositoryWorkspace("localhost"));
		vcs.setProxy("localhost", 123, "username", "pwd");

		PasswordAuthentication resultAuth = Authenticator.requestPasswordAuthentication(
				InetAddress.getByName("localhost"), 123, "http", "", "");
		assertEquals(resultAuth.getUserName(), "username");
		assertEquals(new String(resultAuth.getPassword()), "pwd");

		resultAuth = Authenticator.requestPasswordAuthentication(
				InetAddress.getByName("localhost"), 124, "http", "", "");
		assertEquals(resultAuth, initialAuth);
	}

	@Test
	public void testProxySelector() throws URISyntaxException {
		vcs.setProxy("localhost", 123, "username", "pwd");
		ProxySelector actualPS = ProxySelector.getDefault();
		List<Proxy> proxies = actualPS.select(new URI(vcs.getRepoUrl()));
		assertTrue(proxies.size() == 1);
		Proxy actualP = proxies.get(0);
		assertTrue(actualP.address() instanceof InetSocketAddress);
		InetSocketAddress isa = (InetSocketAddress) actualP.address();
		assertEquals(isa.getHostName(), "localhost");
		assertEquals(isa.getPort(), 123);
	}

	@Test
	public void testParentProxySelectorUsage() throws URISyntaxException {
		ProxySelector mockedPS = Mockito.mock(ProxySelector.class);
		ProxySelector.setDefault(mockedPS);
		vcs.setProxy("localhost", 123, "username", "pwd");
		ProxySelector actualPS = ProxySelector.getDefault();
		URI uri = new URI("http://unknown");
		actualPS.select(uri);
		Mockito.verify(mockedPS).select(uri);
	}

	@Test
	public void testNullProxySelector() throws URISyntaxException {
		ProxySelector.setDefault(null);
		vcs.setProxy("localhost", 123, "username", "pwd");
		ProxySelector actualPS = ProxySelector.getDefault();
		List<Proxy> proxies = actualPS.select(new URI("http://unknown"));
		assertTrue(proxies.size() == 1);
		assertEquals(proxies.get(0), Proxy.NO_PROXY);
	}

	@Test
	public void testParentSelectorCallOnConnectFailed() throws URISyntaxException {
		ProxySelector mockedPS = Mockito.mock(ProxySelector.class);
		ProxySelector.setDefault(mockedPS);
		vcs.setProxy("localhost", 123, "username", "pwd");
		ProxySelector actualPS = ProxySelector.getDefault();
		URI testURI = new URI("http://proxy.net");
		SocketAddress testSA = InetSocketAddress.createUnresolved("http://proxy.net", 123);
		IOException testException = new IOException("test exception");
		actualPS.connectFailed(testURI, testSA, testException);
		Mockito.verify(mockedPS).connectFailed(testURI, testSA, testException);
	}
	@Test
	public void testNoParentSelectorOnConnectFailed() throws URISyntaxException {
		ProxySelector.setDefault(null);
		vcs.setProxy("localhost", 123, "username", "pwd");
		ProxySelector actualPS = Mockito.spy(ProxySelector.getDefault());
		URI testURI = new URI("http://proxy.net");
		SocketAddress testSA = InetSocketAddress.createUnresolved("http://proxy.net", 123);
		IOException testException = new IOException("test exception");
		actualPS.connectFailed(testURI, testSA, testException);
		Mockito.verify(actualPS).connectFailed(testURI, testSA, testException);
		Mockito.verifyNoMoreInteractions(actualPS);
	}

	@Test
	public void testVCSTypeString() {
		assertEquals(vcs.getVCSTypeString(), GitVCS.GIT_VCS_TYPE_STRING);
	}

	@Test
	public void testExceptions() throws Exception {
		GitAPIException eApi = new GitAPIException("test git exception") {};
		Exception eCommon = new Exception("test common exception");
		for (Method m : ArrayUtils.addAll(IVCS.class.getDeclaredMethods(), GitVCS.class.getMethod("createUnannotatedTag", String.class, String.class, String.class))) {
			Object[] params = new Object[m.getParameterTypes().length];
			Integer i = 0;
			for (Class<?> clazz : m.getParameterTypes()) {
				params[i] = clazz.isPrimitive() ? 0: null;
				i++;
			}
			testExceptionThrowing(eApi, m, params);

			testExceptionThrowing(eCommon, m, params);
		}
	}

	private void testExceptionThrowingNoMock(Exception testException, Method m, Object[] params) throws Exception {
		try {
			m.invoke(vcs, params);
			if (!m.getName().equals("checkout") && wasGetLocalGitInvoked()) {
				fail();
			}
		} catch (InvocationTargetException e) {
			if (!m.getName().equals("checkout") && wasGetLocalGitInvoked()) {
				// InvocationTargetException <- EVCSException <- GitAPIException
				assertTrue(e.getCause().getCause().getClass().isAssignableFrom(testException.getClass()));
				assertTrue(e.getCause().getMessage().contains(testException.getMessage()));
			}
		} catch (Exception e) {
			if (!m.getName().equals("checkout") && wasGetLocalGitInvoked()) {
				fail();
			}
		}
	}

	private void testExceptionThrowing(Exception testException, Method m, Object[] params) throws Exception {
		Mockito.reset(git);
		Mockito.doThrow(testException).when(git).getLocalGit(mockedLWC);
		testExceptionThrowingNoMock(testException, m, params);
	}

	private Boolean wasGetLocalGitInvoked() throws Exception {
		try {
			Mockito.verify(git).getLocalGit(mockedLWC);
			return true;
		} catch (WantedButNotInvoked e1) {
			return false;
		}
	}

	@Test
	public void testDefaultChangeTypeToVCSType() {
		for (DiffEntry.ChangeType ct : DiffEntry.ChangeType.values()) {
			if (ct != DiffEntry.ChangeType.ADD && ct != DiffEntry.ChangeType.DELETE && ct != DiffEntry.ChangeType.MODIFY) {
				assertEquals(git.gitChangeTypeToVCSChangeType(ct), VCSChangeType.UNKNOWN);
			}
		}
	}

	@Test
	public void testGitVCSUtilsCreation() {
		assertNotNull(new GitVCSUtils());
	}

	@Test
	public void testNullBranchUsesRemoteDefaultAfterWorkingCopySwitch() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_2, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE);
		vcs.checkout(NEW_BRANCH, mockedLWC.getFolder().getPath(), null);

		assertEquals(LINE_1, vcs.getFileContent(null, FILE1_NAME, null));
	}

	@Test
	public void testCustomRemoteDefaultIsSupported() throws Exception {
		File remoteDir = new File(TEST_BASE_DIR, "custom-default-repo");
		try (Git ignored = GitVCSUtils.createRepository(remoteDir, "stable")) {
			// Repository is ready for access through a separate reusable workspace.
		}
		GitVCS customVcs = createVCS(remoteDir, "custom-default-workspace");

		customVcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);

		assertEquals(LINE_1, customVcs.getFileContent(null, FILE1_NAME, null));
	}

	@Test
	public void testNonSymbolicRemoteHeadIsRejected() throws Exception {
		File remoteDir = new File(TEST_BASE_DIR, "detached-head-repo");
		try (Git remote = GitVCSUtils.createRepository(remoteDir)) {
			ObjectId headCommit = remote.getRepository().resolve(Constants.HEAD);
			RefUpdate head = remote.getRepository().updateRef(Constants.HEAD, true);
			head.setNewObjectId(headCommit);
			assertEquals(RefUpdate.Result.FORCED, head.forceUpdate());
		}
		GitVCS detachedHeadVcs = createVCS(remoteDir, "detached-head-workspace");

		try {
			detachedHeadVcs.getHeadCommit(null);
			fail("Expected a repository with a non-symbolic HEAD to be rejected");
		} catch (EVCSException e) {
			assertTrue(e.getMessage().contains("Could not determine the default branch"));
			assertTrue(e.getMessage().contains("remote HEAD is not symbolic"));
		}
	}

	private GitVCS createVCS(File remoteDir, String workspaceName) {
		IVCSWorkspace workspace = new VCSWorkspace(new File(TEST_BASE_DIR, workspaceName).getPath());
		return new GitVCS(workspace.getVCSRepositoryWorkspace(remoteDir.toURI().toString()));
	}

	@Test
	public void testCheckoutUsesJGit43CompatibleLineEndings() throws Exception {
		File remoteDir = new File(TEST_BASE_DIR, "line-endings-repo");
		try (Git remote = GitVCSUtils.createRepository(remoteDir)) {
			FileUtils.write(new File(remoteDir, ".gitattributes"), "* text=auto\n", StandardCharsets.UTF_8);
			FileUtils.write(new File(remoteDir, "text.txt"), "first\nsecond\n", StandardCharsets.UTF_8);
			remote.add().addFilepattern(".").call();
			remote.commit().setMessage("text files added").call();
		}
		GitVCS compatibleVcs = createVCS(remoteDir, "line-endings-workspace");
		File checkoutDir = new File(TEST_BASE_DIR, "line-endings-checkout");

		compatibleVcs.checkout(null, checkoutDir.getPath(), null);

		assertEquals("first\nsecond\n", FileUtils.readFileToString(
				new File(checkoutDir, "text.txt"), StandardCharsets.UTF_8));
		try (Git checkout = compatibleVcs.getLocalGit(checkoutDir.getPath());
			 Repository checkoutRepo = checkout.getRepository()) {
			assertEquals("lf", checkoutRepo.getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_EOL));

			// Compatibility configuration is retried rather than overwriting local work.
			checkoutRepo.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_EOL, "native");
			checkoutRepo.getConfig().save();
		}
		FileUtils.write(new File(checkoutDir, "text.txt"), "local change\n", StandardCharsets.UTF_8);
		try (Git checkout = compatibleVcs.getLocalGit(checkoutDir.getPath());
			 Repository checkoutRepo = checkout.getRepository()) {
			assertEquals("native", checkoutRepo.getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_EOL));
			assertEquals("local change\n", FileUtils.readFileToString(
					new File(checkoutDir, "text.txt"), StandardCharsets.UTF_8));
		}

		FileUtils.write(new File(checkoutDir, "text.txt"), "first\nsecond\n", StandardCharsets.UTF_8);
		try (Git checkout = compatibleVcs.getLocalGit(checkoutDir.getPath());
			 Repository checkoutRepo = checkout.getRepository()) {
			assertEquals("lf", checkoutRepo.getConfig().getString(
					ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_EOL));
		}
	}

	@Test
	public void testGetTagsUnannotated() throws Exception {
		// create tag in different working copy
		try (IVCSLockedWorkingCopy lwc = localVCSRepo.getVCSLockedWorkingCopyTemp()) {
			IVCSWorkspace tempWS = new VCSWorkspace(lwc.getFolder().toString());
			IVCSRepositoryWorkspace tempRWS = tempWS.getVCSRepositoryWorkspace(vcs.getRepoUrl());
			GitVCS tempVCS = new GitVCS(tempRWS);
			tempVCS.createUnannotatedTag(null, TAG_NAME_1, null);
		}
		List<VCSTag> tags = vcs.getTags();
		assertTrue(tags.size() == 1);
		VCSTag tag = tags.get(0);
		assertNull(tag.getAuthor());
		assertNull(tag.getTagMessage());
		assertEquals(tag.getTagName(), TAG_NAME_1);
		assertEquals(tag.getRelatedCommit(), vcs.getHeadCommit(null));
	}

	@Test
	public void testCheckoutExceptions() throws Exception {
		GitAPIException eApi = new GitAPIException("test git exception") {};
		Exception eCommon = new Exception("test common exception");
		Mockito.doThrow(eCommon).when(git).getLocalGit((String) null);
		try {
			git.checkout(null, null, null);
			fail();
		} catch (RuntimeException e) {
			assertTrue(e.getCause().getClass().isAssignableFrom(eCommon.getClass()));
			assertTrue(e.getCause().getMessage().contains(eCommon.getMessage()));
		}

		Mockito.doThrow(eApi).when(git).getLocalGit((String) null);
		try {
			git.checkout(null, null, null);
			fail();
		} catch (EVCSException e) {
			assertTrue(e.getCause().getClass().isAssignableFrom(eApi.getClass()));
			assertTrue(e.getCause().getMessage().contains(eApi.getMessage()));
		}
	}

	@Test
	public void testSparseCheckoutProcessLaunchFailure() throws Exception {
		vcsTestDataGen.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_1, FILE3_ADDED_COMMIT_MESSAGE);
		IOException failure = new IOException("test native git launch failure");
		Mockito.doThrow(failure).when(git).startGitProcess(Mockito.any(File.class),
				Mockito.eq("sparse-checkout"), Mockito.eq("init"), Mockito.eq("--cone"));

		try {
			git.sparseCheckout(null, new File(TEST_BASE_DIR, "sparse-launch-failure").getPath(), null, "folder");
			fail("Expected native Git launch failure");
		} catch (EVCSException e) {
			assertEquals(failure, e.getCause());
		}
	}

	@Test
	public void testSparseCheckoutNonZeroExitFailure() throws Exception {
		vcsTestDataGen.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_1, FILE3_ADDED_COMMIT_MESSAGE);
		Process failedProcess = Mockito.mock(Process.class);
		Mockito.when(failedProcess.getInputStream()).thenReturn(new ByteArrayInputStream(
				"test native git failure".getBytes(StandardCharsets.UTF_8)));
		Mockito.when(failedProcess.waitFor()).thenReturn(17);
		Mockito.doReturn(failedProcess).when(git)
				.startGitProcess(Mockito.any(File.class), Mockito.eq("sparse-checkout"),
						Mockito.eq("init"), Mockito.eq("--cone"));

		try {
			git.sparseCheckout(null, new File(TEST_BASE_DIR, "sparse-exit-failure").getPath(), null, "folder");
			fail("Expected native Git non-zero exit failure");
		} catch (EVCSException e) {
			assertTrue(e.getMessage().contains("17"));
			assertTrue(e.getMessage().contains("test native git failure"));
		}
	}

	@Test
	public void testGetTagsOnRevisionUnannotated() throws Exception {
		VCSCommit c1 = vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		VCSCommit c2 = vcs.setFileContent(null, FILE1_NAME, LINE_2, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE + " " + LINE_2);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		VCSCommit c3 = vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_3, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE + " " + LINE_3);

		VCSTag tag1 = git.createUnannotatedTag(null, TAG_NAME_1, c1.getRevision());
		VCSTag tag2 = git.createUnannotatedTag(null, TAG_NAME_2, c1.getRevision());
		VCSTag tag3 = git.createUnannotatedTag(NEW_BRANCH, TAG_NAME_3, c3.getRevision());

		assertTrue(vcs.getTagsOnRevision(c1.getRevision()).containsAll(Arrays.asList(
				tag1, tag2)));
		assertTrue(vcs.getTagsOnRevision(c2.getRevision()).isEmpty());
		assertTrue(vcs.getTagsOnRevision(c3.getRevision()).containsAll(Arrays.asList(
				tag3)));
	}

	@Test
	public void testPruneOnBranchCreate() throws Exception {
		// create a local branch, push failed
		RuntimeException eCommon = new RuntimeException("test common exception");
		Mockito.doThrow(eCommon).when(git).push(Mockito.any(Git.class), Mockito.any(RefSpec.class));
		try {
			vcs.createBranch(null, "branch", "branch created");
			fail();
		} catch (RuntimeException e) {
		}

		Mockito.doCallRealMethod().when(git).push(Mockito.any(Git.class), Mockito.any(RefSpec.class));

		// expect no EVCSBRanchExists exception
		vcs.createBranch(null, "branch", "branch created");
		assertTrue(vcs.getBranches("").contains("branch"));
	}

	@Test
	public void testGetBranchesExcludesRemoteHead() throws Exception {
		String remoteHead = "refs/remotes/origin/HEAD";
		try (Git localGit = git.getLocalGit(mockedLWC)) {
			localGit.getRepository().updateRef(remoteHead).link("refs/remotes/origin/main");
			assertTrue(localGit.getRepository().exactRef(remoteHead).isSymbolic());
		}

		assertFalse(vcs.getBranches("").contains("HEAD"));
	}

	@Test
	public void testPruneOnTagCreate() throws Exception {
		// create a local tag, push failed
		RuntimeException eCommon = new RuntimeException("test common exception");
		Mockito.doThrow(eCommon).when(git).push(Mockito.any(Git.class), Mockito.any(RefSpec.class));

		try {
			vcs.createTag(null, "tag", "tag desc", null);
			fail();
		} catch (RuntimeException e) {
		}

		Mockito.doCallRealMethod().when(git).push(Mockito.any(Git.class), Mockito.any(RefSpec.class));

		// expect no exceptions
		vcs.createTag(null, "tag", "tag desc", null);
		assertEquals("tag", vcs.getTags().get(0).getTagName());
	}
}
