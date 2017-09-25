package org.scm4j.commons;

import static org.junit.Assert.*;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CoordsTest {

	@Test
	public void testCoords() {
		try {
			new Coords("");
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	Coords dc(String coords) {
		return new Coords(coords);
	}

	@Test
	public void testComment() {
		assertEquals("", dc("com.myproject:c1").getComment());
		assertEquals("#", dc("com.myproject:c1#").getComment());
		assertEquals("#...$ #", dc("com.myproject:c1#...$ #").getComment());
	}

	@Test
	public void testExtension() {
		assertEquals("", dc("com.myproject:c1").getExtension());
		assertEquals("@", dc("com.myproject:c1@").getExtension());
		assertEquals("@ext", dc("com.myproject:c1@ext#qw").getExtension());
		assertEquals("@ext@", dc("com.myproject:c1@ext@#qw").getExtension());
	}

	@Test
	public void testClassifier() {
		assertEquals("", dc("com.myproject:c1").getClassifier());
		assertEquals(":", dc("com.myproject:c1::").getClassifier());
		assertEquals(":class", dc("com.myproject:c1::class:").getClassifier());
	}

	@Test
	public void testToSting() {
		assertEquals("com.myproject:c1:1.0.0", dc("com.myproject:c1:1.0.0").toString());
		assertEquals("com.myproject:  c1:1.0.0", dc("com.myproject:  c1:1.0.0").toString());
		assertEquals("   com.myproject:  c1:1.0.0", dc("   com.myproject:  c1:1.0.0").toString());
		assertEquals("com.myproject:c1:1.0.0#comment", dc("com.myproject:c1:1.0.0#comment").toString());
		assertEquals("com.myproject:c1:1.0.0@ext #comment", dc("com.myproject:c1:1.0.0@ext #comment").toString());
		assertEquals("com.myproject:c1::dfgd@ext #comment", dc("com.myproject:c1::dfgd@ext #comment").toString());
	}
	
	@Test
	public void testGroupId() {
		assertEquals("com.myproject", dc("com.myproject:c1:1.0.0").getGroupId());
		assertEquals("com.myproject", dc("com.myproject:c1").getGroupId());
		assertEquals("   com.myproject", dc("   com.myproject:c1").getGroupId());
	}
	
	@Test
	public void testArtifactId() {
		assertEquals("c1", dc("com.myproject:c1:1.0.0").getArtifactId());
		assertEquals("c1", dc("com.myproject:c1").getArtifactId());
		assertEquals("c1", dc("   com.myproject:c1").getArtifactId());
	}
	
	@Test
	public void testVersion() {
		assertEquals(new Version("1.0.0"), dc("com.myproject:c1:1.0.0").getVersion());
		assertEquals(new Version("1.0.0"), dc("com.myproject:c1:1.0.0#comment").getVersion());
		assertEquals(new Version("1.0.0"), dc("com.myproject:c1:1.0.0@ext #comment").getVersion());
		assertEquals(new Version(""), dc("com.myproject:c1::dfgd@ext #comment").getVersion());
		assertEquals(new Version("-SNAPSHOT"), dc("com.myproject:c1:-SNAPSHOT:dfgd@ext #comment").getVersion());
	}
	
	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier
				.forClass(Coords.class)
				.withOnlyTheseFields("coordsStringNoComment")
				.usingGetClass()
				.verify();
	}
}
