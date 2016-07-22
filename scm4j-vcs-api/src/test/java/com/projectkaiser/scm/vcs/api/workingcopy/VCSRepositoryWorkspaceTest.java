package com.projectkaiser.scm.vcs.api.workingcopy;

import static org.junit.Assert.*;

import org.junit.Test;

import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public class VCSRepositoryWorkspaceTest extends VCSTestBase {

	@Test
	public void testVCSRepository() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = new VCSRepositoryWorkspace("c:\\test\\utils\\", w);
		assertTrue(r.getRepoFolder().exists());
		assertTrue(r.getRepoFolder().getParentFile().getPath().equals(WORKSPACE_DIR));
		assertEquals(r.getWorkspace(), w);
		
		IVCSRepositoryWorkspace r1 = new VCSRepositoryWorkspace("c:/test/utils/", w);
		assertEquals(r1.getRepoFolder().getPath(), r.getRepoFolder().getPath());
	}
}
