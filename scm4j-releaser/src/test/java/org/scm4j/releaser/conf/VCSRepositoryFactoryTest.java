package org.scm4j.releaser.conf;

import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.URLContentLoader;
import org.scm4j.commons.regexconfig.RegexConfig;
import org.scm4j.releaser.exceptions.EComponentConfigNoUrl;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VCSRepositoryFactoryTest {

	private VCSRepositoryFactory repoFactory;

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
		assertNull(rep.getSubfolder());
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

