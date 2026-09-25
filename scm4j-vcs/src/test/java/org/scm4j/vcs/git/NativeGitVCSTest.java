/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSChangeType;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSDiffEntry;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.abstracttest.VCSAbstractTest;
import org.scm4j.vcs.api.exceptions.EVCSException;
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NativeGitVCSTest extends VCSAbstractTest {

	private static final String COMPONENT = "components/driver";
	private static final String COMPONENT_FILE = COMPONENT + "/src/feature.txt";
	private static final String LATER_COMPONENT_FILE = COMPONENT + "/src/later.txt";
	private static final String SIBLING_FILE = "components/sibling/feature.txt";
	private static final String ANCESTOR_FILE = "components/README.md";
	private static final String ROOT_FILE = "root.txt";

	private Git remoteGit;
	private NativeGitVCS nativeGit;
	private TestGitCli nativeCli;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		remoteGit = GitVCSUtils.createRepository(new File(REPO_DIR, repoName));
		remoteGit.getRepository().getConfig().setString("receive", null, "denyCurrentBranch", "updateInstead");
		remoteGit.getRepository().getConfig().save();
	}

	@After
	public void tearDownNativeRepository() {
		if (remoteGit != null) {
			remoteGit.close();
		}
	}

	@Override
	protected IVCS getVCS(IVCSRepositoryWorkspace repositoryWorkspace) {
		TestGitCli cli = new TestGitCli();
		NativeGitVCS adapter = new NativeGitVCS(repositoryWorkspace, "", cli);
		nativeCli = cli;
		nativeGit = adapter;
		return adapter;
	}

	@Override
	protected void setMakeFailureOnVCSReset(Boolean makeFailure) {
		nativeCli.setFailMergeRecovery(makeFailure);
	}

	@Override
	protected String getVCSTypeString() {
		return "native-git";
	}

	@Test
	public void testVCSTypeString() {
		assertEquals(GitVCS.GIT_VCS_TYPE_STRING, nativeGit.getVCSTypeString());
	}

	@Test
	public void testUnicodePathsAndNulDelimitedDiffRecords() {
		String path = "folder with spaces/文件-漢字.txt";
		String baseMessage = "unicode base\n\nbody 第一";
		String branchMessage = "unicode update\n\nbody 第二";
		nativeGit.setFileContent(null, path, "base 内容", baseMessage);
		nativeGit.createBranch(null, NEW_BRANCH, CREATED_DST_BRANCH_COMMIT_MESSAGE);
		VCSCommit commit = nativeGit.setFileContent(NEW_BRANCH, path, "updated 内容",
				branchMessage);

		assertEquals("updated 内容", nativeGit.getFileContent(NEW_BRANCH, path, null));
		assertEquals(branchMessage, nativeGit.getHeadCommit(NEW_BRANCH).getLogMessage());
		List<VCSDiffEntry> differences = nativeGit.getBranchesDiff(NEW_BRANCH, null);
		VCSDiffEntry difference = findDifference(differences, path);
		assertNotNull(difference);
		assertEquals(VCSChangeType.MODIFY, difference.getChangeType());
		assertTrue(difference.getUnifiedDiff().contains("+updated 内容"));
		assertEquals(commit.getRevision(), nativeGit.getHeadCommit(NEW_BRANCH).getRevision());
	}

	@Test
	public void testAnnotatedAndLightweightTagParsingWithMultilineMessage() throws Exception {
		String annotatedName = "release/native/1.0.0";
		String lightweightName = "release/native/lightweight";
		String message = "annotated subject\n\nannotated body 多行内容";
		VCSCommit commit = nativeGit.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		VCSTag annotated = nativeGit.createTag(null, annotatedName, message, commit.getRevision());

		RefUpdate lightweightUpdate = remoteGit.getRepository().updateRef(Constants.R_TAGS + lightweightName);
		lightweightUpdate.setNewObjectId(ObjectId.fromString(commit.getRevision()));
		assertEquals(RefUpdate.Result.NEW, lightweightUpdate.update());

		List<VCSTag> tags = nativeGit.getTags();
		VCSTag readAnnotated = findTag(tags, annotatedName);
		VCSTag readLightweight = findTag(tags, lightweightName);
		assertNotNull(readAnnotated);
		assertEquals(annotated, readAnnotated);
		assertEquals(message, readAnnotated.getTagMessage());
		assertNotNull(readLightweight);
		assertNull(readLightweight.getAuthor());
		assertNull(readLightweight.getTagMessage());
		assertEquals(commit.getRevision(), readLightweight.getRelatedCommit().getRevision());
		assertTrue(nativeGit.getTagsOnRevision(commit.getRevision()).containsAll(
				Arrays.asList(readAnnotated, readLightweight)));
	}

	@Test
	public void testNonSymbolicRemoteHeadIsRejectedWithoutParsingDiagnostics() throws Exception {
		File remoteDir = new File(TEST_BASE_DIR, "native-detached-head-repo");
		try (Git detachedRemote = GitVCSUtils.createRepository(remoteDir)) {
			ObjectId headCommit = detachedRemote.getRepository().resolve(Constants.HEAD);
			RefUpdate head = detachedRemote.getRepository().updateRef(Constants.HEAD, true);
			head.setNewObjectId(headCommit);
			assertEquals(RefUpdate.Result.FORCED, head.forceUpdate());
		}
		NativeGitVCS detachedHeadVcs = createNativeVCS(remoteDir, "native-detached-head-workspace", "");

		try {
			detachedHeadVcs.getHeadCommit(null);
			fail("Expected a repository with a non-symbolic HEAD to be rejected");
		} catch (EVCSException e) {
			assertTrue(e.getMessage().contains("Could not determine the default branch"));
			assertTrue(e.getMessage().contains("remote HEAD is not symbolic"));
		}
	}

	@Test
	public void testStatusCleanupHandlesNulDelimitedUnicodePath() throws Exception {
		nativeGit.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		IVCSWorkspace workspace = new VCSWorkspace(new File(TEST_BASE_DIR, "native-clean-workspace").getPath());
		IVCSRepositoryWorkspace repository = workspace.getVCSRepositoryWorkspace(repoUrl);
		NativeGitVCS adapter = new NativeGitVCS(repository);
		adapter.getHeadCommit(null);
		String untrackedPath = "untracked space/文件.txt";
		File untracked;
		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			untracked = new File(workingCopy.getFolder(), untrackedPath);
			FileUtils.write(untracked, "untracked", StandardCharsets.UTF_8);
		}

		adapter.getHeadCommit(null);

		try (IVCSLockedWorkingCopy workingCopy = repository.getVCSLockedWorkingCopy()) {
			assertFalse(new File(workingCopy.getFolder(), untrackedPath).exists());
		}
	}

	@Test
	public void testCleanupFailureCorruptsReusableWorkingCopy() throws Exception {
		nativeGit.setFileContent(null, FILE1_NAME, LINE_1, FILE1_ADDED_COMMIT_MESSAGE);
		IVCSWorkspace workspace = new VCSWorkspace(new File(TEST_BASE_DIR, "native-corrupt-workspace").getPath());
		IVCSRepositoryWorkspace realRepository = workspace.getVCSRepositoryWorkspace(repoUrl);
		IVCSRepositoryWorkspace repository = Mockito.spy(realRepository);
		TestGitCli cli = new TestGitCli();
		NativeGitVCS adapter = new NativeGitVCS(repository, "", cli);
		adapter.getHeadCommit(null);

		IVCSLockedWorkingCopy workingCopy = Mockito.spy(realRepository.getVCSLockedWorkingCopy());
		File untracked = new File(workingCopy.getFolder(), "untracked.txt");
		FileUtils.write(untracked, "untracked", StandardCharsets.UTF_8);
		Mockito.doReturn(workingCopy).when(repository).getVCSLockedWorkingCopy();
		cli.setFailCleanup(true);

		try {
			adapter.getHeadCommit(null);
			fail("Expected cleanup failure");
		} catch (EVCSException expected) {
			assertTrue(expected.getMessage().contains("test cleanup failure"));
		}
		assertTrue(workingCopy.getCorrupted());
		assertFalse(workingCopy.getFolder().exists());
		assertFalse(workingCopy.getLockFile().exists());
	}

	@Test
	public void testRootAndSparseCheckoutAtExplicitRevision() throws Exception {
		nativeGit.setFileContent(null, ROOT_FILE, "root", "root added");
		nativeGit.setFileContent(null, ANCESTOR_FILE, "ancestor", "ancestor added");
		nativeGit.setFileContent(null, SIBLING_FILE, "sibling", "sibling added");
		VCSCommit selected = nativeGit.setFileContent(null, COMPONENT_FILE, "component", "component added");
		nativeGit.setFileContent(null, LATER_COMPONENT_FILE, "later", "later component change");

		File checkout = new File(TEST_BASE_DIR, "native-sparse-checkout");
		IVCSWorkspace workspace = new VCSWorkspace(new File(TEST_BASE_DIR, "native-sparse-workspace").getPath());
		IVCSRepositoryWorkspace repository = workspace.getVCSRepositoryWorkspace(repoUrl);
		NativeGitVCS sparse = new NativeGitVCS(repository, COMPONENT);
		NativeGitVCS full = new NativeGitVCS(repository);

		sparse.checkout(null, checkout.getPath(), selected.getRevision());
		assertCheckoutFile(checkout, ROOT_FILE, true);
		assertCheckoutFile(checkout, ANCESTOR_FILE, true);
		assertCheckoutFile(checkout, COMPONENT_FILE, true);
		assertCheckoutFile(checkout, LATER_COMPONENT_FILE, false);
		assertCheckoutFile(checkout, SIBLING_FILE, false);
		assertEquals("sibling", sparse.getFileContent(null, SIBLING_FILE, null));

		full.checkout(null, checkout.getPath(), selected.getRevision());
		assertCheckoutFile(checkout, SIBLING_FILE, true);
		assertCheckoutFile(checkout, LATER_COMPONENT_FILE, false);

		sparse.checkout(null, checkout.getPath(), selected.getRevision());
		assertCheckoutFile(checkout, SIBLING_FILE, false);
		assertCheckoutFile(checkout, COMPONENT_FILE, true);
	}

	@Test
	public void testOnlyTransportCommandsAreClassifiedForRetriesAndReporterIsDelegated() {
		nativeCli.getCommands().clear();
		List<String> retriedOperations = new ArrayList<>();
		BiConsumer<String, Throwable> reporter = (operation, failure) -> retriedOperations.add(operation);
		nativeGit.setRetryStatusReporter(reporter);
		assertSame(reporter, nativeCli.getRetryStatusReporter());

		nativeGit.getHeadCommit(null);

		Set<String> networkCommands = new HashSet<>(Arrays.asList("clone", "ls-remote", "fetch", "push"));
		boolean foundNetwork = false;
		boolean foundLocal = false;
		for (TestGitCli.CommandRecord command : nativeCli.getCommands()) {
			String subcommand = firstKnownSubcommand(command.getArguments());
			if (command.isNetwork()) {
				foundNetwork = true;
				assertTrue("Local command classified for retries: " + command.getArguments(),
						networkCommands.contains(subcommand));
			} else if (!"--version".equals(subcommand)) {
				foundLocal = true;
			}
		}
		assertTrue("No transport command was observed", foundNetwork);
		assertTrue("No local command was observed", foundLocal);
		assertTrue("Successful commands unexpectedly reported retries", retriedOperations.isEmpty());
	}

	private NativeGitVCS createNativeVCS(File remoteDir, String workspaceName, String componentSubfolder) {
		IVCSWorkspace workspace = new VCSWorkspace(new File(TEST_BASE_DIR, workspaceName).getPath());
		return new NativeGitVCS(workspace.getVCSRepositoryWorkspace(remoteDir.toURI().toString()),
				componentSubfolder);
	}

	private VCSDiffEntry findDifference(List<VCSDiffEntry> differences, String path) {
		for (VCSDiffEntry difference : differences) {
			if (path.equals(difference.getFilePath())) {
				return difference;
			}
		}
		return null;
	}

	private VCSTag findTag(List<VCSTag> tags, String name) {
		for (VCSTag tag : tags) {
			if (name.equals(tag.getTagName())) {
				return tag;
			}
		}
		return null;
	}

	private void assertCheckoutFile(File checkout, String relativePath, boolean expected) {
		assertEquals(relativePath, expected, new File(checkout, relativePath).isFile());
	}

	private String firstKnownSubcommand(List<String> arguments) {
		Set<String> commands = new HashSet<>(Arrays.asList("--version", "clone", "ls-remote", "fetch", "push",
				"checkout", "reset", "clean", "sparse-checkout", "show-ref", "for-each-ref", "rev-parse",
				"cat-file", "branch", "merge", "diff", "add", "rm", "commit", "rev-list", "log", "tag",
				"status"));
		for (String argument : arguments) {
			if (commands.contains(argument)) {
				return argument;
			}
		}
		return "";
	}

}
