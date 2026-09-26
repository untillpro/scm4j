# SVN adapter
The SVN adapter executes basic SVN VCS operations such as merges and branch creation. It implements
the [common VCS API](../README.md) and uses [SVNKit](https://svnkit.com/) to work with SVN repositories.
See [SVNVCS](../src/main/java/org/scm4j/vcs/svn/SVNVCS.java) and
[SVNVCSUtils](../src/main/java/org/scm4j/vcs/svn/SVNVCSUtils.java) for the implementation.
Features:
- Branch create and remove
- Branch merge returning result(success or list of conflicted files)
- Commit messages list
- Summarized diff between branches
- Branches list
- File content getting and setting
- File create and remove
- Sparse checkout of one repository-relative directory
- Working with tags: create, remove, browse
- Directional commit history for the whole branch or a selected repository-relative file or directory

Use cases
- VCS server hooks
- Build machines
  - checking in\out, tagging
- Software project management systems
  - Create own branches from GUI, browse commits, product versions management, etc
- Product release automation
  - automatic merging, forking, tagging, version bumping, etc
  - Example: [scm4j-releaser](../../scm4j-releaser/README.md)


# Terms
- Workspace Home
  - Local home folder of all folders used by vcs-related operations. See the [common VCS API](../README.md) for details
- Locked Working Copy, LWC
  - Local folder where vcs-related operations are executed. Provides thread- and process-safe repository of working folders. See the [common VCS API](../README.md) for details
- Test Repository
  - A local file-based SVN repository used for functional testing
  - Creates new before and deletes after each test automatically
  - Named `scm4j-vcs-svn-testrepo` under the shared test base directory

# Using the SVN adapter
- The repository URL must identify the root containing the lowercase `trunk/` and `branches/` directories; tags are stored under `tags/`. Replace the example URL and credentials below with your own.
- Code snippet
	```java
	import java.io.File;
	import org.scm4j.vcs.api.IVCS;
	import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
	import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
	import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
	import org.scm4j.vcs.svn.SVNVCS;

	final String WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"), "svn-workspaces").getPath();
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	String repoUrl = "https://svn.example.com/repos/project";
	IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
	IVCS vcs = new SVNVCS(repoWorkspace, "username", "pass");
	```
- Use methods of the [IVCS interface](../src/main/java/org/scm4j/vcs/api/IVCS.java). See the [common VCS API](../README.md) for details
- Pass `null` as the branch name to operate on `trunk`. Other names resolve under `branches/`, so `release/v1` identifies `branches/release/v1`.
- Path-filtered history uses the `IVCS.getCommitsRange` repository-relative path contract. Absolute, drive, UNC, and parent-traversal paths are rejected before querying SVN.
- `IVCS.sparseCheckout` accepts one repository-relative directory. Unlike path-filtered history, this operation does not validate or normalize the directory; the caller supplies a suitable value and handles any SVNKit failure.
- `getTags()` and `getTagsOnRevision()` return an empty list when the repository has no `tags/` root because the absence of that conventional SVN directory means that no tags exist.
- Use `vcs.setProxy()` and `vcs.setCredentials()` if necessary

# Implementation details
- [SVNKit](https://svnkit.com/) is used to manage SVN repositories
- Sparse checkout uses SVNKit to create or switch the selected branch working copy at empty depth, then updates the requested directory recursively at infinite sticky depth. The update creates the directory's parent working-copy nodes when necessary.
- No native SVN executable is invoked, and the adapter does not pre-validate the sparse directory argument.
- Operations that need an internal working copy obtain an LWC automatically. `checkout` and `sparseCheckout` instead use the caller-provided target folder.

# Functional testing
- Run [SVNVCSTest](../src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java) as a JUnit test, or run `./gradlew :scm4j-vcs:test --tests org.scm4j.vcs.svn.SVNVCSTest` from the repository root (`.\gradlew.bat` in Windows PowerShell).
- This includes inherited [VCSAbstractTest](../src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java) tests. See [shared conformance tests](testing.md) for details.

# Limitations
- SVN branches are directories. `getBranches("")` returns the immediate directories under `branches/`, plus `trunk` if it exists. For example:
	- Assume we have following directory structure:
		- branches/Br1/Folder/file.txt
		- branches/Br2/Folder/file.txt
		- trunk/Folder/file.txt
		- tags/Tag1/
	- Then `getBranches("")` returns the set `[Br1, Br2, trunk]`, with no guaranteed ordering.
- Nested branch listings retain their path relative to `branches/`: `getBranches("release/")` can return `release/v1` and `release/v2`. See the [branch-list examples](../README.md#using-vcs-interface).

# Historical version

[scm4j-vcs-svn standalone repository](https://github.com/scm4j/scm4j-vcs-svn)
