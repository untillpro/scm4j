package org.scm4j.vcs.api.workingcopy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import org.junit.Before;
import org.junit.Test;

public class VCSLockedWorkingCopyTest extends VCSWCTestBase {

	private IVCSRepositoryWorkspace r;
	private IVCSRepositoryWorkspace component;
	private IVCSRepositoryWorkspace sibling;

	@Before
	public void setUp() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		r = w.getVCSRepositoryWorkspace(TEST_REPO_URL, null);
		component = w.getVCSRepositoryWorkspace(TEST_REPO_URL, "components/first");
		sibling = w.getVCSRepositoryWorkspace(TEST_REPO_URL, "components/second");
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
	public void testNewWorkingCopyIsNotPublishedBeforeLockIsAcquired() throws Exception {
		String workingCopyId = "new-working-copy";
		assertTrue(r.getWritableWorkingCopiesFolder().mkdirs());
		File lockFile = new File(r.getWritableWorkingCopiesFolder(),
				VCSLockedWorkingCopy.LOCK_FILE_PREFIX + workingCopyId);
		assertTrue(lockFile.createNewFile());

		try (FileOutputStream stream = new FileOutputStream(lockFile);
			 FileLock ignored = stream.getChannel().lock()) {
			try {
				new VCSLockedWorkingCopy(r, workingCopyId);
				fail();
			} catch (OverlappingFileLockException e) {
				// Expected: the new working-copy directory must not be visible unless its lock is owned.
			}

			assertFalse(new File(r.getWritableWorkingCopiesFolder(), workingCopyId).exists());
		}
	}

	@Test
	public void testInterruptedAllocationDoesNotPublishWorkingCopy() throws Exception {
		String workingCopyId = "interrupted-working-copy";
		assertTrue(r.getWritableWorkingCopiesFolder().mkdirs());
		File lockFile = new File(r.getWritableWorkingCopiesFolder(),
				VCSLockedWorkingCopy.LOCK_FILE_PREFIX + workingCopyId);
		assertTrue(lockFile.createNewFile());
		Thread.currentThread().interrupt();
		try {
			new VCSLockedWorkingCopy(r, workingCopyId);
			fail("Interrupted allocation must fail");
		} catch (FileLockInterruptionException expected) {
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertFalse(new File(r.getWritableWorkingCopiesFolder(), workingCopyId).exists());
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
		assertTrue(r.getPreparedRepositoryFolder().mkdirs());
		File preparedMarker = new File(r.getPreparedRepositoryFolder(), "prepared.marker");
		assertTrue(preparedMarker.createNewFile());
		VCSLockedWorkingCopy workspace = new VCSLockedWorkingCopy(r);
		workspace.setCorrupted(true);
		workspace.close();
		assertFalse(workspace.getFolder().exists());
		assertFalse(workspace.getLockFile().exists());
		assertTrue("Corrupt derivatives must not remove the status snapshot", preparedMarker.exists());
		assertFalse("Corrupt derivatives must not mark the status snapshot corrupt",
				new File(r.getPreparedRepositoryFolder(), VCSLockedWorkingCopy.LOCK_FILE_PREFIX
						+ r.getPreparedRepositoryFolder().getName()).exists());
	}

	@Test
	public void testSiblingMutationWorkingCopiesAreIsolatedFromPreparedRepository() throws Exception {
		assertEquals(component.getPreparedRepositoryFolder(), sibling.getPreparedRepositoryFolder());
		assertEquals(component.getWritableWorkingCopiesFolder(), sibling.getWritableWorkingCopiesFolder());

		try (IVCSLockedWorkingCopy first = component.getVCSLockedWorkingCopy();
			 IVCSLockedWorkingCopy second = sibling.getVCSLockedWorkingCopy()) {
			assertNotEquals(first.getFolder(), second.getFolder());
			assertNotEquals(first.getLockFile(), second.getLockFile());
			assertNotEquals(component.getPreparedRepositoryFolder(), first.getFolder());
			assertNotEquals(component.getPreparedRepositoryFolder(), second.getFolder());
			assertEquals(component, first.getVCSRepository());
			assertEquals(sibling, second.getVCSRepository());
			assertEquals(component.getWritableWorkingCopiesFolder(), first.getFolder().getParentFile());
			assertEquals(component.getWritableWorkingCopiesFolder(), second.getFolder().getParentFile());
		}
	}

	@Test
	public void testToString() throws Exception {
		try (VCSLockedWorkingCopy lwc = new VCSLockedWorkingCopy(r)) {
			assertTrue(lwc.toString().contains(r.getRepoFolder().getPath()));
		}
	}
}
