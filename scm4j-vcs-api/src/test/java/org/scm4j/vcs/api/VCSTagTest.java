package org.scm4j.vcs.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class VCSTagTest {
	
	private static final String TAG_AUTHOR = "tagAuthor";
	private static final String TAG_MESSAGE = "tagMessage";
	private static final String TAG_NAME = "tagName";
	private final VCSCommit RELATED_COMMIT = new VCSCommit("revision", "log mess", "author");;

	@Test
	public void testVCSTag() {
		VCSTag tag = new VCSTag(TAG_NAME, TAG_MESSAGE, TAG_AUTHOR, RELATED_COMMIT);
		assertEquals(tag.getRelatedCommit(), RELATED_COMMIT);
		assertEquals(tag.getAuthor(), TAG_AUTHOR);
		assertEquals(tag.getTagMessage(), TAG_MESSAGE);
		assertEquals(tag.getTagName(), TAG_NAME);
	}
	
	@Test
	public void testEualsAndHashCode() {
		EqualsVerifier.forClass(VCSTag.class).usingGetClass().verify();
	}

	@Test
	public void testToString() {
		VCSTag tag = new VCSTag(TAG_NAME, TAG_MESSAGE, TAG_AUTHOR, RELATED_COMMIT);
		assertTrue(tag.toString().contains(TAG_NAME));
		assertTrue(tag.toString().contains(TAG_MESSAGE));
		assertTrue(tag.toString().contains(TAG_AUTHOR));
		assertTrue(tag.toString().contains(RELATED_COMMIT.toString()));
	}

}
