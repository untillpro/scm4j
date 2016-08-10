package com.projectkaiser.scm.vcs.api;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.projectkaiser.scm.vcs.api.exceptions.EVCSBranchExists;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace;
import com.projectkaiser.scm.vcs.api.workingcopy.VCSWorkspace;

public abstract class VCSAbstractTest {
	public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	public static final String TEST_REPO_URL = "c:/test/utils/";
	public static String REPO_NAME;
	public static final String NEW_BRANCH = "new-branch";
	public static final String SRC_BRANCH = "master";
	public static final String CREATED_DST_BRANCH_COMMIT_MESSAGE = "created dst branch";
	public static final String DELETE_BRANCH_COMMIT_MESSAGE = "deleted";
	protected String repoName;
	protected String repoUrl;
	protected IVCSRepositoryWorkspace localVCSRepo;
	protected IVCSWorkspace localVCSWorkspace;
	protected IVCSRepositoryWorkspace mockedVCSRepo;
	protected IVCSLockedWorkingCopy mockedLWC;
	protected IVCS vcs;

	public IVCS getVcs() {
		return vcs;
	}

	public void setVcs(IVCS vcs) {
		this.vcs = vcs;
	}

	@Before
	@After
	public void setUpAndTearDown() throws IOException {
		FileUtils.deleteDirectory(new File(WORKSPACE_DIR));
	}
	
	@Before
	public void setUp() throws IOException {
		REPO_NAME = "pk-vcs-" + getVCSTypeString() + "-testrepo";
		
		String uuid = UUID.randomUUID().toString();
		repoName = (REPO_NAME + "_" + uuid);
		
		repoUrl = getVCSTypeString() + "." + getVCSRepoUrl() + repoName;
		
		localVCSWorkspace = new VCSWorkspace(WORKSPACE_DIR);
		localVCSRepo = localVCSWorkspace.getVCSRepositoryWorkspace(repoUrl);
		
		mockedLWC = Mockito.spy(localVCSRepo.getVCSLockedWorkingCopy());
		Mockito.doReturn(mockedLWC).when(mockedVCSRepo).getVCSLockedWorkingCopy();
		
		createVCS(mockedVCSRepo);
	}
	
	protected void resetMocks() {
		Mockito.reset(mockedVCSRepo);
		mockedLWC = Mockito.spy(localVCSRepo.getVCSLockedWorkingCopy());
		Mockito.doReturn(mockedLWC).when(mockedVCSRepo).getVCSLockedWorkingCopy();
	}
	
	@Test
	public void testCreateBranch() throws Exception {
		vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		Mockito.verify(mockedVCSRepo).getVCSLockedWorkingCopy();
		Mockito.verify(mockedLWC).close();
		Thread.sleep(2000); // next operation fails time to time. Looks like github has some latency on branch operations
		
		assertTrue(getBranches().contains(NEW_BRANCH));
		assertTrue(getBranches().size() == 2); // master & NEW_BRANCH
		
		try {
			vcs.createBranch(SRC_BRANCH, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
			fail("\"Branch exists\" situation not detected");
		} catch (EVCSBranchExists e) {
		}
		resetMocks();
		vcs.deleteBranch(NEW_BRANCH, DELETE_BRANCH_COMMIT_MESSAGE);
		Mockito.verify(mockedVCSRepo).getVCSLockedWorkingCopy();
		Mockito.verify(mockedLWC).close();
		Thread.sleep(2000); // next operation fails from time to time. Looks like github has some latency on branch operations
		assertTrue (getBranches().size() == 1);
	}
	
	public abstract String getVCSTypeString();
	protected abstract String getVCSRepoUrl();
	protected abstract void createVCS(IVCSRepositoryWorkspace mockedVCSRepo);
	protected abstract Set<String> getBranches() throws IOException;
}

