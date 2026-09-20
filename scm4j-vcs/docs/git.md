# Git adapter
The Git adapter executes basic Git VCS operations such as merges and branch creation. It implements
the [common VCS API](../README.md) and uses [JGit](https://projects.eclipse.org/projects/technology.jgit) to work with Git repositories.
Its implementation classes, [GitVCS](../src/main/java/org/scm4j/vcs/git/GitVCS.java) and
[GitVCSUtils](../src/main/java/org/scm4j/vcs/git/GitVCSUtils.java), are in the `org.scm4j.vcs.git` package.
Features:
- Working with branches: create, remove, browse
- Branch merge with result return (success or list of conflicted files)
- Summarized diff between branches
- File content getting and setting
- File create and remove
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
  - Home local folder of all folders used by vcs-related operations. See the [common VCS API](../README.md) for details
- Locked Working Copy, LWC
  - Local folder where vcs-related operations are executed. Provides thread- and process-safe repository of working folders. See the [common VCS API](../README.md) for details
- Test Repository
  - Git repository which is used to execute functional tests
  - File-based repository is used
  - Generates new one before and deletes after each test
  - Named `scm4j-vcs-git-testrepo` under the shared test base directory

# Using the Git adapter
- Code snippet
	```java
	import java.io.File;
	import org.scm4j.vcs.api.IVCS;
	import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
	import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
	import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
	import org.scm4j.vcs.git.GitVCS;

	final String WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"), "git-workspaces").getPath();
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	String repoUrl = "https://github.com/MyUser/MyRepo";
	IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
	IVCS vcs = new GitVCS(repoWorkspace);
	vcs.setCredentials("user", "password"); // if necessary
	```
- Use methods of the [IVCS interface](../src/main/java/org/scm4j/vcs/api/IVCS.java). See the [common VCS API](../README.md) for details
- Path-filtered history uses the `IVCS.getCommitsRange` repository-relative path contract. Use `/` separators and keep the path within the selected branch.
- Use `vcs.setProxy()` and `vcs.setCredentials()` if necessary
- `GitVCS` additionally exposes `VCSTag createUnannotatedTag(String branchName, String tagName, String revisionToTag)`, which is not part of `IVCS`. Use a `GitVCS` reference to create an unannotated tag named `tagName` at `revisionToTag`. If `branchName` is `null`, the branch targeted by the remote symbolic `HEAD` is used. If `revisionToTag` is `null`, the head of `branchName` is used.

# Implementation details
- [JGit](https://projects.eclipse.org/projects/technology.jgit) is used as the framework to work with Git repositories
- Repository operations use an LWC, except `checkout`, which uses the caller-provided target folder. Configuration methods do not allocate an LWC.
- `getLocalGit(IVCSLockedWorkingCopy wc)` method is used to create a Git implementation to execute vcs operations within `wc` Working Copy
  - If the local Git object database does not exist, the configured repository is cloned into the folder; otherwise the existing repository is opened. Fetching and branch switching are handled by the calling operation.
- `setProxy` installs JVM-wide `ProxySelector` and `Authenticator` defaults. The selector uses the configured proxy for URLs containing the repository URL, ignoring case, and delegates other URLs to the previous selector.

# Functional testing
- New local file-based Test Repository is created before each test and deletes automatically after each test
- Run [GitVCSTest](../src/test/java/org/scm4j/vcs/git/GitVCSTest.java) as a JUnit test, or run `./gradlew :scm4j-vcs:test --tests org.scm4j.vcs.git.GitVCSTest` from the repository root (`.\gradlew.bat` in Windows PowerShell).
- This includes inherited [VCSAbstractTest](../src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java) tests. See [shared conformance tests](testing.md) for details.

# Limitations
- Commit messages can not be attached to branch create and delete operations because Git does not expose these operations as separate commits
- Default-branch lookup requires the remote to advertise a symbolic `HEAD` pointing to an existing branch. The resolved branch name is cached per `GitVCS` instance; there is no fallback to a hard-coded branch name.
- The three-argument `getCommitsRange(branchName, startRevision, endRevision)` excludes the starting revision. Use a `WalkDirection` overload for an inclusive starting cursor.

# Historical version

[scm4j-vcs-git standalone repository](https://github.com/scm4j/scm4j-vcs-git)
