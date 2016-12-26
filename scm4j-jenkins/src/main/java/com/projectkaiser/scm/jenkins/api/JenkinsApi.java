package com.projectkaiser.scm.jenkins.api;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.projectkaiser.scm.jenkins.api.facade.IJenkinsApiFacade;
import com.projectkaiser.scm.jenkins.api.facade.JenkinsApiHttpFacade;
import com.projectkaiser.scm.jenkins.data.JobDetailed;

/*
    http://localhost:8080/hudson/job/toolkit/api/json
    http://gmpxp:8080/hudson/api/json?tree=jobs[name]
    http://localhost:8080/hudson/job/toolkit-2/config.xml
*/

public class JenkinsApi implements IJenkinsApi {

	private IJenkinsApiFacade facade;

	public JenkinsApi(String baseAddress, String user, String password) {
		facade = new JenkinsApiHttpFacade(baseAddress, user, password);
	}

	@Override
	public void runJob(String jobName) {
		String url = "job/" + encodeUrl(jobName) + "/build";
		facade.getResponsePOST(url, null);
	}

	@Override
	public void copyJob(String srcName, String dstName) {
		String url = "createItem";
		Map<String, String> q = new HashMap<String, String>();
		q.put("name", dstName);
		q.put("mode", "copy");
		q.put("from", srcName);
		url = appendQuery(url, q);
		facade.getResponsePOST(url, null);
	}

	@Override
	public void createJob(String jobName, String jobConfigXML) {
		String url = "createItem";
		Map<String, String> q = new HashMap<String, String>();
		q.put("name", jobName);
		url = appendQuery(url, q);
		facade.getResponsePOST(url, null);
	}

	private static String encodeUrl(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8");
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
		String json = facade.getResponseGET(url);
		Gson gson = new GsonBuilder().create();
		Type type = new TypeToken<Map<String, Map<String, String>>>() {
		}.getType();

		Map<String, Map<String, String>> res = gson.fromJson(json, type);
		return new ArrayList(res.get("jobs").values());
	}

	@Override
	public String getJobConfigXml(String jobName) {
		String url = "job/" + encodeUrl(jobName) + "/config.xml";
		return facade.getResponseGET(url);
	}

	@Override
	public void updateJobConfigXml(String jobName, String jobConfigXML) {
		String url = "job/" + encodeUrl(jobName) + "/config.xml";
		facade.getResponsePOST(url, jobConfigXML);
	}

	@Override
	public JobDetailed getJobDetailed(String jobName) {
		String url = "job/" + encodeUrl(jobName) + "/api/json?pretty=true";
		String json = facade.getResponseGET(url);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JobDetailed job = gson.fromJson(json, JobDetailed.class);
		return job;
	}
}
