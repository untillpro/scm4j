package com.projectkaiser.scm.vcs.api;

import java.io.File;
import java.io.FileOutputStream;

public class VCSLockData {
	private File folder;
	private FileOutputStream lockedStream;
	private File lockFile;

	public File getLockFile() {
		return lockFile;
	}

	public void setLockFile(File lockFile) {
		this.lockFile = lockFile;
	}

	public File getFolder() {
		return folder;
	}

	public void setFolder(File folder) {
		this.folder = folder;
	}

	public FileOutputStream getLockedStream() {
		return lockedStream;
	}

	public void setLockedStream(FileOutputStream lockedStream) {
		this.lockedStream = lockedStream;
	}

	public VCSLockData(File folder, FileOutputStream lockedStream, File lockFile) {
		this.folder = folder;
		this.lockedStream = lockedStream;
		this.lockFile = lockFile;
	}

}
