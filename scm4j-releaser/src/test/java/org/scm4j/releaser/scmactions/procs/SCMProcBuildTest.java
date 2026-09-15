package org.scm4j.releaser.scmactions.procs;

import org.junit.After;
import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.commons.progress.IProgress;
import org.scm4j.commons.progress.ProgressConsole;
import org.scm4j.releaser.BuildStatus;
import org.scm4j.releaser.CachedStatuses;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.ExtendedStatus;
import org.scm4j.releaser.Utils;
import org.scm4j.releaser.WorkflowTestBase;
import org.scm4j.releaser.builders.IBuilder;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.exceptions.ENoReleaseBranch;
import org.scm4j.releaser.testutils.TestEnvironment;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SCMProcBuildTest extends WorkflowTestBase {

	private static final String COMPONENT_SUBFOLDER = "components/driver";
	private static final String COMPONENT_FILE = "feature.txt";
	private static final String SIBLING_FILE = "components/sibling/feature.txt";
	private static final String ROOT_FILE = "shared-root.txt";
	private static final String SIBLING_CONTENT = "sibling change after component change";
	private static final String ROOT_CONTENT = "root change after component change";

	@After
	public void cleanDelayedTags() {
		new DelayedTagsFile().delete();
	}

	@Test
	public void testNoReleaseBranch() {
		CachedStatuses cache = new CachedStatuses();
		cache.put(repoUBL.getComponentLocation(), new ExtendedStatus(env.getUblVer(), BuildStatus.BUILD,
				new LinkedHashMap<Component, ExtendedStatus>(), compUBL, repoUBL));
		ISCMProc proc = new SCMProcBuild(compUBL, cache, false, repoUBL);
		try {
			proc.execute(new ProgressConsole());
			fail();
		} catch (ENoReleaseBranch e) {
			assertEquals(Utils.getReleaseBranchName(repoUBL, env.getUblVer()), e.getReleaseBranchName());
		}
	}

	@Test
	public void testBuildRevisionSelection() throws Exception {
		// Run the same scenarios against real Git and SVN repositories so both adapters must select
		// the same build revision.
		for (VCSType vcsType : VCSType.values()) {
			try (TestEnvironment env = new TestEnvironment(vcsType)) {
				env.generateTestEnvironment();

				assertMissingSubfolderReleaseBranch(vcsType, env.getUnTillDbVCS());
				assertNonDelayedTagUsesComponentRevision(vcsType, env.getUnTillDbVCS());
				assertDelayedTagUsesComponentRevision(vcsType, env.getUblVCS());
				assertRootComponentUsesRepositoryHead(vcsType, env.getUnTillVCS());
			}
		}
	}

	private void assertMissingSubfolderReleaseBranch(VCSType vcsType, IVCS vcs) {
		// A missing release branch must produce the same releaser error for root and subfolder components,
		// instead of leaking a Git or SVN adapter exception from the path-filtered history query.
		Version version = new Version("0.1.0");
		RecordingBuilder builder = new RecordingBuilder();
		VCSRepository repository = new VCSRepository("missing", vcs.getRepoUrl(), COMPONENT_SUBFOLDER, null,
				vcsType, null, "release/", vcs, builder);
		Component component = new Component("test:missing:" + version);
		CachedStatuses cache = new CachedStatuses();
		cache.put(repository.getComponentLocation(), new ExtendedStatus(version, BuildStatus.BUILD,
				new LinkedHashMap<Component, ExtendedStatus>(), component, repository));

		try {
			new SCMProcBuild(component, cache, false, repository).execute(new ProgressConsole());
			fail(message(vcsType, repository) + " should reject a missing release branch");
		} catch (ENoReleaseBranch e) {
			assertEquals(message(vcsType, repository), Utils.getReleaseBranchName(repository, version),
					e.getReleaseBranchName());
		}
	}

	private void assertNonDelayedTagUsesComponentRevision(VCSType vcsType, IVCS vcs) {
		// The component changes first, then sibling and root files move the repository head forward.
		// A normal component build must still build and tag the earlier component commit.
		BuildFixture fixture = createFixture(vcsType, vcs, "NonDelayed", new Version("1.2.3"),
				COMPONENT_SUBFOLDER);

		executeBuild(fixture, false);

		assertBuildRevision(fixture, fixture.componentCommit);
		assertTagRevision(fixture, fixture.componentCommit);
		assertFalse(message(fixture), new File(fixture.builder.workingFolder, SIBLING_FILE).exists());
		assertFalse(message(fixture), new File(fixture.builder.workingFolder, ROOT_FILE).exists());
		// The version bump is written on the current branch head, so it must keep the later unrelated changes.
		assertEquals(message(fixture), fixture.version.toNextPatch().toString(),
				vcs.getFileContent(fixture.branchName,
						fixture.repository.getComponentPath(Constants.VER_FILE_NAME), null));
		assertUnrelatedHeadContentPreserved(fixture);
	}

	private void assertDelayedTagUsesComponentRevision(VCSType vcsType, IVCS vcs) {
		// A delayed build saves the revision for tagging later. It must save the component commit,
		// without creating a tag or moving the release branch now.
		BuildFixture fixture = createFixture(vcsType, vcs, "delayed", new Version("2.3.4"),
				COMPONENT_SUBFOLDER);

		executeBuild(fixture, true);

		assertBuildRevision(fixture, fixture.componentCommit);
		DelayedTag delayedTag = new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation());
		assertNotNull(message(fixture), delayedTag);
		assertEquals(message(fixture), fixture.componentCommit.getRevision(), delayedTag.getRevision());
		assertEquals(message(fixture), fixture.version, delayedTag.getVersion());
		assertTrue(message(fixture), vcs.getTags().isEmpty());
		assertEquals(message(fixture), fixture.repositoryHead.getRevision(),
				vcs.getHeadCommit(fixture.branchName).getRevision());
	}

	private void assertRootComponentUsesRepositoryHead(VCSType vcsType, IVCS vcs) {
		// Without a component subfolder, the whole repository is the component and its head is the
		// correct revision to build and tag.
		BuildFixture fixture = createFixture(vcsType, vcs, "root", new Version("3.4.5"), null);

		executeBuild(fixture, false);

		assertBuildRevision(fixture, fixture.repositoryHead);
		assertTagRevision(fixture, fixture.repositoryHead);
		assertTrue(message(fixture), new File(fixture.builder.workingFolder, SIBLING_FILE).exists());
		assertTrue(message(fixture), new File(fixture.builder.workingFolder, ROOT_FILE).exists());
	}

	private BuildFixture createFixture(VCSType vcsType, IVCS vcs, String repositoryName, Version version,
			String subfolder) {
		RecordingBuilder builder = new RecordingBuilder();
		VCSRepository repository = new VCSRepository(repositoryName, vcs.getRepoUrl(), subfolder, null, vcsType,
				null, "release/", vcs, builder);
		Component component = new Component("test:" + repositoryName + ":" + version);
		String branchName = Utils.getReleaseBranchName(repository, version);
		vcs.createBranch(null, branchName, "release branch created");
		vcs.setFileContent(branchName, repository.getComponentPath(Constants.VER_FILE_NAME), version.toString(),
				Constants.SCM_IGNORE + " release version created");
		// This is the latest commit that belongs to a subfolder component and therefore its build revision.
		VCSCommit componentCommit = vcs.setFileContent(branchName, repository.getComponentPath(COMPONENT_FILE),
				"component content", "component changed");
		// These commits deliberately move the repository head without changing the component subfolder.
		vcs.setFileContent(branchName, SIBLING_FILE, SIBLING_CONTENT, "sibling changed");
		VCSCommit repositoryHead = vcs.setFileContent(branchName, ROOT_FILE, ROOT_CONTENT, "root changed");

		return new BuildFixture(vcsType, vcs, repository, component, version, branchName, builder,
				componentCommit, repositoryHead);
	}

	private void executeBuild(BuildFixture fixture, boolean delayedTag) {
		CachedStatuses cache = new CachedStatuses();
		cache.put(fixture.repository.getComponentLocation(), new ExtendedStatus(fixture.version, BuildStatus.BUILD,
				new LinkedHashMap<Component, ExtendedStatus>(), fixture.component, fixture.repository));
		new SCMProcBuild(fixture.component, cache, delayedTag, fixture.repository).execute(new ProgressConsole());
	}

	private void assertBuildRevision(BuildFixture fixture, VCSCommit expectedCommit) {
		assertEquals(message(fixture), Utils.getBuildTimeEnvVars(fixture.vcsType, expectedCommit.getRevision(),
				fixture.branchName, fixture.repository.getUrl()), fixture.builder.buildTimeEnvVars);
	}

	private void assertTagRevision(BuildFixture fixture, VCSCommit expectedCommit) {
		String expectedTagName = Utils.getTagDesc(fixture.repository, fixture.version.toString()).getName();
		VCSTag matchingTag = null;
		for (VCSTag tag : fixture.vcs.getTags()) {
			if (expectedTagName.equals(tag.getTagName())) {
				matchingTag = tag;
				break;
			}
		}
		assertNotNull(message(fixture), matchingTag);
		assertEquals(message(fixture), expectedCommit.getRevision(), matchingTag.getRelatedCommit().getRevision());
	}

	private void assertUnrelatedHeadContentPreserved(BuildFixture fixture) {
		assertEquals(message(fixture), SIBLING_CONTENT,
				fixture.vcs.getFileContent(fixture.branchName, SIBLING_FILE, null));
		assertEquals(message(fixture), ROOT_CONTENT,
				fixture.vcs.getFileContent(fixture.branchName, ROOT_FILE, null));
	}

	private String message(BuildFixture fixture) {
		return message(fixture.vcsType, fixture.repository);
	}

	private String message(VCSType vcsType, VCSRepository repository) {
		return vcsType + " " + repository.getName();
	}

	private static class RecordingBuilder implements IBuilder {

		private File workingFolder;
		private Map<String, String> buildTimeEnvVars;

		@Override
		public void build(Component comp, File workingFolder, IProgress progress,
				Map<String, String> buildTimeEnvVars) {
			this.workingFolder = workingFolder;
			this.buildTimeEnvVars = buildTimeEnvVars;
		}

		@Override
		public String getCommand() {
			return null;
		}
	}

	private static class BuildFixture {

		private final VCSType vcsType;
		private final IVCS vcs;
		private final VCSRepository repository;
		private final Component component;
		private final Version version;
		private final String branchName;
		private final RecordingBuilder builder;
		private final VCSCommit componentCommit;
		private final VCSCommit repositoryHead;

		private BuildFixture(VCSType vcsType, IVCS vcs, VCSRepository repository, Component component,
				Version version, String branchName, RecordingBuilder builder, VCSCommit componentCommit,
				VCSCommit repositoryHead) {
			this.vcsType = vcsType;
			this.vcs = vcs;
			this.repository = repository;
			this.component = component;
			this.version = version;
			this.branchName = branchName;
			this.builder = builder;
			this.componentCommit = componentCommit;
			this.repositoryHead = repositoryHead;
		}
	}
}
