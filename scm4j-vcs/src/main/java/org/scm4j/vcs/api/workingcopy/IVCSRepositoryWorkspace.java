package org.scm4j.vcs.api.workingcopy;

import java.io.File;
import java.io.IOException;

public interface IVCSRepositoryWorkspace {

	/**
	 * Marker implemented by immutable, backend-owned metadata for one prepared repository generation.
	 */
	interface PreparedRepositorySnapshot {
	}

	@FunctionalInterface
	interface PreparedRepositoryInitializer {
		PreparedRepositorySnapshot initialize(File preparedRepositoryFolder) throws Exception;
	}

	@FunctionalInterface
	interface RepositoryMaintenance<T> {
		T execute() throws Exception;
	}

	/**
	 * Allocates an exclusively leased writable working copy. This directory is never the prepared
	 * repository used for concurrent status reads.
	 */
	IVCSLockedWorkingCopy getVCSLockedWorkingCopy() throws IOException;

	/**
	 * Returns the published prepared snapshot, initializing it once when necessary. Concurrent first
	 * callers wait for the same initialization result. After publication, reads return without taking
	 * the repository lock.
	 */
	PreparedRepositorySnapshot getPreparedRepositorySnapshot(PreparedRepositoryInitializer initializer)
			throws Exception;

	/**
	 * Runs repository initialization, repair, refresh, or derivative creation exclusively. Snapshot
	 * reads must not use this method.
	 */
	<T> T withRepoLock(RepositoryMaintenance<T> maintenance) throws Exception;

	/**
	 * Invalidates the current prepared generation after a successful repository mutation.
	 */
	void invalidatePreparedRepositorySnapshot();

	/**
	 * Returns the URL-derived directory that owns the canonical prepared repository and writable pool.
	 */
	File getRepoFolder();

	/**
	 * Returns the {@code repository-cache} directory used to store the shared Git clone.
	 * SVN snapshots do not materialize this directory.
	 */
	File getPreparedRepositoryFolder();

	File getWritableWorkingCopiesFolder();

	IVCSWorkspace getWorkspace();

	String getRepoUrl();

	/**
	 * Returns the logical repository-relative component path, or an empty string for repository root.
	 */
	String getComponentSubfolder();

}
