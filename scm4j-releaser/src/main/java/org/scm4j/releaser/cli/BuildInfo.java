/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.releaser.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class BuildInfo {
	private static final String RESOURCE = "/META-INF/scm4j-releaser-build.properties";
	private static final String COMMIT_PROPERTY = "commit";

	private BuildInfo() {
	}

	static String getCommit() {
		Properties properties = new Properties();
		try (InputStream input = BuildInfo.class.getResourceAsStream(RESOURCE)) {
			if (input == null) {
				throw new IllegalStateException("Missing releaser build information: " + RESOURCE);
			}
			properties.load(input);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read releaser build information: " + RESOURCE, e);
		}

		String commit = properties.getProperty(COMMIT_PROPERTY);
		if (commit == null || commit.trim().isEmpty()) {
			throw new IllegalStateException("Missing releaser build commit in " + RESOURCE);
		}
		return commit.trim();
	}
}
