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
	private final String uuid;

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

	protected VCSLockedWorkingCopy (IVCSRepositoryWorkspace vcsRepo, boolean isTemp) throws IOException {
		this(vcsRepo, isTemp, null);
	}

	// used in tests only
	VCSLockedWorkingCopy(IVCSRepositoryWorkspace vcsRepo, boolean isTemp, String wcUUID) throws IOException {
		this.vcsRepo = vcsRepo;
		this.corrupt = isTemp;
		this.uuid = wcUUID == null ? UUID.randomUUID().toString() : wcUUID;
		init(isTemp);
	}

	@Override
	public Boolean getCorrupted() {
		return corrupt;
	}

	private void init(boolean force) throws IOException {
		File[] files = vcsRepo.getRepoFolder().listFiles();
		if (!force) {
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
			lockedStream = new FileOutputStream(file, false);
			fileLock = lockedStream.getChannel().tryLock();
			if (fileLock == null) {
				closeLockResources();
				return false;
			}
			return true;
		} catch (OverlappingFileLockException | SecurityException | IOException e) {
			closeLockResources(e);
			return false;
		}
	}

	private void createNewLockedWorkingCopy() throws IOException {
		folder = new File(vcsRepo.getRepoFolder(), uuid);
		lockFile = new File(vcsRepo.getRepoFolder(), LOCK_FILE_PREFIX + folder.getName());
		boolean lockFileCreated = lockFile.createNewFile();
		boolean folderCreated = false;
		try {
			// The directory is how other threads discover reusable working copies. Lock first so that
			// no thread can claim this working copy while it is still being created.
			lockFile(lockFile);
			folderCreated = folder.mkdir();
			if (!folderCreated) {
				throw new IOException("Failed to create working copy directory " + folder);
			}
		} catch (IOException | RuntimeException e) {
			closeLockResources(e);
			if (folderCreated && folder.exists() && !folder.delete()) {
				e.addSuppressed(new IOException("Failed to remove working copy directory " + folder));
			}
			if (lockFileCreated && lockFile.exists() && !lockFile.delete()) {
				e.addSuppressed(new IOException("Failed to remove working copy lock file " + lockFile));
			}
			throw e;
		}
	}

	private void closeLockResources() throws IOException {
		FileOutputStream stream = lockedStream;
		FileLock lock = fileLock;
		lockedStream = null;
		fileLock = null;
		try (FileOutputStream ignoredStream = stream; FileLock ignoredLock = lock) {
			// Resources are owned by the working-copy lease and released together.
		}
	}

	private void closeLockResources(Throwable failure) {
		try {
			closeLockResources();
		} catch (IOException e) {
			failure.addSuppressed(e);
		}
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

		closeLockResources();
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
