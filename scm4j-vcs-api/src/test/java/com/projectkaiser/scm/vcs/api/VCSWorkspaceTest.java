package com.projectkaiser.scm.vcs.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;

public class VCSWorkspaceTest {
	
	private static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	
	
	@After
	public void setUp() throws IOException {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
	}
	
	@Test 
	public void testBasicWorkspaceWorkflow() {
		
		VCSWorkspace workspace = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
		assertEquals(workspace.getState(), VCSWorkspaceState.LOCKED);
		try {
			assertTrue(workspace.getFolder().exists());
			assertTrue(workspace.getFolder().getParentFile().getAbsolutePath().equals(WORKSPACE_DIR));
			assertFalse(workspace.getCorrupt());
			assertTrue(workspace.getLockFile().exists());
			assertTrue(workspace.getLockFile().getName().equals(VCSWorkspace.LOCK_FILE_PREFIX + workspace.getFolder().getName()));
		} finally {
			workspace.unlock();
			assertEquals(workspace.getState(), VCSWorkspaceState.OBSOLETE);
			workspace.unlock(); //nothing should happen
		}
		assertEquals(workspace.getState(), VCSWorkspaceState.OBSOLETE);
	}
	
	@Test
	public void testLockingFewWorkspaces() {
		VCSWorkspace w1 = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
		try {
			VCSWorkspace w2 = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
			try {
				assertNotEquals(w1.getFolder().getName(), w2.getFolder().getName());
			} finally {
				w2.unlock();
			}
		} finally {
			w1.unlock();
		}
	}
	
	@Test
	public void testReusingUnlockedWorkspaces() {
		VCSWorkspace w1 = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
		w1.unlock();
		VCSWorkspace w2 = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
		try {
			assertEquals(w1.getFolder().getName(), w2.getFolder().getName());
		} finally {
			w2.unlock();
		}
	}
	
	@Test 
	public void testCorruptingWorkspace() {
		VCSWorkspace workspace = VCSWorkspace.getLockedWorkspace(WORKSPACE_DIR);
		workspace.setCorrupt(true);
		workspace.unlock();
		
		assertFalse(workspace.getFolder().exists());
		assertFalse(workspace.getLockFile().exists());
	}
}
