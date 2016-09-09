# Overview

Pk-vcs-api is set of base classes and interfaces to build VCS support (Git, SVN, etc) libraries. Pk-vcs-api consists of:
- IVCS interface which exposes various vcs-related methods
- Working Copy utility classes which are required if some vcs-related operations needs to be executed on a local file system (such as merge)

Also see [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test) project. It exposes Abstract Test which is used for functional testing and describing behaviour of IVCS implementation

# Terms

- Workspace Home
	- Home folder of all vcs-related operations which are require to use local file system.
- Repository workspace
	- A folder of separate VCS Repository where Working Copies will be located. Need to group few Working Copies used by one Repository into one folder. E.g. if there are Git and SVN version control systems then need to know which VCS type each Working Copy belongs to.
    - Named automatically as repository url replacing all special characters with "_"
- Locked Working Copy, LWC
	- A separate folder used to execute VCS-related operations which are need to be executed on a local file system. E.g. in Git it is need to make checkout somewhere on local file system before making a merge.
	- Named automatically as uuid, located within Repository Workspace fodler
	- Can be reused for another vcs-related operation automatically
	- Deletes automatically if last VCS-related operation left the Working Copy in corrupted state, i.e. can not be reverted, re-checked out and so on
- Lock File
	- A special empty file which is used to show if according LWC locked or free. If a Lock File has exclusive file system lock then the according LWC folder is considered as locked, otherwise as free
	- Lock way: `new FileOutputStream(lockFile, false).getChannel.lock()`
	- named as "lock_" + folder name
- Abstract Test
	- Base functional tests of VCS-related functions which are exposed by IVCS. To implement functional test for a certain IVCS implementation (Git, SVN, etc) just implement VCSAbstractTest subclass.

# Using Locked Working Copy

Let's assume we developing a multiuser server which has ability to merge branches of user's repositories. So few users could request to merge branches of different repositories simultaneously. Git merge operation consists of few underlying operations (check in\out, merge itself, push) which must be executed on a local file system in a certain folder. Also it is neccessary to protect this folder from access by merge request from another user during merging. Locked Working Copy is a solution which represents such certain folder and guarantees that this folder will not be assigned to anoter LWC instance until it's `close()` method will be called
Steps to use LWC:
- Create Workspace Home instance providing path to any folder as Workspace Home folder path. This folder will contain repositories folders (if different vcs or repositories are used)
```java
	public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";
	...
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	...
```
- Obtain Repository Workspace from Workspace Home providing a certain Repository's url. The obtained Repository Workspace will represent a folder within Workspace Home dir and will contain all Working Copies relating to the provided VCS Repository  
```java
	String repoUrl = "https://github.com/ProjectKaiser/pk-vcs-api";
	IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
```
- Obtain Locked Working Copy from Repository Workspace when neccessary. The obtained LWC will represent a locked folder within Workspace Repository. The folder is protected from simultaneouly execute different vcs-related operations by another thread or even process. Use try-with-resources or try...finally to release Working Copy after vcs-related operations will be completed
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
		- `IVCSLockedWorkingCopy.close()` mehtod has been called. Corresponding folder is unlocked and could be used by other `IVCSLockedWorkingCopy` instances. `IVCSLockedWorkingCopy` instance with this state should not be used anymore.
- If vcs Working Copy has been damaged during executing vcs-related operation or vcs Working Copy can not be cleaned, reverted, checked out etc, execute `IVCSLockedWorkingCopy.setCorrupted(true)`. LWC folder will be deleted on close.

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

- Add github-hosted VCS API as maven artifact using jitpack.io. As an example, add following to gradle.build file:
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
	- All VCS-related operations must be executed within a folder assotiated with IVCSLockedWorkingCopy in LOCKED state. That guarantees that the folder will not be used by another VCS operations simultaneously. Call `IVCSRepositoryWorkspace.getLockedWorkingCopy()` to obtain LWC when nessessary
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
	- Normally test class should not include any test, just @After\@Before methods. All neccessary functional testing is implemented within VCSAbstractTest
	- See [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test) for details
- Example of gradle usage to export IVCS implementation, its sources and javadoc as separate single Jars:
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
The `gralde build` command will produce 3 JARs.

# See also

- [pk-vcs-test](https://github.com/ProjectKaiser/pk-vcs-test)
- [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git)
- [pk-vcs-svn](https://github.com/ProjectKaiser/pk-vcs-svn)
