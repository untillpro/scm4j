package org.scm4j.vcs.api.workingcopy;

import static org.junit.Assert.*;

import org.junit.Test;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

public class VCSRepositoryWorkspaceTest extends VCSWCTestBase {

	@Test
	public void testVCSRepository() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = new VCSRepositoryWorkspace(TEST_REPO_URL, w);
		assertEquals(r.getRepoUrl(), TEST_REPO_URL);
		assertTrue(r.getRepoFolder().exists());
		assertTrue(r.getRepoFolder().getParentFile().getPath().equals(WORKSPACE_DIR));
		assertEquals(r.getWorkspace(), w);
		
		IVCSRepositoryWorkspace r1 = new VCSRepositoryWorkspace(TEST_REPO_URL, w);
		assertEquals(r1.getRepoFolder().getPath(), r.getRepoFolder().getPath());
		
		try (IVCSLockedWorkingCopy lwc = r.getVCSLockedWorkingCopy()) {
			assertEquals(lwc.getVCSRepository(), r);
		}
	}
}
