package org.scm4j.vcs.api;

import java.util.List;

public class VCSMergeResult {

	private final Boolean success;
	private final List<String> conflictingFiles;

	public Boolean getSuccess() {
		return success;
	}

	public List<String> getConflictingFiles() {
		return conflictingFiles;
	}

	public VCSMergeResult(Boolean success, List<String> conflictingFiles) {
		this.success = success;
		this.conflictingFiles = conflictingFiles;
	}
}
