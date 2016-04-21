package com.projectkaiser.scm.vcs.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

public class VCSWorkspace {
	private File folder;
	private Boolean isCorrupt = false;
	private FileOutputStream lockedFileStream;


	public File getFolder() {
		return folder;
	}

	public void setFolder(File folder) {
		this.folder = folder;
	}

	public Boolean getIsBroken() {
		return isCorrupt;
	}

	public void setIsCorrupt(Boolean isBroken) {
		this.isCorrupt = isBroken;
	}

	public VCSWorkspace(VCSLockedData lockData) {
		super();
		this.folder = lockData.getFolder();
		this.lockedFileStream = lockData.getLockedStream();
	}
	
	public void unlock() {
		try {
			lockedFileStream.close();
			if (isCorrupt) {
				FileUtils.deleteDirectory(folder);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static VCSWorkspace getLockedWorkspace(String workspaceBasePath) {
		File workspaceBaseFolder = new File(workspaceBasePath);
		if (!workspaceBaseFolder.exists()) {
			workspaceBaseFolder.mkdirs();
		}
		
		VCSLockedData data = getLockedDirStream(workspaceBaseFolder);
		return new VCSWorkspace(data);
	}

	private static VCSLockedData getLockedDirStream(File workspaceBaseFolder) {
		File[] files = workspaceBaseFolder.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				File lockFile = new File(workspaceBaseFolder, "lock_" + file.getName());
				try {
					if (!lockFile.exists()) {
						lockFile.createNewFile();
					}
					try {
						FileOutputStream s = new FileOutputStream(lockFile, false);
						VCSLockedData res = new VCSLockedData(file, s);
						return res;
					} catch (SecurityException e) {
						continue;
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		String guid = UUID.randomUUID().toString();
		File newFolder = new File(workspaceBaseFolder, guid);
		newFolder.mkdirs();
		FileOutputStream s;
		File lockFile = new File(workspaceBaseFolder, "lock_" + newFolder.getName());
		try {
			s = new FileOutputStream(lockFile, false);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		return new VCSLockedData(newFolder, s);
	}
}
