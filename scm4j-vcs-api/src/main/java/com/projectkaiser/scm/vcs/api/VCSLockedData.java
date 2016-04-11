package com.projectkaiser.scm.vcs.api;

import java.io.File;
import java.io.FileOutputStream;

public class VCSLockedData {
	private File folder;
	private FileOutputStream lockedStream;

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

	public VCSLockedData(File folder, FileOutputStream lockedStream) {
		super();
		this.folder = folder;
		this.lockedStream = lockedStream;
	}

}
