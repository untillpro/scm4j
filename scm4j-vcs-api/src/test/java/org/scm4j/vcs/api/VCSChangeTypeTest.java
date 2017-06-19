package org.scm4j.vcs.api;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

public class VCSChangeTypeTest {
	
	@Test
	public void testBackwardCompatibility() {
		VCSChangeType[] orderedEthalon = new VCSChangeType[] {
				VCSChangeType.UNKNOWN,
				VCSChangeType.ADD,
				VCSChangeType.MODIFY,
				VCSChangeType.DELETE };
		assertTrue(Arrays.equals(orderedEthalon, Arrays.copyOfRange(VCSChangeType.values(), 0, 4)));
	}
}
