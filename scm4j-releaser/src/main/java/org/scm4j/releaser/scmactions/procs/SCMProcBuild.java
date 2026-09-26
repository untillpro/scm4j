package org.scm4j.releaser.scmactions.procs;

import lombok.SneakyThrows;
import org.scm4j.releaser.Version;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.*;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.exceptions.ENoBuilder;
import org.scm4j.releaser.exceptions.ENoReleaseBranch;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.WalkDirection;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class SCMProcBuild implements ISCMProc {

	private final IVCS vcs;
	private final Component comp;
	private final String releaseBranchName;
	private final Version versionToBuild;
	private final CachedStatuses cache;
	private final boolean delayedTag;
	private final VCSRepository repo;

	public SCMProcBuild(Component comp, CachedStatuses cache, boolean delayedTag, VCSRepository repo) {
		this.comp = comp;
		this.repo = repo;
		vcs = repo.getVCS();
		this.cache = cache;
		releaseBranchName = Utils.getReleaseBranchName(repo, cache.get(repo.getComponentLocation()).getNextVersion());
		versionToBuild = cache.get(repo.getComponentLocation()).getNextVersion();
		this.delayedTag = delayedTag;
	}

	@Override
	public void execute(IProgress progress) {
		VCSCommit buildCommit = getCommitToBuildOn();
		if (buildCommit == null) {
			throw new ENoReleaseBranch(releaseBranchName);
		}

		if (repo.getBuilder() == null) {
			throw new ENoBuilder(comp);
		}

		build(progress, buildCommit);

		tagBuild(progress, buildCommit);

		if (!delayedTag) {
			raisePatchVersion(progress);
		}

		ExtendedStatus existing = cache.get(repo.getComponentLocation());
		cache.replace(repo.getComponentLocation(), new ExtendedStatus(versionToBuild.toNextPatch(), existing.getStatus(),
				existing.getSubComponents(), comp, repo));

		progress.reportStatus(comp.getName() + " " + versionToBuild + " is built in " + releaseBranchName);
	}

	private VCSCommit getCommitToBuildOn() {
		VCSCommit headCommit = vcs.getHeadCommit(releaseBranchName);
		String subfolder = repo.getSubfolder();
		if (headCommit == null || subfolder.isEmpty()) {
			return headCommit;
		}
		List<VCSCommit> commits = vcs.getCommitsRange(releaseBranchName, headCommit.getRevision(),
				WalkDirection.DESC, 1, subfolder);
		return commits.isEmpty() ? null : commits.get(0);
	}

	@SneakyThrows
	private void build(IProgress progress, VCSCommit buildCommit) {
		File buildDir = Utils.getBuildDir(repo, versionToBuild);
		if (buildDir.exists()) {
			Utils.waitForDeleteDir(buildDir);
		}
		Files.createDirectories(buildDir.toPath());

		String statusMessage = String.format(" out %s on revision %s into %s", comp.getName(), buildCommit.getRevision(), buildDir.getPath());
		progress.reportStatus("checking" + statusMessage + "...");
		String subfolder = repo.getSubfolder();
		Utils.reportDuration(() -> {
			if (subfolder.isEmpty()) {
				vcs.checkout(releaseBranchName, buildDir.getPath(), buildCommit.getRevision());
			} else {
				vcs.sparseCheckout(releaseBranchName, buildDir.getPath(), buildCommit.getRevision(), subfolder);
			}
		}, "checked" + statusMessage, null, progress);
		Map<String, String> btev = Utils.getBuildTimeEnvVars(repo.getType(), buildCommit.getRevision(), releaseBranchName,
				repo.getUrl());
		File buildWorkingDir = subfolder.isEmpty() ? buildDir : new File(buildDir, subfolder);
		repo.getBuilder().build(comp, buildWorkingDir, progress, btev);
	}

	@SneakyThrows
	private void tagBuild(IProgress progress, VCSCommit buildCommit) {
		if (delayedTag) {
			DelayedTagsFile delayedTagsFile = new DelayedTagsFile();
			delayedTagsFile.writeDelayedTag(repo.getComponentLocation(), versionToBuild, buildCommit.getRevision());
			progress.reportStatus("build commit " + buildCommit.getRevision() + " is saved for delayed tagging");
		} else {
			TagDesc tagDesc = Utils.getTagDesc(repo, versionToBuild.toString());
			Utils.reportDuration(() -> vcs.createTag(releaseBranchName, tagDesc.getName(), tagDesc.getMessage(), buildCommit.getRevision()),
					String.format("tag head of %s: %s", releaseBranchName, tagDesc.getName()), null, progress);
		}
	}

	private void raisePatchVersion(IProgress progress) {
		Version nextPatchVersion = versionToBuild.toNextPatch();
		Utils.reportDuration(() -> vcs.setFileContent(releaseBranchName, repo.getComponentPath(Constants.VER_FILE_NAME),
				nextPatchVersion.toString(),
				Constants.SCM_VER + " " + nextPatchVersion),
				"bump patch version in release branch: " + nextPatchVersion, null, progress);
	}
}
