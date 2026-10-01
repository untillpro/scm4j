/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.api;

import org.junit.Test;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class UtilsTest {
	private ScheduledExecutorService immediateScheduler;

	@Before
	public void setUp() {
		immediateScheduler = new ScheduledThreadPoolExecutor(1) {
			@Override
			public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
				return super.schedule(callable, 0, TimeUnit.MILLISECONDS);
			}
		};
	}

	@After
	public void tearDown() {
		immediateScheduler.shutdownNow();
	}

	@Test
	public void retriesRetryableFailureAndReportsScheduledRetries() throws IOException {
		AtomicInteger attempts = new AtomicInteger();
		IOException failure = new IOException("transient");
		List<String> reportedOperations = new ArrayList<>();
		List<Throwable> reportedFailures = new ArrayList<>();

		Utils.runWithRetry("test operation", () -> {
			if (attempts.incrementAndGet() <= 2) {
				throw failure;
			}
		}, candidate -> candidate == failure, (operation, reportedFailure) -> {
			reportedOperations.add(operation);
			reportedFailures.add(reportedFailure);
		}, immediateScheduler);

		assertEquals(3, attempts.get());
		assertEquals(2, reportedOperations.size());
		assertEquals("test operation", reportedOperations.get(0));
		assertEquals("test operation", reportedOperations.get(1));
		assertSame(failure, reportedFailures.get(0));
		assertSame(failure, reportedFailures.get(1));
	}

	@Test
	public void propagatesNonRetryableFailureWithoutReporting() {
		AtomicInteger attempts = new AtomicInteger();
		IOException failure = new IOException("permanent");
		AtomicInteger reports = new AtomicInteger();

		try {
			Utils.runWithRetry("test operation", () -> {
				attempts.incrementAndGet();
				throw failure;
			}, candidate -> false, (operation, reportedFailure) -> reports.incrementAndGet(),
					immediateScheduler);
			fail("IOException is not thrown");
		} catch (IOException actual) {
			assertSame(failure, actual);
		}

		assertEquals(1, attempts.get());
		assertEquals(0, reports.get());
	}

	@Test
	public void propagatesOriginalFailureAfterMaximumAttempts() {
		AtomicInteger attempts = new AtomicInteger();
		IOException failure = new IOException("still transient");
		List<Throwable> reportedFailures = new ArrayList<>();

		try {
			Utils.runWithRetry("test operation", () -> {
				attempts.incrementAndGet();
				throw failure;
			}, candidate -> true, (operation, reportedFailure) -> reportedFailures.add(reportedFailure),
					immediateScheduler);
			fail("IOException is not thrown");
		} catch (IOException actual) {
			assertSame(failure, actual);
		}

		assertEquals(11, attempts.get());
		assertEquals(10, reportedFailures.size());
		for (Throwable reportedFailure : reportedFailures) {
			assertSame(failure, reportedFailure);
		}
	}
}
