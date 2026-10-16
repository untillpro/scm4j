package org.scm4j.vcs.api.workingcopy;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The Class VCSWorkspace.
 */
public class VCSWorkspace implements IVCSWorkspace {

	public static final String DEFAULT_WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"), "scm4j-vcs-workspaces").getPath();

	private final File folder;
	private final ConcurrentMap<String, VCSRepositoryWorkspace.SharedRepositoryState> repositories =
			new ConcurrentHashMap<>();

	@Override
	public IVCSRepositoryWorkspace getVCSRepositoryWorkspace(String repoUrl, String componentSubfolder) {
		String normalizedUrl = normalizeRepositoryUrl(repoUrl);
		VCSRepositoryWorkspace.SharedRepositoryState state = repositories.computeIfAbsent(normalizedUrl,
				ignored -> VCSRepositoryWorkspace.createSharedState(this, repoUrl));
		return new VCSRepositoryWorkspace(repoUrl, componentSubfolder, this, state);
	}

	@Override
	public File getHomeFolder() {
		return folder;
	}

	public VCSWorkspace(String workspacePath) {
		folder = new File(workspacePath);
		folder.mkdirs();
	}
	
	public VCSWorkspace() {
		this(DEFAULT_WORKSPACE_DIR);
	}

	private static String normalizeRepositoryUrl(String repoUrl) {
		String normalized = repoUrl == null ? "" : repoUrl.trim().replace('\\', '/');
		while (normalized.endsWith("/") && !normalized.endsWith("://") && normalized.length() > 1) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	@Override
	public String toString() {
		return folder.toString();
	}
}
