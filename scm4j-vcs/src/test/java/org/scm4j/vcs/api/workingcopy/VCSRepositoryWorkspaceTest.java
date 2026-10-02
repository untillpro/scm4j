package org.scm4j.vcs.api.workingcopy;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class VCSRepositoryWorkspaceTest extends VCSWCTestBase {
	private static final String COMPONENT_SUBFOLDER = "components/driver";

	@Test
	public void testVCSRepository() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertEquals(r.getRepoUrl(), TEST_REPO_URL);
		assertTrue(r.getRepoFolder().exists());
		assertTrue(r.getRepoFolder().getParentFile().getPath().equals(WORKSPACE_DIR));
		assertEquals(r.getWorkspace(), w);

		IVCSRepositoryWorkspace r1 = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertEquals(r1.getRepoFolder().getPath(), r.getRepoFolder().getPath());

		try (IVCSLockedWorkingCopy lwc = r.getVCSLockedWorkingCopy()) {
			assertEquals(lwc.getVCSRepository(), r);
		}
	}

	@Test
	public void testToString() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertTrue(r.toString().contains(WORKSPACE_DIR));
	}

	@Test
	public void testRepositoryFolderNameIncludesSubfolderOnlyForMonorepo() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace rootRepository = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace firstComponent = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, COMPONENT_SUBFOLDER);
		IVCSRepositoryWorkspace secondComponent = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, "components/sibling");

		assertEquals("test.repo.url", rootRepository.getRepoFolder().getName());
		assertEquals("test.repo.url_components_driver", firstComponent.getRepoFolder().getName());
		assertEquals("test.repo.url_components_sibling", secondComponent.getRepoFolder().getName());
		assertNotEquals(firstComponent.getRepoFolder(), secondComponent.getRepoFolder());
	}

	@Test
	public void testRootAndComponentWorkingCopiesAreReusable() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace rootRepository = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace componentRepository = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, COMPONENT_SUBFOLDER);
		assertNotEquals(rootRepository.getRepoFolder(), componentRepository.getRepoFolder());

		assertWorkingCopiesAreReusableAfterClose(rootRepository);
		assertWorkingCopiesAreReusableAfterClose(componentRepository);
	}

	private void assertWorkingCopiesAreReusableAfterClose(IVCSRepositoryWorkspace repository) throws Exception {
		File reusableFolder;
		File reusableLockFile;
		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			reusableFolder = workingCopy.getFolder();
			reusableLockFile = workingCopy.getLockFile();
		}

		// the repository is closed but lock files are kept -> the repo is reusable
		assertTrue(reusableFolder.exists());
		assertTrue(reusableLockFile.exists());
		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			// another repository is created but the same lock files are taken -> the repo is reused here
			assertEquals(reusableFolder, workingCopy.getFolder());
			assertEquals(reusableLockFile, workingCopy.getLockFile());
		}
	}

	@Test
	public void testHTTPAndHTTPSFolderPrefixesStripping() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		String repoUrl = "test.repo.url";
		String httpProto = "http://";
		String httpsProto = "https://";
		String fileProto1 = "file:/";
		String fileProto2 = "file://";
		String fileProto3 = "file:///";

		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(httpProto + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(httpsProto + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto1 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto2 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto3 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());
	}
}
