# Overview
Pk-vcs-test provides base functional test class for VCS implementations used by [Project Kaiser CRM](http://www.projectkaiser.com/). It used as maven dependency for [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git), [pk-vcs-svn](https://github.com/ProjectKaiser/pk-vcs-svn) and other VCS support plugins for [Project Kaiser CRM](http://www.projectkaiser.com/). All neccessary vcs-related functional testing is implemented within pk-vcs-test. It is need to implement few abstract methods.

# Terms
- Abstract Test
  - Functional test of common behaviour of a certain VCS implementation. 
  - Exposed as VCSAbstractTest class
- Workspace Home
  - Home folder of all folders used by vcs-related operations. See [pk-vcs-api](https://github.com/ProjectKaiser/pk-vcs-api) for details
- Repository Workspace
  - Folder for LWC folders related to Repository of one type. See [pk-vcs-api](https://github.com/ProjectKaiser/pk-vcs-api) for details
- Locked Working Copy, LWC
  - Folder where vcs-related operations are executed. Provides thread- and process-safe repository of working folders. See [pk-vcs-api](https://github.com/ProjectKaiser/pk-vcs-api) for details

# Overall testing process

- Workspace Home folder defined as `System.getProperty("java.io.tmpdir") + "pk-vcs-workspaces"`
- A new Repository name and Repository Workspace generates for each test within setUp(). Named as "pk-vcs-" + `getVCSTypeString()` + "-testrepo_" + uuid
- A test method executes
- Mocks are verified. `mockedLWC.close()` call is checked if `mockedVCSRepo.getVCSLockedWorkingCopy()` was called.
- Workspace Home folder deletes. So all files and dirs within Workspace Home must be released

# Implementing VCS test
- Add github-hosted pk-vcs-test project as maven dependency using [jitpack.io](https://jitpack.io/). As a gradle exmaple, add following to gradle.build file:
	```gradle
	allprojects {
		repositories {
			maven { url "https://jitpack.io" }
		}
	}
	
	dependencies {
		testCompile 'com.github.ProjectKaiser:pk-vcs-test:master-SNAPSHOT'
	}
	```
- Create VCSAbstractTest subclass within test package
  - Override setUp method
    - Create all neccessary data, test repositories and so on
    - Note that `svn` instance is already created within `super.setUp()`
  - Create @After method if neccessary
  - Use `mockedVCSRepo` as `IVCSRepositoryWorkspace` parameter passed to VCS constructor. VCS Iimplementation should use this `IVCSRepositoryWorkspace` for obtaining LWC. Also this instance will be tested for method calling using Mockito partial mocking by VCSAbstractTest
  - `createVCS(...)` method must create IVCS implementation providing `mockedVCSRepo` field as `IVCSRepositoryWorkspace` instance and store it as `vcs` field
 
      ```java
      @Override
    	protected void createVCS(IVCSRepositoryWorkspace mockedVCSRepo) {
    		vcs = new GitVCS(mockedVCSRepo);
    	}
      ```
  - `sendFile(...)` methods must commit an existing provided file and send it to test Repository. I.e. commit and push for Git, just commit for SVN.
  - Use `WORKSPACE_DIR` constant as a path to Workspace Home folder
  - Use `localVCSRepo` field for creating utility Locked Working Copies, e.g. for test content generating. See `getCommitMessagesRemote()` method in [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git)
  - `mockedLWC` returns each time as a result of `mockedVCSRepo.getLockedWoringCopy()` call. If neccessary it could be used for additional testing. See `setMakeFailureOnVCSReset()` in [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git)
  - Use `repoName` field to get current testing repository name. It generates again for each test
  - Use `repoUrl` field to get url to current testing repository.

# Examples

- [pk-vcs-git](https://github.com/ProjectKaiser/pk-vcs-git)
- [pk-vcs-svn](https://github.com/ProjectKaiser/pk-vcs-svn)

# See also

- [pk-vcs-api](https://github.com/ProjectKaiser/pk-vcs-api)
