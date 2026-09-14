package org.scm4j.releaser.branch;

import org.junit.Test;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.testutils.TestEnvironment;
import org.scm4j.vcs.api.IVCS;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseBranchFactoryTest {

	private static final String CUSTOM_DEVELOP_BRANCH = "component-develop";
	private static final String COMPONENT_SUBFOLDER = "components/driver";

	@Test
	public void testDevelopmentMDepsUseConfiguredBranch() throws Exception {
		for (VCSType vcsType : VCSType.values()) {
			try (TestEnvironment env = new TestEnvironment(vcsType)) {
				env.generateTestEnvironment();
				IVCS vcs = env.getUnTillVCS();
				vcs.createBranch(null, CUSTOM_DEVELOP_BRANCH, "custom develop branch created");

				assertBranchSelection(vcsType, vcs, null, "root");
				assertBranchSelection(vcsType, vcs, COMPONENT_SUBFOLDER, "component");
			}
		}
	}

	private void assertBranchSelection(VCSType vcsType, IVCS vcs, String subfolder, String locationName) {
		VCSRepository customDevelopRepository = repository(vcsType, vcs, subfolder, CUSTOM_DEVELOP_BRANCH);
		String mDepsPath = customDevelopRepository.getComponentPath(Constants.MDEPS_FILE_NAME);
		String defaultDependencyName = "example:default-" + locationName;
		String customDependencyName = "example:custom-" + locationName;

		vcs.setFileContent(null, mDepsPath, defaultDependencyName + ":1.2.3", "default branch mdeps");
		vcs.setFileContent(CUSTOM_DEVELOP_BRANCH, mDepsPath, customDependencyName + ":4.5.6",
				"custom develop branch mdeps");

		assertDevelopmentDependency(customDevelopRepository, customDependencyName);
		assertDevelopmentDependency(repository(vcsType, vcs, subfolder, null), defaultDependencyName);
	}

	private void assertDevelopmentDependency(VCSRepository repository, String expectedDependencyName) {
		List<Component> dependencies = ReleaseBranchFactory.getMDepsDevelop(repository);
		assertEquals(repository.getType().toString(), 1, dependencies.size());
		assertEquals(repository.getType().toString(), expectedDependencyName, dependencies.get(0).getName());
		assertTrue(repository.getType().toString(), dependencies.get(0).getVersion().isEmpty());
	}

	private VCSRepository repository(VCSType vcsType, IVCS vcs, String subfolder, String developBranch) {
		return new VCSRepository("driver", vcs.getRepoUrl(), subfolder, null, vcsType, developBranch,
				"release/", vcs, null);
	}
}
