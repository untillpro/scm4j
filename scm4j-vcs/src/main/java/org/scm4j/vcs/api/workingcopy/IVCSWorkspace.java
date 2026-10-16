package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	/**
	 * Creates a component-scoped view of a repository workspace.
	 * <p>
	 * The component subfolder is logical scope used by VCS operations. It does not identify a
	 * separate physical clone.
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
