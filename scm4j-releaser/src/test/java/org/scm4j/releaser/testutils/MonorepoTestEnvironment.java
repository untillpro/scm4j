package org.scm4j.releaser.testutils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.scm4j.commons.Version;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.Utils;
import org.scm4j.releaser.builders.BuilderFactory;
import org.scm4j.releaser.conf.DefaultConfigUrls;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSRepositoryFactory;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.vcs.GitVCS;
import org.scm4j.vcs.GitVCSUtils;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
import org.scm4j.vcs.svn.SVNVCS;
import org.scm4j.vcs.svn.SVNVCSUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class MonorepoTestEnvironment implements AutoCloseable {
	public static final String PRODUCT_UNTILL = "eu.untill:unTill";
	public static final String PRODUCT_UBL = "eu.untill:UBL";
	public static final String PRODUCT_POSTGRES = "eu.untill:postgres";
	public static final String PRODUCT_SQLITE = "eu.untill:sqlite";

	public static final String POSTGRES_SUBFOLDER = "components/postgres";
	public static final String SQLITE_SUBFOLDER = "components/sqlite";

	private static final String ROOT_VERSION_SENTINEL = "99.99.99-SNAPSHOT";
	private static final String ROOT_MDEPS_SENTINEL = "# root metadata sentinel";
	private static final String FEATURE_FILE_NAME = "feature.txt";

	private final VCSType vcsType;
	private final File environmentDir;
	private final File vcsWorkspacesDir;
	private final File remoteRepositoriesDir;
	private final File ccFile;
	private final File credentialsFile;

	private final Version unTillVersion = new Version("1.123.3-SNAPSHOT");
	private final Version ublDevelopmentVersion = new Version("1.19.5-SNAPSHOT");
	private final Version ublReleaseVersion = new Version("1.18.0");
	private final Version postgresVersion = new Version("2.59.1-SNAPSHOT");
	private final Version sqliteVersion = new Version("3.4.1-SNAPSHOT");

	private IVCS unTillVCS;
	private IVCS ublVCS;
	private IVCS monorepoVCS;
	private VCSCommit ublReleaseCommit;

	public MonorepoTestEnvironment(VCSType vcsType) {
		this.vcsType = vcsType;
		environmentDir = new File(System.getProperty("java.io.tmpdir"),
				"scm4j-releaser-monorepo-test-" + UUID.randomUUID());
		vcsWorkspacesDir = new File(environmentDir, "vcs-workspaces");
		remoteRepositoriesDir = new File(environmentDir, "remote-repos");
		ccFile = new File(environmentDir, "repos");
		credentialsFile = new File(environmentDir, "credentials");
	}

	public void generate() throws Exception {
		if (!environmentDir.mkdirs()) {
			throw new IOException("failed to create test environment " + environmentDir);
		}
		if (!remoteRepositoriesDir.mkdirs()) {
			throw new IOException("failed to create remote repositories directory " + remoteRepositoriesDir);
		}

		IVCSWorkspace workspace = new VCSWorkspace(vcsWorkspacesDir.getPath());
		unTillVCS = createRepository(workspace, "unTill");
		ublVCS = createRepository(workspace, "UBL");
		monorepoVCS = createRepository(workspace, "components");

		createCredentialsFile();
		createComponentConfig();
		seedComponentsVersionAndMDeps();
		seedReleasedUBL();
	}

	private IVCS createRepository(IVCSWorkspace workspace, String name) throws Exception {
		File repositoryDir = new File(remoteRepositoriesDir, name);
		String url;
		IVCSRepositoryWorkspace repositoryWorkspace;
		switch (vcsType) {
		case GIT:
			GitVCSUtils.createRepository(repositoryDir);
			url = getRepositoryUrl(repositoryDir);
			repositoryWorkspace = workspace.getVCSRepositoryWorkspace(url);
			return new GitVCS(repositoryWorkspace);
		case SVN:
			SVNVCSUtils.createRepository(repositoryDir);
			url = getRepositoryUrl(repositoryDir);
			repositoryWorkspace = workspace.getVCSRepositoryWorkspace(url);
			SVNVCS vcs = new SVNVCS(repositoryWorkspace, null, null);
			SVNVCSUtils.createFolderStructure(vcs, "initial commit");
			return vcs;
		default:
			throw new IllegalStateException("unsupported testing VCS type: " + vcsType);
		}
	}

	private String getRepositoryUrl(File repositoryDir) throws IOException {
		String url = StringUtils.removeEndIgnoreCase(repositoryDir.toURI().toURL().toString(), "/");
		return vcsType == VCSType.SVN ? url.replaceFirst("^file:/+", "file:///") : url;
	}

	private void createCredentialsFile() throws IOException {
		if (!credentialsFile.createNewFile()) {
			throw new IOException("failed to create credentials file " + credentialsFile);
		}
	}

	private void createComponentConfig() throws IOException {
		String releaseCommand = BuilderFactory.SCM4J_BUILDER_CLASS_STRING + TestBuilder.class.getName();
		String type = vcsType.toString().toLowerCase();
		FileUtils.writeLines(ccFile, Arrays.asList(
				"!!omap",
				"- " + PRODUCT_UNTILL + ":",
				"   url: " + unTillVCS.getRepoUrl(),
				"   releaseCommand: " + releaseCommand,
				"   type: " + type,
				"   releaseBranchPrefix: " + VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX,
				"- " + PRODUCT_UBL + ":",
				"   url: " + ublVCS.getRepoUrl(),
				"   releaseCommand: " + releaseCommand,
				"   type: " + type,
				"   releaseBranchPrefix: " + VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX,
				"- " + PRODUCT_POSTGRES + ":",
				"   url: " + monorepoVCS.getRepoUrl(),
				"   subfolder: " + POSTGRES_SUBFOLDER,
				"   releaseCommand: " + releaseCommand,
				"   type: " + type,
				"   releaseBranchPrefix: " + VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX,
				"- " + PRODUCT_SQLITE + ":",
				"   url: " + monorepoVCS.getRepoUrl(),
				"   subfolder: " + SQLITE_SUBFOLDER,
				"   releaseCommand: " + releaseCommand,
				"   type: " + type,
				"   releaseBranchPrefix: " + VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX));
	}

	private void seedComponentsVersionAndMDeps() {
		unTillVCS.setFileContent(null, Constants.VER_FILE_NAME, unTillVersion.toString(),
				Constants.SCM_IGNORE + " unTill version file added");
		unTillVCS.setFileContent(null, Constants.MDEPS_FILE_NAME,
				PRODUCT_UBL + ":" + ublReleaseVersion + "\r\n"
						+ PRODUCT_POSTGRES + ":" + postgresVersion + "\r\n",
				Constants.SCM_IGNORE + " unTill mdeps file added");

		monorepoVCS.setFileContent(null, componentPath(POSTGRES_SUBFOLDER, Constants.VER_FILE_NAME),
				postgresVersion.toString(), Constants.SCM_IGNORE + " postgres version file added");
		monorepoVCS.setFileContent(null, componentPath(SQLITE_SUBFOLDER, Constants.VER_FILE_NAME),
				sqliteVersion.toString(), Constants.SCM_IGNORE + " sqlite version file added");
		// Distinguishable root metadata makes accidental root reads or writes fail component assertions.
		monorepoVCS.setFileContent(null, Constants.VER_FILE_NAME, ROOT_VERSION_SENTINEL,
				Constants.SCM_IGNORE + " root version sentinel added");
		monorepoVCS.setFileContent(null, Constants.MDEPS_FILE_NAME, ROOT_MDEPS_SENTINEL,
				Constants.SCM_IGNORE + " root mdeps sentinel added");
	}

	private void seedReleasedUBL() {
		ublVCS.setFileContent(null, Constants.VER_FILE_NAME, ublDevelopmentVersion.toString(),
				Constants.SCM_VER + " " + ublDevelopmentVersion);
		String releaseBranch = VCSRepository.DEFAULT_RELEASE_BRANCH_PREFIX
				+ ublReleaseVersion.getReleaseNoPatchString();
		ublVCS.createBranch(null, releaseBranch, "existing UBL release branch created");
		ublReleaseCommit = ublVCS.setFileContent(releaseBranch, Constants.VER_FILE_NAME,
				ublReleaseVersion.toString(), Constants.SCM_VER + " " + ublReleaseVersion);
		TagDesc tag = Utils.getTagDesc(ublReleaseVersion.toString());
		ublVCS.createTag(releaseBranch, tag.getName(), tag.getMessage(), ublReleaseCommit.getRevision());
		Version nextPatch = ublReleaseVersion.toNextPatch();
		ublVCS.setFileContent(releaseBranch, Constants.VER_FILE_NAME, nextPatch.toString(),
				Constants.SCM_VER + " " + nextPatch);
	}

	private String componentPath(String subfolder, String relativePath) {
		return subfolder + "/" + relativePath;
	}

	public VCSCommit generateComponentCommit(String branchName, String subfolder, String message) {
		return monorepoVCS.setFileContent(branchName, componentPath(subfolder, FEATURE_FILE_NAME),
				"feature content " + UUID.randomUUID(), message);
	}

	public VCSRepositoryFactory getRepositoryFactory() {
		VCSRepositoryFactory factory = new VCSRepositoryFactory();
		factory.load(new DefaultConfigUrls());
		return factory;
	}

	public VCSType getVcsType() {
		return vcsType;
	}

	public File getCcFile() {
		return ccFile;
	}

	public File getCredentialsFile() {
		return credentialsFile;
	}

	public IVCS getUnTillVCS() {
		return unTillVCS;
	}

	public IVCS getUblVCS() {
		return ublVCS;
	}

	public IVCS getMonorepoVCS() {
		return monorepoVCS;
	}

	public Version getUnTillVersion() {
		return unTillVersion;
	}

	public Version getUblDevelopmentVersion() {
		return ublDevelopmentVersion;
	}

	public Version getUblReleaseVersion() {
		return ublReleaseVersion;
	}

	public Version getPostgresVersion() {
		return postgresVersion;
	}

	public Version getSqliteVersion() {
		return sqliteVersion;
	}

	public VCSCommit getUblReleaseCommit() {
		return ublReleaseCommit;
	}

	@Override
	public void close() throws Exception {
		if (environmentDir.exists()) {
			Utils.waitForDeleteDir(environmentDir);
		}
	}
}
