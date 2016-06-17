package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;

public interface IVCSRepository {

	IVCSLockedWorkingCopy getVCSLockedWorkingCopy();

	File getRepoFolder();

	IVCSWorkspace getWorkspace();

}
