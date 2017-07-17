package org.scm4j.vcs.api;

import org.scm4j.vcs.api.exceptions.EVCSBranchExists;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;
import org.scm4j.vcs.api.exceptions.EVCSTagExists;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;

import java.util.List;
import java.util.Set;

public interface IVCS {
	void createBranch(String srcBranchName, String dstBranchName, String commitMessage) throws EVCSBranchExists;

	VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage);

	void deleteBranch(String branchName, String commitMessage);

	void setCredentials(String user, String password);

	void setProxy(String host, int port, String proxyUser, String proxyPassword);

	String getRepoUrl();

	String getFileContent(String branchName, String fileRelativePath, String encoding) throws EVCSFileNotFound;

	String getFileContent(String branchName, String fileRelativePath) throws EVCSFileNotFound;

	VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage);
	
	List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName);
	
	Set<String> getBranches();
	
	List<VCSCommit> log(String branchName, int limit);
	
	String getVCSTypeString();
	
	VCSCommit removeFile(String branchName, String filePath, String commitMessage);
	
	List<VCSCommit> getCommitsRange(String branchName, String firstCommitId, String untilCommitId);
	
	List<VCSCommit> getCommitsRange(String branchName, String firstCommitId, WalkDirection direction, int limit);
	
	IVCSWorkspace getWorkspace();
	
	VCSCommit getHeadCommit(String branchName);
	
	Boolean fileExists(String branchName, String filePath);
	
	VCSTag createTag(String branchName, String tagName, String tagMessage) throws EVCSTagExists;
	
	List<VCSTag> getTags();
	
	VCSTag getLastTag();
	
	void removeTag(String tagName);
}
