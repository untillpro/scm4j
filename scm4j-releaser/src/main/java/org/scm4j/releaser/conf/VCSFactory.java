package org.scm4j.releaser.conf;

import org.scm4j.vcs.git.GitVCS;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.vcs.svn.SVNVCS;

public final class VCSFactory {

	public static IVCS getVCS(VCSType type, Credentials creds, String url, IVCSWorkspace ws) {
		return getVCS(type, creds, ws.getVCSRepositoryWorkspace(url));
	}

	public static IVCS getVCS(VCSType type, Credentials creds, String url, IVCSWorkspace ws,
			boolean reuseWorkingCopies) {
		return getVCS(type, creds, ws.getVCSRepositoryWorkspace(url, reuseWorkingCopies));
	}

	private static IVCS getVCS(VCSType type, Credentials creds, IVCSRepositoryWorkspace repoWorkspace) {
		IVCS vcs = null;
		switch (type) {
		case GIT: {
			vcs = new GitVCS(repoWorkspace);
			if (creds.getName() != null) {
					vcs.setCredentials(
							creds.getName(),
							creds.getPassword());
			}
			break;
		}
		case SVN: {
			vcs = new SVNVCS(repoWorkspace,
					creds == null ? null : creds.getName(),
					creds == null ? null : creds.getPassword());
			break;
		}
		}
		return vcs;
	}
}
