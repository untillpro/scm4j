package com.projectkaiser.scm.vcs.api.exceptions;

public class EBranchExists extends EVCSException {

	private static final long serialVersionUID = 1806733612822322930L;

	public EBranchExists(Exception e) {
		super(e);
	}
}
