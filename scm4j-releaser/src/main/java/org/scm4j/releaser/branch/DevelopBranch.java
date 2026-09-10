package org.scm4j.releaser.branch;

import org.scm4j.commons.Version;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.exceptions.ENoVersionFile;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.WalkDirection;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;

import java.util.List;

public class DevelopBranch {

	private final VCSRepository repo;
	private final Component comp;
	
	public DevelopBranch(Component comp, VCSRepository repo) {
		this.comp = comp;
		this.repo = repo;
	}

	public boolean isModified() {
		String subfolder = repo.getRepositoryId().getSubfolder();
		List<VCSCommit> log;
		if (subfolder.isEmpty()) {
			// Keep the optimized repository-wide query. For Git, getCommitsRange performs a pull, fetch,
			// and checkout; for SVN, it resolves both history boundaries before requesting the range.
			log = repo.getVCS().log(repo.getDevelopBranch(), 1);
		} else {
			// Component history requires the repository-relative path filter provided by getCommitsRange.
			log = repo.getVCS().getCommitsRange(repo.getDevelopBranch(), null, WalkDirection.DESC, 1, subfolder);
		}
		if (log.isEmpty()) {
			return false;
		}
		VCSCommit lastCommit = log.get(0);
		return !(lastCommit.getLogMessage().contains(Constants.SCM_IGNORE) || lastCommit.getLogMessage().contains(Constants.SCM_VER));
	}
	
	public Version getVersion() {
		try {
			String subfolder = repo.getRepositoryId().getSubfolder();
			String versionFilePath = subfolder.isEmpty()
					? Constants.VER_FILE_NAME
					: subfolder + "/" + Constants.VER_FILE_NAME;
			String verFileContent = repo.getVCS().getFileContent(repo.getDevelopBranch(), versionFilePath, null);
			return new Version(verFileContent.trim());
		} catch (EVCSFileNotFound e) {
			throw new ENoVersionFile(comp);
		}
	}
}
