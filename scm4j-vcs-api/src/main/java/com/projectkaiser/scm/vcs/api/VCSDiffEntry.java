package com.projectkaiser.scm.vcs.api;

public class VCSDiffEntry {

	private String filePath;
	private VCSChangeType changeType;

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public VCSChangeType getChangeType() {
		return changeType;
	}

	public void setChangeType(VCSChangeType changeType) {
		this.changeType = changeType;
	}

	public VCSDiffEntry(String filePath, VCSChangeType changeType) {
		this.filePath = filePath;
		this.changeType = changeType;
	}

	@Override
	public String toString() {
		return "VCSDiffEntry [filePath=" + filePath + ", changeType=" + changeType + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((changeType == null) ? 0 : changeType.hashCode());
		result = prime * result + ((filePath == null) ? 0 : filePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VCSDiffEntry other = (VCSDiffEntry) obj;
		if (changeType != other.changeType)
			return false;
		if (filePath == null) {
			if (other.filePath != null)
				return false;
		} else if (!filePath.equals(other.filePath))
			return false;
		return true;
	}
}
