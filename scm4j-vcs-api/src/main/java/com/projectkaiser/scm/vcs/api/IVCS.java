package com.projectkaiser.scm.vcs.api;

public interface IVCS {
	void createBranch(String srcUrl, String branchUrl, String commitMessage);
	MergeResult merge(String sourceBranchUrl, String destBranchUrl, String commitMessage);
	void deleteBranch(String branchUrl, String commitMessage);
	void setCredentials(String user, String password);
	void setProxy(String host, int port, String proxyUser, String proxyPassword);
}
