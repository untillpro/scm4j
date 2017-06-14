package org.scm4j.vcs.api.workingcopy;

import java.io.File;

public class VCSRepositoryWorkspace implements IVCSRepositoryWorkspace {

	private final IVCSWorkspace workspace;
	private final String repoUrl;
	private File repoFolder;

	protected VCSRepositoryWorkspace(String repoUrl, IVCSWorkspace workspace) {
		this.workspace = workspace;
		this.repoUrl = repoUrl;
		initRepoFolder();
	}

	@Override
	public IVCSWorkspace getWorkspace() {
		return workspace;
	}

	@Override
	public IVCSLockedWorkingCopy getVCSLockedWorkingCopy() {
		return new VCSLockedWorkingCopy(this);
	}

	private String getRepoFolderName() {
		String tmp = repoUrl.replaceAll("[^a-zA-Z0-9.-]", "_");
		return new File(workspace.getHomeFolder(), tmp).getPath().replace("\\", File.separator);
	}

	private void initRepoFolder() {
		String repoFolderName = getRepoFolderName();
		repoFolder = new File(repoFolderName);
		repoFolder.mkdirs();
	}

	@Override
	public File getRepoFolder() {
		return repoFolder;
	}

	@Override
	public String getRepoUrl() {
		return repoUrl;
	}

	@Override
	public String toString() {
		return "VCSRepositoryWorkspace [workspace=" + workspace + ", repoUrl=" + repoUrl + ", repoFolder=" + repoFolder
				+ "]";
	}

}
