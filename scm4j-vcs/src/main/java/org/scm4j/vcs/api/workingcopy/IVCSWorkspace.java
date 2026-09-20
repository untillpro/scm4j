package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl);
	
	File getHomeFolder();

}
