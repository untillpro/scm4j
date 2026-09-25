# Overview
scm4j-vcs provides the common VCS API together with Git and SVN implementations. It exposes basic
version-control operations such as creating and merging branches, reading and writing files, walking
history, and managing tags.

scm4j-vcs provides:
- A simple interface to implement basic VCS-related operations
- Working copies management for operations which must be executed on a local file system
- A native Git command-line adapter available for explicit construction
- `org.scm4j.vcs.git.GitVCS`, the unchanged JGit-backed adapter used by existing call sites
- `SVNVCS`, backed by SVNKit
- A shared conformance suite for all adapters

Detailed documentation:
- [Git adapter](docs/git.md)
- [SVN adapter](docs/svn.md)
- [Shared conformance tests](docs/testing.md)

Source code:
- [IVCS interface](src/main/java/org/scm4j/vcs/api/IVCS.java)
- [Working-copy interfaces and implementations](src/main/java/org/scm4j/vcs/api/workingcopy)
- [GitVCS](src/main/java/org/scm4j/vcs/git/GitVCS.java) and [SVNVCS](src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [VCSAbstractTest](src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)

# Using Git or SVN

Create a workspace and a separate repository workspace for each repository URL. Replace the example
URLs and credentials below with your own. The SVN URL must point to the root containing `trunk/`
and `branches/`.

```java
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
import org.scm4j.vcs.git.GitVCS;
import org.scm4j.vcs.svn.SVNVCS;

IVCSWorkspace workspace = new VCSWorkspace();
IVCSRepositoryWorkspace gitRepository =
        workspace.getVCSRepositoryWorkspace("https://github.com/untillpro/scm4j.git");
IVCSRepositoryWorkspace svnRepository =
        workspace.getVCSRepositoryWorkspace("https://svn.example.com/repos/project");

IVCS git = new GitVCS(gitRepository);
IVCS svn = new SVNVCS(svnRepository, "username", "password");
```

Both adapters shown above implement `IVCS` and use the same working-copy infrastructure; the
native Git adapter implements the same contract.

## Native Git prerequisites

- Always: a Java 8-compatible runtime and Git 2.25 or later as `git` on `PATH`.
- With explicit SCM4J credentials: a POSIX-compatible `sh` for the ask-pass helper.
- With SSH remotes: OpenSSH `ssh` and host-key configuration; `ssh-agent` and `ssh-add` are optional.
- Without explicit credentials: Git may use the host credential helper. Git for Windows bundles
  Git Credential Manager, `sh`, OpenSSH, and Git's transport helpers.

The adapter launches Git directly, uses the existing Failsafe dependency, and adds no Java library;
existing call sites remain on JGit. Builds use JDK 8, Git 2.25+, and `gradlew` or
`gradlew.bat`, not system Gradle. No SVN CLI, `curl`, `rsync`, `sed`, `awk`, `grep`, `git-lfs`, or
submodule initialization is required. See the complete [dependency matrix and command
contract](docs/git.md#native-cli-dependency-matrix).

# Terms
- `IVCS`
	- Basic exposed interface which contains vcs-related methods
- Workspace Home
	- Home folder of all vcs-related operations which are require to use local file system.
	- Defined by IVCS-user side
- Repository Workspace
	- A separate folder where Working Copies of one certain Repository will be located. Need to group few Working Copies used by one Repository into one folder. E.g. if there are Git and SVN version control systems then need to know which VCS type each Working Copy belongs to.
    - Named from the repository URL by replacing characters other than letters, digits, `.` and `-` with `_`, then removing the normalized `http://`, `https://` or `file:` prefix when present
- Locked Working Copy, LWC
	- A separate folder used to execute VCS-related operations which are need to be executed on a local file system. E.g. in Git it is need to make checkout somewhere on local file system before making a merge.
	- Named automatically as uuid, located within Repository Workspace folder
	- Can be reused for another vcs-related operation automatically. I.e. checked out once, then switches between branches.
	- Deletes automatically if last VCS-related operation left the Working Copy in corrupted state, i.e. can not be reverted, re-checked out and so on
- Lock File
	- A special empty file which is used to show if according LWC locked or free. If a Lock File has exclusive file system lock then the according LWC folder is considered as locked, otherwise as free
	- Lock way: `new FileOutputStream(lockFile, false).getChannel().lock()`
	- Named as `lock_` followed by the LWC folder name
- Abstract Test
	- Base functional tests of VCS-related functions which are exposed by IVCS. To implement functional test for a certain IVCS implementation (Git, SVN, etc) just implement VCSAbstractTest subclass
	- Implemented by [VCSAbstractTest](src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java) in this subproject's test sources; it is not included in the main library JAR
- `VCSMergeResult`, Merge Result
	- Result of vcs merge operation. Could be successful or failed. Provides list of conflicting files if failed.
- `VCSDiffEntry`, Diff Entry
	- Result of VCS branches diff operation. Contains Diff type (added, modified, deleted) and unified diff string for a certain file which differs between branches 
- Head, Head Commit, Branch Head
	- The latest commit or state of a branch
- Primary Branch
	- The repository-defined default branch for Git, `trunk` for SVN
- `VCSTag`, Tag
    - Contains tag name, tag log message, tag author and `VCSCommit` instance which represents the tagged commit

# Using VCS interface
The [IVCS interface](src/main/java/org/scm4j/vcs/api/IVCS.java) exposes the following operations.
Note: `null` passed as a branch name represents the repository's primary branch: the branch targeted by the remote symbolic `HEAD` for Git and `trunk` for SVN. Non-null SVN branch names are resolved under `branches/`: use `my-branch`, not `branches/my-branch`. To operate on SVN trunk, pass `null`, even though `getBranches` lists it as `trunk`.
- `void createBranch(String srcBranchName, String dstBranchName, String commitMessage)`
	- Creates a new branch named `dstBranchName` from the Head of `srcBranchName`.
	- commitMessage is a log message which will be attached to branch create operation if it possible (e.g. Git does not posts branch create operation as a separate commit)
- `VCSMergeResult merge(String srcBranchName, String dstBranchName, String commitMessage);`
	- Merge all commits from `srcBranchName` to `dstBranchName` with `commitMessage` attached
	- `VCSMergeResult.getSuccess() == true`
		- merge is successful
	- `VCSMergeResult.getSuccess() == false`
		- Automatic merge can not be completed due of conflicting files
		- `VCSMergeResult.getConflictingFiles()` contains paths to conflicting files
	- Heads of branches `srcBranchName` and `dstBranchName` are used
- `void deleteBranch(String branchName, String commitMessage)`
	- Deletes branch with path `branchName` and attaches `commitMessage` to branch delete operation if possible (e.g. Git does not posts branch delete operation as a separate commit)
-  `void setCredentials(String user, String password)`
	- Applies credentials to existing IVCS implementation. I.e. first a IVCS implementation should be created, then credentials should be applied when necessary
- `void setProxy(String host, int port, String proxyUser, String proxyPassword)`
	- Sets proxy parameters if necessary
- `String getRepoUrl()`
	- Returns string url of current vcs repository
- `String getFileContent(String branchName, String fileRelativePath, String revision)`
	- Returns file content as a string using UTF-8 encoding.
	- `fileRelativePath` is a path to file within `branchName` branch 
	- File state at `revision` revision is used. If `revision` is null then Head state is used
- `VCSCommit setFileContent(String branchName, String filePath, String content, String commitMessage)`
	- Rewrites a file with path `filePath` within branch `branchName` with content `content` and applies `commitMessage` message to commit
	- Creates the file and its parent folders if doesn't exists
- `VCSCommit setFileContent(String branchName, List<VCSChangeListNode> vcsChangeList)`
	- For each `vcsChangeListNode`: rewrites file with path `vcsChangeListNode.getFilePath()` within branch `branchName` with content `vcsChangeListNode.getContent()`
	- Single commit, commit message is all `vcsChangeListNode.getLogMessage()` strings joined with ", "
	- Creates files and its parent folders if aren't exist
	- Returns null if `vcsChangeList` is empty
- `List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName)`
	- Returns list of `VCSDiffEntry` showing what was made within branch `srcBranchName` relative to branch `destBranchName`
	- Compares the branch contents; it does not predict the result of a merge when both branches have changed
- `Set<String> getBranches(String path)`
	- Returns a set of branch names matching `path`, including the primary branch when it matches. Null and empty paths list all Git branches, or SVN's immediate `branches/` subdirectories plus `trunk` if it exists. Tags are excluded.
    - `path` processing
        - Git
            - prefix of branch names to browse
            - Assume we have following branches:
                - branch_1
                - branch_2
                - new-branch
            - then `getBranches("br")` will return [branch_1, branch_2]
        - SVN
            - Assume we have following folders structure:
                - branches/branch_1/folder
                - branches/branch_2/folder
                - branches/release/v1/folder
                - branches/release/v2/folder
                - branches/release/a2/folder
                - branches/new-branch/folder
                - branch_3/folder
                - tags/
                - trunk/
                - new-branch
            - `path` is null or empty - result is all first-level subdirectories within `branches/`, plus `trunk` if it exists
                - `getBranches("")` -> [branch_1, branch_2, release, new-branch, trunk]
            - A non-empty `path` ending with "/" lists the first-level subdirectories within `branches/path`
                - `getBranches("release/")` -> [release/v1, release/v2, release/a2]
            - `path` does not ends with "/" - result is first-level subdirs within `branches/path` dir up to the last slash which names starts with `path` dir from the last slash till end substring
                 - `getBranches("new-")` -> [new-branch]
                 - `getBranches("release/v")` -> [release/v1, release/v2]
            - Returned sets have no guaranteed ordering
- `List<VCSCommit> log(String branchName, int limit)`
	- Returns list of commits of branch `branchName` limited by `limit` in descending order
- `String getVCSTypeString()`
	- Returns short name of current IVCS implementation: "git", "svn" etc
- `VCSCommit removeFile(String branchName, String filePath, String commitMessage)`
	- Removes the file with path `filePath` within branch `branchName` in a separate commit with `commitMessage` attached. Returns the resulting commit. SVN keeps the parent directory; Git does not track empty directories.
- `List<VCSCommit> getCommitsRange(String branchName, String startRevision, String endRevision)`
	- Returns commits between two revision boundaries. For older-to-newer boundaries, results are ordered from older to newer.
	- Git excludes `startRevision` and includes `endRevision`. SVN includes both boundaries.
	- A null `startRevision` selects the branch's first commit as the starting boundary; Git still excludes that commit. A null `endRevision` selects the branch head.
	- Use a `WalkDirection` overload when you need an inclusive starting cursor on both adapters.
- `List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit, String repositoryRelativePath)`
    - Returns commits affecting `repositoryRelativePath`, which is resolved from the root of `branchName`. The path can identify a file or directory; a directory matches changes at any depth below it.
    - A non-empty `repositoryRelativePath` must remain relative: use `/` as the portable separator, do not start with `/` or `\`, do not use a drive or UNC path, and do not include a `..` path segment.
    - `startRevision` is an inclusive cursor. `ASC` walks from that revision toward the branch head and returns commits from older to newer; `DESC` walks toward the branch origin and returns commits from newer to older.
    - If `startRevision` is null, traversal starts at the boundary selected by `direction`: the branch origin for `ASC` or the branch head for `DESC`.
    - A positive `limit` is applied after path filtering, so unrelated commits do not consume the result limit. A limit of `0` returns all matching commits in the requested direction.
    - A null or empty `repositoryRelativePath` selects whole-branch history.
- `List<VCSCommit> getCommitsRange(String branchName, String startRevision, WalkDirection direction, int limit)`
    - Convenience overload for whole-branch history. It delegates to the path-filtered operation with an empty path.
- `VCSCommit getHeadCommit(String branchName)`
    - Returns `VCSCommit` instance pointing to the head (last) commit of the branch `branchName` or `null` if the requested branch does not exists  
- `Boolean fileExists(String branchName, String filePath)`
    - Returns true if the file with path `filePath` exists in repository in branch `branchName`, false otherwise
- `VCSTag createTag(String branchName, String tagName, String tagMessage, String revisionToTag) throws EVCSTagExists`
    - Creates a tag named `tagName` with log message `tagMessage` at `revisionToTag` on `branchName`. Pass null for `revisionToTag` to tag the branch head.
- `List<VCSTag> getTags()`
    - Returns list of all tags, or an empty list when the repository has no tags
- `void removeTag(String tagName)`
    - Removes tag with name `tagName`
- `void checkout(String branchName, String targetPath, String revision)`
    - Checks out a branch `branchName` on a revision `revision` into a local folder `targetPath`
    - A null `revision` selects the branch head. The caller is responsible for exclusive access to `targetPath`; this method does not allocate a locked working copy.
- `List<VCSTag> getTagsOnRevision(String revision)`
    - Returns list of all tags which are related to the commit specified by `revision`, or an empty list when the repository has no tags
    
# Using Locked Working Copy
Let's assume we developing a multiuser server which has ability to merge branches of user's repositories. So few users could request to merge theirs branches of different repositories simultaneously. For example, Git merge operation consists of few underlying operations (check in\out, merge itself, push) which must be executed on a local file system in a certain folder. So we have following requirements:
- The simple way to allocate place for vcs operations execution
- Make this place "transactional", protecting this place of interfere from other vcs operations
- Reusing ability for the same Repository to prevent of checkout operation executions each time

Locked Working Copy is a solution which solves these requirements by providing a certain folder and guarantees that this folder will not be assigned to another LWC instance until its `close()` method will be called

LWC usage scenario:
- Create Workspace Home instance providing path to any folder as Workspace Home folder path. This folder will contain repositories folders (if different vcs or repositories are used)
```java
	public static final String WORKSPACE_DIR = VCSWorkspace.DEFAULT_WORKSPACE_DIR;
	...
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	...
```
- Obtain Repository Workspace from Workspace Home providing a certain Repository's url. The obtained Repository Workspace will represent a folder within Workspace Home dir which will contain all Working Copies relating to the provided VCS Repository  
```java
	String repoUrl = "https://github.com/untillpro/scm4j";
	IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
```
- Obtain Locked Working Copy from Repository Workspace when necessary. The obtained LWC will represent a locked folder within Workspace Repository. The folder is protected from simultaneously execute different vcs-related operations by another thread or even process. Use try-with-resources or try...finally to release Working Copy after vcs-related operations will be completed
```java
	try (IVCSLockedWorkingCopy wc = repoWorkspace.getVCSLockedWorkingCopy()) {
	...
	}
```
- Use `IVCSLockedWorkingCopy.getFolder()` as folder for vcs-related operations
- Do not use `IVCSLockedWorkingCopy` instance after calling `IVCSLockedWorkingCopy.close()` method because after closing `IVCSLockedWorkingCopy` instance does not guarantees that according folder is not in use
- Consider `IVCSLockedWorkingCopy.getState()` values:
	- LOCKED
		- current `IVCSLockedWorkingCopy` represents a locked folder, i.e. a folder which is not used by other `IVCSLockedWorkingCopy` instances. 
	- OBSOLETE
		- `IVCSLockedWorkingCopy.close()` method has been called. Corresponding folder is unlocked and could be used by other `IVCSLockedWorkingCopy` instances. `IVCSLockedWorkingCopy` instance with this state should not be used anymore.
- If a Working copy can not be reused due of VCS system data damage (e.g. .git, .svn folders) or due of vcs Working Copy can not be cleaned, reverted, switched, checked out etc, execute `IVCSLockedWorkingCopy.setCorrupted(true)`. LWC folder will be deleted on close.
- Code snippet
```java
import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

public class WorkingCopyExample {
    public static final String WORKSPACE_DIR = VCSWorkspace.DEFAULT_WORKSPACE_DIR;

    public static void main(String[] args) throws Exception {
        IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
        String repoUrl = "https://github.com/untillpro/scm4j";
        IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
        try (IVCSLockedWorkingCopy wc = repoWorkspace.getVCSLockedWorkingCopy()) {
            // Perform local operations using wc.getFolder().
        }
    }
}
```

# Folder structure
- Workspace Home folder (e.g. `C:\temp\scm4j-vcs-workspaces\`)
	- Repository Workspace 1 (e.g. `<Workspace Home>\github.com_untillpro_scm4j\`)
		- Working Copy 1 
			- Branch1 is checked out, merging executes
		- Working Copy 2
			- branch creating executes
		- ...
	- Repository Workspace 2 (e.g. `<Workspace Home>\C__svn_file_repo\` for `file:///C:/svn/file_repo`)
		- Working Copy 1
		- ...
	- ...

# Working Copy locking way
[VCSLockedWorkingCopy](src/main/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopy.java)
holds an exclusive file lock on `lock_<LWC folder name>` beside the working-copy folder. Allocation
tries existing folders with lock files before creating a new UUID-named folder. A failed lock attempt
skips that folder. The implementation uses `FileChannel.lock()`, which can block when another process
holds the lock. Coordination depends on the filesystem's file-lock support; the folder itself is not
locked against arbitrary filesystem access.

Lock call: `new FileOutputStream(lockFile, false).getChannel().lock()`.

# Developing IVCS implementation
- Implement IVCS interface
	- IVCS implementation should be separate object which normally holds all VCS-related data within
	- Normally IVCSRepositoryWorkspace instance is passed to constructor and stored within IVCS implementation. 
	- Operations using an internal local working copy should obtain an LWC in LOCKED state via `IVCSRepositoryWorkspace.getVCSLockedWorkingCopy()`. Remote-only operations do not need an LWC; `checkout` uses the caller-provided target folder.
	- Use `IVCSLockedWorkingCopy.getFolder()` to get a folder for vcs-related operations
	- Every acquired LWC must be closed, preferably with try-with-resources. The shared test suite checks calls to `close()`.
	- See [GitVCS](src/main/java/org/scm4j/vcs/git/GitVCS.java) and [SVNVCS](src/main/java/org/scm4j/vcs/svn/SVNVCS.java) for implementations.
	- Throw the appropriate exceptions from [org.scm4j.vcs.api.exceptions](src/main/java/org/scm4j/vcs/api/exceptions). The shared suite checks these exceptions.
- Implement functional tests
	- Create VCSAbstractTest subclass within test package, implement all abstract methods
	- Inherit the shared conformance tests and add adapter-specific tests for native behavior, validation and error translation.
	- [GitVCSTest](src/test/java/org/scm4j/vcs/git/GitVCSTest.java) and [SVNVCSTest](src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java) are the reference implementations. See [testing instructions](docs/testing.md).

The root [build.gradle](../build.gradle) already configures the library, sources and Javadoc JARs.
Run `./gradlew :scm4j-vcs:assemble` from the repository root to produce them in `scm4j-vcs/build/libs/`.

# Testing

Run `./gradlew :scm4j-vcs:test` from the repository root to execute the API unit tests, the shared
Git/SVN conformance suite and adapter-specific tests. In Windows PowerShell, use
`.\gradlew.bat :scm4j-vcs:test`. See [shared conformance tests](docs/testing.md) for individual suites.

# Historical standalone repositories

- [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api)
- [scm4j-vcs-git](https://github.com/scm4j/scm4j-vcs-git)
- [scm4j-vcs-svn](https://github.com/scm4j/scm4j-vcs-svn)
- [scm4j-vcs-test](https://github.com/scm4j/scm4j-vcs-test)
