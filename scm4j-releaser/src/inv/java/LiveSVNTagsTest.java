/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.Credentials;
import org.scm4j.releaser.conf.VCSFactory;
import org.scm4j.releaser.conf.VCSRepositoryFactory;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.regexconfig.RegexConfig;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

// ./gradlew :scm4j-releaser:invTest --tests "LiveSVNTagsTest"
public class LiveSVNTagsTest {

	private static final String COMPONENT =
			"eu.untill.web-management:UntillWebManagementGui:213.0@zip";
	private static final String REPOSITORY_URL =
			"https://dev.untill.com/svn/untill/UntillWebManagementGui";
	private static final String CREDENTIALS_FILE = "c:/workspace/credentials.yml";

	@Test
	public void readsTagsOnReleaseBranchHeadCommit() {
		Component component = new Component(COMPONENT);
		String releaseBranch = "B"
				+ component.getVersion().getReleaseNoPatchString();

		RegexConfig credentialsConfig = new RegexConfig();
		credentialsConfig.loadFromYamlUrls(CREDENTIALS_FILE);
		Credentials credentials = new Credentials(
				credentialsConfig.getPropByName(REPOSITORY_URL, "name", null),
				credentialsConfig.getPropByName(REPOSITORY_URL, "password", null),
				false);

		IVCS vcs = VCSFactory.getVCS(VCSType.SVN, credentials, REPOSITORY_URL,
				new VCSWorkspace(VCSRepositoryFactory.DEFAULT_VCS_WORKSPACE_DIR));
		VCSCommit headCommit = vcs.getHeadCommit(releaseBranch);
		assertNotNull("Release branch has no head commit: " + releaseBranch, headCommit);

		List<VCSTag> tags = vcs.getTagsOnRevision(headCommit.getRevision());
		assertNotNull(tags);
	}
}
