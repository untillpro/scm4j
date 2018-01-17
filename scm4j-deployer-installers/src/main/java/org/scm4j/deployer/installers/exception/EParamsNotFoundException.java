package org.scm4j.deployer.installers.exception;

public class EParamsNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public EParamsNotFoundException(String message) {
		super(message);
		printStackTrace();
	}
}
