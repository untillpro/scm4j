package org.scm4j.commons.coords;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import org.scm4j.commons.Version;

import static org.junit.Assert.*;

public class CoordsMavenTest {

	@Test
	public void testCoords() {
		try {
			new CoordsMaven("");
			fail();
		} catch (IllegalArgumentException e) {
		}
		try {
			new CoordsMaven("no-artifactId");
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testComment() {
		assertEquals("", new CoordsMaven("com.myproject:c1:12.13").getComment());
		assertEquals("#", new CoordsMaven("com.myproject:c1:12.13#").getComment());
		assertEquals(" # ", new CoordsMaven("     com.myproject:c1:12.13 # ").getComment());
		assertEquals("#...$ #", new CoordsMaven("com.myproject:c1:12.13#...$ #").getComment());
	}

	@Test
	public void testExtension() {
		assertEquals("ext", new CoordsMaven("com.myproject:c1:ext:12.13").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1:12.13").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1::12.13").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1:::12.13").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1:").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1::").getExtension());
		assertEquals("", new CoordsMaven("com.myproject:c1:::").getExtension());
		assertEquals("ext", new CoordsMaven("com.myproject:c1:ext:class:12.13").getExtension());
		assertEquals("ext@", new CoordsMaven("com.myproject:c1:ext@:12.13#qw").getExtension());
	}

	@Test
	public void testClassifier() {
		assertEquals("class", new CoordsMaven("com.myproject:c1:ext:class:12.13 # comment").getClassifier());
		assertEquals("class", new CoordsMaven("com.myproject:c1::class:12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:ext:12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:ext::12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:ext:: # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:ext: # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:ext # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1::12.13 # comment").getClassifier());
		assertEquals("", new CoordsMaven("com.myproject:c1:::12.13 # comment").getClassifier());
	}

	@Test
	public void testToSting() {
		assertEquals("com.myproject:c1:ext:class:1.0.0 # comment",
				new CoordsMaven("com.myproject:c1:ext:class:1.0.0 # comment").toString());
	}

	@Test
	public void testGroupId() {
		assertEquals("com.myproject", new CoordsMaven("com.myproject:c1:1.0.0").getGroupId());
		assertEquals("com.myproject", new CoordsMaven("com.myproject:c1:12.13").getGroupId());
		assertEquals("   com.myproject", new CoordsMaven("   com.myproject:c1:12.13").getGroupId());
	}

	@Test
	public void testArtifactId() {
		assertEquals("c1", new CoordsMaven("com.myproject:c1:1.0.0").getArtifactId());
		assertEquals("c1", new CoordsMaven("com.myproject:c1").getArtifactId());
		assertEquals("c1", new CoordsMaven("com.myproject:c1:").getArtifactId());
		assertEquals("c1", new CoordsMaven("com.myproject:c1::").getArtifactId());
		assertEquals("c1", new CoordsMaven("com.myproject:c1:::").getArtifactId());
		assertEquals("c1", new CoordsMaven("   com.myproject:c1").getArtifactId());
	}

	@Test
	public void testVersion() {
		assertEquals(new Version("1.0.0"), new CoordsMaven("com.myproject:c1:1.0.0").getVersion());
		assertEquals(new Version("1.0.0"), new CoordsMaven("com.myproject:c1::1.0.0#comment").getVersion());
		assertEquals(new Version("1.0.0"), new CoordsMaven("com.myproject:c1:::1.0.0#comment").getVersion());
		assertEquals(new Version(""), new CoordsMaven("com.myproject:c1 #comment").getVersion());
		assertEquals(new Version(""), new CoordsMaven("com.myproject:c1: #comment").getVersion());
		assertEquals(new Version(""), new CoordsMaven("com.myproject:c1:: #comment").getVersion());
		assertEquals(new Version(""), new CoordsMaven("com.myproject:c1::: #comment").getVersion());
		assertEquals(new Version("-SNAPSHOT"), new CoordsMaven("com.myproject:c1:ext:class:-SNAPSHOT #comment").getVersion());
	}

	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier
				.forClass(CoordsMaven.class)
				.withOnlyTheseFields("coordsStringNoComment")
				.usingGetClass()
				.verify();
	}

	@Test
	public void testVersionChange() {
		assertEquals("com.myproject:c1:12.13", new CoordsMaven("com.myproject:c1:1.0.0").toString("12.13"));
		assertEquals("com.myproject:c1::12.13#comment", new CoordsMaven("com.myproject:c1::1.0.0#comment").toString("12.13"));
		assertEquals("com.myproject:c1:::12.13#comment", new CoordsMaven("com.myproject:c1:::1.0.0#comment").toString("12.13"));
		assertEquals("com.myproject:c1:12.13 #comment", new CoordsMaven("com.myproject:c1 #comment").toString("12.13"));
		assertEquals("com.myproject:c1:12.13 #comment", new CoordsMaven("com.myproject:c1: #comment").toString("12.13"));
		assertEquals("com.myproject:c1::12.13 #comment", new CoordsMaven("com.myproject:c1:: #comment").toString("12.13"));
		assertEquals("com.myproject:c1:::12.13 #comment", new CoordsMaven("com.myproject:c1::: #comment").toString("12.13"));
		assertEquals("com.myproject:c1:ext:class:12.13 #comment", new CoordsMaven("com.myproject:c1:ext:class:-SNAPSHOT #comment").toString("12.13"));
	}

	@Test
	public void testToStringNoComment() {
		assertEquals("com.myproject:c1:12.13", new CoordsMaven("com.myproject:c1:12.13").toStringNoComment());
		assertEquals("com.myproject:c1:12.13", new CoordsMaven("com.myproject:c1:12.13#").toStringNoComment());
		assertEquals("     com.myproject:c1:12.13", new CoordsMaven("     com.myproject:c1:12.13 # ").toStringNoComment());
		assertEquals("com.myproject:c1:12.13", new CoordsMaven("com.myproject:c1:12.13#...$ #").toStringNoComment());
	}

}