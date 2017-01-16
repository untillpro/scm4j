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
  - A Jenkins-level Job description: schtdule, used plugins config etc
- Queue Item
  - A build queue element. Represents a separate build for a task: color, state(running, finished, stuck etc) etc 
  
# Using pk-jenkins Api
- Create IJenkinsApi implementation class providing Jenkins server url, username and password
```java
	IJenkinsApi jenkins = new JenkinsApi("http://localhost:8080", "user", "password");
```
- `void createJob(String jobName, String jobConfigXML)`
  - Creates new job named `jobName` with provided job config xml. Use `getJobConfigXml()` to obtain one from an existing Job
- `JobDetailed getJobDetailed(String jobName) throws EPKJNotFound`
  - Returns Job Detailed object
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
  
#Functional testing
A working Jenkins server is required to run functional tests. 
Also following environment vars or JVM vars must be defined: 
- PK_TEST_JENKINS_URL
  - URL to the Jenkins server used for tests
- TEST_JENKINS_USER
  - Jenkins username used for run tests
- PK_TEST_JENKINS_PASS
  - Password of Jenkins user used for run tests
  
To run functional tests just execute JenkinsApiTest class as JUnit test. All jobs created during testing are deleted after automatically
 

    


