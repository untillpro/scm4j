package org.scm4j.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;

import java.io.File;
import java.io.IOException;

public class GitVCSUtils {
	private static final String DEFAULT_BRANCH_NAME = "main";

	public static Git createRepository(File repoDir) throws GitAPIException {
		return createRepository(repoDir, DEFAULT_BRANCH_NAME);
	}

	public static Git createRepository(File repoDir, String initialBranchName) throws GitAPIException {
		Git git = Git
				.init()
				.setDirectory(repoDir)
				.setBare(false)
				.call();
		try {
			if (initialBranchName != null) {
				RefUpdate.Result result = git.getRepository().updateRef(Constants.HEAD)
						.link(Constants.R_HEADS + initialBranchName);
				if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED
						&& result != RefUpdate.Result.NO_CHANGE) {
					throw new IOException("Could not set initial Git branch to " + initialBranchName + ": " + result);
				}
			}
		} catch (IOException e) {
			if (git != null) {
				git.close();
			}
			throw new JGitInternalException("Could not set initial Git branch to " + initialBranchName, e);
		}
		git
				.commit()
				.setMessage("Initial commit")
				.call();
		return git;
	}
}
