/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.git;

import dev.failsafe.RetryPolicy;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.vcs.api.exceptions.EVCSException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.scm4j.vcs.git.FakeGit.PASSWORD;
import static org.scm4j.vcs.git.FakeGit.PROXY_PASSWORD;
import static org.scm4j.vcs.git.FakeGit.PROXY_USER;
import static org.scm4j.vcs.git.FakeGit.USERNAME;

public class GitCliTest {

	private File testDir;

	@Before
	public void setUp() throws IOException {
		testDir = new File(System.getProperty("java.io.tmpdir"), "scm4j-git-cli-test-" + UUID.randomUUID());
		if (!testDir.mkdirs()) {
			throw new IOException("Could not create test directory " + testDir);
		}
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(testDir);
	}

	@Test
	public void testSupportedVersions() {
		cliForVersion("git version 2.25.0").checkVersion();
		cliForVersion("git version 2.51.2.windows.1").checkVersion();
		cliForVersion("git version 3.0.0").checkVersion();
	}

	@Test
	public void testUnsupportedVersion() {
		assertVersionFailure("git version 2.24.9", "Git 2.25 or later is required");
	}

	@Test
	public void testMalformedVersion() {
		assertVersionFailure("not a git version", "Could not parse Git version");
	}

	@Test
	public void testMissingExecutable() {
		File missing = new File(testDir, "missing-git-" + UUID.randomUUID());
		GitCli cli = new GitCli(Collections.singletonList(missing.getAbsolutePath()));

		try {
			cli.checkVersion();
			fail("Expected a missing Git executable to be rejected");
		} catch (EVCSException e) {
			assertTrue(e.getMessage().contains("Git executable"));
			assertTrue(e.getMessage().contains(missing.getAbsolutePath()));
		}
	}

	@Test
	public void testArgumentsArePassedAsTokens() {
		GitCli.Result result = cliForVersion("git version 2.25.0").execute(
				testDir, null, false, "argument test", "args",
				"argument with spaces", "unicode-文件", "quote\"and'apostrophe", "--leading-option");

		assertEquals(Arrays.asList("argument with spaces", "unicode-文件",
				"quote\"and'apostrophe", "--leading-option"), splitNul(result.getStdout()));
	}

	@Test
	public void testWorkingDirectoryAndStandardInput() throws IOException {
		byte[] input = "first line\nsecond 行\n".getBytes(StandardCharsets.UTF_8);
		GitCli.Result result = cliForVersion("git version 2.25.0").execute(
				testDir, input, false, "input test", "io");

		assertTrue(Arrays.equals(input, result.getStdout()));
		assertEquals(testDir.getCanonicalPath(), new String(result.getStderr(), StandardCharsets.UTF_8));
	}

	@Test(timeout = 15000)
	public void testStdoutAndStderrAreDrainedConcurrently() {
		GitCli.Result result = cliForVersion("git version 2.25.0").execute(
				testDir, null, false, "stream test", "flood");

		assertEquals(FakeGit.FLOOD_BYTES, result.getStdout().length);
		assertEquals(FakeGit.FLOOD_BYTES, result.getStderr().length);
		assertAllBytes(result.getStdout(), (byte) 'O');
		assertAllBytes(result.getStderr(), (byte) 'E');
	}

	@Test
	public void testNonZeroExitIsStructuredAndRedacted() {
		GitCli cli = cliForVersion("git version 2.25.0");
		cli.setCredentials(USERNAME, PASSWORD);
		cli.setProxy("proxy.example", 8080, PROXY_USER, PROXY_PASSWORD);

		try {
			cli.execute(testDir, null, false, "failure test", "fail", "argument with spaces");
			fail("Expected a structured command failure");
		} catch (GitCli.CommandException e) {
			assertEquals(7, e.getExitCode());
			assertEquals(Arrays.asList("fail", "argument with spaces"), e.getArguments());
			assertTrue(e.getStandardOutput().contains("failure stdout"));
			assertTrue(e.getStandardError().contains("failure stderr"));
			assertSecretRedacted(e.getMessage());
			assertSecretRedacted(e.getStandardOutput());
			assertSecretRedacted(e.getStandardError());
		}
	}

	@Test
	public void testNetworkEnvironmentAndAskPassCleanup() {
		GitCli cli = cliForVersion("git version 2.25.0");
		cli.setCredentials(USERNAME, PASSWORD);
		cli.setProxy("proxy.example", 8080, PROXY_USER, PROXY_PASSWORD);

		GitCli.Result result = cli.execute(testDir, null, true, "authentication test", "environment");
		Map<String, String> environment = parseLines(result.getStdout());

		assertEquals("0", environment.get("TERMINAL_PROMPT"));
		assertEquals("true", environment.get("ASKPASS_EXISTED"));
		assertEquals("false", environment.get("ASKPASS_CONTAINED_SECRET"));
		assertEquals("true", environment.get("USERNAME_MATCHED"));
		assertEquals("true", environment.get("PASSWORD_MATCHED"));
		assertEquals("true", environment.get("PROXY_MATCHED"));
		String askPassPath = environment.get("ASKPASS_PATH");
		assertTrue(askPassPath != null && !askPassPath.isEmpty());
		assertFalse("Temporary ask-pass helper was not deleted", new File(askPassPath).exists());
	}

	@Test
	public void testOnlyNetworkCommandsRetryAndRetryReportsAreRedacted() throws IOException {
		File counter = new File(testDir, "attempts");
		RetryPolicy<GitCli.Result> retryPolicy = RetryPolicy.<GitCli.Result>builder()
				.withMaxRetries(2)
				.build();
		GitCli cli = new GitCli(fakeCommand("--fake-version=git version 2.25.0",
				"--counter=" + counter.getAbsolutePath()), retryPolicy);
		cli.setCredentials(USERNAME, PASSWORD);
		List<String> operations = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		cli.setRetryStatusReporter((operation, failure) -> {
			operations.add(operation);
			failures.add(failure);
		});

		GitCli.Result result = cli.execute(testDir, null, true, "fetch", "flaky");
		assertEquals("success", new String(result.getStdout(), StandardCharsets.UTF_8));
		assertEquals("2", read(counter));
		assertEquals(Collections.singletonList("fetch"), operations);
		assertEquals(1, failures.size());
		assertSecretRedacted(failures.get(0).getMessage());

		if (!counter.delete()) {
			throw new IOException("Could not reset counter " + counter);
		}
		try {
			cli.execute(testDir, null, false, "local status", "flaky");
			fail("A local command failure must not be retried");
		} catch (GitCli.CommandException expected) {
			assertEquals(7, expected.getExitCode());
		}
		assertEquals("1", read(counter));
		assertEquals("Local failure unexpectedly reported a retry", 1, failures.size());
	}

	private GitCli cliForVersion(String versionOutput) {
		return new GitCli(fakeCommand("--fake-version=" + versionOutput));
	}

	private void assertVersionFailure(String output, String expectedMessage) {
		try {
			cliForVersion(output).checkVersion();
			fail("Expected Git version to be rejected: " + output);
		} catch (EVCSException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(expectedMessage));
		}
	}

	private List<String> fakeCommand(String... controlArguments) {
		String executable = new File(new File(System.getProperty("java.home"), "bin"),
				isWindows() ? "java.exe" : "java").getAbsolutePath();
		List<String> command = new ArrayList<>();
		command.add(executable);
		command.add("-cp");
		command.add(absoluteClassPath());
		command.add(FakeGit.class.getName());
		command.addAll(Arrays.asList(controlArguments));
		return command;
	}

	private String absoluteClassPath() {
		String[] entries = System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator));
		List<String> absoluteEntries = new ArrayList<>();
		for (String entry : entries) {
			File file = new File(entry);
			absoluteEntries.add(file.isAbsolute() ? file.getPath() : file.getAbsolutePath());
		}
		return String.join(File.pathSeparator, absoluteEntries);
	}

	private boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	private List<String> splitNul(byte[] value) {
		List<String> result = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < value.length; i++) {
			if (value[i] == 0) {
				result.add(new String(value, start, i - start, StandardCharsets.UTF_8));
				start = i + 1;
			}
		}
		assertEquals("Output did not end with NUL", value.length, start);
		return result;
	}

	private Map<String, String> parseLines(byte[] value) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String line : new String(value, StandardCharsets.UTF_8).split("\\r?\\n")) {
			int separator = line.indexOf('=');
			if (separator > 0) {
				result.put(line.substring(0, separator), line.substring(separator + 1));
			}
		}
		return result;
	}

	private void assertAllBytes(byte[] value, byte expected) {
		for (byte actual : value) {
			assertEquals(expected, actual);
		}
	}

	private void assertSecretRedacted(String value) {
		assertFalse("Username leaked: " + value, value.contains(USERNAME));
		assertFalse("Password leaked: " + value, value.contains(PASSWORD));
		assertFalse("Proxy username leaked: " + value, value.contains(PROXY_USER));
		assertFalse("Proxy password leaked: " + value, value.contains(PROXY_PASSWORD));
	}

	private String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}
}
