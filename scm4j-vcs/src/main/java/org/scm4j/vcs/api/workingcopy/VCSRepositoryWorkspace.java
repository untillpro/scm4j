package org.scm4j.vcs.api.workingcopy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class VCSRepositoryWorkspace implements IVCSRepositoryWorkspace {

	private static final String UNPRINTABLE_CHAR_PLACEHOLDER = "_";
	private static final String HTTP_PREFIX = "http" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String HTTPS_PREFIX = "https" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_1 = "file" + String.join("", Collections.nCopies(2, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_2 = "file" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_3 = "file" + String.join("", Collections.nCopies(4, UNPRINTABLE_CHAR_PLACEHOLDER));
	private final IVCSWorkspace workspace;
	private final String repoUrl;
	private final String componentSubfolder;
	private File repoFolder;

	protected VCSRepositoryWorkspace(String repoUrl, IVCSWorkspace workspace) {
		this(repoUrl, null, workspace);
	}

	protected VCSRepositoryWorkspace(String repoUrl, String componentSubfolder, IVCSWorkspace workspace) {
		this.workspace = workspace;
		this.repoUrl = repoUrl;
		this.componentSubfolder = componentSubfolder == null ? "" : componentSubfolder;
		initRepoFolder();
	}

	@Override
	public IVCSWorkspace getWorkspace() {
		return workspace;
	}

	@Override
	public IVCSLockedWorkingCopy getVCSLockedWorkingCopy() throws IOException {
		return new VCSLockedWorkingCopy(this, !componentSubfolder.isEmpty());
	}

	private String getRepoFolderName() {
		String tmp = toFileSystemSafeName(repoUrl);
		if (tmp.toLowerCase().startsWith(HTTPS_PREFIX)) {
			tmp = tmp.substring(HTTPS_PREFIX.length());
		} else if (tmp.toLowerCase().startsWith(HTTP_PREFIX)) {
			tmp = tmp.substring(HTTP_PREFIX.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_3)) {
			tmp = tmp.substring(FILE_PREFIX_3.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_2)) {
			tmp = tmp.substring(FILE_PREFIX_2.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_1)) {
			tmp = tmp.substring(FILE_PREFIX_1.length());
		}
		if (!componentSubfolder.isEmpty()) {
			tmp += UNPRINTABLE_CHAR_PLACEHOLDER + toFileSystemSafeName(componentSubfolder);
		}
		return new File(workspace.getHomeFolder(), tmp).getPath().replace("\\", File.separator);
	}

	private static String toFileSystemSafeName(String value) {
		return value.replaceAll("[^a-zA-Z0-9.-]", UNPRINTABLE_CHAR_PLACEHOLDER);
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

	@Override
	public IVCSLockedWorkingCopy getVCSLockedWorkingCopyTemp() throws IOException {
		return new VCSLockedWorkingCopy(this, true);
	}
	

}
