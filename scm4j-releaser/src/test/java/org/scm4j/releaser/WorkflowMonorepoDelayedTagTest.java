package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.testutils.MonorepoTestEnvironment;
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
public class WorkflowMonorepoDelayedTagTest extends WorkflowMonorepoTestBase {

	@Test
	public void testDelayedTagsAreScopedByComponent() throws Exception {
		// Exercise the same delayed-state isolation through both supported VCS implementations.
		runForEachVcs(this::runDelayedTagScenario);
	}

	private void runDelayedTagScenario(ScenarioContext context) {
		MonorepoTestEnvironment environment = context.environment;
		Component postgres = context.postgres;
		Component sqlite = context.sqlite;
		VCSRepository postgresRepo = context.postgresRepo;
		VCSRepository sqliteRepo = context.sqliteRepo;

		// Both components have independent work even though their development history shares one repository.
		environment.generateComponentCommit(postgresRepo.getDevelopBranch(),
				MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "postgres feature added");
		environment.generateComponentCommit(sqliteRepo.getDevelopBranch(),
				MonorepoTestEnvironment.SQLITE_SUBFOLDER, "sqlite feature added");
		IAction postgresForkAction = execAndGetActionFork(postgres);
		assertActionDoesFork(postgresForkAction, postgres);
		IAction sqliteForkAction = execAndGetActionFork(sqlite);
		assertActionDoesFork(sqliteForkAction, sqlite);

		// Capture the component revisions selected at fork time before sibling commits move either branch head.
		Version postgresRelease = environment.getPostgresVersion().toReleaseZeroPatch();
		Version sqliteRelease = environment.getSqliteVersion().toReleaseZeroPatch();
		String postgresBranch = Utils.getReleaseBranchName(postgresRepo, postgresRelease);
		String sqliteBranch = Utils.getReleaseBranchName(sqliteRepo, sqliteRelease);
		VCSCommit postgresBuildCommit = latestComponentCommit(postgresRepo, postgresBranch);
		VCSCommit sqliteBuildCommit = latestComponentCommit(sqliteRepo, sqliteBranch);
		String sqliteVersionOnPostgresBranch = getComponentFileContent(
				sqliteRepo, postgresBranch, Constants.VER_FILE_NAME);
		String postgresVersionOnSqliteBranch = getComponentFileContent(
				postgresRepo, sqliteBranch, Constants.VER_FILE_NAME);

		// Opposite-sibling changes move each repository head beyond the component revision saved for tagging.
		VCSCommit postgresBranchHead = environment.generateComponentCommit(postgresBranch,
				MonorepoTestEnvironment.SQLITE_SUBFOLDER, "sqlite change on postgres release branch");
		VCSCommit sqliteBranchHead = environment.generateComponentCommit(sqliteBranch,
				MonorepoTestEnvironment.POSTGRES_SUBFOLDER, "postgres change on sqlite release branch");

		// Delayed builds run at the captured component revisions but intentionally defer tagging and patch bumps.
		IAction postgresBuildAction = execAndGetActionBuildDelayedTag(postgres);
		assertActionDoesBuildDelayedTag(postgresBuildAction, postgres);
		IAction sqliteBuildAction = execAndGetActionBuildDelayedTag(sqlite);
		assertActionDoesBuildDelayedTag(sqliteBuildAction, sqlite);
		assertBuildRevision(postgres, postgresRepo, postgresRelease, postgresBuildCommit);
		assertBuildRevision(sqlite, sqliteRepo, sqliteRelease, sqliteBuildCommit);
		assertEquals(postgresBranchHead.getRevision(), postgresRepo.getVCS().getHeadCommit(postgresBranch).getRevision());
		assertEquals(sqliteBranchHead.getRevision(), sqliteRepo.getVCS().getHeadCommit(sqliteBranch).getRevision());

		// The shared delayed-tags file must retain two records because component subfolders distinguish them.
		DelayedTagsFile delayedTagsFile = new DelayedTagsFile();
		DelayedTag postgresDelayedTag = delayedTagsFile.getDelayedTag(postgresRepo.getComponentLocation());
		DelayedTag sqliteDelayedTag = delayedTagsFile.getDelayedTag(sqliteRepo.getComponentLocation());
		assertNotNull(postgresDelayedTag);
		assertNotNull(sqliteDelayedTag);
		assertEquals(postgresRelease, postgresDelayedTag.getVersion());
		assertEquals(postgresBuildCommit.getRevision(), postgresDelayedTag.getRevision());
		assertEquals(sqliteRelease, sqliteDelayedTag.getVersion());
		assertEquals(sqliteBuildCommit.getRevision(), sqliteDelayedTag.getRevision());
		assertEquals(2, delayedTagsFile.getContent().size());
		assertTrue(componentTags(postgresRepo).isEmpty());
		assertTrue(componentTags(sqliteRepo).isEmpty());
		assertEquals(postgresRelease.toString(),
				getComponentFileContent(postgresRepo, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteRelease.toString(),
				getComponentFileContent(sqliteRepo, sqliteBranch, Constants.VER_FILE_NAME));

		// Tagging postgres consumes only its record and changes only its component-local version.
		IAction postgresTagAction = execAndGetActionTag(postgres, null);
		assertActionDoesTag(postgresTagAction, postgres);
		assertTagRevision(postgresRepo, postgresRelease, postgresBuildCommit);
		assertNull(delayedTagsFile.getDelayedTag(postgresRepo.getComponentLocation()));
		assertNotNull(delayedTagsFile.getDelayedTag(sqliteRepo.getComponentLocation()));
		assertEquals(postgresRelease.toNextPatch().toString(),
				getComponentFileContent(postgresRepo, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteRelease.toString(),
				getComponentFileContent(sqliteRepo, sqliteBranch, Constants.VER_FILE_NAME));
		assertEquals(sqliteVersionOnPostgresBranch,
				getComponentFileContent(sqliteRepo, postgresBranch, Constants.VER_FILE_NAME));
		assertEquals(1, componentTags(postgresRepo).size());
		assertTrue(componentTags(sqliteRepo).isEmpty());

		// SQLite remains independently actionable and produces its own namespaced tag at its saved revision.
		IAction sqliteTagAction = execAndGetActionTag(sqlite, null);
		assertActionDoesTag(sqliteTagAction, sqlite);
		assertTagRevision(sqliteRepo, sqliteRelease, sqliteBuildCommit);
		assertTrue(delayedTagsFile.getContent().isEmpty());
		assertEquals(sqliteRelease.toNextPatch().toString(),
				getComponentFileContent(sqliteRepo, sqliteBranch, Constants.VER_FILE_NAME));
		assertEquals(postgresVersionOnSqliteBranch,
				getComponentFileContent(postgresRepo, sqliteBranch, Constants.VER_FILE_NAME));
		// Both component tags coexist in the physical repository after their independent records are consumed.
		assertEquals(1, componentTags(postgresRepo).size());
		assertEquals(1, componentTags(sqliteRepo).size());
		assertEquals(2, postgresRepo.getVCS().getTags().size());
	}
}
