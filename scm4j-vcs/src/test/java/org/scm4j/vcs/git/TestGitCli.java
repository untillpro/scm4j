/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.git;

import org.scm4j.vcs.api.exceptions.EVCSException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

final class TestGitCli extends GitCli {

	private final List<CommandRecord> commands = new ArrayList<>();
	private boolean failMergeRecovery;
	private boolean failCleanup;
	private boolean mergeConflictSeen;
	private BiConsumer<String, Throwable> retryStatusReporter;

	void setFailMergeRecovery(boolean failMergeRecovery) {
		this.failMergeRecovery = failMergeRecovery;
		if (!failMergeRecovery) {
			mergeConflictSeen = false;
		}
	}

	void setFailCleanup(boolean failCleanup) {
		this.failCleanup = failCleanup;
	}

	List<CommandRecord> getCommands() {
		return commands;
	}

	BiConsumer<String, Throwable> getRetryStatusReporter() {
		return retryStatusReporter;
	}

	@Override
	void setRetryStatusReporter(BiConsumer<String, Throwable> reporter) {
		retryStatusReporter = reporter;
		super.setRetryStatusReporter(reporter);
	}

	@Override
	Result execute(File workingDirectory, byte[] standardInput, boolean network, String operation,
			String... arguments) {
		commands.add(new CommandRecord(network, arguments));
		if (failCleanup && contains(arguments, "clean")) {
			throw new EVCSException("test cleanup failure");
		}
		if (failMergeRecovery && mergeConflictSeen
				&& (containsSequence(arguments, "merge", "--abort")
				|| containsSequence(arguments, "reset", "--hard"))) {
			throw new EVCSException("test merge recovery failure");
		}
		try {
			return super.execute(workingDirectory, standardInput, network, operation, arguments);
		} catch (CommandException e) {
			if (contains(arguments, "merge") && !contains(arguments, "--abort")) {
				mergeConflictSeen = true;
			}
			throw e;
		}
	}

	private boolean contains(String[] arguments, String expected) {
		return Arrays.asList(arguments).contains(expected);
	}

	private boolean containsSequence(String[] arguments, String first, String second) {
		for (int i = 0; i + 1 < arguments.length; i++) {
			if (first.equals(arguments[i]) && second.equals(arguments[i + 1])) {
				return true;
			}
		}
		return false;
	}

	static final class CommandRecord {
		private final boolean network;
		private final List<String> arguments;

		private CommandRecord(boolean network, String[] arguments) {
			this.network = network;
			this.arguments = Arrays.asList(arguments.clone());
		}

		boolean isNetwork() {
			return network;
		}

		List<String> getArguments() {
			return arguments;
		}
	}
}
