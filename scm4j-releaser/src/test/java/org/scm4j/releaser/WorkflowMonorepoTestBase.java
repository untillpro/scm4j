package org.scm4j.releaser;

import org.junit.After;
import org.junit.Before;
import org.scm4j.commons.Version;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DefaultConfigUrls;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.testutils.MonorepoTestEnvironment;
import org.scm4j.releaser.testutils.TestBuilder;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Shared harness for workflow tests whose postgres and SQLite components are logical repositories inside
 * one physical repository. It creates an isolated fixture for each VCS adapter, resets process-wide release
 * state between scenarios, and provides assertions that observe component-specific revisions and tags.
 */
public abstract class WorkflowMonorepoTestBase extends WorkflowTestBase {

	private MonorepoTestEnvironment monorepoEnvironment;
	private final List<VCSRepository> monorepoRepositories = new ArrayList<>();

	@Override
	@Before
	public void setUp() throws Exception {
		// Monorepo tests supply their own four-component environment, so only reset shared release state here.
		cleanupReleases();
	}

	@Override
	@After
	public void tearDown() throws Exception {
		try {
			// Close a partially initialized environment as well when a scenario fails midway.
			closeMonorepoEnvironment();
		} finally {
			TestBuilder.setBuilders(null);
			clearReleaseState();
		}
	}

	protected void runForEachVcs(Scenario scenario) throws Exception {
		// Filtering history, creating branches, checking out revisions, and tagging differ between Git and SVN.
		for (VCSType vcsType : VCSType.values()) {
			runScenario(vcsType, scenario);
		}
	}

	private void runScenario(VCSType vcsType, Scenario scenario) throws Exception {
		cleanupReleases();
		monorepoEnvironment = new MonorepoTestEnvironment(vcsType);
		try {
			// Build disposable repositories and redirect the real CLI configuration to this scenario's files.
			monorepoEnvironment.generate();
			configureEnvironment(monorepoEnvironment);
			repoFactory = monorepoEnvironment.getRepositoryFactory();
			ScenarioContext context = new ScenarioContext(monorepoEnvironment);
			monorepoRepositories.add(context.postgresRepo);
			monorepoRepositories.add(context.sqliteRepo);

			// Every scenario must use one physical repository with two independently identified components.
			assertEquals(context.postgresRepo.getUrl(), context.sqliteRepo.getUrl());
			assertNotEquals(context.postgresRepo.getComponentLocation(), context.sqliteRepo.getComponentLocation());

			scenario.run(context);
		} finally {
			try {
				closeMonorepoEnvironment();
			} finally {
				clearReleaseState();
			}
		}
	}

	private void closeMonorepoEnvironment() throws Exception {
		if (monorepoEnvironment == null) {
			return;
		}
		MonorepoTestEnvironment environment = monorepoEnvironment;
		try {
			// Check sentinels before deleting the repositories so a component operation cannot silently touch root metadata.
			assertMonorepoRootFilesUnchanged();
		} finally {
			try {
				environment.close();
			} finally {
				monorepoEnvironment = null;
				monorepoRepositories.clear();
			}
		}
	}

	private void assertMonorepoRootFilesUnchanged() {
		IVCS vcs = monorepoEnvironment.getMonorepoVCS();
		if (vcs == null) {
			return;
		}
		// Component-local operations must leave root metadata untouched on develop and every created release branch.
		String developBranch = monorepoRepositories.isEmpty()
				? VCSRepository.DEFAULT_DEVELOP_BRANCH : monorepoRepositories.get(0).getDevelopBranch();
		assertMonorepoRootFilesUnchanged(vcs, developBranch);
		for (VCSRepository repository : monorepoRepositories) {
			String releaseBranchPrefix = repository.getName() + "/" + repository.getReleaseBranchPrefix();
			boolean releaseNamespaceExists = monorepoEnvironment.getVcsType() != VCSType.SVN
					|| vcs.getBranches(null).contains(repository.getName());
			if (releaseNamespaceExists) {
				for (String branchName : vcs.getBranches(releaseBranchPrefix)) {
					assertMonorepoRootFilesUnchanged(vcs, branchName);
				}
			}
		}
	}

	private void assertMonorepoRootFilesUnchanged(IVCS vcs, String branchName) {
		assertEquals(MONOREPO_ROOT_UNREACHABLE_VERSION,
				vcs.getFileContent(branchName, Constants.VER_FILE_NAME, null));
		assertEquals(MONOREPO_ROOT_UNREACHABLE_MDEPS,
				vcs.getFileContent(branchName, Constants.MDEPS_FILE_NAME, null));
	}

	@SuppressWarnings("deprecation")
	private void configureEnvironment(MonorepoTestEnvironment environment) {
		// Each CLI invocation reloads configuration from environment variables, so point it at the fixture files.
		environmentVariables.set(DefaultConfigUrls.REPOS_LOCATION_ENV_VAR, null);
		environmentVariables.set(DefaultConfigUrls.CC_URLS_ENV_VAR, environment.getCcFile().toString());
		environmentVariables.set(DefaultConfigUrls.CREDENTIALS_URL_ENV_VAR,
				environment.getCredentialsFile().toString());
	}

	private void cleanupReleases() throws Exception {
		// Builders, their captured environment, delayed tags, and checkout folders are process-wide test state.
		TestBuilder.setBuilders(new HashMap<>());
		clearReleaseState();
	}

	private void clearReleaseState() throws Exception {
		TestBuilder.getEnvVars().clear();
		new DelayedTagsFile().delete();
		Utils.waitForDeleteDir(Constants.RELEASES_DIR);
	}

	protected VCSCommit latestComponentCommit(VCSRepository repository, String branchName) {
		// This is the revision scm4j should select when it treats a component subfolder as a logical repository.
		List<VCSCommit> commits = repository.getVCS().getCommitsRange(branchName, null,
				WalkDirection.DESC, 1, repository.getSubfolder());
		assertFalse(commits.isEmpty());
		return commits.get(0);
	}

	protected void assertBuildRevision(Component component, VCSRepository repository, Version releaseVersion,
			VCSCommit expectedCommit) {
		// TestBuilder captures the real build boundary's VCS variables, including the revision actually checked out.
		Map<String, String> actual = TestBuilder.getEnvVars().get(component.getName());
		assertNotNull(actual);
		assertEquals(Utils.getBuildTimeEnvVars(repository.getType(), expectedCommit.getRevision(),
				Utils.getReleaseBranchName(repository, releaseVersion), repository.getUrl()), actual);
	}

	protected void assertTagRevision(VCSRepository repository, Version version, VCSCommit expectedCommit) {
		VCSTag tag = findTag(repository, version);
		assertEquals(expectedCommit.getRevision(), tag.getRelatedCommit().getRevision());
	}

	protected void assertTagExists(VCSRepository repository, Version version) {
		assertNotNull(findTag(repository, version));
	}

	protected VCSTag findTag(VCSRepository repository, Version version) {
		String tagName = Utils.getTagDesc(repository, version.toString()).getName();
		for (VCSTag tag : repository.getVCS().getTags()) {
			if (tagName.equals(tag.getTagName())) {
				return tag;
			}
		}
		throw new AssertionError("missing tag " + tagName + " for " + repository.getComponentLocation());
	}

	protected List<VCSTag> componentTags(VCSRepository repository) {
		// The VCS returns every tag in the shared repository; filter to this component's namespace.
		return repository.getVCS().getTags().stream()
				.filter(tag -> Utils.isTagForRepository(repository, tag.getTagName()))
				.collect(Collectors.toList());
	}

	@FunctionalInterface
	protected interface Scenario {
		void run(ScenarioContext context) throws Exception;
	}

	// All workflow scenarios use the same components so they exercise identical repository identities and topology.
	protected class ScenarioContext {

		final MonorepoTestEnvironment environment;
		final Component unTill;
		final Component ubl;
		final Component postgres;
		final Component sqlite;
		final VCSRepository unTillRepo;
		final VCSRepository ublRepo;
		final VCSRepository postgresRepo;
		final VCSRepository sqliteRepo;

		private ScenarioContext(MonorepoTestEnvironment environment) {
			this.environment = environment;
			unTill = new Component(MonorepoTestEnvironment.PRODUCT_UNTILL);
			ubl = new Component(MonorepoTestEnvironment.PRODUCT_UBL);
			postgres = new Component(MonorepoTestEnvironment.PRODUCT_POSTGRES);
			sqlite = new Component(MonorepoTestEnvironment.PRODUCT_SQLITE);
			unTillRepo = repoFactory.getVCSRepository(unTill);
			ublRepo = repoFactory.getVCSRepository(ubl);
			postgresRepo = repoFactory.getVCSRepository(postgres);
			sqliteRepo = repoFactory.getVCSRepository(sqlite);
		}
	}
}
