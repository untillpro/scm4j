package org.scm4j.vcs.api.workingcopy;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

public class VCSWCTestBase {
	public static final String WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"),
			"scm4j-vcs-test-workspaces").getPath();
	public static final String TEST_REPO_URL = "http://test.repo.url";

	@Before
	@After
	public void setUpAndTearDown() throws IOException {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
	}
}
