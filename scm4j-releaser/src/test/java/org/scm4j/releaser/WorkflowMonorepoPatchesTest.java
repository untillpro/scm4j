package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.testutils.MonorepoTestRepositories;
import org.scm4j.vcs.api.VCSCommit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that a patch release is driven by postgres history inside the shared repository, not by
 * repository-wide head. SQLite commits deliberately surround the postgres patch so the test can prove
 * that build selection, version updates, and tags stay scoped to the requested component.
 */
public class WorkflowMonorepoPatchesTest extends WorkflowTestBase {

	public WorkflowMonorepoPatchesTest() {
		super(WorkflowEnvironment.MONOREPO);
	}

	@Test
	public void testPatchReleaseUsesComponentHistory() {
		// Establish the initial minor release that the versioned patch workflow will target.
		monorepoRepositories.generateComponentCommit(repoPostgres.getDevelopBranch(),
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "postgres feature added");
		IAction forkAction = execAndGetActionFork(compPostgres);
		assertActionDoesFork(forkAction, compPostgres);

		Version initialRelease = monorepoRepositories.getPostgresVersion().toReleaseZeroPatch();
		String releaseBranch = Utils.getReleaseBranchName(repoPostgres, initialRelease);
		VCSCommit initialBuildCommit = latestComponentCommit(repoPostgres, releaseBranch);
		IAction buildAction = execAndGetActionBuild(compPostgres);
		assertActionDoesBuild(buildAction, compPostgres);
		assertBuildRevision(compPostgres, repoPostgres, initialRelease, initialBuildCommit);
		assertTagRevision(repoPostgres, initialRelease, initialBuildCommit);

		// Preserve SQLite's version, then advance physical branch head past the postgres patch with SQLite-only work.
		// This makes repository head intentionally wrong for the postgres patch build.
		String sqliteVersion = getComponentFileContent(repoSqlite, releaseBranch, Constants.VER_FILE_NAME);
		VCSCommit postgresPatchCommit = monorepoRepositories.generateComponentCommit(releaseBranch,
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "postgres patch added");
		VCSCommit sqliteRepositoryHead = monorepoRepositories.generateComponentCommit(releaseBranch,
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "sqlite release-branch change added");
		assertNotEquals(postgresPatchCommit.getRevision(), sqliteRepositoryHead.getRevision());
		assertEquals(sqliteRepositoryHead.getRevision(),
				repoPostgres.getVCS().getHeadCommit(releaseBranch).getRevision());

		// The patch must build and tag the postgres revision even though SQLite moved repository head forward.
		Component postgresPatch = compPostgres.clone(initialRelease);
		Version patchRelease = initialRelease.toNextPatch();
		IAction patchAction = execAndGetActionBuild(postgresPatch);
		assertActionDoesBuild(patchAction, postgresPatch);
		assertBuildRevision(postgresPatch, repoPostgres, patchRelease, postgresPatchCommit);
		assertTagRevision(repoPostgres, patchRelease, postgresPatchCommit);
		// Only postgres advances to the next patch candidate; SQLite receives neither a version change nor a tag.
		assertEquals(patchRelease.toNextPatch().toString(),
				getComponentFileContent(repoPostgres, releaseBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteVersion, getComponentFileContent(repoSqlite, releaseBranch, Constants.VER_FILE_NAME));
		assertEquals(2, componentTags(repoPostgres).size());
		assertTrue(componentTags(repoSqlite).isEmpty());

		// Later sibling-only activity must not look like another postgres patch.
		monorepoRepositories.generateComponentCommit(releaseBranch, MonorepoTestRepositories.SQLITE_SUBFOLDER,
				"second sqlite release-branch change added");
		IAction siblingOnlyAction = execAndGetActionBuild(postgresPatch);
		assertActionDoesNothing(siblingOnlyAction, postgresPatch);
		assertEquals(2, componentTags(repoPostgres).size());
		assertTrue(componentTags(repoSqlite).isEmpty());
	}
}
