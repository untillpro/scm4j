package org.scm4j.vcs.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VCSChangeListNodeTest {

	public static final String FILE_PATH = "file path";
	public static final String CONTENT = "content";
	public static final String LOG_MESSAGE = "logMessage";

	@Test
	public void testVCSChangeListNode() {
		VCSChangeListNode node = new VCSChangeListNode(FILE_PATH, CONTENT, LOG_MESSAGE);
		assertEquals(FILE_PATH, node.getFilePath());
		assertEquals(CONTENT, node.getContent());
		assertEquals(LOG_MESSAGE, node.getLogMessage());
	}

}