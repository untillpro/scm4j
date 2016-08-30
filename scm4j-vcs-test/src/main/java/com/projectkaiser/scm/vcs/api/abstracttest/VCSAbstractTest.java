package com.projectkaiser.scm.vcs.api.abstracttest;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.projectkaiser.scm.vcs.api.IVCS;
import com.projectkaiser.scm.vcs.api.PKVCSMergeResult;
import com.projectkaiser.scm.vcs.api.exceptions.EVCSBranchExists;
import com.projectkaiser.scm.vcs.api.exceptions.EVCSFileNotFound;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public abstract class VCSAbstractTest {
	public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	public static final String TEST_REPO_URL = "c:/test/utils/";
	public static String REPO_NAME;
	public static final String NEW_BRANCH = "new-branch";
	public static final String SRC_BRANCH = "master";
	public static final String CREATED_DST_BRANCH_COMMIT_MESSAGE = "created dst branch";
	public static final String DELETE_BRANCH_COMMIT_MESSAGE = "deleted";
	protected static final String CONTENT_CHANGED_COMMIT_MESSAGE = "changed file content";
	protected static final String FILE1_ADDED_COMMIT_MESSAGE = "test.txt file added";
	protected static final String FILE2_ADDED_COMMIT_MESSAGE = "test-branch added";
	protected static final String MERGE_COMMIT_MESSAGE = "merged.";
	protected static final String LINE_2 = "line 2";
	protected static final String LINE_1 = "line 1";
	protected static final String FILE1_NAME = "test-master.txt";
	protected static final String FILE2_NAME = "test-branch.txt";
	protected static final String TEST_FILE_PATH = "folder/file1.txt";
	protected String repoName;
	protected String repoUrl;
	protected IVCSRepositoryWorkspace localVCSRepo;
	protected IVCSWorkspace localVCSWorkspace;
	protected IVCSRepositoryWorkspace mockedVCSRepo;
	protected VCSLockedWorkingCopy l;
	protected IVCSLockedWorkingCopy mockedLWC;
	protected IVCS vcs;

	public IVCS getVcs() {
		return vcs;
	}

	public void setVcs(IVCS vcs) {
		this.vcs = vcs;
	}

	@After
	public void setUpAndTearDown() throws Exception {
		mockedLWC.close();
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
	}

	@Before
	public void setUp() throws IOException {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
		REPO_NAME = "pk-vcs-" + getVCSTypeString() + "-testrepo";

		String uuid = UUID.randomUUID().toString();
		repoName = (REPO_NAME + "_" + uuid);

		repoUrl = getVCSRepoUrl() + repoName;

		localVCSWorkspace = new VCSWorkspace(WORKSPACE_DIR);
		localVCSRepo = localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl);
		mockedVCSRepo = Mockito.spy(localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl));

		resetMocks();

		createVCS(mockedVCSRepo);
		
		setMakeFailureOnVCSReset(false);
	}

	protected void resetMocks() {
		Mockito.reset(mockedVCSRepo);
		mockedLWC = Mockito.spy(localVCSRepo.getVCSLockedWorkingCopy());
		Mockito.doReturn(mockedLWC).when(mockedVCSRepo).getVCSLockedWorkingCopy();
	}

	@Test
	public void testCreateAndDeleteBranch() throws Exception {
		vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);

		verifyMocks();
		Thread.sleep(2000); // next operation fails time to time. Looks like
							// github has some latency on branch operations

		assertTrue(getBranches().contains(NEW_BRANCH));
		assertTrue(getBranches().size() == 2); // master & NEW_BRANCH

		try {
			vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
			fail("\"Branch exists\" situation not detected");
		} catch (EVCSBranchExists e) {
		}

		resetMocks();
		vcs.deleteBranch(NEW_BRANCH, DELETE_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		Thread.sleep(2000); // next operation fails from time to time. Looks
							// like github has some latency on branch operations
		assertTrue(getBranches().size() == 1);
	}

	private void verifyMocks() throws Exception {
		Mockito.verify(mockedVCSRepo).getVCSLockedWorkingCopy();
		Mockito.verify(mockedLWC).close();
	}

	@Test
	public void testGetSetFileContent() throws Exception {
		createTestContent(TEST_FILE_PATH, LINE_1, "master", FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent("master", TEST_FILE_PATH, LINE_2, CONTENT_CHANGED_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(getCommitMessagesRemote("master").contains(CONTENT_CHANGED_COMMIT_MESSAGE));
		assertEquals(vcs.getFileContent("master", TEST_FILE_PATH), LINE_2);
		assertEquals(vcs.getFileContent("master", TEST_FILE_PATH, "UTF-8"), LINE_2);
		try {
			vcs.getFileContent("master", "sdfsdf1.txt");
			fail("EVCSFileNotFound is not thrown");
		} catch (EVCSFileNotFound e) {
		}
	}

	@Test
	public void testMerge() throws Exception {
		vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		createTestContent(FILE1_NAME, LINE_1, SRC_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		createTestContent(FILE2_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();

		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, SRC_BRANCH, MERGE_COMMIT_MESSAGE);

		verifyMocks();
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getSuccess());
		assertTrue(res.getConflictingFiles().size() == 0);
		String content = vcs.getFileContent(SRC_BRANCH, FILE1_NAME);

		assertEquals(content, LINE_1);
		content = vcs.getFileContent(SRC_BRANCH, FILE2_NAME);
		assertEquals(content, LINE_2);
	}

	@Test
	public void testMergeConflict() throws Exception {
		vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		createTestContent(FILE1_NAME, LINE_1, SRC_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		createTestContent(FILE1_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, SRC_BRANCH, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
	}

	@Test
	public void testMergeConflictWCCorruption() throws Exception {
		vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		createTestContent(FILE1_NAME, LINE_1, SRC_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		createTestContent(FILE1_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();
		setMakeFailureOnVCSReset(true);
		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, SRC_BRANCH, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertTrue(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
		assertFalse(mockedLWC.getFolder().exists());
		assertFalse(mockedLWC.getLockFile().exists());
	}

	public abstract String getVCSTypeString();

	protected abstract String getVCSRepoUrl();

	protected abstract void createVCS(IVCSRepositoryWorkspace mockedVCSRepo);

	protected abstract Set<String> getBranches() throws IOException;

	protected abstract Set<String> getCommitMessagesRemote(String branchName) throws Exception;

	protected abstract void createTestContent(String filePath, String fileContent, String branchName,
			String commitMessage) throws Exception;
	
	protected abstract void setMakeFailureOnVCSReset(Boolean doMakeFailure);
}
