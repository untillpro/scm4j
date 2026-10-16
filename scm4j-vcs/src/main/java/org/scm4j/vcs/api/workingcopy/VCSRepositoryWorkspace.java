package org.scm4j.vcs.api.workingcopy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class VCSRepositoryWorkspace implements IVCSRepositoryWorkspace {

	private static final String UNPRINTABLE_CHAR_PLACEHOLDER = "_";
	private static final String HTTP_PREFIX = "http" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String HTTPS_PREFIX = "https" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_1 = "file" + String.join("", Collections.nCopies(2, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_2 = "file" + String.join("", Collections.nCopies(3, UNPRINTABLE_CHAR_PLACEHOLDER));
	private static final String FILE_PREFIX_3 = "file" + String.join("", Collections.nCopies(4, UNPRINTABLE_CHAR_PLACEHOLDER));
	private final IVCSWorkspace workspace;
	private final String repoUrl;
	private final String componentSubfolder;
	private final SharedRepositoryState sharedState;

	protected VCSRepositoryWorkspace(String repoUrl, IVCSWorkspace workspace) {
		this(repoUrl, null, workspace);
	}

	protected VCSRepositoryWorkspace(String repoUrl, String componentSubfolder, IVCSWorkspace workspace) {
		this(repoUrl, componentSubfolder, workspace, createSharedState(workspace, repoUrl));
	}

	VCSRepositoryWorkspace(String repoUrl, String componentSubfolder, IVCSWorkspace workspace,
			SharedRepositoryState sharedState) {
		this.workspace = workspace;
		this.repoUrl = repoUrl;
		this.componentSubfolder = componentSubfolder == null ? "" : componentSubfolder;
		this.sharedState = sharedState;
	}

	@Override
	public IVCSWorkspace getWorkspace() {
		return workspace;
	}

	@Override
	public IVCSLockedWorkingCopy getVCSLockedWorkingCopy() throws IOException {
		return sharedState.allocateWorkingCopy(this);
	}

	static SharedRepositoryState createSharedState(IVCSWorkspace workspace, String repoUrl) {
		return new SharedRepositoryState(new File(workspace.getHomeFolder(), getRepoFolderName(repoUrl)));
	}

	private static String getRepoFolderName(String repoUrl) {
		String tmp = toFileSystemSafeName(repoUrl);
		if (tmp.toLowerCase().startsWith(HTTPS_PREFIX)) {
			tmp = tmp.substring(HTTPS_PREFIX.length());
		} else if (tmp.toLowerCase().startsWith(HTTP_PREFIX)) {
			tmp = tmp.substring(HTTP_PREFIX.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_3)) {
			tmp = tmp.substring(FILE_PREFIX_3.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_2)) {
			tmp = tmp.substring(FILE_PREFIX_2.length());
		} else if (tmp.toLowerCase().startsWith(FILE_PREFIX_1)) {
			tmp = tmp.substring(FILE_PREFIX_1.length());
		}
		return tmp;
	}

	private static String toFileSystemSafeName(String value) {
		return value.replaceAll("[^a-zA-Z0-9.-]", UNPRINTABLE_CHAR_PLACEHOLDER);
	}

	@Override
	public PreparedRepositorySnapshot getPreparedRepositorySnapshot(PreparedRepositoryInitializer initializer)
			throws Exception {
		return sharedState.getPreparedRepositorySnapshot(initializer);
	}

	@Override
	public <T> T withRepoLock(RepositoryMaintenance<T> maintenance) throws Exception {
		return sharedState.withRepoLock(maintenance);
	}

	@Override
	public void invalidatePreparedRepositorySnapshot() {
		sharedState.invalidatePreparedRepositorySnapshot();
	}

	@Override
	public File getRepoFolder() {
		return sharedState.repoFolder;
	}

	@Override
	public File getPreparedRepositoryFolder() {
		return sharedState.preparedRepositoryFolder;
	}

	@Override
	public File getWritableWorkingCopiesFolder() {
		return sharedState.writableWorkingCopiesFolder;
	}

	@Override
	public String getRepoUrl() {
		return repoUrl;
	}

	@Override
	public String getComponentSubfolder() {
		return componentSubfolder;
	}

	@Override
	public String toString() {
		return "VCSRepositoryWorkspace [workspace=" + workspace + ", repoUrl=" + repoUrl
				+ ", componentSubfolder=" + componentSubfolder + ", repoFolder=" + sharedState.repoFolder + "]";
	}

	static final class SharedRepositoryState {
		private static final String REPOSITORY_CACHE_FOLDER_NAME = "repository-cache";
		private static final String WRITABLE_WORKING_COPIES_FOLDER_NAME = "working-copies";

		private final File repoFolder;
		private final File preparedRepositoryFolder;
		private final File writableWorkingCopiesFolder;
		private final ReentrantLock repoLock = new ReentrantLock();
		private final AtomicReference<FutureTask<PreparedRepositorySnapshot>> preparedSnapshot =
				new AtomicReference<>();

		private SharedRepositoryState(File repoFolder) {
			this.repoFolder = repoFolder;
			preparedRepositoryFolder = new File(repoFolder, REPOSITORY_CACHE_FOLDER_NAME);
			writableWorkingCopiesFolder = new File(repoFolder, WRITABLE_WORKING_COPIES_FOLDER_NAME);
		}

		private IVCSLockedWorkingCopy allocateWorkingCopy(IVCSRepositoryWorkspace repository) throws IOException {
			repoLock.lock();
			try {
				return new VCSLockedWorkingCopy(repository);
			} finally {
				repoLock.unlock();
			}
		}

		private <T> T withRepoLock(RepositoryMaintenance<T> maintenance) throws Exception {
			repoLock.lock();
			try {
				return maintenance.execute();
			} finally {
				repoLock.unlock();
			}
		}

		private PreparedRepositorySnapshot getPreparedRepositorySnapshot(
				PreparedRepositoryInitializer initializer) throws Exception {
			FutureTask<PreparedRepositorySnapshot> task = preparedSnapshot.get();
			if (task == null) {
				FutureTask<PreparedRepositorySnapshot> candidate = new FutureTask<>(() -> withRepoLock(() -> {
					PreparedRepositorySnapshot snapshot = initializer.initialize(preparedRepositoryFolder);
					if (snapshot == null) {
						throw new IllegalStateException("Prepared repository initializer returned no snapshot metadata");
					}
					return snapshot;
				}));
				// Publish the task before initialization so siblings share this attempt.
				task = preparedSnapshot.updateAndGet(current -> current == null ? candidate : current);
			}
			task.run();
			try {
				return task.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			} catch (ExecutionException e) {
				preparedSnapshot.compareAndSet(task, null);
				Throwable failure = e.getCause();
				if (failure instanceof Exception) {
					throw (Exception) failure;
				}
				if (failure instanceof Error) {
					throw (Error) failure;
				}
				throw new RuntimeException(failure);
			}
		}

		private void invalidatePreparedRepositorySnapshot() {
			repoLock.lock();
			try {
				preparedSnapshot.set(null);
			} finally {
				repoLock.unlock();
			}
		}
	}

}
