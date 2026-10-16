package org.scm4j.releaser.scmactions.procs;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.releaser.Version;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.progress.ProgressConsole;
import org.scm4j.releaser.BuildStatus;
import org.scm4j.releaser.CachedStatuses;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.ExtendedStatus;
import org.scm4j.releaser.Utils;
import org.scm4j.releaser.actions.ActionSet;
import org.scm4j.releaser.builders.IBuilder;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.conf.VCSFactory;
import org.scm4j.releaser.conf.Credentials;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
import org.scm4j.releaser.exceptions.ENoReleaseBranch;
import org.scm4j.releaser.scmactions.SCMActionRelease;
import org.scm4j.releaser.scmactions.SCMActionTag;
import org.scm4j.releaser.testutils.TestBuilder;
import org.scm4j.releaser.testutils.TestEnvironment;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.IVCSMutationSession;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;

public class SCMProcBuildTest {

	private static final String COMPONENT_SUBFOLDER = "components/driver";
	private static final String COMPONENT_FILE = "feature.txt";
	private static final String BUILD_OUTPUT = "build/result.txt";
	private static final String BUILD_OUTPUT_CONTENT = "release result";
	private static final String BUILDER_EDIT = "builder-local edit";
	private static final String SIBLING_FILE = "components/sibling/feature.txt";
	private static final String UNRELATED_FILE = "unrelated/shared-root.txt";
	private static final String SIBLING_CONTENT_AT_COMPONENT_REVISION = "sibling content at component revision";
	private static final String UNRELATED_CONTENT_AT_COMPONENT_REVISION = "unrelated content at component revision";
	private static final String SIBLING_HEAD_CONTENT = "sibling change after component change";
	private static final String UNRELATED_HEAD_CONTENT = "unrelated change after component change";

	@Before
	public void cleanTestStateBefore() throws Exception {
		cleanTestState();
	}

	@After
	public void cleanTestStateAfter() throws Exception {
		cleanTestState();
	}

	private void cleanTestState() throws Exception {
		new DelayedTagsFile().delete();
		TestBuilder.getEnvVars().clear();
		TestBuilder.setBuilders(null);
		Utils.waitForDeleteDir(Constants.RELEASES_DIR);
	}

	@Test
	public void testNoReleaseBranch() throws Exception {
		try (TestEnvironment env = new TestEnvironment()) {
			env.generateTestEnvironment();
			Component component = new Component(TestEnvironment.PRODUCT_UBL);
			VCSRepository repository = env.getRepoFactory().getVCSRepository(component);
			CachedStatuses cache = new CachedStatuses();
			cache.put(repository.getComponentLocation(), new ExtendedStatus(env.getUblVer(), BuildStatus.BUILD,
					new LinkedHashMap<Component, ExtendedStatus>(), component, repository));
			ISCMProc proc = new SCMProcBuild(component, cache, false, repository);
			try (IVCSMutationSession session = repository.getVCS().startModify()) {
				proc.execute(new ProgressConsole(), session);
				fail();
			} catch (ENoReleaseBranch e) {
				assertEquals(Utils.getReleaseBranchName(repository, env.getUblVer()), e.getReleaseBranchName());
			}
		}
	}

	@Test
	public void testBuildCheckoutAndRevisionSelection() throws Exception {
		VCSType vcsType = VCSType.GIT;
		try (TestEnvironment env = new TestEnvironment()) {
			env.generateTestEnvironment();

			assertMissingSubfolderReleaseBranch(vcsType, env.getUnTillDbVCS());
			assertNonDelayedTagUsesComponentRevision(vcsType, env.getUnTillDbVCS());
			assertDelayedTagUsesComponentRevision(vcsType, env.getUblVCS());
			assertRootComponentUsesRepositoryHead(vcsType, env.getUnTillVCS());
		}
	}

	private void assertMissingSubfolderReleaseBranch(VCSType vcsType, IVCS vcs) throws Exception {
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

		try (IVCSMutationSession session = vcs.startModify()) {
			new SCMProcBuild(component, cache, false, repository).execute(new ProgressConsole(), session);
			fail(message(vcsType, repository) + " should reject a missing release branch");
		} catch (ENoReleaseBranch e) {
			assertEquals(message(vcsType, repository), Utils.getReleaseBranchName(repository, version),
					e.getReleaseBranchName());
		}
	}

	@Test
	public void testSVNPatchBumpLeavesBuilderEditsUncommitted() throws Exception {
		try (TestEnvironment env = new TestEnvironment(VCSType.SVN)) {
			env.generateTestEnvironment();
			BuildFixture fixture = createFixture(VCSType.SVN, env.getUnTillDbVCS(), "svnBuild",
					new Version("1.2.3"), COMPONENT_SUBFOLDER);
			executeBuild(fixture, false);
			assertEquals("component content", fixture.vcs.getFileContent(fixture.branchName,
					fixture.repository.getComponentPath(COMPONENT_FILE), null));
			assertEquals(BUILDER_EDIT, FileUtils.readFileToString(
					new File(fixture.sessionWorkingDirectory, COMPONENT_FILE), StandardCharsets.UTF_8));
			assertBuildWorkingFolder(fixture);
		}
	}

	@Test
	public void testBuildResultsSurviveWorkingCopyReuse() throws Exception {
		assertBuildResultsSurviveWorkingCopyReuse(false);
	}

	@Test
	public void testExecutableBuildResultsSurviveWorkingCopyReuse() throws Exception {
		assumeTrue("Executable permissions require a POSIX filesystem",
				FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		assertBuildResultsSurviveWorkingCopyReuse(true);
	}

	private void assertBuildResultsSurviveWorkingCopyReuse(boolean executableOutput) throws Exception {
		try (TestEnvironment env = new TestEnvironment()) {
			env.generateTestEnvironment();
			BuildFixture fixture = createFixture(VCSType.GIT, env.getUnTillDbVCS(), "savedResults",
					new Version("1.2.3"), COMPONENT_SUBFOLDER);
			fixture.builder.executableOutput = executableOutput;
			File staleResult = new File(Utils.getBuildDir(fixture.repository, fixture.version), "stale.txt");
			FileUtils.writeStringToFile(staleResult, "previous build", StandardCharsets.UTF_8);
			executeBuild(fixture, true);
			assertBuildWorkingFolder(fixture);
			assertFalse("Rebuilding must replace the previous result snapshot", staleResult.exists());
			try (IVCSMutationSession next = fixture.vcs.startModify()) {
				assertEquals(fixture.sessionWorkingDirectory, next.getWorkingDirectory());
				assertFalse("Session reuse must clean its previous output",
						new File(next.getWorkingDirectory(), BUILD_OUTPUT).exists());
				assertEquals(BUILD_OUTPUT_CONTENT, FileUtils.readFileToString(new File(
						Utils.getBuildDir(fixture.repository, fixture.version),
						fixture.repository.getComponentPath(BUILD_OUTPUT)), StandardCharsets.UTF_8));
				if (executableOutput) {
					Path savedOutput = new File(Utils.getBuildDir(fixture.repository, fixture.version),
							fixture.repository.getComponentPath(BUILD_OUTPUT)).toPath();
					assertEquals(PosixFilePermissions.fromString("rwxr-x--x"), Files.getPosixFilePermissions(savedOutput));
					assertTrue("Saved build output must remain executable", Files.isExecutable(savedOutput));
				}
			}
		}
	}

	@Test
	public void testSVNReleaseAndDelayedTagWithoutTrunk() throws Exception {
		try (TestEnvironment env = new TestEnvironment(VCSType.SVN)) {
			env.generateTestEnvironment();
			String development = "development";
			for (boolean delayed : new boolean[] {false, true}) {
				IVCS writer = delayed ? env.getUblVCS() : env.getUnTillDbVCS();
				writer.createBranch(null, development, "development branch created");
				writer.setFileContent(development, COMPONENT_SUBFOLDER + "/" + Constants.VER_FILE_NAME,
						"1.2.3-SNAPSHOT", "development version added");
				writer.setFileContent(development, COMPONENT_SUBFOLDER + "/" + COMPONENT_FILE,
						"development content", "development content added");
				writer.deleteBranch(null, "trunk removed");
				BuildFixture fixture = createFixture(VCSType.SVN, writer, delayed ? "delayed" : "release",
						new Version("1.2.3"), COMPONENT_SUBFOLDER, development);
				assertNotNull(fixture.vcs.getHeadCommit(fixture.repository.getDevelopBranch()));
				CachedStatuses cache = new CachedStatuses();
				cache.put(fixture.repository.getComponentLocation(), new ExtendedStatus(fixture.version, BuildStatus.BUILD,
						new LinkedHashMap<Component, ExtendedStatus>(), fixture.component, fixture.repository));
				new SCMActionRelease(fixture.component, new ArrayList<>(), cache, env.getRepoFactory(),
						ActionSet.FULL, delayed, fixture.repository).execute(new ProgressConsole());
				fixture.sessionWorkingDirectory = fixture.builder.workingFolder;
				if (delayed) {
					assertNotNull(new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation()));
					new SCMActionTag(fixture.component, fixture.repository).execute(new ProgressConsole());
					assertNull(new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation()));
				}
				assertBuildWorkingFolder(fixture);
				assertTagRevision(fixture, fixture.componentCommit);
				assertEquals(fixture.version.toNextPatch().toString(), fixture.vcs.getFileContent(fixture.branchName,
						fixture.repository.getComponentPath(Constants.VER_FILE_NAME), null));
			}
		}
	}

	private void assertNonDelayedTagUsesComponentRevision(VCSType vcsType, IVCS vcs) throws Exception {
		// Files in unrelated directories exist when the component changes, then later unrelated changes move the
		// repository head forward. A normal component build must sparsely materialize and tag the earlier
		// component commit rather than merely hiding files that did not exist at that revision.
		BuildFixture fixture = createFixture(vcsType, vcs, "NonDelayed", new Version("1.2.3"),
				COMPONENT_SUBFOLDER);

		executeBuild(fixture, false);

		assertBuildWorkingFolder(fixture);
		assertBuildRevision(fixture, fixture.componentCommit);
		assertTagRevision(fixture, fixture.componentCommit);
		assertOnlyComponentMaterialized(fixture);
		// The version bump is written on the current branch head, so it must keep the later unrelated changes.
		assertEquals(message(fixture), fixture.version.toNextPatch().toString(),
				fixture.vcs.getFileContent(fixture.branchName,
						fixture.repository.getComponentPath(Constants.VER_FILE_NAME), null));
		assertUnrelatedHeadContentPreserved(fixture);
	}

	private void assertDelayedTagUsesComponentRevision(VCSType vcsType, IVCS vcs) throws Exception {
		// A delayed build saves the revision for tagging later. It must save the component commit,
		// without creating a tag or moving the release branch now.
		BuildFixture fixture = createFixture(vcsType, vcs, "delayed", new Version("2.3.4"),
				COMPONENT_SUBFOLDER);

		executeBuild(fixture, true);

		assertBuildWorkingFolder(fixture);
		assertBuildRevision(fixture, fixture.componentCommit);
		assertOnlyComponentMaterialized(fixture);
		DelayedTag delayedTag = new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation());
		assertNotNull(message(fixture), delayedTag);
		assertEquals(message(fixture), fixture.componentCommit.getRevision(), delayedTag.getRevision());
		assertEquals(message(fixture), fixture.version, delayedTag.getVersion());
		assertTrue(message(fixture), vcs.getTags().isEmpty());
		assertEquals(message(fixture), fixture.repositoryHead.getRevision(),
				vcs.getHeadCommit(fixture.branchName).getRevision());
	}

	private void assertRootComponentUsesRepositoryHead(VCSType vcsType, IVCS vcs) throws Exception {
		// Without a component subfolder, the whole repository is the component and its head is the
		// correct revision to build and tag.
		BuildFixture fixture = createFixture(vcsType, vcs, "root", new Version("3.4.5"), null);

		executeBuild(fixture, false);

		assertBuildWorkingFolder(fixture);
		assertBuildRevision(fixture, fixture.repositoryHead);
		assertTagRevision(fixture, fixture.repositoryHead);
		assertTrue(message(fixture), new File(fixture.sessionWorkingDirectory, SIBLING_FILE).exists());
		assertTrue(message(fixture), new File(fixture.sessionWorkingDirectory, UNRELATED_FILE).exists());
	}

	private BuildFixture createFixture(VCSType vcsType, IVCS vcs, String repositoryName, Version version,
			String subfolder) {
		return createFixture(vcsType, vcs, repositoryName, version, subfolder, null);
	}

	private BuildFixture createFixture(VCSType vcsType, IVCS vcs, String repositoryName, Version version,
			String subfolder, String development) {
		RecordingBuilder builder = new RecordingBuilder();
		IVCS actionVCS = spy(VCSFactory.getVCS(vcsType, new Credentials(null, null, false), vcs.getRepoUrl(),
				new VCSWorkspace(), subfolder));
		VCSRepository repository = new VCSRepository(repositoryName, actionVCS.getRepoUrl(), subfolder, null,
				vcsType, development, "release/", actionVCS, builder);
		Component component = new Component("test:" + repositoryName + ":" + version);
		String branchName = Utils.getReleaseBranchName(repository, version);
		actionVCS.createBranch(development, branchName, "release branch created");
		if (!repository.getSubfolder().isEmpty()) {
			removeRootMetadata(actionVCS, branchName, Constants.VER_FILE_NAME);
			removeRootMetadata(actionVCS, branchName, Constants.MDEPS_FILE_NAME);
		}
		actionVCS.setFileContent(branchName, repository.getComponentPath(Constants.VER_FILE_NAME), version.toString(),
				Constants.SCM_IGNORE + " release version created");
		actionVCS.setFileContent(branchName, SIBLING_FILE, SIBLING_CONTENT_AT_COMPONENT_REVISION,
				"sibling content created");
		actionVCS.setFileContent(branchName, UNRELATED_FILE, UNRELATED_CONTENT_AT_COMPONENT_REVISION,
				"unrelated content created");
		// This is the latest commit that belongs to a subfolder component and therefore its build revision.
		VCSCommit componentCommit = actionVCS.setFileContent(branchName, repository.getComponentPath(COMPONENT_FILE),
				"component content", "component changed");
		// These commits deliberately move the repository head without changing the component subfolder.
		actionVCS.setFileContent(branchName, SIBLING_FILE, SIBLING_HEAD_CONTENT, "sibling changed");
		VCSCommit repositoryHead = actionVCS.setFileContent(branchName, UNRELATED_FILE, UNRELATED_HEAD_CONTENT,
				"unrelated content changed");
		return new BuildFixture(vcsType, actionVCS, repository, component, version, branchName, builder,
				componentCommit, repositoryHead);
	}

	private void removeRootMetadata(IVCS vcs, String branchName, String fileName) {
		if (vcs.fileExists(branchName, fileName)) {
			vcs.removeFile(branchName, fileName, Constants.SCM_IGNORE + " root metadata removed");
		}
	}

	private void executeBuild(BuildFixture fixture, boolean delayedTag) throws Exception {
		CachedStatuses cache = new CachedStatuses();
		cache.put(fixture.repository.getComponentLocation(), new ExtendedStatus(fixture.version, BuildStatus.BUILD,
				new LinkedHashMap<Component, ExtendedStatus>(), fixture.component, fixture.repository));
		try (IVCSMutationSession session = spy(fixture.vcs.startModify())) {
			fixture.mutationSession = session;
			fixture.sessionWorkingDirectory = session.getWorkingDirectory();
			new SCMProcBuild(fixture.component, cache, delayedTag, fixture.repository)
					.execute(new ProgressConsole(), session);
			assertEquals(fixture.sessionWorkingDirectory, session.getWorkingDirectory());
		}
		verify(fixture.vcs, times(1)).startModify();
		verify(fixture.vcs, never()).checkout(any(), any(), any());
		verify(fixture.vcs, never()).sparseCheckout(
				any(), any(), any(), any());
		verify(fixture.mutationSession).getHeadCommit(fixture.branchName);
		verify(fixture.mutationSession).checkout(fixture.branchName,
				fixture.sessionWorkingDirectory.getPath(), null);
		if (fixture.repository.getSubfolder().isEmpty()) {
			verify(fixture.mutationSession, never()).getCommitsRange(any(), any(), any(), anyInt(), any());
		} else {
			verify(fixture.mutationSession).getCommitsRange(eq(fixture.branchName),
					eq(fixture.repositoryHead.getRevision()), eq(WalkDirection.DESC), anyInt(),
					eq(fixture.repository.getSubfolder()));
		}
	}

	private void assertBuildRevision(BuildFixture fixture, VCSCommit expectedCommit) {
		assertEquals(message(fixture), Utils.getBuildTimeEnvVars(fixture.vcsType, expectedCommit.getRevision(),
				fixture.branchName, fixture.repository.getUrl()), fixture.builder.buildTimeEnvVars);
	}

	private void assertBuildWorkingFolder(BuildFixture fixture) throws Exception {
		assertEquals(message(fixture), fixture.sessionWorkingDirectory.getAbsoluteFile(),
				fixture.builder.workingFolder.getAbsoluteFile());
		assertTrue(message(fixture), new File(fixture.builder.workingFolder, COMPONENT_FILE).exists());
		File resultDirectory = Utils.getBuildDir(fixture.repository, fixture.version);
		assertEquals(BUILD_OUTPUT_CONTENT, FileUtils.readFileToString(new File(resultDirectory,
				fixture.repository.getComponentPath(BUILD_OUTPUT)), StandardCharsets.UTF_8));
		assertEquals("Results must be saved before the patch bump", fixture.version.toString(),
				FileUtils.readFileToString(new File(resultDirectory,
						fixture.repository.getComponentPath(Constants.VER_FILE_NAME)), StandardCharsets.UTF_8));
		assertFalse(new File(resultDirectory, ".git").exists());
		assertFalse(new File(resultDirectory, ".svn").exists());
	}

	private void assertOnlyComponentMaterialized(BuildFixture fixture) {
		assertTrue(message(fixture), fixture.sessionWorkingDirectory.isDirectory());
		assertTrue(message(fixture), new File(fixture.sessionWorkingDirectory, COMPONENT_FILE).isFile());
		assertTrue(message(fixture), new File(fixture.sessionWorkingDirectory, Constants.VER_FILE_NAME).isFile());
		assertFalse(message(fixture), new File(fixture.sessionWorkingDirectory, "../sibling").exists());
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
		assertEquals(message(fixture), SIBLING_HEAD_CONTENT,
				fixture.vcs.getFileContent(fixture.branchName, SIBLING_FILE, null));
		assertEquals(message(fixture), UNRELATED_HEAD_CONTENT,
				fixture.vcs.getFileContent(fixture.branchName, UNRELATED_FILE, null));
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
		private boolean executableOutput;

		@Override
		public void build(Component comp, File workingFolder, IProgress progress,
				Map<String, String> buildTimeEnvVars) throws Exception {
			assertTrue("Release content must exist when the builder runs",
					new File(workingFolder, COMPONENT_FILE).isFile());
			FileUtils.writeStringToFile(new File(workingFolder, COMPONENT_FILE), BUILDER_EDIT, StandardCharsets.UTF_8);
			FileUtils.writeStringToFile(new File(workingFolder, BUILD_OUTPUT), BUILD_OUTPUT_CONTENT, StandardCharsets.UTF_8);
			if (executableOutput) {
				Files.setPosixFilePermissions(new File(workingFolder, BUILD_OUTPUT).toPath(),
						PosixFilePermissions.fromString("rwxr-x--x"));
			}
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
		private File sessionWorkingDirectory;
		private IVCSMutationSession mutationSession;

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
