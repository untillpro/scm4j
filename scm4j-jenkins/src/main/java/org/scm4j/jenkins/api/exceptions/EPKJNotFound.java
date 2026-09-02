package org.scm4j.jenkins.api.exceptions;

public class EPKJNotFound extends EPKJenkinsServerException {

	public EPKJNotFound(Integer code, String errorMes) {
		super(code, errorMes);
	}

	private static final long serialVersionUID = 2478559349028227084L;

}
