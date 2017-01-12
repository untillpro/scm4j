package com.projectkaiser.scm.jenkins.api;

import java.util.List;

import com.projectkaiser.scm.jenkins.data.JobDetailed;
import com.projectkaiser.scm.jenkins.data.QueueItem;

public interface IJenkinsApi {

	void createJob(String jobName, String jobConfigXML);

	JobDetailed getJobDetailed(String jobName);

	Long enqueueBuild(String jobName);
	
	QueueItem getBuild(Long buildId);

	void updateJobConfigXml(String jobName, String configXml);

	void copyJob(String srcName, String dstName);

	List<String> getJobsList();

	String getJobConfigXml(String jobName);
	
	void deleteJob(String jobName);
	
}
