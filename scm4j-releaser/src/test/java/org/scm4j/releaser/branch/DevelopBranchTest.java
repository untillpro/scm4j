package org.scm4j.releaser.branch;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.exceptions.ENoVersionFile;
import org.scm4j.releaser.testutils.TestEnvironment;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.WalkDirection;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DevelopBranchTest {

	private static final String DEVELOP_BRANCH = "component-develop";
	private static final String COMPONENT_SUBFOLDER = "components/driver";
	private static final String SIBLING_SUBFOLDER = "components/sibling";

	@Test
	public void testSubfolderBehavior() throws Exception {
		for (VCSType vcsType : VCSType.values()) {
			try (TestEnvironment env = new TestEnvironment(vcsType)) {
				env.generateTestEnvironment();
				IVCS vcs = env.getUnTillVCS();
				vcs.createBranch(null, DEVELOP_BRANCH, "component develop branch created");

				Version componentVersion = new Version("2.4.0-SNAPSHOT");
				vcs.setFileContent(DEVELOP_BRANCH, COMPONENT_SUBFOLDER + "/" + Constants.VER_FILE_NAME,
						componentVersion.toString(), Constants.SCM_VER + " component version initialized");
				vcs.setFileContent(DEVELOP_BRANCH, SIBLING_SUBFOLDER + "/feature.txt", "sibling feature",
						"sibling feature");

				DevelopBranch developBranch = new DevelopBranch(component(), repository(vcsType, vcs, COMPONENT_SUBFOLDER));
				assertEquals(vcsType.toString(), componentVersion, developBranch.getVersion());
				assertFalse(vcsType.toString(), developBranch.isModified());

				vcs.setFileContent(DEVELOP_BRANCH, COMPONENT_SUBFOLDER + "/feature.txt", "component feature",
						"component feature");
				vcs.setFileContent(DEVELOP_BRANCH, SIBLING_SUBFOLDER + "/feature.txt", "sibling version",
						Constants.SCM_VER + " sibling version");
				assertTrue(vcsType.toString(), developBranch.isModified());

				vcs.setFileContent(DEVELOP_BRANCH, COMPONENT_SUBFOLDER + "/feature.txt", "ignored component change",
						Constants.SCM_IGNORE + " generated component change");
				assertFalse(vcsType.toString(), developBranch.isModified());

				String missingVersionSubfolder = "components/missing-version";
				vcs.setFileContent(DEVELOP_BRANCH, missingVersionSubfolder + "/README.md", "component exists",
						"component created without version");
				assertNoVersionFile(vcsType, vcs, missingVersionSubfolder);
			}
		}
	}

	@Test
	public void testIsNotModifiedIfComponentHasNoCommits() {
		IVCS vcs = mock(IVCS.class);
		VCSRepository repo = repository(VCSType.GIT, vcs, COMPONENT_SUBFOLDER);
		doReturn(new ArrayList<VCSCommit>()).when(vcs).getCommitsRange(
				DEVELOP_BRANCH, null, WalkDirection.DESC, 1, COMPONENT_SUBFOLDER);

		assertFalse(new DevelopBranch(component(), repo).isModified());
		verify(vcs).getCommitsRange(DEVELOP_BRANCH, null, WalkDirection.DESC, 1, COMPONENT_SUBFOLDER);
	}

	@Test
	public void testLegacyBehaviorForNullAndEmptySubfolders() {
		for (String subfolder : Arrays.asList(null, "")) {
			IVCS vcs = mock(IVCS.class);
			VCSRepository repo = repository(VCSType.GIT, vcs, subfolder);
			doReturn("1.2.3-SNAPSHOT").when(vcs).getFileContent(DEVELOP_BRANCH, Constants.VER_FILE_NAME, null);
			doReturn(Arrays.asList(new VCSCommit("revision", "feature", "author")))
					.when(vcs).log(DEVELOP_BRANCH, 1);

			DevelopBranch developBranch = new DevelopBranch(component(), repo);
			assertEquals(new Version("1.2.3-SNAPSHOT"), developBranch.getVersion());
			assertTrue(developBranch.isModified());
			verify(vcs).getFileContent(DEVELOP_BRANCH, Constants.VER_FILE_NAME, null);
			verify(vcs).log(DEVELOP_BRANCH, 1);
		}
	}

	private void assertNoVersionFile(VCSType vcsType, IVCS vcs, String subfolder) {
		try {
			new DevelopBranch(component(), repository(vcsType, vcs, subfolder)).getVersion();
			fail("Expected component version lookup to fail for " + vcsType);
		} catch (ENoVersionFile e) {
			// expected
		}
	}

	private Component component() {
		return new Component(TestEnvironment.PRODUCT_UNTILL);
	}

	private VCSRepository repository(VCSType vcsType, IVCS vcs, String subfolder) {
		return new VCSRepository("driver", vcs.getRepoUrl(), subfolder, null, vcsType, DEVELOP_BRANCH,
				"release/", vcs, null);
	}
}
