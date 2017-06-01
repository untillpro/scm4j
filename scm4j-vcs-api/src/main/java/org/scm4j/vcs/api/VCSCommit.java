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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		VCSCommit vcsCommit = (VCSCommit) o;

		if (revision != null ? !revision.equals(vcsCommit.revision) : vcsCommit.revision != null) return false;
		if (logMessage != null ? !logMessage.equals(vcsCommit.logMessage) : vcsCommit.logMessage != null) return false;
		return !(author != null ? !author.equals(vcsCommit.author) : vcsCommit.author != null);

	}

	@Override
	public int hashCode() {
		int result = revision != null ? revision.hashCode() : 0;
		result = 31 * result + (logMessage != null ? logMessage.hashCode() : 0);
		result = 31 * result + (author != null ? author.hashCode() : 0);
		return result;
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
		return this == EMPTY ? "EMPTY" : 
			"VCSCommit [revision=" + revision + ", logMessage=" + logMessage + ", author=" + author + "]";
	}
}
