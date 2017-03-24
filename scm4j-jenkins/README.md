[![Release](https://jitpack.io/v/ProjectKaiser/pk-jenkins.svg)](https://jitpack.io/#ProjectKaiser/pk-jenkins)	

# Overview
Pk-jenkins is a tiny framework used to manage basic Jenkins tasks:
- Jobs copy, create, read, update, delete
- Run and read builds

# Terms
- Job
  - Alias of Project in Jenkins terms
- Job Detailed
  - An user-level Job description: name, builds list etc 
- Job Config Xml
  - A Jenkins-level Job description: schedule, used plugins config etc
- Queue Item
  - A Jenkins build queue element. Represents a separate build of a Job. Contains such fields as color, state(running, finished, stuck etc) etc 
  
# Using pk-jenkins Api
- Add github-hosted pk-jenkins project as maven dependency using [jitpack.io](https://jitpack.io/). As an example, add following to gradle.build file:
	```gradle
	allprojects {
		repositories {
			maven { url "https://jitpack.io" }
		}
	}
	
	dependencies {
		compile 'com.github.ProjectKaiser:pk-jenkins:master-SNAPSHOT'
	}
	```
- Create IJenkinsApi implementation class providing Jenkins server url, username and password
```java
	IJenkinsApi jenkins = new JenkinsApi("http://localhost:8080", "user", "password");
```
- `void createJob(String jobName, String jobConfigXML)`
  - Creates new job named `jobName` with provided job config xml. Use `getJobConfigXml()` to obtain one from an existing Job
- `JobDetailed getJobDetailed(String jobName) throws EPKJNotFound`
  - Returns Job Detailed object
- `Long enqueueBuild(String jobName)`
  - Initiates a new build of job named `jobName` and returns id of this build which could be used in `getBuild` method
- `QueueItem getBuild(Long buildId) throws EPKJNotFound`
  - Returns Queue item object
- `void updateJobConfigXml(String jobName, String configXml) throws EPKJNotFound`
  - Updates Job Config Xml. Xml manipulating is made by caller side, i.e.: `getJobConfigXml()`, then add\remove\change Xml elements, then `updateJobCOnfigXml()`
- `void copyJob(String srcName, String dstName) throws EPKJNotFound, EPKJExists`
  - Clones an existing Job
- `List<String> getJobsList()`
  - Returns list of all jobs on the server
- `String getJobConfigXml(String jobName) throws EPKJNotFound`
  - Returns Job Config Xml as a string
- `void deleteJob(String jobName) throws EPKJNotFound`
  - Deletes job
  
# Functional testing
A working Jenkins server is required to run functional tests. 
Also following environment vars or JVM vars must be defined: 
- PK_TEST_JENKINS_URL
  - URL to the Jenkins server used for tests
- TEST_JENKINS_USER
  - Jenkins username used for run tests
- PK_TEST_JENKINS_PASS
  - Password of Jenkins user used to run tests
  
To run functional tests just execute JenkinsApiTest class as JUnit test or run `gradle test`. All jobs created during testing are deleted after automatically.
Note: tests are skipped by gradle if `build` task is present in tasks graph.  