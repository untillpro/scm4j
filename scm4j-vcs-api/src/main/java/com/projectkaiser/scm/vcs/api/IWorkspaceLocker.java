package com.projectkaiser.scm.vcs.api;

import java.io.File;
import java.io.FileOutputStream;

public interface IWorkspaceLocker {
	void clearLocks(File workPath);
	void lock(File workPath);
	void unLock(File workPath);
	FileOutputStream getLockedDir(File workPath);
}
