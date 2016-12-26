package com.projectkaiser.scm.jenkins.api.facade;

public interface IJenkinsApiFacade {
	String getResponseGET(String url);

	String getResponsePOST(String url, String entity);
}
