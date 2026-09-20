package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public interface IVCSLockedWorkingCopy extends AutoCloseable {

	VCSLockedWorkingCopyState getState();

	IVCSRepositoryWorkspace getVCSRepository();

	File getFolder();
	
	Boolean getCorrupted();
	
	void setCorrupted(Boolean corrupted);
	
	File getLockFile();
	
}
