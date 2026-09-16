package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.testutils.MonorepoTestEnvironment;
import org.scm4j.vcs.api.VCSCommit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that a patch release is driven by postgres history inside the shared repository, not by
 * repository-wide head. SQLite commits deliberately surround the postgres patch so the test can prove
 * that build selection, version updates, and tags stay scoped to the requested component.
 */
public class WorkflowMonorepoPatchesTest extends WorkflowMonorepoTestBase {

	@Test
	public void testPatchReleaseUsesComponentHistory() throws Exception {
		// Git and SVN implement branch history differently, so the same component-scoping contract covers both.
		runForEachVcs(this::runPatchScenario);
	}

	private void runPatchScenario(ScenarioContext context) {
		MonorepoTestEnvironment environment = context.environment;
		Component postgres = context.postgres;
		VCSRepository postgresRepo = context.postgresRepo;
		VCSRepository sqliteRepo = context.sqliteRepo;

		// Establish the initial minor release that the versioned patch workflow will target.
		environment.generateComponentCommit(postgresRepo.getDevelopBranch(),
				MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "postgres feature added");
		IAction forkAction = execAndGetActionFork(postgres);
		assertActionDoesFork(forkAction, postgres);

		Version initialRelease = environment.getPostgresVersion().toReleaseZeroPatch();
		String releaseBranch = Utils.getReleaseBranchName(postgresRepo, initialRelease);
		VCSCommit initialBuildCommit = latestComponentCommit(postgresRepo, releaseBranch);
		IAction buildAction = execAndGetActionBuild(postgres);
		assertActionDoesBuild(buildAction, postgres);
		assertBuildRevision(postgres, postgresRepo, initialRelease, initialBuildCommit);
		assertTagRevision(postgresRepo, initialRelease, initialBuildCommit);

		// Preserve SQLite's version, then advance physical branch head past the postgres patch with SQLite-only work.
		// This makes repository head intentionally wrong for the postgres patch build.
		String sqliteVersion = getComponentFileContent(sqliteRepo, releaseBranch, Constants.VER_FILE_NAME);
		VCSCommit postgresPatchCommit = environment.generateComponentCommit(releaseBranch,
				MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "postgres patch added");
		VCSCommit sqliteRepositoryHead = environment.generateComponentCommit(releaseBranch,
				MonorepoTestEnvironment.SQLITE_SUBFOLDER, "sqlite release-branch change added");
		assertNotEquals(postgresPatchCommit.getRevision(), sqliteRepositoryHead.getRevision());
		assertEquals(sqliteRepositoryHead.getRevision(),
				postgresRepo.getVCS().getHeadCommit(releaseBranch).getRevision());

		// The patch must build and tag the postgres revision even though SQLite moved repository head forward.
		Component postgresPatch = postgres.clone(initialRelease);
		Version patchRelease = initialRelease.toNextPatch();
		IAction patchAction = execAndGetActionBuild(postgresPatch);
		assertActionDoesBuild(patchAction, postgresPatch);
		assertBuildRevision(postgresPatch, postgresRepo, patchRelease, postgresPatchCommit);
		assertTagRevision(postgresRepo, patchRelease, postgresPatchCommit);
		// Only postgres advances to the next patch candidate; SQLite receives neither a version change nor a tag.
		assertEquals(patchRelease.toNextPatch().toString(),
				getComponentFileContent(postgresRepo, releaseBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteVersion, getComponentFileContent(sqliteRepo, releaseBranch, Constants.VER_FILE_NAME));
		assertEquals(2, componentTags(postgresRepo).size());
		assertTrue(componentTags(sqliteRepo).isEmpty());

		// Later sibling-only activity must not look like another postgres patch.
		environment.generateComponentCommit(releaseBranch, MonorepoTestEnvironment.SQLITE_SUBFOLDER,
				"second sqlite release-branch change added");
		IAction siblingOnlyAction = execAndGetActionBuild(postgresPatch);
		assertActionDoesNothing(siblingOnlyAction, postgresPatch);
		assertEquals(2, componentTags(postgresRepo).size());
		assertTrue(componentTags(sqliteRepo).isEmpty());
	}
}
