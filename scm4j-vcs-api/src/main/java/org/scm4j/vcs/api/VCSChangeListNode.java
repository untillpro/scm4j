package org.scm4j.vcs.api;

public class VCSChangeListNode {
	public static final String COMMIT_MESSAGES_SEPARATOR = ", ";
	private final String filePath;
	private final String content;
	private final String logMessage;

	public VCSChangeListNode(String filePath, String content, String logMessage) {
		this.filePath = filePath;
		this.content = content;
		this.logMessage = logMessage;
	}

	public String getLogMessage() {
		return logMessage;
	}

	public String getContent() {
		return content;
	}

	public String getFilePath() {
		return filePath;
	}
}

