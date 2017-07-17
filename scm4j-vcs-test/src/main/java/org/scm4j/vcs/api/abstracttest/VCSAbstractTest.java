package org.scm4j.vcs.api.abstracttest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.scm4j.vcs.api.*;
import org.scm4j.vcs.api.exceptions.EVCSBranchExists;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;
import org.scm4j.vcs.api.exceptions.EVCSTagExists;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public abstract class VCSAbstractTest {
	protected static final String WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-vcs-workspaces").getPath();
	protected static final String NEW_BRANCH = "new-branch";
	protected static final String NEW_BRANCH_2 = "new-branch-2";
	protected static final String CREATED_DST_BRANCH_COMMIT_MESSAGE = "created dst branch";
	protected static final String DELETE_BRANCH_COMMIT_MESSAGE = "deleted";
	protected static final String FILE1_NAME = "file1.txt";
	protected static final String FILE2_NAME = "file2.txt";
	protected static final String FILE3_IN_FOLDER_NAME = "folder/file3.txt";
	protected static final String MOD_FILE_NAME = "mod file.txt";
	protected static final String FILE1_ADDED_COMMIT_MESSAGE = FILE1_NAME + " file added";
	protected static final String FILE2_ADDED_COMMIT_MESSAGE = FILE2_NAME + " file added";
	protected static final String FILE3_ADDED_COMMIT_MESSAGE = FILE3_IN_FOLDER_NAME + " file added";
	protected static final String MOD_FILE_ADDED_COMMIT_MESSAGE = MOD_FILE_NAME + " file added";
	protected static final String FILE1_CONTENT_CHANGED_COMMIT_MESSAGE = FILE1_NAME + " content changed";
	protected static final String MOD_FILE_CONTENT_CHANGED_COMMIT_MESSAGE = MOD_FILE_NAME + " content changed";
	protected static final String MERGE_COMMIT_MESSAGE = "merged.";
	protected static final String LINE_1 = "line 1";
	protected static final String LINE_2 = "line 2";
	protected static final String LINE_3 = "line 3";
	protected static final String MOD_LINE_1= "original line";
	protected static final String MOD_LINE_2= "modified line";
	protected static final String TAG_MESSAGE_1 = "tag 1 message";
	protected static final String TAG_NAME_1 = "tag1_name";
	protected static final String TAG_MESSAGE_2 = "tag 2 message";
	protected static final String TAG_NAME_2 = "tag2_name";
	protected static final String TAG_MESSAGE_3 = "tag 3 message";
	protected static final String TAG_NAME_3 = "tag3_name";

	protected static final String CONTENT_CHANGED_COMMIT_MESSAGE = "content changed";
	protected static final String FILE2_REMOVED_COMMIT_MESSAGE = FILE2_NAME + " removed";
	protected static final Integer DEFAULT_COMMITS_LIMIT = 100;

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
		File workspaceDir = new File(WORKSPACE_DIR);
		if (workspaceDir.exists()) {
			FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
		}
		
		repoName = "scm4j-vcs-" + getVCSTypeString() + "-testrepo";

		String uuid = UUID.randomUUID().toString();
		repoName = (repoName + "_" + uuid);
		
		localVCSWorkspace = new VCSWorkspace(WORKSPACE_DIR);

		repoUrl = StringUtils.appendIfMissing(getTestRepoUrl(), "/") + repoName;

		localVCSRepo = localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl);
		mockedVCSRepo = Mockito.spy(localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl));
		
		vcs = getVCS(mockedVCSRepo);
		
		resetMocks();
		
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
	public void testBranchCreateAndDelete() throws Exception {
		vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_1, FILE3_ADDED_COMMIT_MESSAGE);
		resetMocks();
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(vcs.getBranches().contains(NEW_BRANCH));
		verifyMocks();
		assertTrue(vcs.getBranches().size() == 2); // Master + NEW_BRANCH, no Folder
		verifyMocks();
		assertTrue(vcs.getFileContent(NEW_BRANCH, FILE3_IN_FOLDER_NAME).equals(LINE_1));
		resetMocks();

		vcs.createBranch(NEW_BRANCH, NEW_BRANCH_2, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(vcs.getBranches().contains(NEW_BRANCH));
		verifyMocks();
		assertTrue(vcs.getBranches().contains(NEW_BRANCH_2));
		verifyMocks();
		assertTrue(vcs.getBranches().size() == 3);
		verifyMocks();

		try {
			vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
			fail("\"Branch exists\" situation not detected");
		} catch (EVCSBranchExists e) {
		}

		// make the branch to delete be checked out
		vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		resetMocks();
		vcs.deleteBranch(NEW_BRANCH, DELETE_BRANCH_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(vcs.getBranches().size() == 2);
		assertFalse(vcs.getBranches().contains(NEW_BRANCH));
	}

	private void verifyMocks() throws Exception {
		try {
			Mockito.verify(mockedVCSRepo).getVCSLockedWorkingCopy();
		} catch (WantedButNotInvoked e) {
			resetMocks();
			return;
		}
		Mockito.verify(mockedLWC).close();
		resetMocks();
	}

	@Test
	public void testFileGetSetContent() throws Exception {
		vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_1, FILE3_ADDED_COMMIT_MESSAGE);
		verifyMocks();
		assertTrue(vcs.getCommitMessages(null, DEFAULT_COMMITS_LIMIT).contains(FILE3_ADDED_COMMIT_MESSAGE));
		verifyMocks();
		assertEquals(vcs.getFileContent(null, FILE3_IN_FOLDER_NAME), LINE_1);
		verifyMocks();
		assertEquals(vcs.getFileContent(null, FILE3_IN_FOLDER_NAME, "UTF-8"), LINE_1);
		verifyMocks();
		vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_2, CONTENT_CHANGED_COMMIT_MESSAGE);
		assertEquals(vcs.getFileContent(null, FILE3_IN_FOLDER_NAME), LINE_2);
		assertEquals(vcs.getFileContent(null, FILE3_IN_FOLDER_NAME, "UTF-8"), LINE_2);
		
		try {
			vcs.getFileContent(null, "sdfsdf1.txt");
			fail("EVCSFileNotFound is not thrown");
		} catch (EVCSFileNotFound e) {
		}
	}

	@Test
	public void testMerge() throws Exception {
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();

		VCSMergeResult res = vcs.merge(NEW_BRANCH, null, MERGE_COMMIT_MESSAGE);

		verifyMocks();
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getSuccess());
		assertTrue(res.getConflictingFiles().size() == 0);
		String content = vcs.getFileContent(null, FILE1_NAME);

		assertEquals(content, LINE_1);
		content = vcs.getFileContent(null, FILE2_NAME);
		assertEquals(content, LINE_2);
	}

	@Test
	public void testMergeConflict() {
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		VCSMergeResult res = vcs.merge(NEW_BRANCH, null, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertFalse(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
	}

	@Test
	public void testMergeConflictWCCorruption() throws Exception {
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();
		setMakeFailureOnVCSReset(true);
		VCSMergeResult res = vcs.merge(NEW_BRANCH, null, MERGE_COMMIT_MESSAGE);
		assertFalse(res.getSuccess());
		assertTrue(mockedLWC.getCorrupted());
		assertTrue(res.getConflictingFiles().size() == 1);
		assertTrue(res.getConflictingFiles().contains(FILE1_NAME));
		assertFalse(mockedLWC.getFolder().exists());
		assertFalse(mockedLWC.getLockFile().exists());
	}
	
	@Test
	public void testBranchesDiff() throws Exception {
		/**
		 * Master Branch
		 *  f1-     
		 *   |     f2-
		 *   |     mfm
		 *   |     mf+ (merge)
		 *   |   /
		 *  mf+  
		 *   |     tf+ (merge) 
		 *   |   /  |
		 *  tf+    f1m
		 *   |     f3+
		 *  f2+  /     	 
		 *  f1+
		 *  
		 *  Result should be: f3+, f1+, f2-, mfm.
		 *  But: Result of merge operation for f1 is missing file even by TortouiseSVN 
		 */
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE2_NAME, LINE_1, FILE2_ADDED_COMMIT_MESSAGE);
		
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE3_IN_FOLDER_NAME, LINE_2, FILE3_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_3, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE);
		
		vcs.setFileContent(null, "trunk file.txt", "dfdfsdf", "trunk file added");
		
		vcs.setFileContent(null, MOD_FILE_NAME, MOD_LINE_1, MOD_FILE_ADDED_COMMIT_MESSAGE);
		
		vcs.merge(null, NEW_BRANCH, MERGE_COMMIT_MESSAGE);
		
		vcs.setFileContent(NEW_BRANCH, MOD_FILE_NAME, MOD_LINE_2, MOD_FILE_CONTENT_CHANGED_COMMIT_MESSAGE);
		
		vcs.merge(null, NEW_BRANCH, "merged from trunk");
		
		vcs.removeFile(NEW_BRANCH, FILE2_NAME, FILE2_REMOVED_COMMIT_MESSAGE);
		
		vcs.removeFile(null,  FILE1_NAME, "file1 removed");
		
		//vcs.setFileContent(null, "folder/file 2 in folder.txt", "file 2 in folder line", "conflicting folder added");
		vcs.setFileContent(null, "moved file trunk.txt", "file 2 in folder line", "moved file added");
		//vcs.merge(null, NEW_BRANCH, "merged moved file trunk.txt from trunk");
		
		
		resetMocks();
		List<VCSDiffEntry> diffs = vcs.getBranchesDiff(NEW_BRANCH, null);
		verifyMocks();
		assertNotNull(diffs);
		VCSDiffEntry diff;
		
		diff = getEntryDiffForFile(diffs, FILE3_IN_FOLDER_NAME);
		assertNotNull(diff);
		assertTrue(diff.getChangeType() == VCSChangeType.ADD);
		assertTrue(diff.getUnifiedDiff().contains("+" + LINE_2));
		
		diff = getEntryDiffForFile(diffs, MOD_FILE_NAME);
		assertNotNull(diff);
		assertTrue(diff.getChangeType() == VCSChangeType.MODIFY);
		assertTrue(diff.getUnifiedDiff().contains("-" + MOD_LINE_1));
		assertTrue(diff.getUnifiedDiff().contains("+" + MOD_LINE_2));
		
		diff = getEntryDiffForFile(diffs, FILE1_NAME);
		assertNotNull(diff);
		assertTrue(diff.getChangeType() == VCSChangeType.ADD);
		assertTrue(diff.getUnifiedDiff().contains("+" + LINE_3));
		
		diff = getEntryDiffForFile(diffs, FILE2_NAME);
		assertNotNull(diff);
		assertTrue(diff.getChangeType() == VCSChangeType.DELETE);
		assertTrue(diff.getUnifiedDiff().contains("-" + LINE_1));
	}
	
	private VCSDiffEntry getEntryDiffForFile(List<VCSDiffEntry> entries, String filePath) {
		for (VCSDiffEntry entry : entries) {
			if (entry.getFilePath().equals(filePath)) {
				return entry;
			}
		}
		return null;
	}

	@Test
	public void testFileRemove() throws Exception {
		vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_1, FILE3_ADDED_COMMIT_MESSAGE);
		resetMocks();
		vcs.removeFile(null, FILE3_IN_FOLDER_NAME, FILE2_REMOVED_COMMIT_MESSAGE);
		verifyMocks();
		try {
			vcs.getFileContent(null, FILE3_IN_FOLDER_NAME);
			fail();
		} catch (EVCSFileNotFound e) {
		}
		
		List<String> commits = vcs.getCommitMessages(null, DEFAULT_COMMITS_LIMIT);
		assertTrue(commits.contains(FILE2_REMOVED_COMMIT_MESSAGE));
	}
	
	@Test
	public void testCommitGetMessages() throws Exception {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_3, FILE3_ADDED_COMMIT_MESSAGE);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		resetMocks();
		List<String> commits = vcs.getCommitMessages(null, DEFAULT_COMMITS_LIMIT);
		verifyMocks();
		assertTrue(commits.contains(FILE1_ADDED_COMMIT_MESSAGE));
		assertTrue(commits.contains(FILE3_ADDED_COMMIT_MESSAGE));
		commits = vcs.getCommitMessages(null, 1);
		assertFalse(commits.contains(FILE1_ADDED_COMMIT_MESSAGE));
		commits = vcs.getCommitMessages(NEW_BRANCH, DEFAULT_COMMITS_LIMIT);
		assertTrue(commits.contains(FILE2_ADDED_COMMIT_MESSAGE));
	}
	
	@Test 
	public void testCommitsGetRange() throws Exception {
		/**
		 * Master Branch
		 * 
		 *        f11+
		 *        f2+
		 *  f5+    |  
		 *  f4+    |
		 *   |   / 
		 *  f3+       	
		 *  f1+ 
		 */
		String c1 = vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE).getRevision();
		String c3 = vcs.setFileContent(null, FILE3_IN_FOLDER_NAME, LINE_3, FILE3_ADDED_COMMIT_MESSAGE).getRevision();
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		String c4 = vcs.setFileContent(null, "file 4.txt", "dfdfsdf", "File 4 master added").getRevision();
		String c5 = vcs.setFileContent(null, "file 5.txt", "dfdfsdf", "File 5 master added").getRevision();
		String c2 = vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE).getRevision();
		String c11 = vcs.setFileContent(NEW_BRANCH, FILE1_NAME, LINE_2, "file 1 branch added").getRevision();

		resetMocks();
		List<VCSCommit> commits = vcs.getCommitsRange(null, c1, null);
		verifyMocks();
		assertTrue(commitsConsistsOfIds(commits, c3, c4, c5));
		
		commits = vcs.getCommitsRange(null, null, null);
		assertTrue(commitsConsistsOfIds(commits, c3, c4, c5));
		
		commits = vcs.getCommitsRange(NEW_BRANCH, c1, null);
		assertTrue(commitsContainsIds(commits, c2, c11));
		
		commits = vcs.getCommitsRange(null, c1, c4);
		assertTrue(commitsConsistsOfIds(commits, c3, c4));


		resetMocks();
		commits = vcs.getCommitsRange(null, c1, WalkDirection.ASC, 0);
		verifyMocks();
		assertTrue(commitsContainsSequenceOfIds(commits, c1, c3, c4, c5));

		commits = vcs.getCommitsRange(null, c1, WalkDirection.ASC, 2);
		assertTrue(commitsContainsSequenceOfIds(commits, c1, c3));
		assertTrue(commits.get(0).getRevision().equals(c1));

		commits = vcs.getCommitsRange(null, null, WalkDirection.ASC, 0);
		assertTrue(commitsContainsSequenceOfIds(commits, c1, c3, c4, c5));

		commits = vcs.getCommitsRange(NEW_BRANCH, c1, WalkDirection.ASC, 0);
		assertTrue(commitsContainsSequenceOfIds(commits, c2, c11));


		commits = vcs.getCommitsRange(null, c5, WalkDirection.DESC, 0);
		assertTrue(commitsContainsSequenceOfIds(commits, c5, c4, c3, c1));

		commits = vcs.getCommitsRange(null, c1, WalkDirection.DESC, 1);
		assertTrue(commits.get(0).getRevision().equals(c1));

		commits = vcs.getCommitsRange(null, null, WalkDirection.DESC, 0);
		assertTrue(commitsContainsSequenceOfIds(commits, c5, c4, c3, c1));
		assertTrue(commits.get(0).getRevision().equals(c5));

		commits = vcs.getCommitsRange(NEW_BRANCH, c11, WalkDirection.DESC, 0);
		assertTrue(commitsContainsSequenceOfIds(commits, c11, c2));
		assertTrue(commits.get(0).getRevision().equals(c11));
	}

	@Test
	public void testCommitGetHead() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		VCSCommit commit2 = vcs.setFileContent(null, FILE2_NAME, LINE_1, FILE2_ADDED_COMMIT_MESSAGE);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		VCSCommit commit3 = vcs.setFileContent(NEW_BRANCH, FILE3_IN_FOLDER_NAME, LINE_2, FILE3_ADDED_COMMIT_MESSAGE);
		assertTrue(vcs.getHeadCommit(null).equals(commit2));
		assertTrue(vcs.getHeadCommit(NEW_BRANCH).equals(commit3));
	}
	
	@Test
	public void testFileExists() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE3_IN_FOLDER_NAME, LINE_2, FILE3_ADDED_COMMIT_MESSAGE);
		assertTrue(vcs.fileExists(null, FILE1_NAME));
		assertTrue(vcs.fileExists(NEW_BRANCH, FILE3_IN_FOLDER_NAME));
		assertFalse(vcs.fileExists(null, "no file"));
	}
	
	@Test
	public void testTagCreate() throws InterruptedException {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		VCSCommit initialCommit = vcs.setFileContent(null, FILE2_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		VCSTag ethalonTag = vcs.createTag(null, TAG_NAME_1, TAG_MESSAGE_1);
		assertEquals(ethalonTag.getRelatedCommit(), initialCommit);
		assertEquals(ethalonTag.getTagMessage(), TAG_MESSAGE_1);
		assertEquals(ethalonTag.getTagName(), TAG_NAME_1);
		assertEquals(ethalonTag.getAuthor(), initialCommit.getAuthor());
		Thread.sleep(1000);
		try {
			vcs.createTag(null, TAG_NAME_1, TAG_MESSAGE_1);
			fail();
		} catch (EVCSTagExists e) {
			
		}
		
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		initialCommit = vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_1, FILE2_ADDED_COMMIT_MESSAGE);
		ethalonTag = vcs.createTag(NEW_BRANCH, TAG_NAME_2, TAG_MESSAGE_2);
		assertEquals(ethalonTag.getRelatedCommit(), initialCommit);
		assertEquals(ethalonTag.getTagMessage(), TAG_MESSAGE_2);
		assertEquals(ethalonTag.getTagName(), TAG_NAME_2);
		assertEquals(ethalonTag.getAuthor(), initialCommit.getAuthor());
		Thread.sleep(1000);
		try {
			vcs.createTag(NEW_BRANCH, TAG_NAME_2, TAG_MESSAGE_2);
			fail();
		} catch (EVCSTagExists e) {
			
		}
	}
	
	@Test
	public void testTagsList() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		VCSTag ethalonTag1 = vcs.createTag(null, TAG_NAME_1, TAG_MESSAGE_1);
		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_1, FILE2_ADDED_COMMIT_MESSAGE);
		VCSTag ethalonTag2 = vcs.createTag(NEW_BRANCH, TAG_NAME_2, TAG_MESSAGE_2);
		List<VCSTag> tags = vcs.getTags();
		assertTrue(tags.size() == 2);
		
		VCSTag actualTag1 = tags.get(0);
		VCSTag actualTag2 = tags.get(1);
		assertEquals(actualTag1, ethalonTag1);
		assertEquals(actualTag2, ethalonTag2);
	}

	@Test
	public void testGetWorkspace() {
		assertEquals(vcs.getWorkspace(), localVCSWorkspace);
	}

	@Test
	public void testToString() {
		assertTrue(vcs.toString().contains(repoUrl));
	}

	@Test
	public void testGetLastTag() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.createTag(null, TAG_NAME_1, TAG_MESSAGE_1);
		vcs.setFileContent(null, FILE1_NAME, LINE_2, FILE1_CONTENT_CHANGED_COMMIT_MESSAGE);
		VCSTag ethalonTag2 = vcs.createTag(null, TAG_NAME_2, TAG_MESSAGE_2);
		assertEquals(vcs.getLastTag(), ethalonTag2);

		vcs.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		vcs.setFileContent(NEW_BRANCH, FILE2_NAME, LINE_1, FILE2_ADDED_COMMIT_MESSAGE);
		VCSTag ethalonTag3 = vcs.createTag(NEW_BRANCH, TAG_NAME_3, TAG_MESSAGE_3);
		assertEquals(vcs.getLastTag(), ethalonTag3);
	}
	
	@Test
	public void removeTag() {
		vcs.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		vcs.setFileContent(null, FILE2_NAME, LINE_2, FILE2_ADDED_COMMIT_MESSAGE);
		vcs.createTag(null, TAG_NAME_1, TAG_MESSAGE_1);
		assertTrue(vcs.getTags().contains(TAG_NAME_1));
		vcs.removeTag(TAG_NAME_1);
		assertFalse(vcs.getTags().contains(TAG_NAME_1));
	}

	private Boolean commitsContainsIds(List<VCSCommit> commits, String... ids) {
		if (commits.size() == 0 || ids.length == 0) {
			return false;
		}
		Integer count = 0;
		for (String id : ids) {
			for(VCSCommit commit : commits) {
				if (commit.getRevision().equals(id)) {
					count++;
					break;
				}
			}
		}
		return count == ids.length;
	}

	private Boolean commitsContainsSequenceOfIds(List<VCSCommit> commits,String... ids) {
		if (commits.size() == 0 || ids.length == 0) {
			return false;
		}
		Integer idIndex = 0;
		for (VCSCommit commit : commits) {
			if (commit.getRevision().equals(ids[idIndex])) {
				idIndex++;
			} else if (idIndex != 0) {
				return false;
			}
			if (idIndex >= ids.length) {
				return true;
			}
		}
		return false;
	}
	
	private Boolean commitsConsistsOfIds(List<VCSCommit> commits, String... ids) {
		if (commits.size() == 0 || ids.length == 0) {
			return false;
		}
		Integer count = 0;
		Boolean found;
		for (String id : ids) {
			found = false;
			for(VCSCommit commit : commits) {
				if (commit.getRevision().equals(id)) {
					count++;
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return count == ids.length;
	}
	
	protected abstract String getTestRepoUrl();
	
	protected abstract IVCS getVCS(IVCSRepositoryWorkspace mockedVCSRepo);

	protected abstract void setMakeFailureOnVCSReset(Boolean doMakeFailure) throws Exception;
	
	protected abstract String getVCSTypeString();
}
