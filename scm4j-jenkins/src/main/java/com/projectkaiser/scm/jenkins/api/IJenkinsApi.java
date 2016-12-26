package com.projectkaiser.scm.jenkins.api;

import java.util.List;

import com.projectkaiser.scm.jenkins.api.exceptions.EPKJExists;
import com.projectkaiser.scm.jenkins.api.exceptions.EPKJNotFound;
import com.projectkaiser.scm.jenkins.data.JobDetailed;
import com.projectkaiser.scm.jenkins.data.QueueItem;

public interface IJenkinsApi {

	void createJob(String jobName, String jobConfigXML) throws EPKJExists;

	JobDetailed getJobDetailed(String jobName) throws EPKJNotFound;

	Long enqueueBuild(String jobName) throws EPKJNotFound;
	
	QueueItem getBuild(Long buildId) throws EPKJNotFound;

	void updateJobConfigXml(String jobName, String configXml) throws EPKJNotFound;

	void copyJob(String srcName, String dstName) throws EPKJNotFound, EPKJExists;

	List<String> getJobsList();

	String getJobConfigXml(String jobName) throws EPKJNotFound;
	
	void deleteJob(String jobName) throws EPKJNotFound;
	
}
