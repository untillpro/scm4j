package com.projectkaiser.scm.jenkins.api.facade;

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


public class JenkinsApiHttpFacade implements IJenkinsApiFacade {

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
	    if (null == s)
	        return true;
	    if (s.length() == 0)
	        return true;
	    return false;
	}

	private HttpResponse getResponse (HttpRequestBase request) {
		if (!isEmptyString(user) && !isEmptyString(password)) {
			String userpass = user + ":" + password;
			String basicAuth = "Basic " + new String(Base64.encodeBase64(userpass.getBytes()));
			request.setHeader("Authorization", basicAuth);
		}

		try {
			HttpResponse response = client.execute(request);
			try {
				processResponse(response);
				return response;
			} finally {
				request.releaseConnection();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
			String errorMes;
			if (response.containsHeader("X-Error")) {
				errorMes = response.getLastHeader("X-Error").getValue();
			} else if (response.containsHeader(HTTP.CONTENT_TYPE) && 
					response.getLastHeader(HTTP.CONTENT_TYPE).getValue().contains(("text/html"))) {
				try {
					errorMes = IOUtils.toString(response.getEntity().getContent());
				} catch(Exception e) {
					errorMes = "Failed to read response fom Jenkins server: " + e.getMessage();
				}
			} else {
				errorMes = "Unknown error";
			}
			throw new RuntimeException(
					String.format("Jenkins server returned code %d: %s", code, errorMes));
		}
	}

}
