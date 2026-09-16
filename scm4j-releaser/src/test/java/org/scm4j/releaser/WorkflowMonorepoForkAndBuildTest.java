package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.testutils.MonorepoTestEnvironment;
import org.scm4j.releaser.testutils.TestBuilder;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;

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
public class WorkflowMonorepoForkAndBuildTest extends WorkflowTestBase {

	public WorkflowMonorepoForkAndBuildTest() {
		super(WorkflowEnvironment.MONOREPO);
	}

	@Test
	public void testMonorepoComponents() throws Exception {
		// Run the complete workflow through both adapters because their history and branch models differ.
		runForEachVcs(this::runScenario);
	}

	private void runScenario(ScenarioContext context) {
		MonorepoTestEnvironment monorepoEnvironment = context.environment;
		Component unTill = context.unTill;
		Component ubl = context.ubl;
		Component postgres = context.postgres;
		Component sqlite = context.sqlite;
		VCSRepository unTillRepo = context.unTillRepo;
		VCSRepository ublRepo = context.ublRepo;
		VCSRepository postgresRepo = context.postgresRepo;
		VCSRepository sqliteRepo = context.sqliteRepo;

		// Capture the negative control before any application workflow runs: UBL is already released and DONE.
		assertUblBaselineUnchanged(ubl, ublRepo, monorepoEnvironment);

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
		assertUnTillMDeps(unTillRepo, firstUnTillRelease, firstPostgresRelease, monorepoEnvironment);
		assertTagExists(unTillRepo, firstUnTillRelease);
		assertReleaseCounts(postgresRepo, unTillRepo, 1);
		assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo, monorepoEnvironment);
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
		assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo, monorepoEnvironment);

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
		assertUnTillMDeps(unTillRepo, secondUnTillRelease, secondPostgresRelease, monorepoEnvironment);
		assertReleaseCounts(postgresRepo, unTillRepo, 2);
		assertUnchangedComponents(sqlite, sqliteRepo, ubl, ublRepo, monorepoEnvironment);
	}

	private void assertUnTillMDeps(VCSRepository unTillRepo, Version unTillRelease,
			Version postgresRelease, MonorepoTestEnvironment monorepoEnvironment) {
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
		assertEquals(expected.keySet(), actual.stream()
				.map(Component::getName)
				.collect(Collectors.toSet()));
		for (Component component : actual) {
			assertTrue(component.getVersion().isLocked());
			assertEquals(expected.get(component.getName()), component.getVersion());
		}
	}

	private void assertReleaseCounts(VCSRepository postgresRepo, VCSRepository unTillRepo, int expected) {
		assertEquals(expected, componentTags(postgresRepo).size());
		assertEquals(expected, componentTags(unTillRepo).size());
	}

	private void assertUnchangedComponents(Component sqlite, VCSRepository sqliteRepo,
			Component ubl, VCSRepository ublRepo, MonorepoTestEnvironment monorepoEnvironment) {
		assertNoSqliteRelease(sqlite, sqliteRepo, monorepoEnvironment);
		assertUblBaselineUnchanged(ubl, ublRepo, monorepoEnvironment);
	}

	private void assertNoSqliteRelease(Component sqlite, VCSRepository sqliteRepo,
			MonorepoTestEnvironment monorepoEnvironment) {
		// sqlite has repository activity but is not an unTill dependency, so the workflow must create no
		// sqlite release branch, tag, or builder invocation.
		String releaseBranch = Utils.getReleaseBranchName(sqliteRepo,
				monorepoEnvironment.getSqliteVersion().toReleaseZeroPatch());
		assertNull(sqliteRepo.getVCS().getHeadCommit(releaseBranch));
		assertTrue(componentTags(sqliteRepo).isEmpty());
		assertFalse(TestBuilder.getBuilders().containsKey(sqlite.getName()));
	}

	private void assertUblBaselineUnchanged(Component ubl, VCSRepository ublRepo,
			MonorepoTestEnvironment monorepoEnvironment) {
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
