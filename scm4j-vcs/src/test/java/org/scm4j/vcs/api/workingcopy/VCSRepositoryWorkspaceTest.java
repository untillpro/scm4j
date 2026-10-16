package org.scm4j.vcs.api.workingcopy;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace.PreparedRepositoryInitializer;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace.PreparedRepositorySnapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VCSRepositoryWorkspaceTest extends VCSWCTestBase {
	private static final String COMPONENT_SUBFOLDER = "components/driver";

	@Test(timeout = 20000)
	public void testSiblingsWaitForOneInitializer() throws Exception {
		IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace first = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, COMPONENT_SUBFOLDER);
		IVCSRepositoryWorkspace sibling = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, "components/sibling");
		PreparedRepositorySnapshot snapshot = new PreparedRepositorySnapshot() {};
		AtomicInteger initializations = new AtomicInteger();
		CountDownLatch initializing = new CountDownLatch(1);
		CountDownLatch finishInitialization = new CountDownLatch(1);
		CountDownLatch waiterStarted = new CountDownLatch(1);
		AtomicReference<Thread> waiterThread = new AtomicReference<>();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PreparedRepositorySnapshot> firstRead = executor.submit(() -> first.getPreparedRepositorySnapshot(folder -> {
				initializations.incrementAndGet();
				initializing.countDown();
				assertTrue(finishInitialization.await(5, TimeUnit.SECONDS));
				return snapshot;
			}));
			assertTrue(initializing.await(5, TimeUnit.SECONDS));
			Future<PreparedRepositorySnapshot> siblingRead = executor.submit(() -> {
				waiterThread.set(Thread.currentThread());
				waiterStarted.countDown();
				return sibling.getPreparedRepositorySnapshot(folder -> {
					initializations.incrementAndGet();
					return new PreparedRepositorySnapshot() {};
				});
			});
			assertTrue(waiterStarted.await(5, TimeUnit.SECONDS));
			awaitWaiting(waiterThread.get());
			assertFalse(firstRead.isDone());
			assertFalse(siblingRead.isDone());
			finishInitialization.countDown();
			assertSame(snapshot, firstRead.get(5, TimeUnit.SECONDS));
			assertSame(snapshot, siblingRead.get(5, TimeUnit.SECONDS));
			assertEquals(1, initializations.get());
		} finally {
			finishInitialization.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test(timeout = 20000)
	public void testReadySiblingReadsDoNotAcquireRepoLock() throws Exception {
		IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace first = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, COMPONENT_SUBFOLDER);
		IVCSRepositoryWorkspace sibling = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, "components/sibling");
		PreparedRepositorySnapshot snapshot = first.getPreparedRepositorySnapshot(folder -> new PreparedRepositorySnapshot() {});
		CountDownLatch readersReady = new CountDownLatch(2);
		CountDownLatch finishReads = new CountDownLatch(1);
		List<Future<?>> reads = new ArrayList<>();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			first.withRepoLock(() -> {
				for (IVCSRepositoryWorkspace repository : new IVCSRepositoryWorkspace[] {first, sibling}) {
					reads.add(executor.submit(() -> {
						assertSame(snapshot, repository.getPreparedRepositorySnapshot(folder -> {
							throw new AssertionError("A ready snapshot must not initialize again");
						}));
						readersReady.countDown();
						assertTrue(finishReads.await(5, TimeUnit.SECONDS));
						return null;
					}));
				}
				assertTrue("Both siblings must read while another thread holds repoLock",
						readersReady.await(5, TimeUnit.SECONDS));
				return null;
			});
			finishReads.countDown();
			for (Future<?> read : reads) {
				read.get(5, TimeUnit.SECONDS);
			}
		} finally {
			finishReads.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test(timeout = 20000)
	public void testInitializerFailureReachesWaiterAndAllowsRetry() throws Exception {
		IOException failure = new IOException("clone failed");
		assertInitializerFailureReachesWaiterAndAllowsRetry(folder -> {
			throw failure;
		}, failure);
	}

	@Test(timeout = 20000)
	public void testInitializerErrorReachesWaiterAndAllowsRetry() throws Exception {
		AssertionError failure = new AssertionError("initializer failed");
		assertInitializerFailureReachesWaiterAndAllowsRetry(folder -> {
			throw failure;
		}, failure);
	}

	@Test(timeout = 20000)
	public void testNullInitializerResultIsRejectedAndAllowsRetry() throws Exception {
		IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace first = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace sibling = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, COMPONENT_SUBFOLDER);
		try {
			first.getPreparedRepositorySnapshot(folder -> null);
			fail("Null snapshot metadata must be rejected");
		} catch (IllegalStateException expected) {
			assertEquals("Prepared repository initializer returned no snapshot metadata", expected.getMessage());
		}
		assertSuccessfulRetryIsShared(first, sibling);
	}

	private void assertInitializerFailureReachesWaiterAndAllowsRetry(
			PreparedRepositoryInitializer initializer, Throwable failure) throws Exception {
		IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace first = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace sibling = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, COMPONENT_SUBFOLDER);
		CountDownLatch initializing = new CountDownLatch(1);
		CountDownLatch failInitialization = new CountDownLatch(1);
		CountDownLatch waiterStarted = new CountDownLatch(1);
		AtomicReference<Thread> waiterThread = new AtomicReference<>();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PreparedRepositorySnapshot> firstRead = executor.submit(() -> first.getPreparedRepositorySnapshot(folder -> {
				initializing.countDown();
				assertTrue(failInitialization.await(5, TimeUnit.SECONDS));
				return initializer.initialize(folder);
			}));
			assertTrue(initializing.await(5, TimeUnit.SECONDS));
			Future<PreparedRepositorySnapshot> siblingRead = executor.submit(() -> {
				waiterThread.set(Thread.currentThread());
				waiterStarted.countDown();
				return sibling.getPreparedRepositorySnapshot(folder -> {
					throw new AssertionError("A waiting sibling must receive the first initializer's failure");
				});
			});
			assertTrue(waiterStarted.await(5, TimeUnit.SECONDS));
			awaitWaiting(waiterThread.get());
			failInitialization.countDown();
			assertFailedWith(firstRead, failure);
			assertFailedWith(siblingRead, failure);
			assertSuccessfulRetryIsShared(first, sibling);
		} finally {
			failInitialization.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private void assertSuccessfulRetryIsShared(IVCSRepositoryWorkspace first,
			IVCSRepositoryWorkspace sibling) throws Exception {
		PreparedRepositorySnapshot recovered = new PreparedRepositorySnapshot() {};
		assertSame(recovered, sibling.getPreparedRepositorySnapshot(folder -> recovered));
		assertSame(recovered, first.getPreparedRepositorySnapshot(folder -> {
			throw new AssertionError("The recovered snapshot must be shared");
		}));
	}

	@Test(timeout = 20000)
	public void testInterruptingWaiterPreservesFlagAndDoesNotCancelInitializer() throws Exception {
		IVCSRepositoryWorkspace repository = new VCSWorkspace(WORKSPACE_DIR).getVCSRepositoryWorkspace(TEST_REPO_URL);
		PreparedRepositorySnapshot snapshot = new PreparedRepositorySnapshot() {};
		CountDownLatch initializing = new CountDownLatch(1);
		CountDownLatch finishInitialization = new CountDownLatch(1);
		CountDownLatch waiterStarted = new CountDownLatch(1);
		AtomicReference<Thread> waiterThread = new AtomicReference<>();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PreparedRepositorySnapshot> firstRead = executor.submit(() -> repository.getPreparedRepositorySnapshot(folder -> {
				initializing.countDown();
				assertTrue(finishInitialization.await(5, TimeUnit.SECONDS));
				return snapshot;
			}));
			assertTrue(initializing.await(5, TimeUnit.SECONDS));
			Future<Boolean> interrupted = executor.submit(() -> {
				waiterThread.set(Thread.currentThread());
				waiterStarted.countDown();
				try {
					repository.getPreparedRepositorySnapshot(folder -> {
						throw new AssertionError("The waiter must not initialize");
					});
					throw new AssertionError("The waiter must be interrupted");
				} catch (InterruptedException expected) {
					return Thread.currentThread().isInterrupted();
				}
			});
			assertTrue(waiterStarted.await(5, TimeUnit.SECONDS));
			awaitWaiting(waiterThread.get());
			waiterThread.get().interrupt();
			assertTrue(interrupted.get(5, TimeUnit.SECONDS));
			finishInitialization.countDown();
			assertSame(snapshot, firstRead.get(5, TimeUnit.SECONDS));
			assertSame(snapshot, repository.getPreparedRepositorySnapshot(folder -> {
				throw new AssertionError("Interrupting a waiter must not invalidate the snapshot");
			}));
		} finally {
			finishInitialization.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	public void testInvalidatingOneComponentRefreshesAllSiblings() throws Exception {
		IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace first = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace sibling = workspace.getVCSRepositoryWorkspace(TEST_REPO_URL, COMPONENT_SUBFOLDER);
		PreparedRepositorySnapshot original = first.getPreparedRepositorySnapshot(folder -> new PreparedRepositorySnapshot() {});
		assertSame(original, sibling.getPreparedRepositorySnapshot(folder -> {
			throw new AssertionError("Siblings must share the published generation");
		}));
		sibling.invalidatePreparedRepositorySnapshot();
		PreparedRepositorySnapshot refreshed = first.getPreparedRepositorySnapshot(folder -> new PreparedRepositorySnapshot() {});
		assertFalse(original == refreshed);
		assertSame(refreshed, sibling.getPreparedRepositorySnapshot(folder -> {
			throw new AssertionError("Siblings must share the refreshed generation");
		}));
	}

	private void awaitWaiting(Thread thread) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
			Thread.yield();
		}
		assertEquals("The sibling must be waiting for snapshot initialization", Thread.State.WAITING, thread.getState());
	}

	private void assertFailedWith(Future<?> read, Throwable failure) throws Exception {
		try {
			read.get(5, TimeUnit.SECONDS);
			fail("Expected snapshot initialization to fail");
		} catch (ExecutionException expected) {
			assertSame(failure, expected.getCause());
		}
	}

	@Test
	public void testVCSRepository() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertEquals(r.getRepoUrl(), TEST_REPO_URL);
		assertFalse(r.getRepoFolder().exists());
		assertTrue(r.getRepoFolder().getParentFile().getPath().equals(WORKSPACE_DIR));
		assertEquals(r.getWorkspace(), w);

		IVCSRepositoryWorkspace r1 = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertEquals(r1.getRepoFolder().getPath(), r.getRepoFolder().getPath());

		try (IVCSLockedWorkingCopy lwc = r.getVCSLockedWorkingCopy()) {
			assertEquals(lwc.getVCSRepository(), r);
			assertTrue(r.getRepoFolder().exists());
		}
	}

	@Test
	public void testToString() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		assertTrue(r.toString().contains(WORKSPACE_DIR));
	}

	@Test
	public void testRepositoryFolderNameDoesNotIncludeComponentSubfolder() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace rootRepository = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace firstComponent = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, COMPONENT_SUBFOLDER);
		IVCSRepositoryWorkspace secondComponent = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, "components/sibling");

		assertEquals("test.repo.url", rootRepository.getRepoFolder().getName());
		assertEquals(rootRepository.getRepoFolder(), firstComponent.getRepoFolder());
		assertEquals(rootRepository.getRepoFolder(), secondComponent.getRepoFolder());
	}

	@Test
	public void testRootAndComponentWorkingCopiesAreReusable() throws Exception {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		IVCSRepositoryWorkspace rootRepository = w.getVCSRepositoryWorkspace(TEST_REPO_URL);
		IVCSRepositoryWorkspace componentRepository = w.getVCSRepositoryWorkspace(
				TEST_REPO_URL, COMPONENT_SUBFOLDER);
		assertEquals(rootRepository.getRepoFolder(), componentRepository.getRepoFolder());

		assertWorkingCopiesAreReusableAfterClose(rootRepository);
		assertWorkingCopiesAreReusableAfterClose(componentRepository);
	}

	private void assertWorkingCopiesAreReusableAfterClose(IVCSRepositoryWorkspace repository) throws Exception {
		File reusableFolder;
		File reusableLockFile;
		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			reusableFolder = workingCopy.getFolder();
			reusableLockFile = workingCopy.getLockFile();
		}

		// the repository is closed but lock files are kept -> the repo is reusable
		assertTrue(reusableFolder.exists());
		assertTrue(reusableLockFile.exists());
		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			// another repository is created but the same lock files are taken -> the repo is reused here
			assertEquals(reusableFolder, workingCopy.getFolder());
			assertEquals(reusableLockFile, workingCopy.getLockFile());
		}
	}

	@Test
	public void testHTTPAndHTTPSFolderPrefixesStripping() {
		IVCSWorkspace w = new VCSWorkspace(WORKSPACE_DIR);
		String repoUrl = "test.repo.url";
		String httpProto = "http://";
		String httpsProto = "https://";
		String fileProto1 = "file:/";
		String fileProto2 = "file://";
		String fileProto3 = "file:///";

		IVCSRepositoryWorkspace r = w.getVCSRepositoryWorkspace(httpProto + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(httpsProto + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto1 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto2 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());

		r = w.getVCSRepositoryWorkspace(fileProto3 + repoUrl);
		assertEquals(repoUrl, r.getRepoFolder().getName());
	}
}
