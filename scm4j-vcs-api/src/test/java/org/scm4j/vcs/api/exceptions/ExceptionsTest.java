package org.scm4j.vcs.api.exceptions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExceptionsTest {

	public static final String TEST_MESSAGE = "test message";

	@Test
	public void testEVCSException() {
		EVCSException e = new EVCSException(TEST_MESSAGE);
		assertTrue(e.getMessage().contains(TEST_MESSAGE));
		EVCSException e1 = new EVCSException(e);
		assertTrue(e1.getMessage().contains(TEST_MESSAGE));
		assertEquals(e1.getCause(), e);
	}

	@Test
	public void testEVCSBranchExists() {
		Exception e = new Exception(TEST_MESSAGE);
		EVCSBranchExists e1 = new EVCSBranchExists(e);
		assertTrue(e1.getMessage().contains(TEST_MESSAGE));
		assertEquals(e1.getCause(), e);
	}

	@Test
	public void testEVCSFileNotFound() {
		EVCSFileNotFound e = new EVCSFileNotFound(TEST_MESSAGE);
		assertTrue(e.getMessage().contains(TEST_MESSAGE));
		EVCSFileNotFound e1 = new EVCSFileNotFound(e);
		assertTrue(e1.getMessage().contains(TEST_MESSAGE));
		assertEquals(e1.getCause(), e);
	}
}
