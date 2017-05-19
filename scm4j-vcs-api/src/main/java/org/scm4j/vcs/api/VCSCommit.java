package org.scm4j.vcs.api;

public class VCSCommit {
	private String id;
	private String logMessage;
	private String author;
	
	public String getAuthor() {
		return author;
	}

	public String getId() {
		return id;
	}

	public String getLogMessage() {
		return logMessage;
	}

	public VCSCommit(String id, String logMessage, String author) {
		super();
		this.id = id;
		this.logMessage = logMessage;
		this.author = author;
	}

	@Override
	public String toString() {
		return "VCSCommit [id=" + id + ", logMessage=" + logMessage + ", author=" + author + "]";
	}
}
