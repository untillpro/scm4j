package org.scm4j.commons.regexconfig;

public class EConfig extends RuntimeException {
	public EConfig(String message, Exception e) {
		super(message, e);
	}

	public EConfig(String message) {
		super(message);
	}
}
