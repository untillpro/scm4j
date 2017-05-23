package org.scm4j.jenkins.api;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.scm4j.jenkins.api.exceptions.EPKJExists;
import org.scm4j.jenkins.api.exceptions.EPKJNotFound;
import org.scm4j.jenkins.api.exceptions.EPKJenkinsException;
import org.scm4j.jenkins.api.facade.IJenkinsApiFacade;
import org.scm4j.jenkins.api.facade.JenkinsApiHttpFacade;
import org.scm4j.jenkins.data.JobDetailed;
import org.scm4j.jenkins.data.JobListElement;
import org.scm4j.jenkins.data.QueueItem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class JenkinsApi implements IJenkinsApi {

	private IJenkinsApiFacade facade;

	public JenkinsApi(String baseAddress, String user, String password) {
		facade = new JenkinsApiHttpFacade(baseAddress, user, password);
	}

	@Override
	public Long enqueueBuild(String jobName) throws EPKJNotFound {
		String url = "job/" + encodeUrl(jobName) + "/build";
		HttpResponse resp = facade.getResponsePOST(url, null);
		Header[] headers = resp.getHeaders("Location");
		if (headers == null || headers.length == 0) {
			throw new EPKJenkinsException("Failed to obtain Location header from response");
		}
		String[] strs = headers[0].getValue().split("/");
		return Long.parseLong(strs[strs.length - 1]);
	}
	
	@Override
	public QueueItem getBuild(Long buildId) throws EPKJNotFound {
		String url = String.format("queue/item/%d/api/json?pretty=true", buildId);
		String queueItemJson = facade.getResponseContentGET(url);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		QueueItem queueItem = gson.fromJson(queueItemJson, QueueItem.class);
		return queueItem;
	}

	@Override
	public void copyJob(String srcName, String dstName) throws EPKJNotFound, EPKJExists {
		String url = "createItem";
		Map<String, String> q = new HashMap<String, String>();
		q.put("name", dstName);
		q.put("mode", "copy");
		q.put("from", srcName);
		url = appendQuery(url, q);
		facade.getResponsePOST(url, null);
	}

	@Override
	public void createJob(String jobName, String jobConfigXML) throws EPKJExists {
		String url = "createItem";
		Map<String, String> q = new HashMap<String, String>();
		q.put("name", jobName);
		url = appendQuery(url, q);
		facade.getResponsePOST(url, jobConfigXML);
	}

	private static String encodeUrl(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private static String appendQuery(String url, Map<String, String> query) {
		if (query != null && query.size() > 0) {
			url += "?";
			boolean first = true;
			for (String key : query.keySet()) {
				url += (first ? "" : "&") + encodeUrl(key) + "=" + encodeUrl(query.get(key));
				first = false;
			}
		}
		return url;
	}

	@Override
	public List<String> getJobsList() {
		String url = "api/json";
		Map<String, String> q = new HashMap<String, String>();
		q.put("tree", "jobs[name]");
		url = appendQuery(url, q);
		String json = facade.getResponseContentGET(url);
		Gson gson = new GsonBuilder().create();
		Type type = new TypeToken<Map<String, List<JobListElement>>>() {
		}.getType();

		Map<String, List<JobListElement>> jobsMap = gson.fromJson(json, type);
		List<JobListElement> jobs = jobsMap.get("jobs");
		List<String> res = new ArrayList<>();
		for (JobListElement job : jobs) {
			res.add(job.getName());
		}
		return res;
	}

	@Override
	public String getJobConfigXml(String jobName) throws EPKJNotFound {
		String url = "job/" + encodeUrl(jobName) + "/config.xml";
		return facade.getResponseContentGET(url);
	}

	@Override
	public void updateJobConfigXml(String jobName, String configXml) throws EPKJNotFound {
		String url = "job/" + encodeUrl(jobName) + "/config.xml";
		facade.getResponsePOST(url, configXml);
	}

	@Override
	public JobDetailed getJobDetailed(String jobName) throws EPKJNotFound {
		String url = "job/" + encodeUrl(jobName) + "/api/json?pretty=true";
		String json = facade.getResponseContentGET(url);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JobDetailed job = gson.fromJson(json, JobDetailed.class);
		return job;
	}

	@Override
	public void deleteJob(String jobName) throws EPKJNotFound {
		String url = "job/" + encodeUrl(jobName) + "/doDelete";
		facade.getResponsePOST(url, null);
	}

	
}
