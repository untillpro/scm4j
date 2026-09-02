package org.scm4j.vcs.api.exceptions;

public class EVCSFileNotFound extends EVCSException {

	private static final long serialVersionUID = 1L;

	public EVCSFileNotFound(String repoUrl, String branchName, String filePath, String revision) {
		super(String.format("file %s is not found in branch %s at revision %s of repo %s", filePath, branchName, revision == null ? "HEAD" : revision, repoUrl));
	}
}
