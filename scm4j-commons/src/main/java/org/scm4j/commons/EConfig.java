package org.scm4j.commons;

public class EConfig extends RuntimeException {

	public EConfig(String message, Exception e) {
		super(message, e);
	}

	private static final long serialVersionUID = 1L;

}
