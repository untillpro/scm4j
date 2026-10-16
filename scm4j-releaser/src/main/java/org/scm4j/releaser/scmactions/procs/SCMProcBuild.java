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
import org.scm4j.vcs.api.IVCSMutationSession;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.WalkDirection;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class SCMProcBuild implements ISCMProc {

	private final Component comp;
	private final String releaseBranchName;
	private final Version versionToBuild;
	private final CachedStatuses cache;
	private final boolean delayedTag;
	private final VCSRepository repo;

	public SCMProcBuild(Component comp, CachedStatuses cache, boolean delayedTag, VCSRepository repo) {
		this.comp = comp;
		this.repo = repo;
		this.cache = cache;
		releaseBranchName = Utils.getReleaseBranchName(repo, cache.get(repo.getComponentLocation()).getNextVersion());
		versionToBuild = cache.get(repo.getComponentLocation()).getNextVersion();
		this.delayedTag = delayedTag;
	}

	@Override
	public void execute(IProgress progress, IVCSMutationSession vcs) {
		VCSCommit buildCommit = getCommitToBuildOn(vcs);
		if (buildCommit == null) {
			throw new ENoReleaseBranch(releaseBranchName);
		}

		if (repo.getBuilder() == null) {
			throw new ENoBuilder(comp);
		}

		vcs.checkout(releaseBranchName, vcs.getWorkingDirectory().getPath(), null);
		build(progress, buildCommit, vcs.getWorkingDirectory());

		tagBuild(progress, vcs, buildCommit);

		if (!delayedTag) {
			raisePatchVersion(progress, vcs);
		}

		ExtendedStatus existing = cache.get(repo.getComponentLocation());
		cache.replace(repo.getComponentLocation(), new ExtendedStatus(versionToBuild.toNextPatch(), existing.getStatus(),
				existing.getSubComponents(), comp, repo));

		progress.reportStatus(comp.getName() + " " + versionToBuild + " is built in " + releaseBranchName);
	}

	private VCSCommit getCommitToBuildOn(IVCSMutationSession vcs) {
		VCSCommit headCommit = vcs.getHeadCommit(releaseBranchName);
		String subfolder = repo.getSubfolder();
		if (headCommit == null || subfolder.isEmpty()) {
			return headCommit;
		}
		List<VCSCommit> commits = vcs.getCommitsRange(releaseBranchName, headCommit.getRevision(),
				WalkDirection.DESC, 1, subfolder);
		return commits.isEmpty() ? null : commits.get(0);
	}

	private void build(IProgress progress, VCSCommit buildCommit, File buildWorkingDir) {
		String statusMessage = String.format(" %s on component revision %s in %s", comp.getName(),
				buildCommit.getRevision(), buildWorkingDir.getPath());
		progress.reportStatus("building" + statusMessage + "...");
		Map<String, String> btev = Utils.getBuildTimeEnvVars(repo.getType(), buildCommit.getRevision(), releaseBranchName,
				repo.getUrl());
		Utils.reportDuration(() -> runBuilder(buildWorkingDir, progress, btev),
				"built" + statusMessage, null, progress);
	}

	@SneakyThrows
	private void runBuilder(File buildWorkingDir, IProgress progress, Map<String, String> buildTimeEnvVars) {
		repo.getBuilder().build(comp, buildWorkingDir, progress, buildTimeEnvVars);
		// Keep results outside the disposable session without creating another checkout.
		File buildDir = Utils.getBuildDir(repo, versionToBuild);
		if (buildDir.exists()) {
			Utils.waitForDeleteDir(buildDir);
		}
		Path source = buildWorkingDir.toPath();
		Path target = new File(buildDir, repo.getSubfolder()).toPath();
		Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
				if (isVCSMetadata(directory)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(target.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (!isVCSMetadata(file)) {
					Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
				}
				return FileVisitResult.CONTINUE;
			}

			private boolean isVCSMetadata(Path path) {
				String name = path.getFileName().toString();
				return ".git".equals(name) || ".svn".equals(name);
			}
		});
	}

	@SneakyThrows
	private void tagBuild(IProgress progress, IVCSMutationSession vcs, VCSCommit buildCommit) {
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

	private void raisePatchVersion(IProgress progress, IVCSMutationSession vcs) {
		Version nextPatchVersion = versionToBuild.toNextPatch();
		Utils.reportDuration(() -> vcs.setFileContent(releaseBranchName, repo.getComponentPath(Constants.VER_FILE_NAME),
				nextPatchVersion.toString(),
				Constants.SCM_VER + " " + nextPatchVersion),
				"bump patch version in release branch: " + nextPatchVersion, null, progress);
	}
}
