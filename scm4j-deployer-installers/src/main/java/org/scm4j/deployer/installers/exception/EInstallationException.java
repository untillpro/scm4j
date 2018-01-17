package org.scm4j.deployer.installers.exception;

public class EInstallationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EInstallationException(String message) {
		super(message);
		printStackTrace();
	}
}
