package org.scm4j.vcs.api;

public class VCSCommit {
	public static final VCSCommit EMPTY = new VCSCommit();
	private final String id;
	private final String logMessage;
	private final String author;
	
	public String getAuthor() {
		return author;
	}

	public String getId() {
		return id;
	}

	public String getLogMessage() {
		return logMessage;
	}
	
	protected VCSCommit() {
		id = null;
		logMessage = null;
		author = null;
	}

	public VCSCommit(String id, String logMessage, String author) {
		this.id = id;
		this.logMessage = logMessage;
		this.author = author;
	}

	@Override
	public String toString() {
		return this == EMPTY ? "EMPTY" : 
			"VCSCommit [id=" + id + ", logMessage=" + logMessage + ", author=" + author + "]";
	}
}
