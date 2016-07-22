package com.projectkaiser.scm.vcs.api.workingcopy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

public class VCSLockedWorkingCopy implements IVCSLockedWorkingCopy, AutoCloseable {
	
	private IVCSRepositoryWorkspace vcsRepo;

	public static final String LOCK_FILE_PREFIX = "lock_";

	private Boolean corrupt = false;
	private File folder;
	private FileOutputStream lockedStream;
	private File lockFile;
	private FileLock fileLock;
	private VCSLockedWorkingCopyState state = VCSLockedWorkingCopyState.NOT_INITIALIZED;
	
	public VCSLockedWorkingCopyState getState() {
		return state;
	}
	
	@Override
	public File getLockFile() {
		return lockFile;
	}

	public File getFolder() {
		return folder;
	}

	@Override
	public void setCorrupted(Boolean isCorrupt) {
		this.corrupt = isCorrupt;
	}
	
	/**
	 * Instantiates a new VCS locked working copy.
	 *
	 * @param vcsRepo the vcs repo
	 */
	public VCSLockedWorkingCopy (IVCSRepositoryWorkspace vcsRepo) {
		this.vcsRepo = vcsRepo;
		init();
	}
	
	/**
	 * Instantiates a new VCS locked working copy.
	 *
	 * @param workspacePath the workspace path
	 * @param repoUrl the repo url
	 */
	public VCSLockedWorkingCopy(String workspacePath, String repoUrl) {
		IVCSWorkspace w = new VCSWorkspace(workspacePath);
		IVCSRepositoryWorkspace r = new VCSRepositoryWorkspace(repoUrl, w);
		this.vcsRepo = r;
		init();
	}
	
	@Override
	public Boolean getCorrupted() {
		return corrupt;
	}

	private void init() {
		File[] files = vcsRepo.getRepoFolder().listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				lockFile = new File( vcsRepo.getRepoFolder(), LOCK_FILE_PREFIX + file.getName());
				if (!lockFile.exists()) {
					continue;
				}
				if (!tryLockFile(lockFile)) {
					continue;
				} else {
					folder = file;
					state = VCSLockedWorkingCopyState.LOCKED;
					return;
				}
			}
		}
		
		createNewLockedWorkingCopy();
		state = VCSLockedWorkingCopyState.LOCKED;
	}
	
	private void lockFile(File file) throws IOException {
		lockedStream = new FileOutputStream(file, false);
		fileLock = lockedStream.getChannel().lock();
	}
	
	private Boolean tryLockFile(File file) {
		try {
			lockFile(file);
			return true;
		} catch (OverlappingFileLockException | SecurityException | IOException e) {
			try {
				if (lockedStream != null) {
					lockedStream.close();
				}
			} catch (IOException e1) {
				throw new RuntimeException(e);
			}
			return false;
		}
	}

	private void createNewLockedWorkingCopy() {
		String guid = UUID.randomUUID().toString();
		folder = new File(vcsRepo.getRepoFolder(), guid);
		folder.mkdirs();
		lockFile = new File(vcsRepo.getRepoFolder(), LOCK_FILE_PREFIX + folder.getName());
		try {
			lockFile.createNewFile();
			lockFile(lockFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return "VCSWorkspace [corrupt=" + corrupt + ", state=" + state.toString() + ", folder=" + folder.toString() + "]";
	}

	@Override
	public void close() throws Exception {
		if (state != VCSLockedWorkingCopyState.LOCKED) {
			return;
		}
		
		try {
			fileLock.close();
			lockedStream.close();
			state = VCSLockedWorkingCopyState.OBSOLETE;
			if (corrupt) {
				FileUtils.deleteDirectory(folder);
				lockFile.delete();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public IVCSRepositoryWorkspace getVCSRepository() {
		return vcsRepo;
	}
}
