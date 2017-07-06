package org.scm4j.vcs.api.workingcopy;

import java.io.File;
import java.io.IOException;

public interface IVCSRepositoryWorkspace {

	IVCSLockedWorkingCopy getVCSLockedWorkingCopy() throws IOException;

	File getRepoFolder();

	IVCSWorkspace getWorkspace();
	
	String getRepoUrl();

}
