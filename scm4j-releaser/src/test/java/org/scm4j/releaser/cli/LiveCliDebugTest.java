/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.releaser.cli;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

public class LiveCliDebugTest {

	private static final String COMMON_POOL_PARALLELISM_PROPERTY =
			"java.util.concurrent.ForkJoinPool.common.parallelism";

	@Test
	@Ignore
	public void run() {
		System.setProperty(COMMON_POOL_PARALLELISM_PROPERTY, "64");

		String[] args = {
				"status",
				"eu.untill:Untill:152",
				"--stacktrace"
		};

		CLI cli = new CLI();
		int exitCode = cli.exec(args);
		if (exitCode != CLI.EXIT_CODE_OK) {
			if (cli.getLastException() != null) {
				throw cli.getLastException();
			}
			fail("CLI exited with code " + exitCode);
		}
	}
}
