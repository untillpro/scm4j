/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.api;

import dev.failsafe.RetryPolicyBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class UtilsTest {
	private static final Consumer<RetryPolicyBuilder<Object>> NO_RETRY_DELAY = retryPolicy -> {};

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
		}, NO_RETRY_DELAY);

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
					NO_RETRY_DELAY);
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
					NO_RETRY_DELAY);
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
