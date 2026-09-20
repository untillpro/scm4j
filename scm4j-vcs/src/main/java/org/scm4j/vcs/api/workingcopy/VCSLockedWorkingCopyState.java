package org.scm4j.vcs.api.workingcopy;

public enum VCSLockedWorkingCopyState {
	/**
	 * NOT_INITIALIZED state of the Locked Working Copy is internal state which
	 * exists only during constructor executing. It means the
	 * {@link VCSLockedWorkingCopy} is not initialized and does not corresponds
	 * to a Working copy folder
	 */
	NOT_INITIALIZED,

	/**
	 * LOCKED state is normal state of a {@link VCSLockedWorkingCopy} instance.
	 * It means that current {@link VCSLockedWorkingCopy} instance corresponds
	 * to a locked folder.
	 */
	LOCKED,

	/**
	 * OBSOLETE state means that {@link IVCSLockedWorkingCopy#close()}
	 * had been called and current instance does not corresponds to a locked
	 * folder anymore. Instance of {@link VCSLockedWorkingCopy} with this state
	 * should not be used anymore.
	 */
	OBSOLETE
}
