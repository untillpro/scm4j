package org.scm4j.jenkins.api;

import java.util.List;

import org.scm4j.jenkins.api.exceptions.EPKJExists;
import org.scm4j.jenkins.api.exceptions.EPKJNotFound;
import org.scm4j.jenkins.data.JobDetailed;
import org.scm4j.jenkins.data.QueueItem;

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
