package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public interface IVCSWorkspace {

	IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl);

	default IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl, boolean reuseWorkingCopies) {
		if (!reuseWorkingCopies) {
			throw new UnsupportedOperationException("Non-reusable working copies are not supported");
		}
		return getVCSRepositoryWorkspace(repoUrl);
	}
	
	File getHomeFolder();

}
