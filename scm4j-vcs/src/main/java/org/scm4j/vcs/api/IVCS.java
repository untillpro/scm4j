package org.scm4j.vcs.api;

import org.scm4j.vcs.api.exceptions.EVCSBranchExists;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;
import org.scm4j.vcs.api.exceptions.EVCSTagExists;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public interface IVCS {
	void createBranch(String srcBranchName, String dstBranchName, String commitMessage) throws EVCSBranchExists;

	VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage);

	void deleteBranch(String branchName, String commitMessage);

	void setCredentials(String user, String password);

	void setProxy(String host, int port, String proxyUser, String proxyPassword);

	default void setRetryStatusReporter(BiConsumer<String, Throwable> reporter) {}

	String getRepoUrl();

	String getFileContent(String branchName, String fileRelativePath, String revision) throws EVCSFileNotFound;

	VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage);

	VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList);

	List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName);

	Set<String> getBranches(String path);

	List<VCSCommit> log(String branchName, int limit);

	String getVCSTypeString();

	VCSCommit removeFile(String branchName, String filePath, String commitMessage);

	List<VCSCommit> getCommitsRange(String branchName, String startRevision, String endRevision);

	default List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit) {
		return getCommitsRange(branchName, startRevision, direction, limit, "");
	}

	List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit, String repositoryRelativePath);

	VCSCommit getHeadCommit(String branchName);

	Boolean fileExists(String branchName, String filePath);

	VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists;

	List<VCSTag> getTags();

	void removeTag(String tagName);

	void checkout(String branchName, String targetPath, String revision);

	/**
	 * Checks out one repository directory into a caller-owned target folder.
	 * Implementations must preserve the same branch and revision semantics as
	 * {@link #checkout(String, String, String)}. The directory value is passed to
	 * the backend without common API validation; callers are responsible for
	 * supplying a suitable value and handling backend failures.
	 *
	 * @param branchName branch to check out, or {@code null} for the repository's primary branch
	 * @param targetPath local target folder
	 * @param revision revision to check out, or {@code null} for the branch head
	 * @param repositoryRelativeDirectory repository-relative directory selected for materialization
	 */
	void sparseCheckout(String branchName, String targetPath, String revision,
			String repositoryRelativeDirectory);

	List<VCSTag> getTagsOnRevision(String revision);
}
