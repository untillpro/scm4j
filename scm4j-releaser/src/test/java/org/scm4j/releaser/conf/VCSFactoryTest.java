package org.scm4j.releaser.conf;

import java.io.File;

import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.git.GitVCS;
import org.scm4j.vcs.svn.SVNVCS;

import static org.junit.Assert.*;

public class VCSFactoryTest {
	
	private static final String PWD = "pwd";
	private static final String NAME = "name";
	private static final String URL = "http://my.url.com.git";
	private static final String COMPONENT_SUBFOLDER = "components/driver";

	@Test
	public void testGitCreate() {
		IVCSWorkspace mockedWS = Mockito.mock(IVCSWorkspace.class);
		IVCSRepositoryWorkspace mockedRW = Mockito.mock(IVCSRepositoryWorkspace.class);
		Mockito.doReturn(mockedRW).when(mockedWS)
				.getVCSRepositoryWorkspace(URL);
		Mockito.doReturn(URL).when(mockedRW).getRepoUrl();

		IVCS vcs = VCSFactory.getVCS(VCSType.GIT, new Credentials(NAME, PWD, true), URL, mockedWS);

		assertTrue(vcs instanceof GitVCS);
		assertEquals(URL, vcs.getRepoUrl());
		Mockito.verify(mockedWS).getVCSRepositoryWorkspace(URL);
	}

	@Test
	public void testGitCreateForMonorepoComponent() {
		IVCSWorkspace mockedWS = Mockito.mock(IVCSWorkspace.class);
		IVCSRepositoryWorkspace mockedRW = Mockito.mock(IVCSRepositoryWorkspace.class);
		Mockito.doReturn(mockedRW).when(mockedWS)
				.getVCSRepositoryWorkspace(URL, COMPONENT_SUBFOLDER);
		Mockito.doReturn(URL).when(mockedRW).getRepoUrl();

		IVCS vcs = VCSFactory.getVCS(VCSType.GIT, new Credentials(NAME, PWD, true), URL, mockedWS,
				COMPONENT_SUBFOLDER);

		assertTrue(vcs instanceof GitVCS);
		assertEquals(URL, vcs.getRepoUrl());
		Mockito.verify(mockedWS).getVCSRepositoryWorkspace(URL, COMPONENT_SUBFOLDER);
	}

	@Test
	public void testSiblingComponentsRequestOneRepositoryStateWithDistinctScopes() {
		String siblingSubfolder = "components/sibling";
		IVCSWorkspace workspace = Mockito.mock(IVCSWorkspace.class);
		IVCSRepositoryWorkspace first = repositoryWorkspace(COMPONENT_SUBFOLDER, new File("shared/repository-cache"));
		IVCSRepositoryWorkspace second = repositoryWorkspace(siblingSubfolder, new File("shared/repository-cache"));
		Mockito.doReturn(first).when(workspace)
				.getVCSRepositoryWorkspace(URL, COMPONENT_SUBFOLDER);
		Mockito.doReturn(second).when(workspace)
				.getVCSRepositoryWorkspace(URL, siblingSubfolder);

		IVCS firstVCS = VCSFactory.getVCS(VCSType.GIT, new Credentials(NAME, PWD, true), URL, workspace,
				COMPONENT_SUBFOLDER);
		IVCS secondVCS = VCSFactory.getVCS(VCSType.GIT, new Credentials(NAME, PWD, true), URL, workspace,
				siblingSubfolder);

		assertTrue(firstVCS instanceof GitVCS);
		assertTrue(secondVCS instanceof GitVCS);
		assertEquals(first.getPreparedRepositoryFolder(), second.getPreparedRepositoryFolder());
		assertNotEquals(first.getComponentSubfolder(), second.getComponentSubfolder());
		Mockito.verify(workspace).getVCSRepositoryWorkspace(
				URL, COMPONENT_SUBFOLDER);
		Mockito.verify(workspace).getVCSRepositoryWorkspace(
				URL, siblingSubfolder);
	}

	@Test
	public void testVCSFactoryDoesNotAddBackendToRepositoryIdentity() {
		IVCSWorkspace workspace = Mockito.mock(IVCSWorkspace.class);
		IVCSRepositoryWorkspace repository = repositoryWorkspace(COMPONENT_SUBFOLDER,
				new File("shared/repository-cache"));
		Mockito.doReturn(repository).when(workspace)
				.getVCSRepositoryWorkspace(URL, COMPONENT_SUBFOLDER);

		assertTrue(VCSFactory.getVCS(VCSType.GIT, new Credentials(NAME, PWD, true), URL, workspace,
				COMPONENT_SUBFOLDER) instanceof GitVCS);
		assertTrue(VCSFactory.getVCS(VCSType.SVN, new Credentials(NAME, PWD, true), URL, workspace,
				COMPONENT_SUBFOLDER) instanceof SVNVCS);
		Mockito.verify(workspace, Mockito.times(2)).getVCSRepositoryWorkspace(URL, COMPONENT_SUBFOLDER);
	}

	private IVCSRepositoryWorkspace repositoryWorkspace(String componentSubfolder, File preparedFolder) {
		IVCSRepositoryWorkspace repository = Mockito.mock(IVCSRepositoryWorkspace.class);
		Mockito.doReturn(URL).when(repository).getRepoUrl();
		Mockito.doReturn(componentSubfolder).when(repository).getComponentSubfolder();
		Mockito.doReturn(preparedFolder).when(repository).getPreparedRepositoryFolder();
		return repository;
	}
}
