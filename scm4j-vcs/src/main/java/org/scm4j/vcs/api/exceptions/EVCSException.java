package org.scm4j.vcs.api.exceptions;

public class EVCSException extends RuntimeException {

	private static final long serialVersionUID = -7123206240680231070L;

	public EVCSException(Exception e) {
		super(e);
	}

	public EVCSException(String message) {
		super(message);
	}

}
