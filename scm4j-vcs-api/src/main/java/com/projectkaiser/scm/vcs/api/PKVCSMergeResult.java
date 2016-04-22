package com.projectkaiser.scm.vcs.api;

import java.util.ArrayList;
import java.util.List;

public class PKVCSMergeResult {

	private Boolean isSuccess;
	private List<String> conflictingFiles = new ArrayList<String>();

	public Boolean getIsSuccess() {
		return isSuccess;
	}

	public void setIsSuccess(Boolean isSuccess) {
		this.isSuccess = isSuccess;
	}

	public List<String> getConflictingFiles() {
		return conflictingFiles;
	}

	public void setConflictingFiles(List<String> conflictingFiles) {
		this.conflictingFiles = conflictingFiles;
	}

}
