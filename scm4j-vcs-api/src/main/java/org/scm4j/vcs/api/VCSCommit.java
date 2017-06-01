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
