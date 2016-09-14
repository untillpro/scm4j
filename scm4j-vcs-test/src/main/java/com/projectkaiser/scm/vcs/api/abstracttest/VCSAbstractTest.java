package com.projectkaiser.scm.vcs.api.abstracttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.WantedButNotInvoked;

import com.projectkaiser.scm.vcs.api.IVCS;
import com.projectkaiser.scm.vcs.api.PKVCSMergeResult;
import com.projectkaiser.scm.vcs.api.exceptions.EVCSBranchExists;
import com.projectkaiser.scm.vcs.api.exceptions.EVCSFileNotFound;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public abstract class VCSAbstractTest {
	private static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	private static final String NEW_BRANCH = "new-branch";
	private static final String CREATED_DST_BRANCH_COMMIT_MESSAGE = "created dst branch";
	private static final String DELETE_BRANCH_COMMIT_MESSAGE = "deleted";
	private static final String CONTENT_CHANGED_COMMIT_MESSAGE = "changed file content";
	private static final String FILE1_ADDED_COMMIT_MESSAGE = "test.txt file added";
	private static final String FILE2_ADDED_COMMIT_MESSAGE = "test-branch added";
	private static final String FILE1_CONTENT_CHANGED_COMMIT_MESSAGE = "test-branch content changed";
	private static final String MERGE_COMMIT_MESSAGE = "merged.";
	private static final String LINE_1 = "line 1";
	private static final String LINE_2 = "line 2";
	private static final String LINE_3 = "line 3";
	private static final String FILE1_NAME = "test-master.txt";
	private static final String FILE2_NAME = "test-branch.txt";
	private static final String TEST_FILE_PATH = "folder/file1.txt";
	private static final String TEST_FILE_PATH_2 = "folder2/file2.txt";
	protected static final String MASTER_BRANCH = "master";
	
	protected String repoName;
	protected String repoUrl;
	protected IVCSWorkspace localVCSWorkspace;
	protected IVCSRepositoryWorkspace localVCSRepo;
	protected IVCSRepositoryWorkspace mockedVCSRepo;
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
	public void setUp() throws Exception {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
		repoName = "pk-vcs-" + getVCSTypeString() + "-testrepo";

		String uuid = UUID.randomUUID().toString();
		repoName = (repoName + "_" + uuid);
		
		localVCSWorkspace = new VCSWorkspace(WORKSPACE_DIR);

		repoUrl = getTestRepoUrl() + repoName;

		localVCSRepo = localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl);
		mockedVCSRepo = Mockito.spy(localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl));
		
		resetMocks();

		vcs = getVCS(mockedVCSRepo);
		
		setMakeFailureOnVCSReset(false);
	}

	protected void resetMocks() throws Exception {
		if (mockedLWC != null) {
			mockedLWC.close();
		}
		Mockito.reset(mockedVCSRepo);
		mockedLWC = Mockito.spy(localVCSRepo.getVCSLockedWorkingCopy());
		Mockito.doReturn(mockedLWC).when(mockedVCSRepo).getVCSLockedWorkingCopy();
	}

	@Test
	public void testCreateAndDeleteBranch() throws Exception {
		vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(getBranches().contains(NEW_BRANCH));
		assertTrue(getBranches().size() == 2); // master & NEW_BRANCH

		try {
			vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
			fail("\"Branch exists\" situation not detected");
		} catch (EVCSBranchExists e) {
		}

		resetMocks();
		vcs.deleteBranch(NEW_BRANCH, DELETE_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(getBranches().size() == 1);
	}

	private void verifyMocks() throws Exception {
		try {
			Mockito.verify(mockedVCSRepo).getVCSLockedWorkingCopy();
		} catch (WantedButNotInvoked e) {
			return;
		}
		Mockito.verify(mockedLWC).close();
	}

	@Test
	public void testGetSetFileContent() throws Exception {
		setTestContent(TEST_FILE_PATH, LINE_1, MASTER_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(MASTER_BRANCH, TEST_FILE_PATH, LINE_2, CONTENT_CHANGED_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(getCommitMessagesRemote(MASTER_BRANCH).contains(CONTENT_CHANGED_COMMIT_MESSAGE));
		assertEquals(vcs.getFileContent(MASTER_BRANCH, TEST_FILE_PATH), LINE_2);
		assertEquals(vcs.getFileContent(MASTER_BRANCH, TEST_FILE_PATH, "UTF-8"), LINE_2);
		try {
			vcs.getFileContent(MASTER_BRANCH, "sdfsdf1.txt");
			fail("EVCSFileNotFound is not thrown");
		} catch (EVCSFileNotFound e) {
		}
		
		// test unexisting file creation
		vcs.setFileContent(MASTER_BRANCH, TEST_FILE_PATH_2, LINE_2, CONTENT_CHANGED_COMMIT_MESSAGE);
		assertEquals(vcs.getFileContent(MASTER_BRANCH, TEST_FILE_PATH_2), LINE_2);
		
	}

	@Test
	public void testMerge() throws Exception {
		vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_1, MASTER_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		setTestContent(FILE2_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();

		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, MASTER_BRANCH, MERGE_COMMIT_MESSAGE);

		verifyMocks();
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getSuccess());
		assertTrue(res.getConflictingFiles().size() == 0);
		String content = vcs.getFileContent(MASTER_BRANCH, FILE1_NAME);

		assertEquals(content, LINE_1);
		content = vcs.getFileContent(MASTER_BRANCH, FILE2_NAME);
		assertEquals(content, LINE_2);
	}

	@Test
	public void testMergeConflict() throws Exception {
		vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_1, MASTER_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, MASTER_BRANCH, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
	}

	@Test
	public void testMergeConflictWCCorruption() throws Exception {
		vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_1, MASTER_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();
		setMakeFailureOnVCSReset(true);
		PKVCSMergeResult res = vcs.merge(NEW_BRANCH, MASTER_BRANCH, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertTrue(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
		assertFalse(mockedLWC.getFolder().exists());
		assertFalse(mockedLWC.getLockFile().exists());
	}
	
	@Test
	public void testBranchesDiff() throws Exception {
		setTestContent(FILE1_NAME, LINE_1, MASTER_BRANCH, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.createBranch(MASTER_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		setTestContent(TEST_FILE_PATH, LINE_2, NEW_BRANCH, FILE2_ADDED_COMMIT_MESSAGE);
		setTestContent(FILE1_NAME, LINE_3, NEW_BRANCH, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE);
		List<String> changedFiles = vcs.getBranchesDiff(NEW_BRANCH, MASTER_BRANCH);
		assertNotNull(changedFiles);
		assertTrue(changedFiles.contains(FILE1_NAME));
		assertTrue(changedFiles.contains(TEST_FILE_PATH));
	}
	
	protected void setTestContent(String filePath, String fileContent, String branchName,
			String commitMessage) throws Exception {
		try (IVCSLockedWorkingCopy wc = localVCSRepo.getVCSLockedWorkingCopy()) {
			checkout(branchName, wc);
			File file = new File(wc.getFolder(), filePath);
			file.getParentFile().mkdirs();
			file.createNewFile();
			PrintWriter out = new PrintWriter(file);
			out.print(fileContent);
			out.close();
			
			sendFile(wc, branchName, filePath, commitMessage);
		}
	}
	
	
	
	public abstract String getVCSTypeString();

	protected abstract String getTestRepoUrl();

	protected abstract IVCS getVCS(IVCSRepositoryWorkspace mockedVCSRepo);

	protected abstract Set<String> getBranches() throws Exception;

	protected abstract Set<String> getCommitMessagesRemote(String branchName) throws Exception;

	protected abstract void checkout(String branchName, IVCSLockedWorkingCopy wc) throws Exception;
	
	protected abstract void setMakeFailureOnVCSReset(Boolean doMakeFailure);
	
	protected abstract void sendFile(IVCSLockedWorkingCopy wc, String branchName, String filePath, String commitMessage) 
			throws Exception;
}
