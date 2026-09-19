package org.scm4j.releaser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranchCurrent;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
import org.scm4j.releaser.cli.CLI;
import org.scm4j.releaser.cli.CLICommand;
import org.scm4j.releaser.cli.Option;
import org.scm4j.releaser.conf.*;
import org.scm4j.releaser.scmactions.SCMActionRelease;
import org.scm4j.releaser.scmactions.SCMActionTag;
import org.scm4j.releaser.testutils.MonorepoTestRepositories;
import org.scm4j.releaser.testutils.TestBuilder;
import org.scm4j.releaser.testutils.TestEnvironment;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Creates fresh repositories before each test and deletes them afterward.
 * Tests run on Git by default; SCM4J_WORKFLOW_TEST_ALL_VCS=true adds SVN.
 * Monorepo tests call super(WorkflowEnvironment.MONOREPO) and use the prepared component and repository fields
 * directly in ordinary @Test methods. No additional setup or runner callback is needed for a new test.
 */
@RunWith(Parameterized.class)
public abstract class WorkflowTestBase {
	static final String ALL_VCS_ENV_VAR = "SCM4J_WORKFLOW_TEST_ALL_VCS";
	// unreachable version and mdeps for tests that use monorepo repositories
	// stored in the root of the monorepo to make test fail on try to read version or mdeps from the root, not from the subfolder
	protected static final String MONOREPO_ROOT_UNREACHABLE_VERSION = "99.99.99-SNAPSHOT";
	protected static final String MONOREPO_ROOT_UNREACHABLE_MDEPS = "# root metadata sentinel";
	protected TestEnvironment env;
	protected static final String UNTILL = TestEnvironment.PRODUCT_UNTILL;
	protected static final String UNTILLDB = TestEnvironment.PRODUCT_UNTILLDB;
	protected static final String UBL = TestEnvironment.PRODUCT_UBL;
	protected Component compUnTill;
	protected Component compUnTillDb;
	protected Component compUBL;
	protected VCSRepository repoUnTill;
	protected VCSRepository repoUnTillDb;
	protected VCSRepository repoUBL;
	protected VCSRepositoryFactory repoFactory;
	protected MonorepoTestRepositories monorepoRepositories;
	protected Component compPostgres;
	protected Component compSqlite;
	protected VCSRepository repoPostgres;
	protected VCSRepository repoSqlite;
	private final WorkflowEnvironment workflowEnvironment;
	private final Map<String, StandardComponentContext> standardComponentContexts = new HashMap<>();

	@Parameterized.Parameter
	public VCSType testingVcsType;

	@Parameterized.Parameters(name = "{0}")
	public static Iterable<Object[]> workflowVcsParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (VCSType vcsType : selectVcsTypes(System.getenv(ALL_VCS_ENV_VAR))) {
			parameters.add(new Object[] {vcsType});
		}
		return parameters;
	}

	static List<VCSType> selectVcsTypes(String runAllVcs) {
		return Boolean.parseBoolean(runAllVcs)
				? Arrays.asList(VCSType.GIT, VCSType.SVN)
				: Collections.singletonList(VCSType.GIT);
	}

	protected enum WorkflowEnvironment {
		STANDARD,
		MONOREPO
	}

	protected WorkflowTestBase() {
		this(WorkflowEnvironment.STANDARD);
	}

	protected WorkflowTestBase(WorkflowEnvironment workflowEnvironment) {
		this.workflowEnvironment = workflowEnvironment;
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Before
	public void setUp() throws Exception {
		if (workflowEnvironment == WorkflowEnvironment.MONOREPO) {
			setUpMonorepoRepositories();
		} else {
			setUpStandardEnvironment();
		}
		cleanupReleases();
	}

	private void setUpStandardEnvironment() throws Exception {
		env = new TestEnvironment(testingVcsType);
		env.generateTestEnvironment();
		repoFactory = env.getRepoFactory();
		compUnTill = new Component(UNTILL);
		compUnTillDb = new Component(UNTILLDB);
		compUBL = new Component(UBL);
		refreshStandardRepositories();
	}

	private void setUpMonorepoRepositories() throws Exception {
		monorepoRepositories = new MonorepoTestRepositories(testingVcsType);
		monorepoRepositories.generate();
		configureMonorepoEnvironment();
		repoFactory = monorepoRepositories.getRepositoryFactory();
		compUnTill = new Component(MonorepoTestRepositories.PRODUCT_UNTILL);
		compUBL = new Component(MonorepoTestRepositories.PRODUCT_UBL);
		compPostgres = new Component(MonorepoTestRepositories.PRODUCT_POSTGRES);
		compSqlite = new Component(MonorepoTestRepositories.PRODUCT_SQLITE);
		repoUnTill = repoFactory.getVCSRepository(compUnTill);
		repoUBL = repoFactory.getVCSRepository(compUBL);
		repoPostgres = repoFactory.getVCSRepository(compPostgres);
		repoSqlite = repoFactory.getVCSRepository(compSqlite);

		// Two independent components must share one physical repository.
		assertEquals(repoPostgres.getUrl(), repoSqlite.getUrl());
		assertNotEquals(repoPostgres.getComponentLocation(), repoSqlite.getComponentLocation());
	}

	private void refreshStandardRepositories() {
		repoUnTill = repoFactory.getVCSRepository(compUnTill);
		repoUnTillDb = repoFactory.getVCSRepository(compUnTillDb);
		repoUBL = repoFactory.getVCSRepository(compUBL);
		standardComponentContexts.clear();
		standardComponentContexts.put(compUnTill.getName(), new StandardComponentContext(
				StandardComponentRole.UNTILL, env.getUnTillVCS(), repoUnTill, env.getUnTillVer()));
		standardComponentContexts.put(compUBL.getName(), new StandardComponentContext(
				StandardComponentRole.UBL, env.getUblVCS(), repoUBL, env.getUblVer()));
		standardComponentContexts.put(compUnTillDb.getName(), new StandardComponentContext(
				StandardComponentRole.UNTILL_DB, env.getUnTillDbVCS(), repoUnTillDb, env.getUnTillDbVer()));
	}

	@SuppressWarnings("unchecked")
	protected void configureRepositorySubfolder(String subfolder) throws IOException {
		Yaml yaml = new Yaml();
		Map<String, ?> content = (Map<String, ?>) yaml.load(
				FileUtils.readFileToString(env.getCcFile(), StandardCharsets.UTF_8));
		Map<String, Object> repositoryConfig = (Map<String, Object>) content.get("eu.untill:(.*)");
		repositoryConfig.put("subfolder", subfolder);
		FileUtils.writeStringToFile(env.getCcFile(), yaml.dumpAsMap(content), StandardCharsets.UTF_8);

		repoFactory = env.getRepoFactory();
		refreshStandardRepositories();
		writeComponentFiles(repoUnTill, env.getUnTillVer());
		writeComponentFiles(repoUnTillDb, env.getUnTillDbVer());
		writeComponentFiles(repoUBL, env.getUblVer());

		// write version and mdeps file to the monorepo root
		// if he code is broken and it reads mdeps or version from the monorepo root, not from a subfolder,
		// then we're expecting that a corresponding test will because root files contains unexpected content
		protectMonorepoFromRootRead(repoUnTill, repoUnTillDb, repoUBL);
	}

	private void writeComponentFiles(VCSRepository repo, Version version) {

		String rootMDeps = repo.getVCS().fileExists(repo.getDevelopBranch(), Constants.MDEPS_FILE_NAME)

				? repo.getVCS().getFileContent(repo.getDevelopBranch(), Constants.MDEPS_FILE_NAME, null)

				: null;

		repo.getVCS().setFileContent(repo.getDevelopBranch(), repo.getComponentPath(Constants.VER_FILE_NAME), version.toString(),

				Constants.SCM_IGNORE + " component version file added");
		if (rootMDeps != null) {
			repo.getVCS().setFileContent(repo.getDevelopBranch(), repo.getComponentPath(Constants.MDEPS_FILE_NAME),

					rootMDeps, Constants.SCM_IGNORE + " component mdeps file added");
		}
	}

	private void protectMonorepoFromRootRead(VCSRepository... repositories) {
		for (VCSRepository repo : repositories) {
			repo.getVCS().setFileContent(repo.getDevelopBranch(), Constants.VER_FILE_NAME,
					MONOREPO_ROOT_UNREACHABLE_VERSION, Constants.SCM_IGNORE + " root version sentinel added");
			if (repo.getVCS().fileExists(repo.getDevelopBranch(), Constants.MDEPS_FILE_NAME)) {
				repo.getVCS().setFileContent(repo.getDevelopBranch(), Constants.MDEPS_FILE_NAME,
						MONOREPO_ROOT_UNREACHABLE_MDEPS, Constants.SCM_IGNORE + " root mdeps sentinel added");
			}
		}
	}

	protected String getComponentFileContent(VCSRepository repo, String branchName, String relativePath) {

		return repo.getVCS().getFileContent(branchName, repo.getComponentPath(relativePath), null);

	}

	@After
	public void tearDown() throws Exception {
		try {
			closeWorkflowEnvironment();
		} finally {
			TestBuilder.setBuilders(null);
			clearReleaseState();
		}
	}

	private void closeWorkflowEnvironment() throws Exception {
		if (workflowEnvironment == WorkflowEnvironment.MONOREPO) {
			closeMonorepoRepositories();
			return;
		}
		try {
			assertConfiguredRepositoryRootsUnchanged();
		} finally {
			if (env != null) {
				env.close();
			}
		}
	}

	private void assertConfiguredRepositoryRootsUnchanged() {
		for (VCSRepository repo : new VCSRepository[] {repoUnTill, repoUnTillDb, repoUBL}) {

			if (repo == null || repo.getSubfolder().isEmpty()) {

				continue;

			}

			boolean hasRootMDeps = repo.getVCS().fileExists(repo.getDevelopBranch(), Constants.MDEPS_FILE_NAME);

			assertConfiguredRepositoryRootUnchanged(repo, repo.getDevelopBranch(), hasRootMDeps);
			boolean releaseNamespaceExists = repo.getType() != VCSType.SVN
					|| repo.getVCS().getBranches(null).contains(repo.getName());
			if (releaseNamespaceExists) {
				String releaseBranchPrefix = repo.getName() + "/" + repo.getReleaseBranchPrefix();
				for (String branchName : repo.getVCS().getBranches(releaseBranchPrefix)) {
					assertConfiguredRepositoryRootUnchanged(repo, branchName, hasRootMDeps);
				}

			}

		}

	}

	private void assertConfiguredRepositoryRootUnchanged(VCSRepository repo, String branchName,
			boolean hasRootMDeps) {
		assertEquals(MONOREPO_ROOT_UNREACHABLE_VERSION,
				repo.getVCS().getFileContent(branchName, Constants.VER_FILE_NAME, null));
		if (hasRootMDeps) {
			assertEquals(MONOREPO_ROOT_UNREACHABLE_MDEPS,
					repo.getVCS().getFileContent(branchName, Constants.MDEPS_FILE_NAME, null));
		}
	}

	private void closeMonorepoRepositories() throws Exception {
		if (monorepoRepositories == null) {
			return;
		}
		try {
			// Check sentinels before deleting the repositories so a component operation cannot silently touch root metadata.
			assertDisposableMonorepoRootFilesUnchanged();
		} finally {
			try {
				monorepoRepositories.close();
			} finally {
				monorepoRepositories = null;
			}
		}
	}

	private void assertDisposableMonorepoRootFilesUnchanged() {
		// A failed setup still needs cleanup, but may not have seeded the root files yet.
		if (repoPostgres == null || repoSqlite == null) {
			return;
		}
		IVCS vcs = monorepoRepositories.getMonorepoVCS();
		// Component-local operations must leave root metadata untouched on develop and every created release branch.
		assertDisposableMonorepoRootFilesUnchanged(vcs, repoPostgres.getDevelopBranch());
		Set<String> svnBranchNamespaces = monorepoRepositories.getVcsType() == VCSType.SVN
				? vcs.getBranches(null) : null;
		for (VCSRepository repository : new VCSRepository[] {repoPostgres, repoSqlite}) {
			String releaseBranchPrefix = repository.getName() + "/" + repository.getReleaseBranchPrefix();
			boolean releaseNamespaceExists = svnBranchNamespaces == null
					|| svnBranchNamespaces.contains(repository.getName());
			if (releaseNamespaceExists) {
				for (String branchName : vcs.getBranches(releaseBranchPrefix)) {
					assertDisposableMonorepoRootFilesUnchanged(vcs, branchName);
				}
			}
		}
	}

	private void assertDisposableMonorepoRootFilesUnchanged(IVCS vcs, String branchName) {
		assertEquals(MONOREPO_ROOT_UNREACHABLE_VERSION,
				vcs.getFileContent(branchName, Constants.VER_FILE_NAME, null));
		assertEquals(MONOREPO_ROOT_UNREACHABLE_MDEPS,
				vcs.getFileContent(branchName, Constants.MDEPS_FILE_NAME, null));
	}

	@SuppressWarnings("deprecation")
	private void configureMonorepoEnvironment() {
		// Each CLI invocation reloads configuration from environment variables, so point it at the fixture files.
		environmentVariables.set(DefaultConfigUrls.REPOS_LOCATION_ENV_VAR, null);
		environmentVariables.set(DefaultConfigUrls.CC_URLS_ENV_VAR, monorepoRepositories.getCcFile().toString());
		environmentVariables.set(DefaultConfigUrls.CREDENTIALS_URL_ENV_VAR,
				monorepoRepositories.getCredentialsFile().toString());
	}

	private void cleanupReleases() throws Exception {
		// Builders, their captured environment, delayed tags, and checkout folders are process-wide test state.
		resetBuilders();
		clearReleaseState();
	}

	private void resetBuilders() {
		TestBuilder.setBuilders(new HashMap<>());
	}

	private void clearReleaseState() throws Exception {
		TestBuilder.getEnvVars().clear();
		new DelayedTagsFile().delete();
		Utils.waitForDeleteDir(Constants.RELEASES_DIR);
	}

	protected VCSCommit latestComponentCommit(VCSRepository repository, String branchName) {
		// This is the revision scm4j should select when it treats a component subfolder as a logical repository.
		List<VCSCommit> commits = repository.getVCS().getCommitsRange(branchName, null,
				WalkDirection.DESC, 1, repository.getSubfolder());
		assertFalse(commits.isEmpty());
		return commits.get(0);
	}

	protected void assertBuildRevision(Component component, VCSRepository repository, Version releaseVersion,
			VCSCommit expectedCommit) {
		// TestBuilder captures the real build boundary's VCS variables, including the revision actually checked out.
		Map<String, String> actual = TestBuilder.getEnvVars().get(component.getName());
		assertNotNull(actual);
		assertEquals(Utils.getBuildTimeEnvVars(repository.getType(), expectedCommit.getRevision(),
				Utils.getReleaseBranchName(repository, releaseVersion), repository.getUrl()), actual);
	}

	protected void assertTagRevision(VCSRepository repository, Version version, VCSCommit expectedCommit) {
		VCSTag tag = findTag(repository, version);
		assertEquals(expectedCommit.getRevision(), tag.getRelatedCommit().getRevision());
	}

	protected void assertTagExists(VCSRepository repository, Version version) {
		assertNotNull(findTag(repository, version));
	}

	protected VCSTag findTag(VCSRepository repository, Version version) {
		String tagName = Utils.getTagDesc(repository, version.toString()).getName();
		return repository.getVCS().getTags().stream()
				.filter(tag -> tagName.equals(tag.getTagName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"missing tag " + tagName + " for " + repository.getComponentLocation()));
	}

	protected List<VCSTag> componentTags(VCSRepository repository) {
		// The VCS returns every tag in the shared repository; filter to this component's namespace.
		return repository.getVCS().getTags().stream()
				.filter(tag -> Utils.isTagForRepository(repository, tag.getTagName()))
				.collect(Collectors.toList());
	}

	private enum StandardComponentRole {
		UNTILL,
		UBL,
		UNTILL_DB
	}

	private static class StandardComponentContext {

		final StandardComponentRole role;
		final IVCS vcs;
		final VCSRepository repository;
		final Version initialVersion;

		private StandardComponentContext(StandardComponentRole role, IVCS vcs,
				VCSRepository repository, Version initialVersion) {
			this.role = role;
			this.vcs = vcs;
			this.repository = repository;
			this.initialVersion = initialVersion;
		}
	}

	private StandardComponentContext standardComponentContext(Component component) {
		StandardComponentContext context = standardComponentContexts.get(component.getName());
		if (context == null) {
			throw new AssertionError("unexpected component: " + component);
		}
		return context;
	}

	protected Version getCrbVersion(Component comp) {
		VCSRepository repo = repoFactory.getVCSRepository(comp);
		Version crbFirstVersion = Utils.getDevVersion(repo).toPreviousMinor().toReleaseZeroPatch();
		return new Version(getComponentFileContent(repo, Utils.getReleaseBranchName(repo, crbFirstVersion),

				Constants.VER_FILE_NAME));

	}


	protected void checkCompBuilt(int times, Component comp) {
		checkCompBuilt(times, comp, standardComponentContext(comp));
	}

	private void checkCompBuilt(int times, Component comp, StandardComponentContext context) {
		checkCompForked(times, comp, context);
		VCSRepository repository = context.repository;
		ReleaseBranchCurrent crb = ReleaseBranchFactory.getCRB(repository);
		Version latestVersion = crb.getVersion();

		assertNotNull(TestBuilder.getBuilders().get(comp.getName()));
		assertTrue(Utils.getBuildDir(repository, latestVersion).exists());

		DelayedTagsFile dtf = new DelayedTagsFile();
		DelayedTag dt = dtf.getDelayedTag(repository.getComponentLocation());
		boolean tagDelayed = dt != null
				&& crb.getName().equals(Utils.getReleaseBranchName(repository, dt.getVersion()));
		String expectedPatch = tagDelayed ? "0" : "1";

		assertEquals(expectedPatch, latestVersion.getPatch());

		// check tags
		List<VCSTag> tags = componentTags(repository);
		assertEquals(tagDelayed ? times - 1 : times, tags.size());

		// check has tags for each built version
		Version expectedCompReleaseVer = context.initialVersion.toReleaseZeroPatch().toPreviousMinor();
		for (int i = 0; i < times; i++) {
			expectedCompReleaseVer = expectedCompReleaseVer.toNextMinor();
			if (!tagDelayed) {
				assertTrue(hasTagForVersion(repository, tags, expectedCompReleaseVer));
			}
		}

		// check if the pre-last commit of each release branch is tagged
		for (VCSTag tag : tags) {
			List<VCSCommit> commits = context.vcs.getCommitsRange(Utils.getReleaseBranchName(
					repository, getVersionFromTagName(tag.getTagName())), null, WalkDirection.DESC, 2);
			assertEquals(commits.get(1), tag.getRelatedCommit());
		}

		// check Env Vars
		String latestReleaseBranchName = Utils.getReleaseBranchName(repository, latestVersion);
		List<VCSCommit> lastCommits = context.vcs.getCommitsRange(
				latestReleaseBranchName, null, WalkDirection.DESC, 2);
		String buildRevision = lastCommits.get(tagDelayed ? 0 : 1).getRevision();
		Map<String, String> actualBuildEnvironment = TestBuilder.getEnvVars().get(comp.getName());
		assertNotNull(actualBuildEnvironment);
		Map<String, String> expectedBuildEnvironment = Utils.getBuildTimeEnvVars(
				repository.getType(), buildRevision, latestReleaseBranchName, repository.getUrl());
		assertEquals(expectedBuildEnvironment, actualBuildEnvironment);
	}

	public void checkUnTillDbBuilt(int times) {
		checkCompBuilt(times, compUnTillDb);
	}

	protected void checkUBLBuilt(int times) {
		checkUnTillDbBuilt(times);
		checkCompBuilt(times, compUBL);
		checkUBLMDepsVersions(times);
	}

	public void checkUnTillBuilt(int times) {
		checkUBLBuilt(times);
		checkCompBuilt(times, compUnTill);
		checkUnTillMDepsVersions(times);
	}

	protected void checkUBLMDepsVersions(int times) {
		Version latestVersion = getCrbVersion(compUBL);
		List<Component> ublReleaseMDeps = ReleaseBranchFactory.getMDepsRelease(
				Utils.getReleaseBranchName(repoUBL, latestVersion), repoUBL);
		assertEquals(1, ublReleaseMDeps.size());
		assertEquals(compUnTillDb.getName(), ublReleaseMDeps.get(0).getName());
		assertTrue(ublReleaseMDeps.get(0).getVersion().isLocked());
		checkCompMinorVersions(times, ublReleaseMDeps.get(0).getVersion(), env.getUnTillDbVer(), repoUnTillDb);
	}

	protected void checkUnTillMDepsVersions(int times) {
		Version latestVersion = getCrbVersion(compUnTill);
		List<Component> untillReleaseMDeps = ReleaseBranchFactory.getMDepsRelease(
				Utils.getReleaseBranchName(repoUnTill, latestVersion), repoUnTill);
		assertEquals(2, untillReleaseMDeps.size());
		assertEquals(compUnTillDb.getName(), untillReleaseMDeps.get(1).getName());
		assertTrue(untillReleaseMDeps.get(1).getVersion().isLocked());
		checkCompMinorVersions(times, untillReleaseMDeps.get(1).getVersion(), env.getUnTillDbVer(), repoUnTillDb);

		assertEquals(compUBL.getName(), untillReleaseMDeps.get(0).getName());
		assertTrue(untillReleaseMDeps.get(0).getVersion().isLocked());
		checkCompMinorVersions(times, untillReleaseMDeps.get(0).getVersion(), env.getUblVer(), repoUBL);
	}

	private void checkCompMinorVersions(int times, Version actualVersion, Version initialVersion, VCSRepository repo) {
		Version expectedVer = initialVersion.toReleaseZeroPatch().toPreviousMinor();
		for (int i = 0; i < times; i++) {
			expectedVer = expectedVer.toNextMinor();
		}
		assertEquals(expectedVer, actualVersion.toReleaseZeroPatch());
		Version expectedDevVer = expectedVer.toNextMinor().setPatch(initialVersion.getPatch()).toSnapshot();
		assertEquals(expectedDevVer, Utils.getDevVersion(repo));
	}

	private boolean hasTagForVersion(VCSRepository repo, List<VCSTag> tags, Version expectedUBLReleaseVer) {
		String expectedTagName = Utils.getTagDesc(repo, expectedUBLReleaseVer.toString()).getName();
		return tags.stream().anyMatch(tag -> expectedTagName.equals(tag.getTagName()));
	}

	private Version getVersionFromTagName(String tagName) {
		return new Version(tagName.substring(tagName.lastIndexOf('/') + 1));
	}

	protected void checkUBLBuilt() {
		checkUBLBuilt(1);
	}

	protected void checkUBLForked() {
		checkUBLForked(1);
	}

	protected void checkCompForked(int times, Component comp) {
		checkCompForked(times, comp, standardComponentContext(comp));
	}

	private void checkCompForked(int times, Component comp, StandardComponentContext context) {
		Version latestVersion = getCrbVersion(comp);
		String releaseBranchName = Utils.getReleaseBranchName(context.repository, latestVersion);
		assertTrue(context.repository.getVCS().getBranches(releaseBranchName).contains(releaseBranchName));
		checkCompMinorVersions(times, latestVersion, context.initialVersion, context.repository);
	}

	public void checkUBLForked(int times) {
		checkUnTillDbForked(times);
		checkCompForked(times, compUBL);
		checkUBLMDepsVersions(times);
	}

	public void checkUnTillDbForked(int times) {
		checkCompForked(times, compUnTillDb);
	}

	public void checkUnTillDbForked() {
		checkUnTillDbForked(1);
	}

	public void checkUnTillOnlyForked(int times) {
		checkCompForked(times, compUnTill);
		Version latestVersion = getCrbVersion(compUnTill);
		checkCompMinorVersions(times, latestVersion, env.getUnTillVer(), repoUnTill);
		checkUnTillMDepsVersions(times - 1);
	}

	public void checkUnTillForked(int times) {
		checkUBLForked(times);
		checkCompForked(times, compUnTill);
	}

	private IAction findActionByComp(IAction action, Component comp) {
		for (IAction nestedAction : action.getChildActions()) {
			IAction result = findActionByComp(nestedAction, comp);
			if (result != null) {
				return result;
			}
		}
		if (action.getComp().getName().equals(comp.getName())) {
			return action;
		}
		return null;
	}

	private IAction getActionByComp(IAction action, Component comp) {
		IAction result = findActionByComp(action, comp);
		if (result == null) {
			throw new AssertionError("No action for " + comp);
		}
		return result;
	}

	protected void assertThatAction(IAction action, Matcher<? super IAction> matcher, Component... comps) {
		for (Component comp : comps) {
			IAction actionForComp = getActionByComp(action, comp);
			Assert.assertThat("action for " + comp, actionForComp, matcher);
		}
	}

	protected void assertActionDoesForkAll(IAction action) {
		assertActionDoesFork(action, getAllComps());
	}

	protected void assertActionDoesFork(IAction action, Component... comps) {
		assertThatAction(action, allOf(
				instanceOf(SCMActionRelease.class),
				hasProperty("bsFrom", equalTo(BuildStatus.FORK)),
				hasProperty("bsTo", equalTo(BuildStatus.LOCK))), comps);
	}

	protected void assertActionDoesBuild(IAction action, Component comp, BuildStatus fromStatus) {
		assertThatAction(action, getBuildMatcher(fromStatus, BuildStatus.BUILD, false), comp);
	}

	protected void assertActionDoesBuild(IAction action, Component... comps) {
		assertThatAction(action, getBuildMatcher(BuildStatus.BUILD, BuildStatus.BUILD, false), comps);
	}

	protected void assertActionDoesNothing(IAction action, BuildStatus bsFrom, BuildStatus bsTo, Component... comps) {
		assertThatAction(action, allOf(
				getBuildMatcher(bsFrom, bsTo, false),
				hasProperty("procs", empty())), comps);
	}

	protected void assertActionDoesBuildDelayedTag(IAction action, Component comp, BuildStatus fromStatus) {
		assertThatAction(action, getBuildMatcher(fromStatus, BuildStatus.BUILD, true), comp);
	}

	protected void assertActionDoesBuildDelayedTag(IAction action, Component comp) {
		assertThatAction(action, getBuildMatcher(BuildStatus.BUILD, BuildStatus.BUILD, true), comp);
	}

	private Matcher<IAction> getBuildMatcher(BuildStatus bsFrom, BuildStatus bsTo, boolean delayedTag) {
		return allOf(
				instanceOf(SCMActionRelease.class),
				hasProperty("bsFrom", equalTo(bsFrom)),
				hasProperty("bsTo", equalTo(bsTo)),
				hasProperty("delayedTag", equalTo(delayedTag)));
	}

	private Component[] getAllComps() {
		return new Component[] {compUBL, compUnTillDb, compUnTill};
	}

	protected void assertActionDoesNothing(IAction action, Component... comps) {
		assertActionDoesNothing(action, BuildStatus.DONE, null, comps);
	}

	protected void assertActionDoesTag(IAction action, Component comp) {
		assertThatAction(action, allOf(
				instanceOf(SCMActionTag.class),
				hasProperty("childActions", empty())), comp);
	}

	protected void assertActionDoesBuildAll(IAction action) {
		assertActionDoesBuild(action, compUnTillDb, BuildStatus.BUILD);
		assertActionDoesBuild(action, compUnTill, BuildStatus.BUILD_MDEPS);
		assertActionDoesBuild(action, compUBL, BuildStatus.BUILD_MDEPS);
	}

	protected void assertActionDoesBuildAllDelayedTag(IAction action) {
		assertActionDoesBuild(action, compUnTillDb, BuildStatus.BUILD);
		assertActionDoesBuildDelayedTag(action, compUnTill, BuildStatus.BUILD_MDEPS);
		assertActionDoesBuild(action, compUBL, BuildStatus.BUILD_MDEPS);
	}

	protected IAction execAndGetActionFork(Component comp) {
		return execAndGetAction(CLICommand.FORK.getCmdLineStr(), comp.getCoords().toString());
	}

	protected IAction execAndGetActionBuild(Component comp) {
		return execAndGetAction(CLICommand.BUILD.getCmdLineStr(), comp.getCoords().toString());
	}

	private CLI execAndGetCLI(Runnable preExec, String... args) {
		CLI cli = new CLI();
		cli.setPreExec(preExec);
		System.out.println("Command line: " + StringUtils.join(args, " "));
		if (cli.exec(args) != CLI.EXIT_CODE_OK) {
			throw cli.getLastException();
		}
		return cli;
	}

	private IAction execAndGetAction(Runnable preExec, String... args)  {
		CLI cli = execAndGetCLI(preExec, args);
		IAction action = cli.getAction();
		if (action != null) {
			action.toString(); // cover
		}
		return action;
	}

	protected ExtendedStatus execAndGetNode(Runnable preExec, String... args) {
		CLI cli = execAndGetCLI(preExec, args);
		return cli.getNode();
	}

	private IAction execAndGetAction(String... args) {
		return execAndGetAction(null, args);
	}

	protected IAction execAndGetActionTag(Component comp, Runnable preExec) {
		return execAndGetAction(preExec, CLICommand.TAG.getCmdLineStr(), comp.getCoords().toString());
	}

	protected IAction execAndGetActionBuildDelayedTag(Component comp) {
		return execAndGetAction(CLICommand.BUILD.getCmdLineStr(), comp.getCoords().toString(), Option.DELAYED_TAG.getCmdLineStr());
	}

	protected void status(Component comp) {
		execAndGetAction(CLICommand.STATUS.getCmdLineStr(), comp.getCoords().toString());
	}

	protected void forkAndBuild(Component comp) {
		forkAndBuild(comp, 1);
	}

	protected void fork(Component comp) {
		fork(comp, 1);
	}

	protected void build(Component comp) {
		build(comp, 1);
	}

	protected void fork(Component comp, int times) {
		IAction action = execAndGetActionFork(comp);
		switch (standardComponentContext(comp).role) {
		case UNTILL:
			assertActionDoesForkAll(action);
			checkUnTillForked(times);
			break;
		case UBL:
			assertActionDoesFork(action, compUBL, compUnTillDb);
			checkUBLForked(times);
			break;
		case UNTILL_DB:
			assertActionDoesFork(action, compUnTillDb);
			checkUnTillDbForked(times);
			break;
		default:
			throw new AssertionError("unexpected component: " + comp);
		}
	}

	protected void build(Component comp, int times) {
		IAction action = execAndGetActionBuild(comp);
		switch (standardComponentContext(comp).role) {
		case UNTILL:
			assertActionDoesBuildAll(action);
			checkUnTillBuilt(times);
			break;
		case UBL:
			assertActionDoesBuild(action, compUBL, BuildStatus.BUILD_MDEPS);
			assertActionDoesBuild(action, compUnTillDb);
			checkUBLBuilt(times);
			break;
		case UNTILL_DB:
			assertActionDoesBuild(action, compUnTillDb);
			checkUnTillDbBuilt(times);
			break;
		default:
			throw new AssertionError("unexpected component: " + comp);
		}
	}

	protected void forkAndBuild(Component comp, int times) {
		fork(comp, times);
		build(comp, times);
	}
}
