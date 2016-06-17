package com.projectkaiser.scm.vcs.api.workingcopy;

import static org.junit.Assert.*;

import org.junit.Test;

import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepository;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSRepository;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public class VCSRepositoryTest extends VCSTestBase {

	@Test
	public void testVCSRepository() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepository r = new VCSRepository("c:\\test\\utils\\", w);
		assertTrue(r.getRepoFolder().exists());
		assertTrue(r.getRepoFolder().getParentFile().getPath().equals(WORKSPACE_DIR));
		assertEquals(r.getWorkspace(), w);
		
		IVCSRepository r1 = new VCSRepository("c:/test/utils/", w);
		assertEquals(r1.getRepoFolder().getPath(), r.getRepoFolder().getPath());
	}
}
