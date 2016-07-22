package com.projectkaiser.scm.vcs.api.workingcopy;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public class VCSWorkspaceTest extends VCSTestBase {

	@Test
	public void testWorkspace() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		assertTrue(w.getHomeFolder().exists());
		assertNotNull(w.getVCSRepositoryWorkspace(""));
	}
}
