package org.scm4j.releaser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises one application that uses postgres from a postgres/sqlite monorepo and an already released UBL
 * from a standalone repository. The same scenario proves that repository-wide changes do not leak across
 * component boundaries and that a real dependency change still propagates to the application release.
 *
 * <pre>
 * product to release
 * `-- unTill 1.123.3-SNAPSHOT                    initial release: 1.123.0
 *     |-- UBL 1.19.5-SNAPSHOT                    existing release: 1.18.0, unchanged
 *     `-- postgres 2.59.1-SNAPSHOT               initial release: 2.59.0
 *
 * repositories
 * |-- unTill repository
 * |-- UBL repository
 * `-- drivers monorepo
 *     |-- components/postgres 2.59.1-SNAPSHOT    used by unTill
 *     `-- components/sqlite   3.4.1-SNAPSHOT     not used by unTill, must remain unreleased
 *
 * after the next postgres change
 *     |-- postgres 2.60.1-SNAPSHOT               next release: 2.60.0
 *     `-- unTill  1.124.3-SNAPSHOT               next release: 1.124.0
 * </pre>
 */
public class WorkflowMonorepoTest extends WorkflowTestBase {

	private MonorepoTestEnvironment monorepoEnvironment;
	private VCSRepository monorepoRepository;

	@Override
	@Before
	public void setUp() throws Exception {
		// This test supplies its own four-component environment, so only reset the shared release state here.
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
			new DelayedTagsFile().delete();
			Utils.waitForDeleteDir(Constants.RELEASES_DIR);
		}
	}

	@Test
	public void testMonorepoComponents() throws Exception {
		// Run the complete workflow through both adapters because Git and SVN implement branches,
		// filtered history, checkout, and tagging differently.
		for (VCSType vcsType : VCSType.values()) {
			runScenario(vcsType);
		}
	}

	private void runScenario(VCSType vcsType) throws Exception {
		cleanupReleases();
		monorepoEnvironment = new MonorepoTestEnvironment(vcsType);
		try {
			// Build the dedicated repository topology and point the CLI at its generated component configuration.
			// TODO: move this lifecycle to the test base when all workflow tests run for Git and SVN automatically.
			monorepoEnvironment.generate();
			configureEnvironment(monorepoEnvironment);
			repoFactory = monorepoEnvironment.getRepositoryFactory();

			Component unTill = new Component(MonorepoTestEnvironment.PRODUCT_UNTILL);
			Component ubl = new Component(MonorepoTestEnvironment.PRODUCT_UBL);
			Component postgres = new Component(MonorepoTestEnvironment.PRODUCT_POSTGRES);
			Component sqlite = new Component(MonorepoTestEnvironment.PRODUCT_SQLITE);
			VCSRepository unTillRepo = repoFactory.getVCSRepository(unTill);
			VCSRepository ublRepo = repoFactory.getVCSRepository(ubl);
			VCSRepository postgresRepo = repoFactory.getVCSRepository(postgres);
			monorepoRepository = postgresRepo;
			VCSRepository sqliteRepo = repoFactory.getVCSRepository(sqlite);

			// Prove the fixture really models one physical repository with two distinct component identities.
			assertEquals(postgresRepo.getUrl(), sqliteRepo.getUrl());
			assertNotEquals(postgresRepo.getComponentLocation(), sqliteRepo.getComponentLocation());
			// Capture the negative control before any application workflow runs: UBL is already released and DONE.
			assertUblBaselineUnchanged(ubl, ublRepo);

			// The shared develop branch gets one postgres change and the first of two sqlite changes.
			monorepoEnvironment.generateComponentCommit(postgresRepo.getDevelopBranch(),
					MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "postgres feature added");
			monorepoEnvironment.generateComponentCommit(sqliteRepo.getDevelopBranch(),
					MonorepoTestEnvironment.SQLITE_SUBFOLDER, "sqlite feature added");

			IAction forkAction = execAndGetActionFork(unTill);
			// postgres and unTill need their first release branches; the existing UBL release must be reused.
			assertActionDoesFork(forkAction, postgres, unTill);
			assertActionDoesNothing(forkAction, ubl);

			Version firstPostgresRelease = monorepoEnvironment.getPostgresVersion().toReleaseZeroPatch();
			Version firstUnTillRelease = monorepoEnvironment.getUnTillVersion().toReleaseZeroPatch();
			String postgresReleaseBranch = Utils.getReleaseBranchName(postgresRepo, firstPostgresRelease);
			VCSCommit postgresBuildCommit = latestComponentCommit(postgresRepo, postgresReleaseBranch);

			// Move the release branch head with the second sqlite change. The postgres build must stay on
			// the earlier postgres revision selected by component-filtered history.
			VCSCommit sqliteRepositoryHead = monorepoEnvironment.generateComponentCommit(postgresReleaseBranch,
					MonorepoTestEnvironment.SQLITE_SUBFOLDER, "second sqlite feature added");
			assertEquals(sqliteRepositoryHead.getRevision(),
					postgresRepo.getVCS().getHeadCommit(postgresReleaseBranch).getRevision());

			IAction buildAction = execAndGetActionBuild(unTill);
			// Building the root application must first build postgres, then lock that release into unTill.
			assertActionDoesBuild(buildAction, postgres);
			assertActionDoesBuild(buildAction, unTill, BuildStatus.BUILD_MDEPS);
			assertActionDoesNothing(buildAction, ubl);
			// The builder environment and tag must both identify the postgres revision, not the newer sqlite head.
			assertBuildRevision(postgres, postgresRepo, firstPostgresRelease, postgresBuildCommit);
			assertTagRevision(postgresRepo, firstPostgresRelease, postgresBuildCommit);
			assertNotEquals(sqliteRepositoryHead.getRevision(), postgresBuildCommit.getRevision());
			// Verify the externally durable result: locked dependencies and exactly one release of each built component.
			assertUnTillMDeps(unTillRepo, firstUnTillRelease, firstPostgresRelease);
			assertReleaseCounts(postgresRepo, unTillRepo, 1);
			assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo);
			// Once released, postgres has no work of its own left to execute.
			assertActionDoesNothing(execAndGetActionBuild(postgres), postgres);

			// A sibling-only develop change must not schedule another postgres or unTill release.
			monorepoEnvironment.generateComponentCommit(sqliteRepo.getDevelopBranch(),
					MonorepoTestEnvironment.SQLITE_SUBFOLDER, "third sqlite feature added");
			// Check postgres directly as well as through the unTill dependency tree: a sqlite change must
			// not produce any action for the unchanged component that shares its repository.
			assertActionDoesNothing(execAndGetActionBuild(postgres), postgres);
			IAction siblingOnlyAction = execAndGetActionBuild(unTill);
			// The application tree must also remain a complete no-op after the sibling-only change.
			assertActionDoesNothing(siblingOnlyAction, postgres, ubl, unTill);
			assertReleaseCounts(postgresRepo, unTillRepo, 1);
			assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo);

			// A new postgres change must release postgres and propagate its new version to unTill.
			monorepoEnvironment.generateComponentCommit(postgresRepo.getDevelopBranch(),
					MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "second postgres feature added");
			IAction secondForkAction = execAndGetActionFork(unTill);
			assertActionDoesFork(secondForkAction, postgres, unTill);
			assertActionDoesNothing(secondForkAction, ubl);

			Version secondPostgresRelease = firstPostgresRelease.toNextMinor();
			Version secondUnTillRelease = firstUnTillRelease.toNextMinor();
			String secondPostgresBranch = Utils.getReleaseBranchName(postgresRepo, secondPostgresRelease);
			VCSCommit secondPostgresBuildCommit = latestComponentCommit(postgresRepo, secondPostgresBranch);
			String secondUnTillBranch = Utils.getReleaseBranchName(unTillRepo, secondUnTillRelease);
			VCSCommit secondUnTillBuildCommit = unTillRepo.getVCS().getHeadCommit(secondUnTillBranch);
			assertNotNull(secondUnTillBuildCommit);
			IAction secondBuildAction = execAndGetActionBuild(unTill);
			// The postgres change must now build both the dependency and its consuming application, but not UBL.
			assertActionDoesBuild(secondBuildAction, postgres);
			assertActionDoesBuild(secondBuildAction, unTill, BuildStatus.BUILD_MDEPS);
			assertActionDoesNothing(secondBuildAction, ubl);
			// Confirm both builds and tags use the captured revisions, and unTill locks the new postgres version only.
			assertBuildRevision(postgres, postgresRepo, secondPostgresRelease, secondPostgresBuildCommit);
			assertTagRevision(postgresRepo, secondPostgresRelease, secondPostgresBuildCommit);
			assertBuildRevision(unTill, unTillRepo, secondUnTillRelease, secondUnTillBuildCommit);
			assertTagRevision(unTillRepo, secondUnTillRelease, secondUnTillBuildCommit);
			assertUnTillMDeps(unTillRepo, secondUnTillRelease, secondPostgresRelease);
			assertReleaseCounts(postgresRepo, unTillRepo, 2);
			assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo);
		} finally {
			try {
				closeMonorepoEnvironment();
			} finally {
				new DelayedTagsFile().delete();
				Utils.waitForDeleteDir(Constants.RELEASES_DIR);
			}
		}
	}

	private void closeMonorepoEnvironment() throws Exception {
		if (monorepoEnvironment == null) {
			return;
		}
		try {
			assertMonorepoRootFilesUnchanged();
		} finally {
			monorepoEnvironment.close();
			monorepoEnvironment = null;
			monorepoRepository = null;
		}
	}

	private void assertMonorepoRootFilesUnchanged() {
		IVCS vcs = monorepoEnvironment.getMonorepoVCS();
		if (vcs == null) {
			return;
		}
		String developBranch = monorepoRepository == null
				? VCSRepository.DEFAULT_DEVELOP_BRANCH : monorepoRepository.getDevelopBranch();
		assertMonorepoRootFilesUnchanged(vcs, developBranch);
		if (monorepoRepository == null) {
			return;
		}
		String releaseBranchPrefix = monorepoRepository.getName() + "/"
				+ monorepoRepository.getReleaseBranchPrefix();
		boolean releaseNamespaceExists = monorepoEnvironment.getVcsType() != VCSType.SVN
				|| vcs.getBranches(null).contains(monorepoRepository.getName());
		if (releaseNamespaceExists) {
			for (String branchName : vcs.getBranches(releaseBranchPrefix)) {
				assertMonorepoRootFilesUnchanged(vcs, branchName);
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
		// CLI instances load configuration from environment variables, so redirect them to this scenario's files.
		environmentVariables.set(DefaultConfigUrls.REPOS_LOCATION_ENV_VAR, null);
		environmentVariables.set(DefaultConfigUrls.CC_URLS_ENV_VAR, environment.getCcFile().toString());
		environmentVariables.set(DefaultConfigUrls.CREDENTIALS_URL_ENV_VAR,
				environment.getCredentialsFile().toString());
	}

	private void cleanupReleases() throws Exception {
		// Builder observations, delayed tags, and checkout folders are process-wide test state and must not
		// leak from Git to SVN or from this test to another workflow test.
		TestBuilder.setBuilders(new HashMap<>());
		TestBuilder.getEnvVars().clear();
		new DelayedTagsFile().delete();
		Utils.waitForDeleteDir(Constants.RELEASES_DIR);
	}

	private VCSCommit latestComponentCommit(VCSRepository repository, String branchName) {
		// This is the revision scm4j is expected to select when it treats a subfolder as a logical repository.
		List<VCSCommit> commits = repository.getVCS().getCommitsRange(branchName, null,
				WalkDirection.DESC, 1, repository.getSubfolder());
		assertFalse(commits.isEmpty());
		return commits.get(0);
	}

	private void assertBuildRevision(Component component, VCSRepository repository, Version releaseVersion,
			VCSCommit expectedCommit) {
		// TestBuilder records the VCS variables passed to the real build boundary, revealing the checked-out revision.
		Map<String, String> actual = TestBuilder.getEnvVars().get(component.getName());
		assertNotNull(actual);
		assertEquals(Utils.getBuildTimeEnvVars(repository.getType(), expectedCommit.getRevision(),
				Utils.getReleaseBranchName(repository, releaseVersion), repository.getUrl()), actual);
	}

	private void assertTagRevision(VCSRepository repository, Version version, VCSCommit expectedCommit) {
		VCSTag tag = findTag(repository, version);
		assertEquals(expectedCommit.getRevision(), tag.getRelatedCommit().getRevision());
	}

	private VCSTag findTag(VCSRepository repository, Version version) {
		String tagName = Utils.getTagDesc(repository, version.toString()).getName();
		for (VCSTag tag : repository.getVCS().getTags()) {
			if (tagName.equals(tag.getTagName())) {
				return tag;
			}
		}
		throw new AssertionError("missing tag " + tagName + " for " + repository.getComponentLocation());
	}

	private List<VCSTag> componentTags(VCSRepository repository) {
		// The shared VCS returns every monorepo tag, so retain only this component's tag namespace.
		return repository.getVCS().getTags().stream()
				.filter(tag -> Utils.isTagForRepository(repository, tag.getTagName()))
				.collect(Collectors.toList());
	}

	private void assertUnTillMDeps(VCSRepository unTillRepo, Version unTillRelease,
			Version postgresRelease) {
		Map<String, Version> expected = new LinkedHashMap<>();
		expected.put(MonorepoTestEnvironment.PRODUCT_UBL, monorepoEnvironment.getUblReleaseVersion());
		expected.put(MonorepoTestEnvironment.PRODUCT_POSTGRES, postgresRelease);
		assertLockedMDeps(unTillRepo, unTillRelease, expected);
	}

	private void assertLockedMDeps(VCSRepository repository, Version releaseVersion,
			Map<String, Version> expected) {
		// Read the release branch rather than cached status so the assertion covers persisted mdeps content.
		List<Component> actual = ReleaseBranchFactory.getMDepsRelease(
				Utils.getReleaseBranchName(repository, releaseVersion), repository);
		assertEquals(expected.size(), actual.size());
		for (Component component : actual) {
			assertTrue("unexpected dependency " + component, expected.containsKey(component.getName()));
			assertTrue(component.getVersion().isLocked());
			assertEquals(expected.get(component.getName()), component.getVersion());
		}
	}

	private void assertReleaseCounts(VCSRepository postgresRepo, VCSRepository unTillRepo, int expected) {
		assertEquals(expected, componentTags(postgresRepo).size());
		assertEquals(expected, componentTags(unTillRepo).size());
	}

	private void assertUnchangedComponents(Component sqlite, VCSRepository sqliteRepo,
			Component ubl, VCSRepository ublRepo) {
		assertNoSqliteRelease(sqlite, sqliteRepo);
		assertUblBaselineUnchanged(ubl, ublRepo);
	}

	private void assertNoSqliteRelease(Component sqlite, VCSRepository sqliteRepo) {
		// sqlite has repository activity but is not an unTill dependency, so the workflow must create no
		// sqlite release branch, tag, or builder invocation.
		String releaseBranch = Utils.getReleaseBranchName(sqliteRepo,
				monorepoEnvironment.getSqliteVersion().toReleaseZeroPatch());
		assertNull(sqliteRepo.getVCS().getHeadCommit(releaseBranch));
		assertTrue(componentTags(sqliteRepo).isEmpty());
		assertFalse(TestBuilder.getBuilders().containsKey(sqlite.getName()));
	}

	private void assertUblBaselineUnchanged(Component ubl, VCSRepository ublRepo) {
		// UBL is the standalone control dependency: its development version, release version, original tag,
		// and lack of a new build must remain stable across both unTill release cycles.
		Version releaseVersion = monorepoEnvironment.getUblReleaseVersion();
		String releaseBranch = Utils.getReleaseBranchName(ublRepo, releaseVersion);
		assertEquals(monorepoEnvironment.getUblDevelopmentVersion().toString(),
				ublRepo.getVCS().getFileContent(ublRepo.getDevelopBranch(), Constants.VER_FILE_NAME, null));
		assertEquals(releaseVersion.toNextPatch().toString(),
				ublRepo.getVCS().getFileContent(releaseBranch, Constants.VER_FILE_NAME, null));
		List<VCSTag> tags = componentTags(ublRepo);
		assertEquals(1, tags.size());
		assertEquals(Utils.getTagDesc(ublRepo, releaseVersion.toString()).getName(), tags.get(0).getTagName());
		assertEquals(monorepoEnvironment.getUblReleaseCommit().getRevision(),
				tags.get(0).getRelatedCommit().getRevision());
		assertFalse(TestBuilder.getBuilders().containsKey(ubl.getName()));
	}
}
