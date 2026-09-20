package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.testutils.MonorepoTestRepositories;
import org.scm4j.vcs.api.VCSCommit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that delayed-tag state is keyed by component identity when postgres and SQLite share one
 * repository. Each release branch is moved by its sibling so the eventual tag must use the saved
 * component revision rather than the newer physical repository head.
 */
public class WorkflowMonorepoDelayedTagTest extends WorkflowTestBase {

	public WorkflowMonorepoDelayedTagTest() {
		super(WorkflowEnvironment.MONOREPO);
	}

	@Test
	public void testDelayedTagsAreScopedByComponent() {
		// Both components have independent work even though their development history shares one repository.
		monorepoRepositories.generateComponentCommit(repoPostgres.getDevelopBranch(),
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "postgres feature added");
		monorepoRepositories.generateComponentCommit(repoSqlite.getDevelopBranch(),
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "sqlite feature added");
		IAction postgresForkAction = execAndGetActionFork(compPostgres);
		assertActionDoesFork(postgresForkAction, compPostgres);
		IAction sqliteForkAction = execAndGetActionFork(compSqlite);
		assertActionDoesFork(sqliteForkAction, compSqlite);

		// Capture the component revisions selected at fork time before sibling commits move either branch head.
		Version postgresRelease = monorepoRepositories.getPostgresVersion().toReleaseZeroPatch();
		Version sqliteRelease = monorepoRepositories.getSqliteVersion().toReleaseZeroPatch();
		String postgresBranch = Utils.getReleaseBranchName(repoPostgres, postgresRelease);
		String sqliteBranch = Utils.getReleaseBranchName(repoSqlite, sqliteRelease);
		VCSCommit postgresBuildCommit = latestComponentCommit(repoPostgres, postgresBranch);
		VCSCommit sqliteBuildCommit = latestComponentCommit(repoSqlite, sqliteBranch);
		String sqliteVersionOnPostgresBranch = getComponentFileContent(
				repoSqlite, postgresBranch, Constants.VER_FILE_NAME);
		String postgresVersionOnSqliteBranch = getComponentFileContent(
				repoPostgres, sqliteBranch, Constants.VER_FILE_NAME);

		// Opposite-sibling changes move each repository head beyond the component revision saved for tagging.
		VCSCommit postgresBranchHead = monorepoRepositories.generateComponentCommit(postgresBranch,
				MonorepoTestRepositories.SQLITE_SUBFOLDER, "sqlite change on postgres release branch");
		VCSCommit sqliteBranchHead = monorepoRepositories.generateComponentCommit(sqliteBranch,
				MonorepoTestRepositories.POSTGRES_SUBFOLDER, "postgres change on sqlite release branch");

		// Delayed builds run at the captured component revisions but intentionally defer tagging and patch bumps.
		IAction postgresBuildAction = execAndGetActionBuildDelayedTag(compPostgres);
		assertActionDoesBuildDelayedTag(postgresBuildAction, compPostgres);
		IAction sqliteBuildAction = execAndGetActionBuildDelayedTag(compSqlite);
		assertActionDoesBuildDelayedTag(sqliteBuildAction, compSqlite);
		assertBuildRevision(compPostgres, repoPostgres, postgresRelease, postgresBuildCommit);
		assertBuildRevision(compSqlite, repoSqlite, sqliteRelease, sqliteBuildCommit);
		assertEquals(postgresBranchHead.getRevision(), repoPostgres.getVCS().getHeadCommit(postgresBranch).getRevision());
		assertEquals(sqliteBranchHead.getRevision(), repoSqlite.getVCS().getHeadCommit(sqliteBranch).getRevision());

		// The shared delayed-tags file must retain two records because component subfolders distinguish them.
		DelayedTagsFile delayedTagsFile = new DelayedTagsFile();
		DelayedTag postgresDelayedTag = delayedTagsFile.getDelayedTag(repoPostgres.getComponentLocation());
		DelayedTag sqliteDelayedTag = delayedTagsFile.getDelayedTag(repoSqlite.getComponentLocation());
		assertNotNull(postgresDelayedTag);
		assertNotNull(sqliteDelayedTag);
		assertEquals(postgresRelease, postgresDelayedTag.getVersion());
		assertEquals(postgresBuildCommit.getRevision(), postgresDelayedTag.getRevision());
		assertEquals(sqliteRelease, sqliteDelayedTag.getVersion());
		assertEquals(sqliteBuildCommit.getRevision(), sqliteDelayedTag.getRevision());
		assertEquals(2, delayedTagsFile.getContent().size());
		assertTrue(componentTags(repoPostgres).isEmpty());
		assertTrue(componentTags(repoSqlite).isEmpty());
		assertEquals(postgresRelease.toString(),
				getComponentFileContent(repoPostgres, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteRelease.toString(),
				getComponentFileContent(repoSqlite, sqliteBranch, Constants.VER_FILE_NAME));

		// Tagging postgres consumes only its record and changes only its component-local version.
		IAction postgresTagAction = execAndGetActionTag(compPostgres, null);
		assertActionDoesTag(postgresTagAction, compPostgres);
		assertTagRevision(repoPostgres, postgresRelease, postgresBuildCommit);
		assertNull(delayedTagsFile.getDelayedTag(repoPostgres.getComponentLocation()));
		assertNotNull(delayedTagsFile.getDelayedTag(repoSqlite.getComponentLocation()));
		assertEquals(postgresRelease.toNextPatch().toString(),
				getComponentFileContent(repoPostgres, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteRelease.toString(),
				getComponentFileContent(repoSqlite, sqliteBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteVersionOnPostgresBranch,
				getComponentFileContent(repoSqlite, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(1, componentTags(repoPostgres).size());
		assertTrue(componentTags(repoSqlite).isEmpty());

		// SQLite remains independently actionable and produces its own namespaced tag at its saved revision.
		IAction sqliteTagAction = execAndGetActionTag(compSqlite, null);
		assertActionDoesTag(sqliteTagAction, compSqlite);
		assertTagRevision(repoSqlite, sqliteRelease, sqliteBuildCommit);
		assertTrue(delayedTagsFile.getContent().isEmpty());
		assertEquals(sqliteRelease.toNextPatch().toString(),
				getComponentFileContent(repoSqlite, sqliteBranch, Constants.VER_FILE_NAME));
		assertEquals(postgresVersionOnSqliteBranch,
				getComponentFileContent(repoPostgres, sqliteBranch, Constants.VER_FILE_NAME));
		// Both component tags coexist in the physical repository after their independent records are consumed.
		assertEquals(1, componentTags(repoPostgres).size());
		assertEquals(1, componentTags(repoSqlite).size());
		assertEquals(2, repoPostgres.getVCS().getTags().size());
	}
}
