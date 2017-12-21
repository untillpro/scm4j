package org.scm4j.vcs.api.exceptions;

public class EVCSBranchExists extends EVCSException {

	private static final long serialVersionUID = 1806733612822322930L;

	public EVCSBranchExists(String branchName) {
		super("branch already exists: " + branchName);
	}
}
