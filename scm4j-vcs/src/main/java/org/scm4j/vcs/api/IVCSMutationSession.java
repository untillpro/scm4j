/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.api;

import java.io.File;

import org.scm4j.vcs.api.workingcopy.IVCSLockedWorkingCopy;

/**
 * Release-scoped VCS access backed by one isolated component checkout.
 * <p>
 * Reads observe mutations performed earlier in this session. Successful remote writes invalidate
 * the owning prepared snapshot. Marking the session corrupt removes its derivative on close.
 */
public interface IVCSMutationSession extends IVCS, AutoCloseable {
	/**
	 * Returns a view whose {@code close()} leaves a session-owned working-copy lease open.
	 * Backend operations use this view in try-with-resources while the session remains the sole
	 * owner responsible for releasing the underlying lease.
	 */
	static IVCSLockedWorkingCopy nonClosingView(IVCSLockedWorkingCopy delegate) {
		return new IVCSLockedWorkingCopy() {
			@Override
			public org.scm4j.vcs.api.workingcopy.VCSLockedWorkingCopyState getState() {
				return delegate.getState();
			}

			@Override
			public org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace getVCSRepository() {
				return delegate.getVCSRepository();
			}

			@Override
			public File getFolder() {
				return delegate.getFolder();
			}

			@Override
			public Boolean getCorrupted() {
				return delegate.getCorrupted();
			}

			@Override
			public void setCorrupted(Boolean corrupted) {
				delegate.setCorrupted(corrupted);
			}

			@Override
			public File getLockFile() {
				return delegate.getLockFile();
			}

			@Override
			public void close() {
				// The mutation session owns the retained lease.
			}
		};
	}

	/** Returns the materialized component directory used by builders. */
	File getWorkingDirectory();

	/** Marks the isolated checkout unusable so it is deleted during {@link #close()}. */
	void setCorrupted(Boolean corrupted);

	@Override
	void close();
}
