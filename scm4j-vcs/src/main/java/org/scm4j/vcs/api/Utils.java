/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.api;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyBuilder;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Utils {
	private static final int MAX_RETRIES = 10;
	private static final long MIN_BACKOFF_MILLIS = 500;
	private static final long MAX_BACKOFF_MILLIS = 2000;
	private static final double JITTER_FACTOR = .25;

	private Utils() {
	}

	@FunctionalInterface
	public interface CheckedRunnable<E extends Exception> {
		void run() throws E;
	}

	public static <E extends Exception> void runWithRetry(String operation,
			CheckedRunnable<E> command, Predicate<Throwable> retryableFailure,
			BiConsumer<String, Throwable> retryStatusReporter) throws E {
		runWithRetry(operation, command, retryableFailure, retryStatusReporter,
				Utils::configureDefaultRetryDelay);
	}

	static <E extends Exception> void runWithRetry(String operation,
			CheckedRunnable<E> command, Predicate<Throwable> retryableFailure,
			BiConsumer<String, Throwable> retryStatusReporter,
			Consumer<RetryPolicyBuilder<Object>> retryDelayConfigurer) throws E {
		RetryPolicyBuilder<Object> retryPolicy = RetryPolicy.<Object>builder()
				.handleIf(retryableFailure::test)
				.withMaxRetries(MAX_RETRIES)
				.onRetryScheduled(event -> retryStatusReporter.accept(operation, event.getLastException()));
		retryDelayConfigurer.accept(retryPolicy);
		AtomicReference<Exception> commandFailure = new AtomicReference<>();

		try {
			Failsafe.with(retryPolicy.build()).run(() -> {
				try {
					command.run();
				} catch (Exception failure) {
					commandFailure.set(failure);
					throw failure;
				}
			});
		} catch (FailsafeException failure) {
			Exception lastCommandFailure = commandFailure.get();
			if (failure.getCause() == lastCommandFailure) {
				throwOriginal(lastCommandFailure);
			}
			throw failure;
		}
	}

	private static void configureDefaultRetryDelay(RetryPolicyBuilder<Object> retryPolicy) {
		retryPolicy
				.withBackoff(MIN_BACKOFF_MILLIS, MAX_BACKOFF_MILLIS, ChronoUnit.MILLIS)
				.withJitter(JITTER_FACTOR);
	}

	@SuppressWarnings("unchecked")
	private static <E extends Exception> void throwOriginal(Throwable failure) throws E {
		throw (E) failure;
	}
}
