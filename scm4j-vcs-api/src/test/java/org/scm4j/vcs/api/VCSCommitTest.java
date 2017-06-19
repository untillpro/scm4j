package org.scm4j.vcs.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class VCSCommitTest {

	private static final String TEST_AUTHOR = "author";
	private static final String TEST_LOG_MES = "log_mes";
	private static final String TEST_REV = "rev";

	@Test
	public void testVCSCommit() {
		VCSCommit commit = new VCSCommit(TEST_REV, TEST_LOG_MES, TEST_AUTHOR);
		assertEquals(commit.getAuthor(), TEST_AUTHOR);
		assertEquals(commit.getLogMessage(), TEST_LOG_MES);
		assertEquals(commit.getRevision(), TEST_REV);
		assertTrue(commit.toString().contains(TEST_AUTHOR));
		assertTrue(commit.toString().contains(TEST_LOG_MES));
		assertTrue(commit.toString().contains(TEST_REV));
	}

	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier.forClass(VCSCommit.class).usingGetClass().verify();
	}

	@Test
	public void testEmptyVCSCommit() {
		VCSCommit empty = VCSCommit.EMPTY;
		assertNull(empty.getAuthor());
		assertNull(empty.getLogMessage());
		assertNull(empty.getRevision());
		assertEquals(empty.toString(), "EMPTY");

	}
}
