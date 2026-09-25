package org.scm4j.releaser.conf;

import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.vcs.git.GitVCS;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;

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
		Mockito.doReturn(mockedRW).when(mockedWS).getVCSRepositoryWorkspace(URL);
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
}
