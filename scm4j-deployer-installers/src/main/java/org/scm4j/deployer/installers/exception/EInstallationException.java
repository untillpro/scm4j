package org.scm4j.deployer.installers.exception;

public class EInstallationException extends RuntimeException {

    public EInstallationException(String message) {
        super(message);
        printStackTrace();
    }

    private static final long serialVersionUID = 1L;
}
