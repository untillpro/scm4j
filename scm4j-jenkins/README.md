[![Release](https://jitpack.io/v/ProjectKaiser/pk-jenkins.svg)](https://jitpack.io/#ProjectKaiser/pk-jenkins)	

# Overview
Pk-jenkins is a framework used to communicate with Jenkins server exposing base Jenkins tasks management:
- Jobs copy, create, read, update, delete
- Run and read builds

# Terms
- Job
  - Alias of project in Jenkins terms
  
# Using pk-jenkins Api
- Create IJenkinsApi implementation class providing Jenkins server url, username and password
```java
	IJenkinsApi jenkins = new JenkinsApi("http://localhost:8080", "user", "password");
```
- `void createJob(String jobName, String jobConfigXML)`
  - Creates new job named `jobName` with provide job config xml. Use `getJobConfigXml()` to obtain one from an existing Job
  


