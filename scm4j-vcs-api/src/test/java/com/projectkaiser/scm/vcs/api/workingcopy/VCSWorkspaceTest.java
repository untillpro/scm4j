package com.projectkaiser.scm.vcs.api.workingcopy;

import static org.junit.Assert.*;

import org.junit.Test;

import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

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
}
