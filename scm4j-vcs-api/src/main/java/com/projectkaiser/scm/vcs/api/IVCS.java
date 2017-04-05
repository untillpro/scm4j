package com.projectkaiser.scm.vcs.api;

import java.util.List;
import java.util.Set;

import com.projectkaiser.scm.vcs.api.exceptions.EVCSFileNotFound;

public interface IVCS {
	void createBranch(String srcBranchName, String dstBranchName, String commitMessage);

	VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage);

	void deleteBranch(String branchName, String commitMessage);

	void setCredentials(String user, String password);

	void setProxy(String host, int port, String proxyUser, String proxyPassword);

	String getRepoUrl();

	String getFileContent(String branchName, String fileRelativePath, String encoding) throws EVCSFileNotFound;

	String getFileContent(String branchName, String fileRelativePath) throws EVCSFileNotFound;

	String setFileContent(String branchName, String filePath, String content, String commitMessage);
	
	List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName);
	
	Set<String> getBranches();
	
	List<String> getCommitMessages(String branchName, Integer limit);
	
	String getVCSTypeString();
	
	String removeFile(String branchName, String filePath, String commitMessage);
	
	List<VCSCommit> getCommitsRange(String branchName, String afterCommitId, String untilCommitId);
}
