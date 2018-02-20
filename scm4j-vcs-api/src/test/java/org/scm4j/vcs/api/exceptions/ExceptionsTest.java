package org.scm4j.vcs.api.exceptions;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExceptionsTest {

	public static final String TEST_MESSAGE = "test message";
	public static final String TEST_REPO_URL = "test repo url";
	public static final String TEST_BRANCH_NAME = "test branchName";

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
		EVCSBranchExists e = new EVCSBranchExists(TEST_BRANCH_NAME);
		assertTrue(e.getMessage().contains(TEST_BRANCH_NAME));
	}

	@Test
	public void testEVCSFileNotFound() {
		EVCSFileNotFound e = new EVCSFileNotFound("test repo", "test file", "test revision");
		assertFalse(e.getMessage().isEmpty());
	}
	
	@Test
	public void testEVCSTagExists() {
		Exception e = new Exception(TEST_MESSAGE);
		EVCSTagExists e1 = new EVCSTagExists(e);
		assertTrue(e1.getMessage().contains(TEST_MESSAGE));
		assertEquals(e1.getCause(), e);
	}

	@Test
	public void testEVCSBranchNotFound() {
		EVCSBranchNotFound e = new EVCSBranchNotFound(TEST_REPO_URL, TEST_BRANCH_NAME);
		assertTrue(e.getMessage().contains(TEST_REPO_URL));
		assertTrue(e.getMessage().contains(TEST_BRANCH_NAME));
	}
}
