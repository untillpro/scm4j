package org.scm4j.vcs.api;

public enum VCSChangeType {
	
	/** Failed to get file status **/
	UNKNOWN, 
	
	/** Add a new file to the project */
	ADD,

	/** Modify an existing file in the project (content and/or mode) */
	MODIFY,

	/** Delete an existing file from the project */
	DELETE
}
