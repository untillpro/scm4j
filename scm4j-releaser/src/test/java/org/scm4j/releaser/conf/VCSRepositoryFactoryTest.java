package org.scm4j.releaser.conf;

import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.scm4j.releaser.URLContentLoader;
import org.scm4j.releaser.regexconfig.RegexConfig;
import org.scm4j.releaser.exceptions.EComponentConfigNoUrl;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VCSRepositoryFactoryTest {

	private VCSRepositoryFactory repoFactory;

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		File urlsMapping = new File(Resources.getResource(this.getClass(), "urls-mapping.yml").toURI());
		File urlsSeq = new File(Resources.getResource(this.getClass(), "urls-sequence.yml").toURI());
		File urlsSeqOmap = new File(Resources.getResource(this.getClass(), "urls-sequence-omap.yml").toURI());
		File credsFile = new File(Resources.getResource(this.getClass(), "creds.yml").toURI());
		IConfigUrls configUrls = new IConfigUrls() {
			@Override
			public String getCCUrls() {
				return urlsMapping.toString() + URLContentLoader.URL_SEPARATOR +
						urlsSeq.toString() + URLContentLoader.URL_SEPARATOR + urlsSeqOmap.toString();
			}

			@Override
			public String getCredsUrl() {
				return credsFile.toString();
			}
		};
		repoFactory = new VCSRepositoryFactory();
		repoFactory.load(configUrls);
	}

	@Test
	public void getMy() {
		VCSRepository rep = getRepositoryForConfigName("myDiskFormatter");
		assertEquals("myDiskFormatter", rep.getName());
		assertEquals("http://localhost/git/myProjDiskFormatter", rep.getUrl());
		assertEquals("components/DiskFormatter", rep.getSubfolder());
		assertEquals(VCSType.GIT, rep.getType());
		assertEquals("B", rep.getReleaseBranchPrefix());
		assertEquals("gradlew", rep.getBuilder().getCommand());
		assertEquals("dev", rep.getDevelopBranch());
		assertEquals(null, rep.getCredentials().getName());
		assertEquals(null, rep.getCredentials().getPassword());
	}

	@Test
	public void testRepositoryLocationFromConfiguration() {
		VCSComponentLocation componentLocation = repoFactory.getVCSComponentLocation("myDiskFormatter");

		assertEquals("http://localhost/git/myProjDiskFormatter", componentLocation.getUrl());
		assertEquals("components/DiskFormatter", componentLocation.getSubfolder());
	}

	@Test
	public void getFromComponentUsesArtifactIdAsRepositoryName() {
		Component component = new Component(
				"eu.untill.sdk.drivers:vmax-fiscal-printer-driver:7.0@zip # drivers");
		String componentName = component.getName();
		String url = "http://localhost/git/untill-drivers";
		RegexConfig cc = mock(RegexConfig.class);
		RegexConfig creds = mock(RegexConfig.class);
		when(cc.getPlaceholderedStringByName(componentName, "url", null)).thenReturn(url);
		when(cc.getPlaceholderedStringByName(componentName, "subfolder", null))
				.thenReturn("components/vmax-fiscal-printer-driver");
		VCSRepositoryFactory factory = new VCSRepositoryFactory(cc, creds);

		VCSRepository rep = factory.getVCSRepository(component);

		assertEquals("vmax-fiscal-printer-driver", rep.getName());
		assertEquals(url, rep.getUrl());
		assertEquals("components/vmax-fiscal-printer-driver", rep.getSubfolder());
		verify(cc).getPlaceholderedStringByName(componentName, "url", null);
		verify(cc).getPlaceholderedStringByName(componentName, "subfolder", null);
		verify(cc).getPropByName(componentName, "releaseBranchPrefix",
				VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX);
	}

	@Test
	public void get1() {
		VCSRepository rep = getRepositoryForConfigName("artA1");
		assertEquals("components/artifacts", rep.getSubfolder());
		assertThat(new Object[] { rep.getName(), rep.getUrl(), rep.getType(), rep.getDevelopBranch(), rep.getReleaseBranchPrefix() },
				is(new Object[] { "artA1", "http://url.com/svn/prjA", VCSType.SVN, "branches/", "release/" }));
		assertThat(new Object[] { rep.getCredentials().getName(), rep.getCredentials().getPassword() },
				is(new Object[] { "user", "password" }));
	}

	@Test
	public void getFromNameUsesFullCoordinatesForFallbackUrl() {
		VCSRepository rep = repoFactory.getVCSRepository("abyrvalg");
		assertEquals("", rep.getSubfolder());
		assertThat(new Object[] { rep.getName(), rep.getUrl(), rep.getType(), rep.getDevelopBranch(), rep.getReleaseBranchPrefix() },
				is(new Object[] { "abyrvalg", "https://github.com/qwerty/abyrvalg:abyrvalg", VCSType.SVN, "branches/", "release/" }));
		assertThat(new Object[] { rep.getCredentials().getName(), rep.getCredentials().getPassword() },
				is(new Object[] { "guest", "guest" }));
	}

	@Test
	public void testGitVCSTypeDetermination() {
		VCSRepository repo = getRepositoryForConfigName("git1");
		assertEquals(VCSType.GIT, repo.getType());
		repo = getRepositoryForConfigName("git2");
		assertEquals(VCSRepositoryFactory.DEFAULT_VCS_TYPE, repo.getType());
	}

	@Test
	public void testSVNAlternativeDetermination() {
		VCSRepository repo = getRepositoryForConfigName("svn1");
		assertEquals(VCSType.SVN, repo.getType());
		assertEquals("http://localhost/myProj", repo.getUrl());
	}

	@Test
	public void testFactoryReusesWorkspaceAndURLScopedStateForSiblingComponents() throws IOException {
		String url = "https://example.test/shared.git";
		RegexConfig cc = mock(RegexConfig.class);
		RegexConfig creds = mock(RegexConfig.class);
		RecordingWorkspace workspace = new RecordingWorkspace(temporaryFolder.newFolder("siblings"));
		configureComponent(cc, "first", url, "components/first", "git");
		configureComponent(cc, "second", url, "components/second", "git");
		VCSRepositoryFactory factory = new VCSRepositoryFactory(cc, creds, workspace);

		factory.getVCSRepository(component("first"));
		factory.getVCSRepository(component("second"));

		assertEquals(2, workspace.repositories.size());
		IVCSRepositoryWorkspace first = workspace.repositories.get(0);
		IVCSRepositoryWorkspace second = workspace.repositories.get(1);
		assertSame(first.getWorkspace(), second.getWorkspace());
		assertEquals(workspace.getHomeFolder(), first.getWorkspace().getHomeFolder());
		assertEquals(first.getRepoFolder(), second.getRepoFolder());
		assertEquals(first.getPreparedRepositoryFolder(), second.getPreparedRepositoryFolder());
		assertEquals("components/first", first.getComponentSubfolder());
		assertEquals("components/second", second.getComponentSubfolder());
	}

	@Test
	public void testDifferentURLsUseDifferentRepositoryStateAndCloneFolders() throws IOException {
		String firstUrl = "https://example.test/first.git";
		String secondUrl = "https://example.test/second.git";
		RegexConfig cc = mock(RegexConfig.class);
		RegexConfig creds = mock(RegexConfig.class);
		RecordingWorkspace workspace = new RecordingWorkspace(temporaryFolder.newFolder("urls"));
		configureComponent(cc, "first", firstUrl, "components/first", "git");
		configureComponent(cc, "second", secondUrl, "components/second", "git");
		VCSRepositoryFactory factory = new VCSRepositoryFactory(cc, creds, workspace);

		factory.getVCSRepository(component("first"));
		factory.getVCSRepository(component("second"));

		assertEquals(2, workspace.repositories.size());
		IVCSRepositoryWorkspace first = workspace.repositories.get(0);
		IVCSRepositoryWorkspace second = workspace.repositories.get(1);
		assertNotEquals(first.getRepoFolder(), second.getRepoFolder());
		assertNotEquals(first.getPreparedRepositoryFolder(), second.getPreparedRepositoryFolder());
	}

	private void configureComponent(RegexConfig cc, String name, String url, String subfolder, String type) {
		when(cc.getPlaceholderedStringByName(name, "url", null)).thenReturn(url);
		when(cc.getPlaceholderedStringByName(name, "subfolder", null)).thenReturn(subfolder);
		when(cc.getPropByName(name, "type", null)).thenReturn(type);
	}

	private Component component(String name) {
		Component component = spy(new Component("test:" + name));
		when(component.getName()).thenReturn(name);
		return component;
	}

	private static final class RecordingWorkspace implements IVCSWorkspace {
		private final IVCSWorkspace delegate;
		private final List<IVCSRepositoryWorkspace> repositories = new ArrayList<>();

		private RecordingWorkspace(File homeFolder) {
			delegate = new VCSWorkspace(homeFolder.getPath());
		}

		@Override
		public IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl, String componentSubfolder) {
			IVCSRepositoryWorkspace repository = delegate.getVCSRepositoryWorkspace(repoUrl, componentSubfolder);
			repositories.add(repository);
			return repository;
		}

		@Override
		public File getHomeFolder() {
			return delegate.getHomeFolder();
		}
	}

	private VCSRepository getRepositoryForConfigName(String componentName) {
		// The YAML fixtures use bare names as configuration keys.
		Component component = spy(new Component(componentName + ":" + componentName));
		when(component.getName()).thenReturn(componentName);
		return repoFactory.getVCSRepository(component);
	}

	@Test
	public void testNullConfigUrls() throws IOException {
		IConfigUrls configUrls = new IConfigUrls() {
			@Override
			public String getCCUrls() {
				return null;
			}

			@Override
			public String getCredsUrl() {
				return null;
			}
		};
		// expect no exceptions
		repoFactory = new VCSRepositoryFactory();
		repoFactory.load(configUrls);
	}

	@Test
	public void testNoRepoUrlException() throws IOException {
		IConfigUrls configUrls = new IConfigUrls() {
			@Override
			public String getCCUrls() {
				return "";
			}

			@Override
			public String getCredsUrl() {
				return "";
			}
		};
		VCSRepositoryFactory repoFactory = new VCSRepositoryFactory();
		repoFactory.load(configUrls);
		try {
			repoFactory.getUrl("wrong comp");
			fail();
		} catch (EComponentConfigNoUrl e) {
		}
	}
}

