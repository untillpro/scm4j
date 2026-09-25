package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	/**
	 * Creates a repository workspace for a repository root or an isolated component subfolder.
	 *
	 * @param repoUrl repository URL
	 * @param componentSubfolder normalized component path, or {@code null} or empty for the repository root
	 */
	IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl, String componentSubfolder);

	// Creates a reusable workspace for the repository root.
	default IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl) {
		return getVCSRepositoryWorkspace(repoUrl, null);
	}

	File getHomeFolder();

}
