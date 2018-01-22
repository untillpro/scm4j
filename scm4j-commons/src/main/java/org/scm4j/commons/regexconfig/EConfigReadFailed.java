package org.scm4j.commons.regexconfig;

public class EConfigReadFailed extends EConfig {
	
	private static final long serialVersionUID = 1L;
	
	public EConfigReadFailed(String message, Exception e) {
		super(message, e);
	}
}
