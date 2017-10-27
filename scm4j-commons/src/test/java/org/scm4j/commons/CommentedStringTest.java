package org.scm4j.commons;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommentedStringTest {

	@Test
	public void testGetComment() {
		assertEquals("#comment", new CommentedString("dsfdf#comment").getComment());
		assertEquals("#comment", new CommentedString("#comment").getComment());
		assertEquals("##comment", new CommentedString("dsfdf##comment").getComment());
		assertEquals(" ##comment", new CommentedString("dsfdf ##comment").getComment());
		assertEquals("#  comment  ", new CommentedString("dsfdf#  comment  ").getComment());
		assertEquals("#", new CommentedString("dsfdf#").getComment());
		assertEquals("", new CommentedString("dsfdf").getComment());
		assertEquals("# ", new CommentedString("dsfdf# ").getComment());
		assertEquals("", new CommentedString("dsfdf# ", ";").getComment());
		assertEquals(";comment", new CommentedString("dsfdf;comment", ";").getComment());
		assertEquals("", new CommentedString("").getComment());
	}

	@Test
	public void testGetStrNoComment() {
		assertEquals("dsfdf", new CommentedString("dsfdf#comment").getStrNoComment());
		assertEquals("   dsfdf", new CommentedString("   dsfdf#comment").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf#  comment  ").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf #  comment  ").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf#").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf").getStrNoComment());
		assertEquals("", new CommentedString("#comment").getStrNoComment());
		assertEquals("dsfdf# ", new CommentedString("dsfdf# ", ";").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf;comment", ";").getStrNoComment());
		assertEquals("dsfdf", new CommentedString("dsfdf;comment", ";").getStrNoComment());
		assertEquals("", new CommentedString("").getStrNoComment());
	}

	@Test
	public void testToString() {
		assertEquals("dsfdf#comment", new CommentedString("dsfdf#comment").toString());
	}

	@Test
	public void testIsValuable() {
		assertTrue(new CommentedString("dfsdf").isValuable());
		assertTrue(new CommentedString("dfsdf#comment").isValuable());
		assertFalse(new CommentedString("").isValuable());
		assertFalse(new CommentedString("#comment").isValuable());
		assertFalse(new CommentedString(" #comment").isValuable());
	}

}