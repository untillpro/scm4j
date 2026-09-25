/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.git;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FakeGit {

	static final int FLOOD_BYTES = 2 * 1024 * 1024;
	static final String USERNAME = "native-user";
	static final String PASSWORD = "native-password";
	static final String PROXY_USER = "proxy-user";
	static final String PROXY_PASSWORD = "proxy-password";
	private static final String PROXY_URL = "http://" + PROXY_USER + ":" + PROXY_PASSWORD
			+ "@proxy.example:8080";

	private FakeGit() {
	}

	public static void main(String[] originalArguments) throws Exception {
		List<String> arguments = new ArrayList<>(Arrays.asList(originalArguments));
		String version = "git version 2.25.0";
		File counter = null;
		while (!arguments.isEmpty() && arguments.get(0).startsWith("--fake-")) {
			String option = arguments.remove(0);
			if (option.startsWith("--fake-version=")) {
				version = option.substring("--fake-version=".length());
			}
		}
		while (!arguments.isEmpty() && arguments.get(0).startsWith("--counter=")) {
			counter = new File(arguments.remove(0).substring("--counter=".length()));
		}

		if (arguments.equals(Collections.singletonList("--version"))) {
			System.out.print(version);
			return;
		}
		if (arguments.isEmpty()) {
			System.exit(64);
		}

		String command = arguments.remove(0);
		switch (command) {
		case "args":
			writeNulArguments(arguments);
			return;
		case "io":
			copy(System.in, System.out);
			System.err.print(new File(".").getCanonicalPath());
			return;
		case "flood":
			floodStreams();
			return;
		case "fail":
			System.out.print("failure stdout " + USERNAME + " " + PROXY_USER);
			System.err.print("failure stderr " + PASSWORD + " " + PROXY_PASSWORD);
			System.exit(7);
			return;
		case "environment":
			printEnvironment();
			return;
		case "flaky":
			flaky(counter);
			return;
		default:
			System.err.print("unknown fake command " + command);
			System.exit(64);
		}
	}

	private static void writeNulArguments(List<String> arguments) throws IOException {
		for (String argument : arguments) {
			System.out.write(argument.getBytes(StandardCharsets.UTF_8));
			System.out.write(0);
		}
	}

	private static void floodStreams() throws IOException {
		byte[] stdout = new byte[4096];
		byte[] stderr = new byte[4096];
		Arrays.fill(stdout, (byte) 'O');
		Arrays.fill(stderr, (byte) 'E');
		for (int written = 0; written < FLOOD_BYTES; written += stdout.length) {
			System.out.write(stdout);
			System.err.write(stderr);
		}
	}

	private static void printEnvironment() throws IOException {
		Map<String, String> environment = System.getenv();
		String askPass = environment.get("GIT_ASKPASS");
		File askPassFile = askPass == null ? null : new File(askPass);
		String askPassContent = askPassFile != null && askPassFile.isFile()
				? new String(Files.readAllBytes(askPassFile.toPath()), StandardCharsets.UTF_8) : "";
		String proxy = first(environment, "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
		System.out.println("TERMINAL_PROMPT=" + environment.get("GIT_TERMINAL_PROMPT"));
		System.out.println("ASKPASS_PATH=" + (askPass == null ? "" : askPass));
		System.out.println("ASKPASS_EXISTED=" + (askPassFile != null && askPassFile.isFile()));
		System.out.println("ASKPASS_CONTAINED_SECRET="
				+ (askPassContent.contains(USERNAME) || askPassContent.contains(PASSWORD)));
		System.out.println("USERNAME_MATCHED=" + USERNAME.equals(environment.get("SCM4J_GIT_USERNAME")));
		System.out.println("PASSWORD_MATCHED=" + PASSWORD.equals(environment.get("SCM4J_GIT_PASSWORD")));
		System.out.println("PROXY_MATCHED=" + PROXY_URL.equals(proxy));
	}

	private static String first(Map<String, String> environment, String... names) {
		for (String name : names) {
			if (environment.containsKey(name)) {
				return environment.get(name);
			}
		}
		return null;
	}

	private static void flaky(File counter) throws IOException {
		if (counter == null) {
			throw new IOException("Counter file was not supplied");
		}
		int attempt = counter.isFile() ? Integer.parseInt(
				new String(Files.readAllBytes(counter.toPath()), StandardCharsets.UTF_8)) + 1 : 1;
		try (FileOutputStream output = new FileOutputStream(counter, false)) {
			output.write(Integer.toString(attempt).getBytes(StandardCharsets.UTF_8));
		}
		if (attempt == 1) {
			System.err.print("transient failure " + PASSWORD);
			System.exit(7);
		}
		System.out.print("success");
	}

	private static void copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[4096];
		int read;
		while ((read = input.read(buffer)) >= 0) {
			output.write(buffer, 0, read);
		}
	}
}
