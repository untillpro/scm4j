package org.scm4j.jenkins.api.facade;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HTTP;
import org.scm4j.jenkins.api.exceptions.EPKJExists;
import org.scm4j.jenkins.api.exceptions.EPKJNotFound;
import org.scm4j.jenkins.api.exceptions.EPKJenkinsServerException;


public class JenkinsApiHttpFacade implements IJenkinsApiFacade {

	private static final String JOB_EXISTS_MESSAGE = "A job already exists with the name";
	private static final CharSequence NO_SUCH_JOB_MESSAGE = "No such job";
	private CloseableHttpClient client;
	private String baseAddress;
	private String user;
	private String password;

	public CloseableHttpClient getClient() {
		return client;
	}

	public void setClient(CloseableHttpClient client) {
		this.client = client;
	}

	public String getBaseAddress() {
		if (baseAddress.trim().charAt(baseAddress.trim().length() - 1) != '/') {
			return baseAddress + "/";
		} else {
			return baseAddress;
		}
	}

	public void setBaseAddress(String baseAddress) {
		this.baseAddress = baseAddress;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public JenkinsApiHttpFacade(String baseAddress, String user, String password) {
		this.baseAddress = baseAddress;
		this.user = user;
		this.password = password;
		client = HttpClientBuilder.create().build();
	}
	
	public static boolean isEmptyString(String s) {
		return s == null || s.isEmpty();
	}

	private HttpResponse getResponse (HttpRequestBase request) {
		if (!isEmptyString(user) && !isEmptyString(password)) {
			String userpass = user + ":" + password;
			String basicAuth = "Basic " + new String(Base64.encodeBase64(userpass.getBytes()));
			request.setHeader("Authorization", basicAuth);
		}
		
		HttpResponse response;
		try {
			response = client.execute(request);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			request.releaseConnection();
		}
		
		processResponse(response);
		return response;
	}
	
	private String responseToString(HttpResponse response) {
		try {
			return IOUtils.toString(response.getEntity().getContent());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getResponseContentGET(String url) {
		return responseToString(getResponseGET(url));
	}
	
	@Override
	public HttpResponse getResponseGET(String url) {
		HttpGet request = new HttpGet(getBaseAddress() + url);
		return getResponse(request);
	}

	@Override
	public HttpResponse getResponsePOST(String url, String entity) {
		HttpPost request = new HttpPost(getBaseAddress() + url);
		if (entity != null) {
			request.setEntity(new StringEntity(entity, ContentType.create("text/xml", "utf-8")));
		} else {
			request.setHeader(HTTP.CONTENT_TYPE, "application/xml");
		}
		return getResponse(request);
	}

	private void processResponse(HttpResponse response) {
		int code = response.getStatusLine().getStatusCode();
		if (code < HttpStatus.SC_OK || code >= HttpStatus.SC_BAD_REQUEST) {
			if (code == HttpStatus.SC_NOT_FOUND) {
				throw new EPKJNotFound(code, "Resource not found");
			}
			if (response.containsHeader("X-Error")) {
				String errorMes = response.getLastHeader("X-Error").getValue();
				if (errorMes.contains(JOB_EXISTS_MESSAGE)) {
					throw new EPKJExists(code, errorMes);
				} else if(errorMes.contains(NO_SUCH_JOB_MESSAGE)) {
					throw new EPKJNotFound(code, errorMes);
				}
			} else {
				throw new EPKJenkinsServerException(code, "Server error");
			}
		}
	}
}
