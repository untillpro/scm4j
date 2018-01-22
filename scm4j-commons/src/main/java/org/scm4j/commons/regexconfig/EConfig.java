package org.scm4j.commons.regexconfig;

public class EConfig extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EConfig(String message, Exception e) {
		super(message, e);
	}

	public EConfig(String message) {
		super(message);
	}
}
