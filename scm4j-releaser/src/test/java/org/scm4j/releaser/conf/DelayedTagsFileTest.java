package org.scm4j.releaser.conf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.Version;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DelayedTagsFileTest {

	private static final String TEST_REVISION = "test revision";
	private static final Version TEST_VERSION = new Version("1.0");
	private static final Version SECOND_VERSION = new Version("2.0");
	private static final String TEST_URL = "test url";
	
	@Before
	@After
	public void setUp() {
		new DelayedTagsFile().delete();
	}

	@Test
	public void testGetRemoveContent() throws IOException {
		DelayedTagsFile dtf = new DelayedTagsFile();
		
		assertTrue(dtf.getContent().isEmpty());
		assertNull(dtf.getDelayedTagByUrl(TEST_URL));
		
		dtf.writeUrlDelayedTag(TEST_URL, TEST_VERSION, TEST_REVISION);
		assertEquals(TEST_REVISION, dtf.getDelayedTagByUrl(TEST_URL).getRevision());
		
		dtf.removeTagByUrl(TEST_URL);
		assertNull(dtf.getDelayedTagByUrl(TEST_URL));
	}

	@Test
	public void testDelayedTagsAreScopedByRepositorySubfolder() throws IOException {
		DelayedTagsFile dtf = new DelayedTagsFile();
		VCSRepositoryId first = new VCSRepositoryId(TEST_URL, "components/first");
		VCSRepositoryId second = new VCSRepositoryId(TEST_URL, "components/second");

		dtf.writeDelayedTag(first, TEST_VERSION, TEST_REVISION);
		dtf.writeDelayedTag(second, SECOND_VERSION, "second revision");

		assertEquals(TEST_VERSION, dtf.getDelayedTag(first).getVersion());
		assertEquals(SECOND_VERSION, dtf.getDelayedTag(second).getVersion());

		dtf.removeTag(first);
		assertNull(dtf.getDelayedTag(first));
		assertEquals(SECOND_VERSION, dtf.getDelayedTag(second).getVersion());
	}

	@Test
	public void testLegacyUrlOnlyContentIsReadable() throws IOException {
		DelayedTagsFile dtf = new DelayedTagsFile();
		dtf.saveContent(TEST_URL + ":\n  revision: " + TEST_REVISION + "\n  version: '" + TEST_VERSION + "'\n");

		assertEquals(TEST_VERSION, dtf.getDelayedTagByUrl(TEST_URL).getVersion());
	}
	
	
	@Test
	public void testGetContentException() throws IOException {
		DelayedTagsFile dtf = spy(new DelayedTagsFile());
		IOException testException = new IOException("test exception");
		
		dtf.writeUrlDelayedTag(TEST_URL, TEST_VERSION, TEST_REVISION);
		
		doThrow(testException).when(dtf).loadContent();
		
		try {
			dtf.getContent();
			fail();
		} catch (RuntimeException e) {
			assertEquals(testException, e.getCause());
		}
	}
	
	@Test
	public void testWriteUrlException() throws IOException {
		DelayedTagsFile dtf = spy(new DelayedTagsFile());
		IOException testException = new IOException("test exception");
		
		doThrow(testException).when(dtf).saveContent(anyString());
		
		try {
			dtf.writeUrlDelayedTag(TEST_URL, TEST_VERSION, TEST_REVISION);
			fail();
		} catch (RuntimeException e) {
			assertEquals(testException, e.getCause());
		}
	}
	
	@Test
	public void testToString() throws IOException {
		DelayedTagsFile dtf = spy(new DelayedTagsFile());
		assertEquals(DelayedTagsFile.MISSING_TO_STRING_MESSAGE, dtf.toString());
		
		dtf.writeUrlDelayedTag(TEST_URL, TEST_VERSION, TEST_REVISION);
		assertNotNull(dtf.toString());
		assertFalse(dtf.toString().equals(DelayedTagsFile.MISSING_TO_STRING_MESSAGE));
		
	}
}
