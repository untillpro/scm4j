[![Release](https://jitpack.io/v/User/Repo.svg)](https://jitpack.io/#User/Repo)
# Overview
Pk-vcs-api is set of base classes and interfaces to build VCS support (Git, SVN, etc) libraries which exposes basic vcs-related operations: merge, branch create etc. Pk-vcs-api consists of:
- IVCS interface which exposes various vcs-related methods
- Working Copy utility classes which are required for vcs-related operations which are need to be executed on a local file system (such as merge)

Also see [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test) project. It exposes Abstract Test which is used for functional testing and describing behaviour of IVCS implementation

# Terms
- IVCS
	- Basic exposed interface which contains vcs-related methods
- Workspace Home
	- Home folder of all vcs-related operations which are require to use local file system.
	- Defined by IVCS-user side
- Repository Workspace
	- A folder of separate VCS Repository where Working Copies will be located. Need to group few Working Copies used by one Repository into one folder. E.g. if there are Git and SVN version control systems then need to know which VCS type each Working Copy belongs to.
    - Named automatically as repository url replacing all special characters with "_"
- Locked Working Copy, LWC
	- A separate folder used to execute VCS-related operations which are need to be executed on a local file system. E.g. in Git it is need to make checkout somewhere on local file system before making a merge.
	- Named automatically as uuid, located within Repository Workspace folder
	- Can be reused for another vcs-related operation automatically
	- Deletes automatically if last VCS-related operation left the Working Copy in corrupted state, i.e. can not be reverted, re-checked out and so on
- Lock File
	- A special empty file which is used to show if according LWC locked or free. If a Lock File has exclusive file system lock then the according LWC folder is considered as locked, otherwise as free
	- Lock way: `new FileOutputStream(lockFile, false).getChannel.lock()`
	- named as "lock_" + LWC folder name
- Abstract Test
	- Base functional tests of VCS-related functions which are exposed by IVCS. To implement functional test for a certain IVCS implementation (Git, SVN, etc) just implement VCSAbstractTest subclass.
	- Implemented as [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test) separate project
- `VCSMergeResult`, Merge Result
	- Result of vcs merge operation. Could be successful or failed. Provides list of conflicting files if failed.
- Head, Head Commit, Branch Head
	- The latest commit or state of a branch
- Master Branch
	- "Master" for Git, "Trunk" for SVN etc

# Using VCS interface
IVCS interface consists of few basic vcs functions.
Note: null passed as a branch name is considered as Master Branch. Any non-null branch name is considered as user-created branch within conventional place for branches: any branch except "master" for Git, any branch within "Branches" branch for SVN etc. For SVN do not use "Branches\my-branch" as branch name, use "my-branch" instead.
- `void createBranch(String srcBranchName, String dstBranchName, String commitMessage)`
	- Creates a new branch with name `dstBranchName` from the Head of `srcBranchName`. 
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
- `String getFileContent(String branchName, String fileRelativePath, String encoding)`
	- Returns file content as a string using `encoding` encoding.
	- `fileRelativePath` is a path to file within `branchName` branch 
	- The Head file state is used
	- Use `String getFileContent(String branchName, String fileRelativePath)` overload to use UTF-8 encoding by default
- `void setFileContent(String branchName, String filePath, String content, String commitMessage)`
	- Rewrites a file with path `filePath` within branch `branchName` with content `content` and applies `commitMessage` message to commit
	- Creates the file and its parent folders if doesn't exists
- `List<VCSDiffEntry> getBranchesDiff(String srcBranchName, String destBranchName)`
	- Returns list of `VCSDiffEntry` showing what was made within branch `srcBranchName` relative to branch `destBranchName`
	- Note: result is summarized commit of a branch `srcBranchName`. It does not depends of what was made in branch `destBranchName` 
- `Set<String> getBranches()`
	- Returns list of names of all branches. Branches here are considered as user-created branches and Master Branch. I.e. any branch for Git, "Trunk" and any branch within "Branches" branch (not "Tags" branches) for SVN etc
- `List<String> getCommitMessages(Sting branchName, Integer limit)`
	- Returns list of commit messages of branch `branchName` limited by `limit` in descending order
- `String getVCSTypeString`
	- Returns short name of current IVCS implementation: "git", "svn" etc
- `void removeFile(String branchName, String filePath, String commitMessage)`
	- Removes the file located by `filePath` within branch `branchName`. Operation is executed as separate commit with `commitMessage` message attached. Note: filePath = "folder\file.txt" -> file.txt is removed, folder is kept

# Using Locked Working Copy
Let's assume we developing a multiuser server which has ability to merge branches of user's repositories. So few users could request to merge theirs branches of different repositories simultaneously. For example, Git merge operation consists of few underlying operations (check in\out, merge itself, push) which must be executed on a local file system in a certain folder. So we have following requirements:
- The simple way to allocate place for vcs operations execution
- Make this place "transactional", protecting this place of interfere from other vcs operations
- Reusing ability for the same Repository to prevent of checkout operation executions each time

Locked Working Copy is a solution which solves these requirements by providing a certain folder and guarantees that this folder will not be assigned to another LWC instance until its `close()` method will be called

LWC usage scenario:
- Create Workspace Home instance providing path to any folder as Workspace Home folder path. This folder will contain repositories folders (if different vcs or repositories are used)
```java
	public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	...
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	...
```
- Obtain Repository Workspace from Workspace Home providing a certain Repository's url. The obtained Repository Workspace will represent a folder within Workspace Home dir which will contain all Working Copies relating to the provided VCS Repository  
```java
	String repoUrl = "https://github.com/ProjectKaiser/pk-vcs-api";
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

# Folder structure
- Workspace Home folder (e.g. c:\temp\pk-vcs-workspces\)
	- Repository Workspace 2 (e.g. <Workspace Home>\https_github_com_projectkaiser\
		- Working Copy 1 
			- Branch1 is checked out, merging executes
		- Working Copy 2
			- branch creating executes
		- ...
	-  Repository Workspace 2 (e.g. <Workspace Home>\c_svn_file_repo\)
		- Working Copy 1
		- ...
	- ...

# Working Copy locking way
A special Lock File is created on `IVCSLockedWorkingCopy` instance creation and placed beside the LWC folder which been locking. This file is keeping opened with exclusive lock so any other process (from local or remote PC) can not open it again. So to check if a LWC folder is free it is need to try to lock according Lock File. If success then the according folder was free and can be assigned to current `IVCSLockedWorkingCopy` instance. Otherwise folder is locked and we need to check other folders. If there are no folders left then new folder is created and locked.
So actually a Lock File is locked, not the LWC folder itself. 
Lock way: `new FileOutputStream(lockFile, false).getChannel.lock()`

# Developing IVCS implementation
- Add github-hosted VCS API as maven artifact using [jitpack.io](https://jitpack.io/). As an example, add following to gradle.build file:
	```gradle
	allprojects {
		repositories {
			maven { url "https://jitpack.io" }
		}
	}
	
	dependencies {
		compile 'com.github.ProjectKaiser:pk-vcs-api:master-SNAPSHOT'
		testCompile 'com.github.ProjectKaiser:pk-vcs-test:master-SNAPSHOT'
	}
	```
	This will include VCS API (IVCS, LWC) and Abstract Ttest to your project.
- Implement IVCS interface
	- IVCS implementation should be separate object which normally holds all VCS-related data within
	- Normally IVCSRepositoryWorkspace instance is passed to constructor and stored within IVCS implementation. 
	- All VCS-related operations must be executed within a folder associated with IVCSLockedWorkingCopy in LOCKED state. That guarantees that the folder will not be used by another VCS operations simultaneously. Call `IVCSRepositoryWorkspace.getLockedWorkingCopy()` to obtain LWC when necessary
	- Use `IVCSLockedWorkingCopy.getFolder()` to get a folder for vcs-related operations
	- Note: if `IVCSRepositoryWorkspace.getLockedWorkingCopy()` was called then IVCSLockedWorkingCopy.close() must be called. LWC `close()` call is checked by Abstract Test 
	```java
	public class GitVCS implements IVCS {
	
		IVCSRepositoryWorkspace repo;
		
		public GitVCS(IVCSRepositoryWorkspace repo) {
			this.repo = repo;
		}
		
		@Override
		public void createBranch(String srcBranchName, String newBranchName, String commitMessage) {
		try {
			try (IVCSLockedWorkingCopy wc = repo.getVCSLockedWorkingCopy()) {
				// execute branch create
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// ...
	} 
	```	
	- Throw exceptions from exceptions package. Abstract Test checks throwning of these exceptions.
- Implement functional tests
	- Create VCSAbstractTest subclass within test package, implement all abstract methods
	- Normally test class should not include any test, just @After\@Before methods. All necessary functional testing is implemented within VCSAbstractTest
	- See [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test) for details
- Example of gradle usage to export IVCS implementation, its sources and javadoc as separate single JARs:
```gradle
task sourcesJar(type: Jar, dependsOn: classes) {
	classifier = 'sources'
	from sourceSets.main.allSource
}
	
task javadocJar(type: Jar, dependsOn: javadoc) {
	classifier = 'javadoc'
	from javadoc.destinationDir
}

artifacts {
	archives sourcesJar
	archives javadocJar
}
```
After that the `gralde build` command will produce 3 JARs.

# See also
- [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test)
- [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git)
- [pk-vcs-svn](https://github.com/ProjectKaiser/pk-vcs-svn)
