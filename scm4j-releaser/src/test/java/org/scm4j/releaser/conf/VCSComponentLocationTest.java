package org.scm4j.releaser.conf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VCSComponentLocationTest {

	@Test
	public void testSubfolderNormalization() {
		assertEquals("", location(null).getSubfolder());
		assertEquals("", location("").getSubfolder());
		assertEquals("components/driver", location("components/driver/").getSubfolder());
		assertEquals("components/driver", location("components\\driver").getSubfolder());
	}

	@Test
	public void testEqualityAndHashCode() {
		VCSComponentLocation first = location("components/driver/");
		VCSComponentLocation equivalent = location("components\\driver");

		assertEquals(first, equivalent);
		assertEquals(first.hashCode(), equivalent.hashCode());
		assertNotEquals(first, new VCSComponentLocation("other-url", "components/driver"));
		assertNotEquals(first, location("components/other"));
	}

	@Test
	public void testDisplayValue() {
		assertEquals("url", location(null).toString());
		assertEquals("url [components/driver]", location("components/driver").toString());
	}

	@Test
	public void testJoinPath() {
		assertEquals("version", location(null).joinPath("version"));
		assertEquals("components/driver/version", location("components/driver").joinPath("version"));
	}

	private VCSComponentLocation location(String subfolder) {
		return new VCSComponentLocation("url", subfolder);
	}
}
