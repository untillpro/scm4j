package org.scm4j.vcs.api;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class WalkDirectionTest {

	@Test
	public void testBackwardCompatibility() {
		WalkDirection[] orderedEthalon = new WalkDirection[] {
				WalkDirection.ASC,
				WalkDirection.DESC };
		assertTrue(Arrays.equals(orderedEthalon, Arrays.copyOfRange(WalkDirection.values(), 0, 2)));
	}
}
