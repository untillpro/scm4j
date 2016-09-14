package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

public class VCSWCTestBase {
	public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	public static final String TEST_REPO_URL = System.getProperty("java.io.tmpdir") + "pk-vcs-test/utils";

	@Before
	@After
	public void setUpAndTearDown() throws IOException {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
	}
}
