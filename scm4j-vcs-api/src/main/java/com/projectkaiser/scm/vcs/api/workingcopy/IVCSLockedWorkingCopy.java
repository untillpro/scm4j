package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public interface IVCSLockedWorkingCopy extends AutoCloseable {

	VCSLockedWorkingCopyState getState();

	IVCSRepository getVCSRepository();

	File getFolder();
	
	Boolean getCorrupt();
	
	void setCorrupt(Boolean corrupt);
	
	File getLockFile();
	
}
