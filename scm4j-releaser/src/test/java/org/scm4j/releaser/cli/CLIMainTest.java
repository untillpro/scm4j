/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.releaser.cli;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class CLIMainTest {
	@Rule
	public final ExpectedSystemExit exit = ExpectedSystemExit.none();

	@Rule
	public final SystemOutRule output = new SystemOutRule().enableLog();

	@Test
	public void testPrintsBuildCommitBeforeExecution() throws Exception {
		exit.expectSystemExitWithStatus(CLI.EXIT_CODE_ERROR);
		exit.checkAssertionAfterwards(() -> assertTrue(
				output.getLog().matches("(?s)^scm4j-releaser [0-9a-fA-F]+\\r?\\n.*")));

		CLI.main(new String[0]);
	}
}
