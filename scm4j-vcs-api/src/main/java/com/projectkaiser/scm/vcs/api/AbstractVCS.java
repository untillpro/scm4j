package com.projectkaiser.scm.vcs.api;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;

public abstract class AbstractVCS {
	protected String repoFolder;
	protected Log logger;
	protected String baseUrl;
	protected String workspaceBasePath;
		
	public AbstractVCS(Log logger, String workspaceBasePath, String baseUrl) {
		repoFolder = buildRepoFolder(workspaceBasePath, baseUrl);
		this.logger = logger;
		this.baseUrl = baseUrl;
		this.workspaceBasePath = workspaceBasePath;
	}
	
	private String buildRepoFolder(String workspaceBasePath, String baseUrl) {
		URI uri;
		try {
			uri = new URI(baseUrl);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		String path = uri.getPath();
		path = path.replaceAll("[^a-zA-Z0-9.-]", "_");
		return FilenameUtils.concat(workspaceBasePath, path);
	}
	
	public String getRepoFolder() {
		return repoFolder;
	}
	
	public String getBaseUrl() {
		return baseUrl;
	}
}
