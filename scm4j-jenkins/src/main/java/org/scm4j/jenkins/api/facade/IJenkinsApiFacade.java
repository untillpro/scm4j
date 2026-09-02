package org.scm4j.jenkins.api.facade;

import org.apache.http.HttpResponse;

public interface IJenkinsApiFacade {
	HttpResponse getResponseGET(String url);
	
	String getResponseContentGET(String url);

	HttpResponse getResponsePOST(String url, String entity);
}
