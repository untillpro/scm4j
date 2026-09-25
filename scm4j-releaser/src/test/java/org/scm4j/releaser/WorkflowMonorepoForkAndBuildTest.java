package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.testutils.MonorepoTestRepositories;
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
	public void testStatusCalculatesSharedRepositoryComponentsIndependently() {
		// Change only unTill's dependency metadata so one status calculation must traverse both
		// component subfolders of the shared monorepo. Neither monorepo component is updated here.
		repoUnTill.getVCS().setFileContent(repoUnTill.getDevelopBranch(),
				repoUnTill.getComponentPath(Constants.MDEPS_FILE_NAME),
				MonorepoTestRepositories.PRODUCT_POSTGRES + ":" + monorepoRepositories.getPostgresVersion() + "\r\n"
						+ MonorepoTestRepositories.PRODUCT_SQLITE + ":" + monorepoRepositories.getSqliteVersion()
						+ "\r\n",
				Constants.SCM_IGNORE + " both monorepo components added to status graph");

		ExtendedStatus rootStatus = new ExtendedStatusBuilder(repoFactory)
				.getAndCacheMinorStatus(compUnTill, new CachedStatuses());
		Map<String, ExtendedStatus> componentStatuses = rootStatus.getSubComponents().values().stream()
				.collect(Collectors.toMap(status -> status.getComp().getName(), status -> status));

		// Each result must come from its component's own subfolder despite both status tasks using
		// the same physical repository. This guards against shared-working-copy interference.
		assertEquals(2, componentStatuses.size());
		ExtendedStatus postgresStatus = componentStatuses.get(compPostgres.getName());
		ExtendedStatus sqliteStatus = componentStatuses.get(compSqlite.getName());
		assertEquals(BuildStatus.FORK, postgresStatus.getStatus());
		assertEquals(BuildStatus.FORK, sqliteStatus.getStatus());
		assertEquals(monorepoRepositories.getPostgresVersion().toReleaseZeroPatch(),
				postgresStatus.getNextVersion());
		assertEquals(monorepoRepositories.getSqliteVersion().toReleaseZeroPatch(),
				sqliteStatus.getNextVersion());
	}

	@Test
	public void testDependencyReleasesIgnoreSiblingChanges() {
		// Capture the negative control before any application workflow runs: UBL is already released and DONE.
		assertUblBaselineUnchanged();

		// The shared develop branch gets one postgres change and the first of two sqlite changes.
		monorepoRepositories.generateComponentCommit(repoPostgres.getDevelopBranch(),
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "postgres feature added");
		monorepoRepositories.generateComponentCommit(repoSqlite.getDevelopBranch(),
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "sqlite feature added");

		IAction forkAction = execAndGetActionFork(compUnTill);
		// postgres and unTill need their first release branches; the existing UBL release must be reused.
		assertActionDoesFork(forkAction, compPostgres, compUnTill, compPostgres);
		assertActionDoesNothing(forkAction, compUBL);

		Version firstPostgresRelease = monorepoRepositories.getPostgresVersion().toReleaseZeroPatch();
		Version firstUnTillRelease = monorepoRepositories.getUnTillVersion().toReleaseZeroPatch();
		String postgresReleaseBranch = Utils.getReleaseBranchName(repoPostgres, firstPostgresRelease);
		VCSCommit postgresBuildCommit = latestComponentCommit(repoPostgres, postgresReleaseBranch);

		// Move the release branch head with the second sqlite change. The postgres build must stay on
		// the earlier postgres revision selected by component-filtered history.
		VCSCommit sqliteRepositoryHead = monorepoRepositories.generateComponentCommit(postgresReleaseBranch,
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "second sqlite feature added");
		assertEquals(sqliteRepositoryHead.getRevision(),
				repoPostgres.getVCS().getHeadCommit(postgresReleaseBranch).getRevision());

		IAction buildAction = execAndGetActionBuild(compUnTill);
		// Building the root application must first build postgres, then lock that release into unTill.
		assertActionDoesBuild(buildAction, compPostgres);
		assertActionDoesBuild(buildAction, compUnTill, BuildStatus.BUILD_MDEPS);
		assertActionDoesNothing(buildAction, compUBL);
		// The builder environment and tag must both identify the postgres revision, not the newer sqlite head.
		assertBuildRevision(compPostgres, repoPostgres, firstPostgresRelease, postgresBuildCommit);
		assertTagRevision(repoPostgres, firstPostgresRelease, postgresBuildCommit);
		assertNotEquals(sqliteRepositoryHead.getRevision(), postgresBuildCommit.getRevision());
		// Verify the externally durable result: locked dependencies and exactly one release of each built component.
		assertUnTillMDeps(firstUnTillRelease, firstPostgresRelease);
		assertTagExists(repoUnTill, firstUnTillRelease);
		assertReleaseCounts(1);
		assertUnchangedComponents();
		// Once released, postgres has no work of its own left to execute.
		assertActionDoesNothing(execAndGetActionBuild(compPostgres), compPostgres);

		// A sibling-only develop change must not schedule another postgres or unTill release.
		monorepoRepositories.generateComponentCommit(repoSqlite.getDevelopBranch(),
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "third sqlite feature added");
		// Check postgres directly as well as through the unTill dependency tree: a sqlite change must
		// not produce any action for the unchanged component that shares its repository.
		assertActionDoesNothing(execAndGetActionBuild(compPostgres), compPostgres);
		IAction siblingOnlyAction = execAndGetActionBuild(compUnTill);
		// The application tree must also remain a complete no-op after the sibling-only change.
		assertActionDoesNothing(siblingOnlyAction, compPostgres, compUBL, compUnTill);
		assertReleaseCounts(1);
		assertUnchangedComponents();

		// A new postgres change must release postgres and propagate its new version to unTill.
		monorepoRepositories.generateComponentCommit(repoPostgres.getDevelopBranch(),
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "second postgres feature added");
		IAction secondForkAction = execAndGetActionFork(compUnTill);
		assertActionDoesFork(secondForkAction, compPostgres, compUnTill);
		assertActionDoesNothing(secondForkAction, compUBL);

		Version secondPostgresRelease = firstPostgresRelease.toNextMinor();
		Version secondUnTillRelease = firstUnTillRelease.toNextMinor();
		String secondPostgresBranch = Utils.getReleaseBranchName(repoPostgres, secondPostgresRelease);
		VCSCommit secondPostgresBuildCommit = latestComponentCommit(repoPostgres, secondPostgresBranch);
		String secondUnTillBranch = Utils.getReleaseBranchName(repoUnTill, secondUnTillRelease);
		VCSCommit secondUnTillBuildCommit = repoUnTill.getVCS().getHeadCommit(secondUnTillBranch);
		assertNotNull(secondUnTillBuildCommit);
		IAction secondBuildAction = execAndGetActionBuild(compUnTill);
		// The postgres change must now build both the dependency and its consuming application, but not UBL.
		assertActionDoesBuild(secondBuildAction, compPostgres);
		assertActionDoesBuild(secondBuildAction, compUnTill, BuildStatus.BUILD_MDEPS);
		assertActionDoesNothing(secondBuildAction, compUBL);
		// Confirm both builds and tags use the captured revisions, and unTill locks the new postgres version only.
		assertBuildRevision(compPostgres, repoPostgres, secondPostgresRelease, secondPostgresBuildCommit);
		assertTagRevision(repoPostgres, secondPostgresRelease, secondPostgresBuildCommit);
		assertBuildRevision(compUnTill, repoUnTill, secondUnTillRelease, secondUnTillBuildCommit);
		assertTagRevision(repoUnTill, secondUnTillRelease, secondUnTillBuildCommit);
		assertUnTillMDeps(secondUnTillRelease, secondPostgresRelease);
		assertReleaseCounts(2);
		assertUnchangedComponents();
	}

	private void assertUnTillMDeps(Version unTillRelease, Version postgresRelease) {
		Map<String, Version> expected = new LinkedHashMap<>();
		expected.put(MonorepoTestRepositories.PRODUCT_UBL, monorepoRepositories.getUblReleaseVersion());
		expected.put(MonorepoTestRepositories.PRODUCT_POSTGRES, postgresRelease);
		assertLockedMDeps(repoUnTill, unTillRelease, expected);
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

	private void assertReleaseCounts(int expected) {
		assertEquals(expected, componentTags(repoPostgres).size());
		assertEquals(expected, componentTags(repoUnTill).size());
	}

	private void assertUnchangedComponents() {
		assertNoSqliteRelease();
		assertUblBaselineUnchanged();
	}

	private void assertNoSqliteRelease() {
		// sqlite has repository activity but is not an unTill dependency, so the workflow must create no
		// sqlite release branch, tag, or builder invocation.
		String releaseBranch = Utils.getReleaseBranchName(repoSqlite,
				monorepoRepositories.getSqliteVersion().toReleaseZeroPatch());
		assertNull(repoSqlite.getVCS().getHeadCommit(releaseBranch));
		assertTrue(componentTags(repoSqlite).isEmpty());
		assertFalse(TestBuilder.getBuilders().containsKey(compSqlite.getName()));
	}

	private void assertUblBaselineUnchanged() {
		// UBL is the standalone control dependency: its development version, release version, original tag,
		// and lack of a new build must remain stable across both unTill release cycles.
		Version releaseVersion = monorepoRepositories.getUblReleaseVersion();
		String releaseBranch = Utils.getReleaseBranchName(repoUBL, releaseVersion);
		assertEquals(monorepoRepositories.getUblDevelopmentVersion().toString(),
				repoUBL.getVCS().getFileContent(repoUBL.getDevelopBranch(), Constants.VER_FILE_NAME, null));
		assertEquals(releaseVersion.toNextPatch().toString(),
				repoUBL.getVCS().getFileContent(releaseBranch, Constants.VER_FILE_NAME, null));
		List<VCSTag> tags = componentTags(repoUBL);
		assertEquals(1, tags.size());
		assertEquals(Utils.getTagDesc(repoUBL, releaseVersion.toString()).getName(), tags.get(0).getTagName());
		assertEquals(monorepoRepositories.getUblReleaseCommit().getRevision(),
				tags.get(0).getRelatedCommit().getRevision());
		assertFalse(TestBuilder.getBuilders().containsKey(compUBL.getName()));
	}
}
