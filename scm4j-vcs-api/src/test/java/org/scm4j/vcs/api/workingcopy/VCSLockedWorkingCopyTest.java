package org.scm4j.vcs.api.workingcopy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;

import org.junit.Before;
import org.junit.Test;

public class VCSLockedWorkingCopyTest extends VCSWCTestBase {

	private IVCSRepositoryWorkspace r;

	@Before
	public void setUp() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		r = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
	}

	@Test
	public void testBasicWorkspaceWorkflow() throws Exception {
		try (IVCSLockedWorkingCopy lwc = r.getVCSLockedWorkingCopy()) {
			assertEquals(lwc.getVCSRepository(), r);
			assertTrue(lwc.getFolder().exists());
			assertEquals(lwc.getState(),  VCSLockedWorkingCopyState.LOCKED);
			assertFalse(lwc.getCorrupted());
			assertTrue(lwc.getLockFile().exists());
			assertTrue(lwc.getLockFile().getName().equals(VCSLockedWorkingCopy.LOCK_FILE_PREFIX + lwc.getFolder().getName()));
			try (FileChannel channel = new FileOutputStream(lwc.getLockFile()).getChannel()) {
				channel.tryLock();
				fail();
			} catch (OverlappingFileLockException e) {

			}
			lwc.close();
			assertEquals(lwc.getState(), VCSLockedWorkingCopyState.OBSOLETE);
			lwc.close(); //nothing should happen
			assertEquals(lwc.getState(), VCSLockedWorkingCopyState.OBSOLETE);
		}
	}

	@Test
	public void testLockingFewWorkspaces() throws Exception {
		try (IVCSLockedWorkingCopy w1 = r.getVCSLockedWorkingCopy()) {
			try (IVCSLockedWorkingCopy w2 = r.getVCSLockedWorkingCopy()) {
				assertNotEquals(w1.getFolder().getName(), w2.getFolder().getName());
				assertNotEquals(w1.getLockFile().getName(), w2.getLockFile().getName());
			}
		}
	}

	@Test
	public void testReusingUnlockedWorkspaces() throws Exception {
		VCSLockedWorkingCopy w1 = new VCSLockedWorkingCopy(r, false);
		w1.close();
		VCSLockedWorkingCopy w2 = new VCSLockedWorkingCopy(r, false);
		assertEquals(w1.getFolder().getName(), w2.getFolder().getName());
		w2.close();

		// if lock file does not exists then a new WC should be created
		w1.getLockFile().delete();
		w2 = new VCSLockedWorkingCopy(r, false);
		assertNotEquals(w1.getFolder().getName(), w2.getFolder().getName());
		w2.close();
	}

	@Test
	public void testCorruptingWorkspace() throws Exception {
		VCSLockedWorkingCopy workspace = new VCSLockedWorkingCopy(r, false);
		workspace.setCorrupted(true);
		workspace.close();
		assertFalse(workspace.getFolder().exists());
		assertFalse(workspace.getLockFile().exists());
	}

	@Test
	public void testToString() throws Exception {
		try (VCSLockedWorkingCopy lwc = new VCSLockedWorkingCopy(r, false)) {
			assertTrue(lwc.toString().contains(r.getRepoFolder().getPath()));
		}
	}
	
	@Test
	public void testTempLWCObtain() throws Exception {
		File lwcFolder;
		try (IVCSLockedWorkingCopy lwc = r.getVCSLockedWorkingCopy()) {
			lwcFolder = lwc.getFolder();
		}
		try (IVCSLockedWorkingCopy tempLWC = r.getVCSLockedWorkingCopyTemp()) {
			assertFalse(lwcFolder.getName().equals(tempLWC.getFolder().getName()));
			assertTrue(tempLWC.getCorrupted());
		}
	}
}
