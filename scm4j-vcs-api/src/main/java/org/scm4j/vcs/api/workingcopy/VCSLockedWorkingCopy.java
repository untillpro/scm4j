package org.scm4j.vcs.api.workingcopy;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.UUID;

public class VCSLockedWorkingCopy implements IVCSLockedWorkingCopy, AutoCloseable {
	
	private final IVCSRepositoryWorkspace vcsRepo;

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
	
	protected VCSLockedWorkingCopy (IVCSRepositoryWorkspace vcsRepo) throws IOException {
		this.vcsRepo = vcsRepo;
		init();
	}
	
	@Override
	public Boolean getCorrupted() {
		return corrupt;
	}

	private void init() throws IOException {
		File[] files = vcsRepo.getRepoFolder().listFiles();
		for (File file : files != null ? files : new File[0]) {
			if (file.isDirectory()) {
				lockFile = new File( vcsRepo.getRepoFolder(), LOCK_FILE_PREFIX + file.getName());
				if (!lockFile.exists()) {
					continue;
				}
				if (tryLockFile(lockFile)) {
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
	
	private Boolean tryLockFile(File file) throws IOException {
		try {
			lockFile(file);
			return true;
		} catch (OverlappingFileLockException | SecurityException | IOException e) {
			if (lockedStream != null) {
				lockedStream.close();
			}
			return false;
		}
	}

	private void createNewLockedWorkingCopy() throws IOException {
		String guid = UUID.randomUUID().toString();
		folder = new File(vcsRepo.getRepoFolder(), guid);
		folder.mkdirs();
		lockFile = new File(vcsRepo.getRepoFolder(), LOCK_FILE_PREFIX + folder.getName());
		System.out.println(lockFile.getPath());
		lockFile.createNewFile();
		lockFile(lockFile);
	}

	@Override
	public String toString() {
		return "LWC [folder=" + folder.toString() + ", corrupt=" + corrupt + ", state=" + state.toString() +"]";
	}

	@Override
	public void close() throws Exception {
		if (state != VCSLockedWorkingCopyState.LOCKED) {
			return;
		}
		
		fileLock.close();
		lockedStream.close();
		state = VCSLockedWorkingCopyState.OBSOLETE;
		if (corrupt) {
			FileUtils.deleteDirectory(folder);
			lockFile.delete();
		}
	}

	@Override
	public IVCSRepositoryWorkspace getVCSRepository() {
		return vcsRepo;
	}
}
