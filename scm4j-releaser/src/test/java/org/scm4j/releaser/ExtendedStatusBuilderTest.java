package org.scm4j.releaser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.Test;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.branch.ReleaseBranchPatch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.VCSComponentLocation;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSRepositoryFactory;
import org.scm4j.releaser.exceptions.EBuildStatus;
import org.scm4j.releaser.exceptions.EReleaserException;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

public class ExtendedStatusBuilderTest {
	private static final long CONCURRENT_TEST_TIMEOUT_SECONDS = 5;

	@Test
	public void testStatusCalculationThreadIsNamedAfterComponent() {
		Component component = new Component("test:thread-name:1.0");
		AtomicReference<String> calculationThreadName = new AtomicReference<>();
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, calculationThreadName,
				repository -> status("1.0", component, repository));
		Thread currentThread = Thread.currentThread();
		String originalThreadName = currentThread.getName();

		try {
			builder.getAndCacheMinorStatus(component, new CachedStatuses());

			assertEquals(component.getName(), calculationThreadName.get());
			assertEquals(originalThreadName, currentThread.getName());
		} finally {
			currentThread.setName(originalThreadName);
		}
	}

	@Test
	public void testStatusCalculationThreadNameIsRestoredAfterFailure() {
		Component component = new Component("test:failed-thread-name:1.0");
		AtomicReference<String> calculationThreadName = new AtomicReference<>();
		RuntimeException calculationFailure = new RuntimeException("status calculation failed");
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, calculationThreadName,
				repository -> {
				throw calculationFailure;
			});
		Thread currentThread = Thread.currentThread();
		String originalThreadName = currentThread.getName();

		try {
			try {
				builder.getAndCacheMinorStatus(component, new CachedStatuses());
				fail("Expected status calculation to fail");
			} catch (EBuildStatus e) {
				assertSame(calculationFailure, e.getCause());
			}

			assertEquals(component.getName(), calculationThreadName.get());
			assertEquals(originalThreadName, currentThread.getName());
		} finally {
			currentThread.setName(originalThreadName);
		}
	}

	@Test
	public void testConcurrentRequestsShareSuccessfulCalculation() throws Exception {
		Component component = new Component("test:concurrent-success:1.0");
		AtomicInteger calculations = new AtomicInteger();
		CountDownLatch calculationStarted = new CountDownLatch(1);
		CountDownLatch allowCompletion = new CountDownLatch(1);
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, new AtomicReference<>(), repository -> {
			calculations.incrementAndGet();
			calculationStarted.countDown();
			await(allowCompletion);
			return status("1.0", component, repository);
		});
		CoordinatedCachedStatuses cache = new CoordinatedCachedStatuses(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<ExtendedStatus> first = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitFirstReservation();
			Future<ExtendedStatus> second = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitReservations();
			await(calculationStarted);
			allowCompletion.countDown();

			assertEquals(new Version("1.0"), resultOf(first).getNextVersion());
			assertEquals(new Version("1.0"), resultOf(second).getNextVersion());
			assertEquals(1, calculations.get());
			assertEquals(new Version("1.0"), builder.getAndCacheMinorStatus(component, cache).getNextVersion());
			assertEquals(1, calculations.get());
			assertEquals(1, cache.size());
		} finally {
			allowCompletion.countDown();
			shutdown(executor);
		}
	}

	@Test
	public void testConcurrentOrdinaryFailureIsSharedAndCanBeRetried() throws Exception {
		Component component = new Component("test:concurrent-failure:1.0");
		RuntimeException calculationFailure = new RuntimeException("status calculation failed");
		AtomicInteger calculations = new AtomicInteger();
		CountDownLatch failedCalculationStarted = new CountDownLatch(1);
		CountDownLatch allowFailure = new CountDownLatch(1);
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, new AtomicReference<>(), repository -> {
			if (calculations.incrementAndGet() == 1) {
				failedCalculationStarted.countDown();
				await(allowFailure);
				throw calculationFailure;
			}
			return status("1.0", component, repository);
		});
		CoordinatedCachedStatuses cache = new CoordinatedCachedStatuses(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<ExtendedStatus> first = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitFirstReservation();
			Future<ExtendedStatus> second = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitReservations();
			await(failedCalculationStarted);
			allowFailure.countDown();

			Throwable firstFailure = failureOf(first);
			Throwable secondFailure = failureOf(second);
			assertTrue(firstFailure instanceof EBuildStatus);
			assertSame(firstFailure, secondFailure);
			assertSame(calculationFailure, firstFailure.getCause());
			assertEquals(1, calculations.get());

			assertEquals(new Version("1.0"), builder.getAndCacheMinorStatus(component, cache).getNextVersion());
			assertEquals(2, calculations.get());
			assertEquals(1, cache.size());
		} finally {
			allowFailure.countDown();
			shutdown(executor);
		}
	}

	@Test
	public void testExistingReleaserFailureIsSharedUnchanged() throws Exception {
		assertConcurrentFailureIsShared(new EReleaserException("expected releaser failure"));
	}

	@Test
	public void testErrorIsSharedUnchanged() throws Exception {
		assertConcurrentFailureIsShared(new AssertionError("expected error"));
	}

	@Test
	public void testInterruptedWaiterLeavesOwnerCalculationCached() throws Exception {
		Component component = new Component("test:interrupted-waiter:1.0");
		AtomicInteger calculations = new AtomicInteger();
		CountDownLatch calculationStarted = new CountDownLatch(1);
		CountDownLatch allowCompletion = new CountDownLatch(1);
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, new AtomicReference<>(), repository -> {
			calculations.incrementAndGet();
			calculationStarted.countDown();
			await(allowCompletion);
			return status("1.0", component, repository);
		});
		CoordinatedCachedStatuses cache = new CoordinatedCachedStatuses(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		AtomicReference<Thread> waiterThread = new AtomicReference<>();
		AtomicBoolean waiterInterruptRestored = new AtomicBoolean();

		try {
			Future<ExtendedStatus> owner = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitFirstReservation();
			Future<Throwable> waiter = executor.submit(() -> {
				waiterThread.set(Thread.currentThread());
				try {
					builder.getAndCacheMinorStatus(component, cache);
					return null;
				} catch (Throwable failure) {
					waiterInterruptRestored.set(Thread.currentThread().isInterrupted());
					return failure;
				}
			});
			cache.awaitReservations();
			await(calculationStarted);
			waiterThread.get().interrupt();

			Throwable waiterFailure = waiter.get(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			assertTrue(waiterFailure instanceof EBuildStatus);
			assertTrue(waiterFailure.getCause() instanceof InterruptedException);
			assertTrue(waiterInterruptRestored.get());
			assertFalse(owner.isDone());

			allowCompletion.countDown();
			assertEquals(new Version("1.0"), resultOf(owner).getNextVersion());
			assertEquals(new Version("1.0"), builder.getAndCacheMinorStatus(component, cache).getNextVersion());
			assertEquals(1, calculations.get());
			assertEquals(1, cache.size());
		} finally {
			allowCompletion.countDown();
			shutdown(executor);
		}
	}

	private ExtendedStatusBuilder threadNameCapturingBuilder(Component component,
			AtomicReference<String> calculationThreadName,
			Function<VCSRepository, ExtendedStatus> calculation) {
		VCSRepository repository = repository("thread-name");
		VCSRepositoryFactory repositoryFactory = mock(VCSRepositoryFactory.class);
		when(repositoryFactory.getVCSRepository(component)).thenReturn(repository);
		return new ExtendedStatusBuilder(repositoryFactory) {
			@Override
			ExtendedStatus getMinorStatus(Component comp, CachedStatuses cache, IProgress progress,
					VCSRepository repo, DelayedTag dt) {
				calculationThreadName.set(Thread.currentThread().getName());
				return calculation.apply(repo);
			}
		};
	}

	@Test
	public void testStatusesAreCachedByRepositorySubfolder() {
		Component firstComponent = new Component("test:first:");
		Component secondComponent = new Component("test:second:");
		VCSRepository firstRepository = repository("first");
		VCSRepository secondRepository = repository("second");
		ExtendedStatus firstStatus = status("1.0", firstComponent, firstRepository);
		ExtendedStatus secondStatus = status("2.0", secondComponent, secondRepository);
		VCSRepositoryFactory repositoryFactory = mock(VCSRepositoryFactory.class);
		when(repositoryFactory.getVCSRepository(firstComponent)).thenReturn(firstRepository);
		when(repositoryFactory.getVCSRepository(secondComponent)).thenReturn(secondRepository);
		ExtendedStatusBuilder builder = spy(new ExtendedStatusBuilder(repositoryFactory));
		doReturn(firstStatus).when(builder).getMinorStatus(eq(firstComponent), any(CachedStatuses.class),
				any(IProgress.class), eq(firstRepository), any(DelayedTag.class));
		doReturn(secondStatus).when(builder).getMinorStatus(eq(secondComponent), any(CachedStatuses.class),
				any(IProgress.class), eq(secondRepository), any(DelayedTag.class));
		CachedStatuses cache = new CachedStatuses();

		ExtendedStatus firstResult = builder.getAndCacheMinorStatus(firstComponent, cache);
		ExtendedStatus secondResult = builder.getAndCacheMinorStatus(secondComponent, cache);

		assertEquals(new Version("1.0"), firstResult.getNextVersion());
		assertEquals(new Version("2.0"), secondResult.getNextVersion());
		assertEquals(2, cache.size());
	}

	@Test
	public void testOwnSubfolderTagMarksReleaseBoundary() {
		assertTrue(noValueableCommitsAfterLastTag("driver", "components/driver", "driver/1.2.3"));
	}

	@Test
	public void testOtherSubfolderTagsDoNotMarkReleaseBoundary() {
		assertFalse(noValueableCommitsAfterLastTag("driver", "components/driver",
				"components/sibling/1.2.3", "1.2.3"));
	}

	@Test
	public void testNestedComponentTagDoesNotMarkReleaseBoundary() {
		assertFalse(noValueableCommitsAfterLastTag("components", "components",
				"components/driver/1.2.3"));
	}

	@Test
	public void testAnyTagRemainsReleaseBoundaryWithoutSubfolder() {
		assertTrue(noValueableCommitsAfterLastTag("name", null, "other-tag"));
	}

	@Test
	public void testCommitTraversalUsesSubfolderForEveryPage() {
		String branchName = "driver/release/1.2";
		String subfolder = "components/driver";
		IVCS vcs = mock(IVCS.class);
		VCSRepository repository = repository("driver", subfolder, vcs);
		ReleaseBranchPatch releaseBranch = mock(ReleaseBranchPatch.class);
		when(releaseBranch.getName()).thenReturn(branchName);
		List<VCSCommit> firstPage = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			firstPage.add(new VCSCommit("revision-" + i, Constants.SCM_IGNORE, "author"));
		}
		when(vcs.getCommitsRange(branchName, null, WalkDirection.DESC, 10, subfolder)).thenReturn(firstPage);
		when(vcs.getCommitsRange(branchName, "revision-9", WalkDirection.DESC, 10, subfolder))
				.thenReturn(Collections.emptyList());
		when(vcs.getTagsOnRevision(any(String.class))).thenReturn(Collections.emptyList());

		assertTrue(new ExtendedStatusBuilder(mock(VCSRepositoryFactory.class))
				.noValueableCommitsAfterLastTag(repository, releaseBranch));

		verify(vcs).getCommitsRange(branchName, null, WalkDirection.DESC, 10, subfolder);
		verify(vcs).getCommitsRange(branchName, "revision-9", WalkDirection.DESC, 10, subfolder);
	}

	private VCSRepository repository(String subfolder) {
		return repository("name", subfolder, mock(IVCS.class));
	}

	private VCSRepository repository(String name, String subfolder, IVCS vcs) {
		return new VCSRepository(name, "url", subfolder, null, null, null, "release/", vcs, null);
	}

	private ExtendedStatus status(String version, Component component, VCSRepository repository) {
		return new ExtendedStatus(new Version(version), BuildStatus.BUILD, new LinkedHashMap<>(), component, repository);
	}

	private void assertConcurrentFailureIsShared(Throwable calculationFailure) throws Exception {
		Component component = new Component("test:shared-failure:1.0");
		AtomicInteger calculations = new AtomicInteger();
		CountDownLatch calculationStarted = new CountDownLatch(1);
		CountDownLatch allowFailure = new CountDownLatch(1);
		ExtendedStatusBuilder builder = threadNameCapturingBuilder(component, new AtomicReference<>(), repository -> {
			calculations.incrementAndGet();
			calculationStarted.countDown();
			await(allowFailure);
			return rethrow(calculationFailure);
		});
		CoordinatedCachedStatuses cache = new CoordinatedCachedStatuses(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<ExtendedStatus> first = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitFirstReservation();
			Future<ExtendedStatus> second = executor.submit(() -> builder.getAndCacheMinorStatus(component, cache));
			cache.awaitReservations();
			await(calculationStarted);
			allowFailure.countDown();

			assertSame(calculationFailure, failureOf(first));
			assertSame(calculationFailure, failureOf(second));
			assertEquals(1, calculations.get());
		} finally {
			allowFailure.countDown();
			shutdown(executor);
		}
	}

	private static ExtendedStatus rethrow(Throwable failure) {
		if (failure instanceof Error) {
			throw (Error) failure;
		}
		throw (RuntimeException) failure;
	}

	private static ExtendedStatus resultOf(Future<ExtendedStatus> result) throws Exception {
		return result.get(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static Throwable failureOf(Future<ExtendedStatus> result) throws Exception {
		try {
			resultOf(result);
			fail("Expected status calculation to fail");
			return null;
		} catch (ExecutionException e) {
			return e.getCause();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting for concurrent test coordination");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

	private static void shutdown(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	private static class CoordinatedCachedStatuses extends CachedStatuses {
		private final CountDownLatch firstReservation = new CountDownLatch(1);
		private final CountDownLatch reservations;

		private CoordinatedCachedStatuses(int reservationCount) {
			reservations = new CountDownLatch(reservationCount);
		}

		@Override
		CompletableFuture<ExtendedStatus> putIfAbsent(VCSComponentLocation componentLocation,
				CompletableFuture<ExtendedStatus> candidate) {
			CompletableFuture<ExtendedStatus> existing = super.putIfAbsent(componentLocation, candidate);
			firstReservation.countDown();
			reservations.countDown();
			await(reservations);
			return existing;
		}

		private void awaitFirstReservation() {
			await(firstReservation);
		}

		private void awaitReservations() {
			await(reservations);
		}
	}

	private boolean noValueableCommitsAfterLastTag(String name, String subfolder, String... tagNames) {
		IVCS vcs = mock(IVCS.class);
		VCSRepository repository = new VCSRepository(name, "status-test-url", subfolder,
				null, null, null, "release/", vcs, null);
		ReleaseBranchPatch releaseBranch = mock(ReleaseBranchPatch.class);
		VCSCommit commit = new VCSCommit("revision", "valuable change", "author");
		List<VCSTag> tags = new ArrayList<>();
		for (String tagName : tagNames) {
			tags.add(new VCSTag(tagName, tagName + " message", "author", commit));
		}
		when(releaseBranch.getName()).thenReturn("release-branch");
		when(vcs.getCommitsRange("release-branch", null, WalkDirection.DESC, 10, subfolder))
				.thenReturn(Collections.singletonList(commit));
		when(vcs.getTagsOnRevision(commit.getRevision())).thenReturn(tags);

		ExtendedStatusBuilder builder = new ExtendedStatusBuilder(mock(VCSRepositoryFactory.class));
		return builder.noValueableCommitsAfterLastTag(repository, releaseBranch);
	}
}
