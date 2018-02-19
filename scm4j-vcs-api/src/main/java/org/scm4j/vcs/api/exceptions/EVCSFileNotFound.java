package org.scm4j.vcs.api.exceptions;

public class EVCSFileNotFound extends EVCSException {

	private static final long serialVersionUID = 1L;

	public EVCSFileNotFound(String repoUrl, String filePath, String revision) {
		super(String.format("file %s is not found in at revision %s of repo %s", filePath, revision == null ? "HEAD" : revision, repoUrl));
	}
}
