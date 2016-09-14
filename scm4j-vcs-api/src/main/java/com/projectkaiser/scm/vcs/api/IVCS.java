package com.projectkaiser.scm.vcs.api;

import java.util.List;

import com.projectkaiser.scm.vcs.api.exceptions.EVCSFileNotFound;

public interface IVCS {
	void createBranch(String srcBranchPath, String dstBranchPath, String commitMessage);

	PKVCSMergeResult merge(String srcBranchPath, String dstBranchPath, String commitMessage);

	void deleteBranch(String branchPath, String commitMessage);

	void setCredentials(String user, String password);

	void setProxy(String host, int port, String proxyUser, String proxyPassword);

	String getRepoUrl();

	String getFileContent(String branchName, String fileRelativePath, String encoding) throws EVCSFileNotFound;

	String getFileContent(String branchName, String fileRelativePath) throws EVCSFileNotFound;

	void setFileContent(String branchName, String filePath, String content, String commitMessage);
	
	List<String> getBranchesDiff(String srcBranchName, String destBranchName);
}
