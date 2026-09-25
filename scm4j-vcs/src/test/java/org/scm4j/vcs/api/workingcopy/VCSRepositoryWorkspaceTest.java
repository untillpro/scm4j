package org.scm4j.vcs.api.workingcopy;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	public void testRootWorkingCopiesAreReusableAndComponentWorkingCopiesAreDisposable() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace rootRepository = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		File reusableFolder;
		try (IVCSLockedWorkingCopy workingCopy = rootRepository.getVCSLockedWorkingCopy()) {
			reusableFolder = workingCopy.getFolder();
		}
		assertTrue(reusableFolder.exists());
		try (IVCSLockedWorkingCopy workingCopy = rootRepository.getVCSLockedWorkingCopy()) {
			assertEquals(reusableFolder, workingCopy.getFolder());
		}

		IVCSRepositoryWorkspace componentRepository = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, COMPONENT_SUBFOLDER);
		File disposableFolder;
		File disposableLockFile;
		try (IVCSLockedWorkingCopy workingCopy = componentRepository.getVCSLockedWorkingCopy()) {
			disposableFolder = workingCopy.getFolder();
			disposableLockFile = workingCopy.getLockFile();
			assertTrue(disposableFolder.exists());
			assertTrue(disposableLockFile.exists());
		}
		assertFalse(disposableFolder.exists());
		assertFalse(disposableLockFile.exists());
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
