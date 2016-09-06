# pk-vcs-api
# Terms
- Workspace home
	- Home folder of all vcs-related operations which are require to use local file system.
- Repository workspace
	- Folder of separate VCS Repository where working copies will be located. Need to group few working copies used by one Repository into one folder. E.g. if there are Git and SVN version control systems then need to know which VCS type each Working Copy belongs to. 
    - Named automatically as repository url replacing all special characters to "_"
- Locked Working Copy, LWC
	- A separate folder used as a place to execute VCS-related operations which are need to be executed on a local file system. E.g. in Git it is need to make checkout somewhere before make merge.
	- Named automatically as guid. 
	- Can be reused for another vcs-related operation by executing switch operation
	- Deletes automatically if last VCS-related operation left the working copy in corrupted state, i.e. can not be reverted, re-checkouted and so on
	
# Using IVCS implementations (pk-vcs-git, pk-vcs-svn)
- Add github-hosted IVCS implementation as maven artifact using jitpack.io. Add following to gradle.build file:
	```gradle
	allprojects {
		repositories {
			maven { url "https://jitpack.io" }
		}
	}
	
	dependencies {
		compile 'com.github.ProjectKaiser:pk-vcs-git:master-SNAPSHOT'
	}
	```
- Create IVCSWorkspace instance, provide any folder as workspace folder, e.g. `System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces";`
- Call `IVCSWorkspace.getVCSRepositoryWorkspace()` providing Repository url you want to work with
	- Repository foldr will be created within Workspace folder
- 

# Developing IVCS implementation
- Add github-hosted VCS API as maven artifact using jitpack.io. Add following to gradle.build file:
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
	This will include VCS API (IVCS, LWC) and abstract test to your project. 
- implement IVCS interface
	- IVCS implementation should be separate object which normally holds all VCS-related data within
	- Normally IVCSRepositoryWorkspace instance is passed to constructor and stored within IVCS implementation. 
	- Use IVCSRepositoryWorkspace.getLockedWorkingCopy() to obtain LWC to execute VCS-related operation which requires to be executed in separate folder. 
	- Note: if IVCSRepositoryWorkspace.getLockedWorkingCopy() was called then IVCSLockedWorkingCopy.close() must be called. LWC close() call is checked by Abstract Test. Example:
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
	- Use exceptions classes from exceptions package
- Implement functional tests
	- Create VCSAbstractTest subclass, implement all abstract methods
	- Normally test class should not include any test, just @After\@Before methods. All neccessary testing is implemented within VCSAbstractTest
	- createVCS() methods should create and store vcs instance which is being testing.
	


    

    


