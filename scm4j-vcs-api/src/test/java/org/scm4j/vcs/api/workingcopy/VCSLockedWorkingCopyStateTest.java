package org.scm4j.vcs.api.workingcopy;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class VCSLockedWorkingCopyStateTest {

	@Test
	public void testBackwardCompatibility() {
		VCSLockedWorkingCopyState[] orderedEthalon = new VCSLockedWorkingCopyState[] {
				VCSLockedWorkingCopyState.NOT_INITIALIZED,
				VCSLockedWorkingCopyState.LOCKED,
				VCSLockedWorkingCopyState.OBSOLETE };
		assertTrue(Arrays.equals(orderedEthalon, Arrays.copyOfRange(VCSLockedWorkingCopyState.values(), 0, 3)));
	}
}
