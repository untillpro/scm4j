package org.scm4j.vcs.api;

import static org.junit.Assert.*;

import org.junit.Test;

public class VCSDiffEntryTest {
	
	private static final String TEST_UNIFIED_DIFF = "unified diff";
	private static final String TEST_FILE_PATH = "file path";

	@Test
	public void testVCSDiffEntry() {
		VCSDiffEntry entry = new VCSDiffEntry(TEST_FILE_PATH, VCSChangeType.ADD, TEST_UNIFIED_DIFF);
		assertEquals(entry.getChangeType(), VCSChangeType.ADD);
		assertEquals(entry.getFilePath(), TEST_FILE_PATH);
		assertEquals(entry.getUnifiedDiff(), TEST_UNIFIED_DIFF);
	}
	
	@Test
	public void testToString() {
		VCSDiffEntry entry = new VCSDiffEntry(TEST_FILE_PATH, VCSChangeType.ADD, TEST_UNIFIED_DIFF);
		assertTrue(entry.toString().contains(TEST_FILE_PATH));
		assertTrue(entry.toString().contains(VCSChangeType.ADD.toString()));
	}

}
