package com.projectkaiser.scm.jenkins.api;

import java.util.List;

import com.projectkaiser.scm.jenkins.data.JobDetailed;

public interface IJenkinsApi {

	void createJob(String jobName, String jobConfigXML);

	JobDetailed getJobDetailed(String jobName);

	void runJob(String jobName);

	void updateJobConfigXml(String jobName, String jobConfigXML);

	void copyJob(String srcName, String dstName);

	List<String> getJobsList();

	String getJobConfigXml(String jobName);
	
}
