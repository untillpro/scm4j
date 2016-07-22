package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl);
	
	File getHomeFolder();

}
