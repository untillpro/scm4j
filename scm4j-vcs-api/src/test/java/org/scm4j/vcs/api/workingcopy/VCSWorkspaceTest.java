package org.scm4j.vcs.api.workingcopy;

import org.junit.Test;

import static org.junit.Assert.*;

public class VCSWorkspaceTest extends VCSWCTestBase {

	@Test
	public void testWorkspace() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		assertEquals(w.getHomeFolder().getAbsolutePath(), WORKSPACE_DIR);
		assertTrue(w.getHomeFolder().exists());
		assertNotNull(w.getVCSRepositoryWorkspace(""));
		assertEquals(w.getVCSRepositoryWorkspace("").getWorkspace(), w);
		assertEquals(w.getVCSRepositoryWorkspace(TEST_REPO_URL).getRepoUrl(), TEST_REPO_URL);
	}

	@Test
	public void testWorskpaceDefaultDir() {
		IVCSWorkspace w = new VCSWorkspace();
		assertEquals(w.getHomeFolder().getPath(), VCSWorkspace.DEFAULT_WORKSPACE_DIR);
	}
}
