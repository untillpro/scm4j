package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	IVCSRepository getVCSRepository(String repoUrl);
	
	File getFolder();

}
