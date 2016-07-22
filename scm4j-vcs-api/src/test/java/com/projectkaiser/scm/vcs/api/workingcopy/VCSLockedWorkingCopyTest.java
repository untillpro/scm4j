package com.projectkaiser.scm.vcs.api.workingcopy;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.projectkaiser.scm.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSLockedWorkingCopyState;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public class VCSLockedWorkingCopyTest extends VCSTestBase {
	
	private IVCSWorkspace w;
	private IVCSRepositoryWorkspace r;
	
	@Before
	public void setUp() {
		w = new VCSWorkspace(WORKSPACE_DIR);
		r = new VCSRepositoryWorkspace(TEST_REPO_URL, w);
	}
	
	@Test 
	public void testBasicWorkspaceWorkflow() throws Exception {
		for (Integer i = 1; i < 3; i++) {
			try (IVCSLockedWorkingCopy wc = i == 1 ? new VCSLockedWorkingCopy(r) 
						: new VCSLockedWorkingCopy(WORKSPACE_DIR, TEST_REPO_URL)) {
				if (i == 1) {
					assertEquals(wc.getVCSRepository(), r);
				}
				assertTrue(wc.getFolder().exists());
				assertEquals(wc.getState(),  VCSLockedWorkingCopyState.LOCKED);
				assertFalse(wc.getCorrupted());
				assertTrue(wc.getLockFile().exists());
				assertTrue(wc.getLockFile().getName().equals(VCSLockedWorkingCopy.LOCK_FILE_PREFIX + wc.getFolder().getName()));
				wc.close();
				assertEquals(wc.getState(), VCSLockedWorkingCopyState.OBSOLETE);
				wc.close(); //nothing should happen
				assertEquals(wc.getState(), VCSLockedWorkingCopyState.OBSOLETE);
			}
		}
	}
	
	@Test
	public void testLockingFewWorkspaces() throws Exception {
		try (IVCSLockedWorkingCopy w1 = new VCSLockedWorkingCopy(r)) {
			try (IVCSLockedWorkingCopy w2 = new VCSLockedWorkingCopy(r)) {
				assertNotEquals(w1.getFolder().getName(), w2.getFolder().getName());
				assertNotEquals(w1.getLockFile().getName(), w2.getLockFile().getName());
			}
		}
	}
	
	@Test
	public void testReusingUnlockedWorkspaces() throws Exception {
		VCSLockedWorkingCopy w1 = new VCSLockedWorkingCopy(r);
		w1.close();
		VCSLockedWorkingCopy w2 = new VCSLockedWorkingCopy(r);
		assertEquals(w1.getFolder().getName(), w2.getFolder().getName());
		w2.close();
		
		// if lock file does not exists then a new WC should be created
		w1.getLockFile().delete();
		w2 = new VCSLockedWorkingCopy(r);
		assertNotEquals(w1.getFolder().getName(), w2.getFolder().getName());
		w2.close();
	}
	
	@Test 
	public void testCorruptingWorkspace() throws Exception {
		VCSLockedWorkingCopy workspace = new VCSLockedWorkingCopy(r);
		workspace.setCorrupted(true);
		workspace.close();
		assertFalse(workspace.getFolder().exists());
		assertFalse(workspace.getLockFile().exists());
	}
}
