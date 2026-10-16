package org.scm4j.vcs.api;

import org.scm4j.vcs.api.exceptions.EVCSBranchExists;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;
import org.scm4j.vcs.api.exceptions.EVCSTagExists;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Access to one logical component in a repository.
 * <p>
 * Query methods such as file, branch, tag, diff, and history lookups are read-only and may execute
 * concurrently against a prepared repository snapshot. Mutation methods remain available for
 * checkout-free or legacy callers, while release orchestration must use
 * {@link #startModify()} so all writes share one isolated component checkout.
 */
public interface IVCS {
	/**
	 * Opens one isolated, component-scoped writable checkout for a release action. The caller owns the
	 * returned session and must close it.
	 */
	IVCSMutationSession startModify();

	/** Repository mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	void createBranch(String srcBranchName, String dstBranchName, String commitMessage) throws EVCSBranchExists;

	/** Working-copy mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage);

	/** Repository mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	void deleteBranch(String branchName, String commitMessage);

	void setCredentials(String user, String password);

	void setProxy(String host, int port, String proxyUser, String proxyPassword);

	default void setRetryStatusReporter(BiConsumer<String, Throwable> reporter) {}

	String getRepoUrl();

	/** Read-only snapshot query. */
	String getFileContent(String branchName, String fileRelativePath, String revision) throws EVCSFileNotFound;

	/**
	 * Reads multiple files relative to the selected branch root from the same revision.
	 * A null branch selects the repository's primary branch, and a null revision selects
	 * the branch head. File contents are decoded as UTF-8, matching {@link #getFileContent}.
	 * This is a required operation; custom {@code IVCS} implementations must implement it.
	 * The operation is a read-only snapshot query.
	 *
	 * @param branchName branch to read, or {@code null} for the repository's primary branch
	 * @param filePaths paths relative to the selected branch root
	 * @param revision revision to read, or {@code null} for the branch head
	 * @return a map associating each requested path with its content
	 * @throws EVCSFileNotFound if a requested file is not found
	 * @throws org.scm4j.vcs.api.exceptions.EVCSBranchNotFound if the selected branch is not found
	 */
	Map<String, String> getFilesContent(String branchName, List<String> filePaths, String revision)
			throws EVCSFileNotFound;

	/** Working-copy mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage);

	/** Working-copy mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList);

	/** Read-only snapshot query. */
	List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName);

	/** Read-only snapshot query. */
	Set<String> getBranches(String path);

	/** Read-only snapshot query. */
	List<VCSCommit> log(String branchName, int limit);

	String getVCSTypeString();

	/** Repository mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	VCSCommit removeFile(String branchName, String filePath, String commitMessage);

	/** Read-only snapshot query. */
	List<VCSCommit> getCommitsRange(String branchName, String startRevision, String endRevision);

	/** Read-only snapshot query. */
	default List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit) {
		return getCommitsRange(branchName, startRevision, direction, limit, "");
	}

	/** Read-only snapshot query, optionally restricted to one repository-relative path. */
	List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit, String repositoryRelativePath);

	/** Read-only snapshot query. */
	VCSCommit getHeadCommit(String branchName);

	/** Read-only snapshot query. */
	Boolean fileExists(String branchName, String filePath);

	/** Repository mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
	VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists;

	/** Read-only snapshot query. */
	List<VCSTag> getTags();

	/** Repository mutation. Release actions invoke this through an {@link IVCSMutationSession}. */
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

	/** Read-only snapshot query. */
	List<VCSTag> getTagsOnRevision(String revision);
}
