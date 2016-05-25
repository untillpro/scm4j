package com.projectkaiser.scm.vcs.api;

import java.util.ArrayList;
import java.util.List;

public class PKVCSMergeResult {

	private Boolean success;
	private List<String> conflictingFiles = new ArrayList<String>();

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public List<String> getConflictingFiles() {
		return conflictingFiles;
	}

	public void setConflictingFiles(List<String> conflictingFiles) {
		this.conflictingFiles = conflictingFiles;
	}

}
