[![Release](https://jitpack.io/v/scm4j/scm4j-vcs-test.svg)](https://jitpack.io/#scm4j/scm4j-vcs-test)
[![Build Status](https://travis-ci.org/scm4j/scm4j-vcs-test.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-vcs-test)

# Overview
scm4j-vcs-test project provides base functional test class for IVCS implementations subclassed from [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api). It used as maven dependency for [scm4j-vcs-git](https://github.com/scm4j/scm4j-vcs-git), [scm4j-vcs-svn](https://github.com/scm4j/scm4j-vcs-svn) and other VCS support libraries. All necessary vcs-related functional testing is implemented within scm4j-vcs-test. It is need to implement just few abstract methods.

# Terms
- Abstract Test
  - Functional test of common behaviour of a certain VCS implementation. 
  - Exposed as VCSAbstractTest class
- Workspace Home
  - Home folder of all folders used by vcs-related operations. See [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api) for details
- Repository Workspace
  - Folder for LWC folders related to Repository of one type. See [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api) for details
- Locked Working Copy, LWC
  - Folder where vcs-related operations are executed. Provides thread- and process-safe repository of working folders. See [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api) for details
- Test Repository
  - A VCS Repository which is used to execute vcs operations which are being tested.
  - New Test Repository is generated before and deleted after each test
  - Named randomly (uuid is used)

# Overall testing process
- Workspace Home folder is defined as `System.getProperty("java.io.tmpdir") + "scm4j-vcs-workspaces"`
- A new Test Repository name and Repository Workspace generates for each test within `setUp()`. Test Repository is named as `"scm4j-vcs-" + getVCSTypeString() + "-testrepo_" + uuid`
- A test method executes
- Mocks are verified. LWC closing is checked if one was obtained
- Workspace Home folder deletes. So all files and dirs within Workspace Home must be released

# Implementing VCS test
- Add github-hosted scm4j-vcs-test project as maven dependency using [jitpack.io](https://jitpack.io/). As a gradle example, add following to gradle.build file:
```gradle
allprojects {
	repositories {
		maven { url "https://jitpack.io" }
	}
}

dependencies {
	// versioning: master-SNAPSHOT (lastest build, unstable), + (lastest release, stable) or certain version (e.g. 1.0)
	testCompile 'com.github.scm4j:scm4j-vcs-test:+'
}
```
Or download release jars from https://github.com/scm4j/scm4j-vcs-test/releases
- Create VCSAbstractTest subclass within test package
- Override `setUp()` method
	- Call `super.setUp()` 
	- Create all necessary data, test repositories and so on
	- Note that `vcs` instance is already created within `super.setUp()`
- Create @After method if necessary
- Use `mockedVCSRepo` as `IVCSRepositoryWorkspace` parameter passed to VCS constructor. VCS implementation must use this `IVCSRepositoryWorkspace` for obtaining LWC. Also this instance will be tested for method calling using Mockito partial mocking by VCSAbstractTest
- `mockedLWC` returns each time as a result of `mockedVCSRepo.getLockedWoringCopy()` call. If necessary it could be used for additional testing. See `setMakeFailureOnVCSReset()` in [scm4j-vcs-git](https://github.com/scm4j/scm4j-vcs-git)
- `getVCS(...)` method must return IVCS implementation which will be tested. Also this IVCS implementation must use provided `mockedVCSRepo`
```java
    @Override
    protected IVCS getVCS(IVCSRepositoryWorkspace mockedVCSRepo) {
    	return new GitVCS(mockedVCSRepo);
    }
```
- `getVCSTypeString()` method must return short VCS name, e.g. "git", "svn" (same as `IVC.getVCSTypeString()`)
- `getTestRepoUrl()` method must return string url to Test Repository
- `setMakeFailureOnVCSReset(Boolean doMakeFailure)` must make so next `merge` operation will fail on LWC reset caused by merge conflict. This need to test LWC corruption. See examples below.
- Use `localVCSWorkspace` field as Workspace Home
- Use `localVCSRepo` field for creating utility Locked Working Copies, e.g. for test content generating. See `getCommitMessagesRemote()` method in [scm4j-vcs-git](https://github.com/scm4j/scm4j-vcs-git) as an example
- Use `repoName` field to get current testing repository name. It generates again for each test randomly (uuid is used)
- Use `repoUrl` field to get url to current testing repository.
- Use `vcs` field as current IVCS implementation which is being testing

# Examples
- [scm4j-vcs-git](https://github.com/scm4j/scm4j-vcs-git)
- [scm4j-vcs-svn](https://github.com/scm4j/scm4j-vcs-svn)

# See also
- [scm4j-vcs-api](https://github.com/scm4j/scm4j-vcs-api)
