package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

/**
 * The Class VCSWorkspace.
 */
public class VCSWorkspace implements IVCSWorkspace {

	File folder;

	/* (non-Javadoc)
	 * @see com.projectkaiser.scm.vcs.api.workingcopy.IVCSWorkspace#getVCSRepositoryWorkspace(java.lang.String)
	 */
	@Override
	public IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl) {
		return new VCSRepositoryWorkspace(repoUrl, this);
	}

	@Override
	public File getHomeFolder() {
		return folder;
	}

	public VCSWorkspace(String workspacePath) {
		folder = new File(workspacePath);
		folder.mkdirs();
	}

	@Override
	public String toString() {
		return folder.toString();
	}
}
