package org.scm4j.releaser.scmactions.procs;

import org.scm4j.releaser.Version;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.*;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.exceptions.ENoVersionFile;
import org.scm4j.vcs.api.IVCSMutationSession;
import org.scm4j.vcs.api.VCSChangeListNode;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;

import java.util.List;

public class SCMProcForkBranch implements ISCMProc {

	private final Component comp;
	private final ExtendedStatus status;
	private final String newBranchName;
	private final VCSRepository repo;
	private final List<VCSChangeListNode> vcsChangeList;

	public SCMProcForkBranch(Component comp, CachedStatuses cache, VCSRepository repo, List<VCSChangeListNode> vcsChangeList) {
		this.comp = comp;
		this.repo = repo;
		status = cache.get(repo.getComponentLocation());
		newBranchName = Utils.getReleaseBranchName(repo, status.getNextVersion());
		this.vcsChangeList = vcsChangeList;
	}

	@Override
	public void execute(IProgress progress, IVCSMutationSession vcs) {
		createBranch(progress, vcs);

		truncateSnapshotReleaseVersion();

		bumpTrunkMinorVersion(progress, vcs);
	}

	private void createBranch(IProgress progress, IVCSMutationSession vcs) {
		Utils.reportDuration(() -> vcs.createBranch(repo.getDevelopBranch(), newBranchName, "release branch created"),
				"create branch " + newBranchName, null, progress);
	}

	private void truncateSnapshotReleaseVersion() {
		String noSnapshotVersion = status.getNextVersion().toString();
		vcsChangeList.add(new VCSChangeListNode(repo.getComponentPath(Constants.VER_FILE_NAME), noSnapshotVersion,
				Constants.SCM_VER + " " + noSnapshotVersion));
	}

	private void bumpTrunkMinorVersion(IProgress progress, IVCSMutationSession vcs) {
		Version newMinorVersion = getDevelopmentVersion(vcs).toNextMinor();
		Utils.reportDuration(() -> vcs.setFileContent(repo.getDevelopBranch(), repo.getComponentPath(Constants.VER_FILE_NAME),
				newMinorVersion.toString(), Constants.SCM_VER + " " + newMinorVersion),
				"change to version " + newMinorVersion + " in trunk", null, progress);
	}

	private Version getDevelopmentVersion(IVCSMutationSession vcs) {
		try {
			String version = vcs.getFileContent(repo.getDevelopBranch(),
					repo.getComponentPath(Constants.VER_FILE_NAME), null);
			return new Version(version.trim());
		} catch (EVCSFileNotFound e) {
			throw new ENoVersionFile(comp);
		}
	}
}
