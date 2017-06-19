package org.scm4j.vcs.api;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class VCSMergeResultTest {
	
	private static final List<String> TEST_CONFILCTING_FILES = Arrays.asList("file 1", "file 2");
	
	@Test
	public void testVCSMergeResult() {
		VCSMergeResult res = new VCSMergeResult(false, TEST_CONFILCTING_FILES);
		assertFalse(res.getSuccess());
		assertTrue(res.getConflictingFiles().containsAll(TEST_CONFILCTING_FILES));
		assertEquals(res.getConflictingFiles().size(), TEST_CONFILCTING_FILES.size());
	}

}
