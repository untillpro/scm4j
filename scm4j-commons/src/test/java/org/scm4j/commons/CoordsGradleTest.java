package org.scm4j.commons;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.junit.Assert.*;

public class CoordsGradleTest {

	@Test
	public void testCoords() {
		try {
			new CoordsGradle("");
			fail();
		} catch (IllegalArgumentException e) {
		}
		try {
			new CoordsGradle("no-artifactId");
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	CoordsGradle dc(String coords) {
		return new CoordsGradle(coords);
	}

	@Test
	public void testComment() {
		assertEquals("", dc("com.myproject:c1").getComment());
		assertEquals("#", dc("com.myproject:c1#").getComment());
		assertEquals(" # ", dc("     com.myproject:c1 # ").getComment());
		assertEquals("#...$ #", dc("com.myproject:c1#...$ #").getComment());
	}

	@Test
	public void testExtension() {
		assertEquals("", dc("com.myproject:c1").getExtension());
		assertEquals("", dc("com.myproject:c1@").getExtension());
		assertEquals("ext", dc("com.myproject:c1@ext#qw").getExtension());
		assertEquals("ext@", dc("com.myproject:c1@ext@#qw").getExtension());
	}

	@Test
	public void testClassifier() {
		assertEquals("", dc("com.myproject:c1").getClassifier());
		assertEquals("", dc("com.myproject:c1::").getClassifier());
		assertEquals("class", dc("com.myproject:c1::class:").getClassifier());
	}

	@Test
	public void testToSting() {
		assertEquals("com.myproject:c1::dfgd@ext # comment", dc("com.myproject:c1::dfgd@ext # comment").toString());
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
				.forClass(CoordsGradle.class)
				.withOnlyTheseFields("coordsStringNoComment")
				.usingGetClass()
				.verify();
	}

	@Test
	public void testVersionChange() {
		assertEquals("eu.untill:JTerminal:12.13  # comment", new CoordsGradle("eu.untill:JTerminal:  # comment").toString("12.13"));
		assertEquals("eu.untill:JTerminal:12.13:abc@efg # comment", new CoordsGradle("eu.untill:JTerminal::abc@efg # comment").toString("12.13"));
		assertEquals("eu.untill:JTerminal::abc@efg # comment", new CoordsGradle("eu.untill:JTerminal:12.13:abc@efg # comment").toString(""));
		assertEquals("eu.untill:JTerminal::# comment", new CoordsGradle("eu.untill:JTerminal:12.13:# comment").toString(""));
		assertEquals("eu.untill:JTerminal  # comment", new CoordsGradle("eu.untill:JTerminal:12.13  # comment").toString(""));
		assertEquals("eu.untill:JTerminal  # comment", new CoordsGradle("eu.untill:JTerminal  # comment").toString(""));
		assertEquals("eu.untill:JTerminal:12.13  # comment", new CoordsGradle("eu.untill:JTerminal  # comment").toString("12.13"));
	}
	
	@Test
	public void testToStringNoComment() {
		assertEquals("com.myproject:c1:1.0.0", dc("com.myproject:c1:1.0.0#comment").toStringNoComment());
		assertEquals("com.myproject:c1:1.0.0@ext", dc("com.myproject:c1:1.0.0@ext # comment").toStringNoComment());
		assertEquals("com.myproject:c1::dfgd@ext", dc("com.myproject:c1::dfgd@ext # comment").toStringNoComment());
		assertEquals("com.myproject:c1:", dc("com.myproject:c1: # comment").toStringNoComment());
	}
}
