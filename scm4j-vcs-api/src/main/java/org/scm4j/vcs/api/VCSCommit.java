package org.scm4j.vcs.api;

public class VCSCommit {
	public static final VCSCommit EMPTY = new VCSCommit();
	private final String revision;
	private final String logMessage;
	private final String author;

	public String getAuthor() {
		return author;
	}

	public String getRevision() {
		return revision;
	}

	public String getLogMessage() {
		return logMessage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((author == null) ? 0 : author.hashCode());
		result = prime * result + ((logMessage == null) ? 0 : logMessage.hashCode());
		result = prime * result + ((revision == null) ? 0 : revision.hashCode());
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
		VCSCommit other = (VCSCommit) obj;
		if (author == null) {
			if (other.author != null)
				return false;
		} else if (!author.equals(other.author))
			return false;
		if (logMessage == null) {
			if (other.logMessage != null)
				return false;
		} else if (!logMessage.equals(other.logMessage))
			return false;
		if (revision == null) {
			if (other.revision != null)
				return false;
		} else if (!revision.equals(other.revision))
			return false;
		return true;
	}

	protected VCSCommit() {
		revision = null;
		logMessage = null;
		author = null;
	}

	public VCSCommit(String revision, String logMessage, String author) {
		this.revision = revision;
		this.logMessage = logMessage;
		this.author = author;
	}

	@Override
	public String toString() {
		return this == EMPTY ? "EMPTY"
				: "VCSCommit [revision=" + revision + ", logMessage=" + logMessage + ", author=" + author + "]";
	}
}
