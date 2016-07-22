package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public interface IVCSRepositoryWorkspace {

	IVCSLockedWorkingCopy getVCSLockedWorkingCopy();

	File getRepoFolder();

	IVCSWorkspace getWorkspace();
	
	String getRepoUrl();

}
