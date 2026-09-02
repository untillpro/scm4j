package org.scm4j.jenkins.api.exceptions;

public class EPKJExists extends EPKJenkinsServerException {

	public EPKJExists(Integer code, String errorMessage) {
		super(code, errorMessage);
	}

	private static final long serialVersionUID = 7637144484632267911L;

}
