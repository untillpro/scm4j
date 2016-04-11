package com.projectkaiser.scm.vcs.api;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;

public abstract class AbstractVCS {
	protected String repoFolder;
	protected Log logger;
	protected String trunkUrl;
	protected String workspaceBasePath;
		
	public AbstractVCS(Log logger, String workspaceBasePath, String trunkUrl) {
		repoFolder = getRepoFolder(workspaceBasePath, trunkUrl);
		this.logger = logger;
		this.trunkUrl = trunkUrl;
		this.workspaceBasePath = workspaceBasePath;
	}
	
	private String getRepoFolder(String workspaceBasePath, String trunkUrl) {
		URI uri;
		try {
			uri = new URI(trunkUrl);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		String path = uri.getPath();
		path = path.replaceAll("[^a-zA-Z0-9.-]", "_");
		return FilenameUtils.concat(workspaceBasePath, path);
	}
}
