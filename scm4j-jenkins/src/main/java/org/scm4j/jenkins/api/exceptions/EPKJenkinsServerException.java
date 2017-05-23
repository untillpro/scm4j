package org.scm4j.jenkins.api.exceptions;

import org.apache.commons.httpclient.HttpStatus;

public class EPKJenkinsServerException extends EPKJenkinsException {

	private static final long serialVersionUID = 8612747334003389372L;
	private String errorMessage;
	private Integer code;

	public String getErrorMessage() {
		return errorMessage;
	}

	public Integer getCode() {
		return code;
	}

	public EPKJenkinsServerException(Integer code, String errorMessage) {
		super(HttpStatus.getStatusText(code));
		this.code = code;
		this.errorMessage = errorMessage;
	}
	
	@Override
	public String toString() {
		return String.format("%s (%d: %s)", errorMessage, code, super.getMessage());
	}
}
