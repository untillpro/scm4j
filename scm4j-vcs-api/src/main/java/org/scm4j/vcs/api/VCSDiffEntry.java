package org.scm4j.vcs.api;

public class VCSDiffEntry {

	private final String filePath;
	private final VCSChangeType changeType;
	private final String unifiedDiff;

	public String getUnifiedDiff() {
		return unifiedDiff;
	}

	public String getFilePath() {
		return filePath;
	}

	public VCSChangeType getChangeType() {
		return changeType;
	}

	public VCSDiffEntry(String filePath, VCSChangeType changeType, String unifiedDiff) {
		this.filePath = filePath;
		this.changeType = changeType;
		this.unifiedDiff = unifiedDiff;
	}

	@Override
	public String toString() {
		return "VCSDiffEntry [filePath=" + filePath + ", changeType=" + changeType + "]";
	}
}
