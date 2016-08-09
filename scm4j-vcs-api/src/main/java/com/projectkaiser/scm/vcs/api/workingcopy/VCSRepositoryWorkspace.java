package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.FilenameUtils;

public class VCSRepositoryWorkspace implements IVCSRepositoryWorkspace {

	IVCSWorkspace workspace;
	String repoUrl;
	File repoFolder;

	protected VCSRepositoryWorkspace(String repoUrl, IVCSWorkspace workspace) {
		this.workspace = workspace;
		this.repoUrl = repoUrl;
		initRepoFolder();
	}

	@Override
	public IVCSWorkspace getWorkspace() {
		return workspace;
	}

	public void setWorkspace(IVCSWorkspace workspace) {
		this.workspace = workspace;
	}

	@Override
	public IVCSLockedWorkingCopy getVCSLockedWorkingCopy() {
		return new VCSLockedWorkingCopy(this);
	}

	private String getRepoFolderName() {
		URI uri;
		try {
			uri = new URI(repoUrl.replace("\\", "/"));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		String path = uri.getPath();
		path = path.replaceAll("[^a-zA-Z0-9.-]", "_");
		return FilenameUtils.concat(workspace.getHomeFolder().getPath(), path);
	}

	private void initRepoFolder() {
		String repoFolderName = getRepoFolderName();
		repoFolder = new File(repoFolderName);
		repoFolder.mkdirs();
		// FIXME: check multithreading
	}

	@Override
	public File getRepoFolder() {
		return repoFolder;
	}

	@Override
	public String getRepoUrl() {
		return repoUrl;
	}

}
