package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public class VCSWorkspace implements IVCSWorkspace {

	File folder;

	@Override
	public IVCSRepository getVCSRepository(String repoUrl) {
		return new VCSRepository(repoUrl, this);
	}

	@Override
	public File getFolder() {
		return folder;
	}

	public VCSWorkspace(String workspacePath) {
		folder = new File(workspacePath);
		folder.mkdirs();
	}
}
