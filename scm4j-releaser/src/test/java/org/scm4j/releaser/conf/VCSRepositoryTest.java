package org.scm4j.releaser.conf;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VCSRepositoryTest {

	@Test
	public void testLocationDelegation() {
		VCSRepository repository = repository("components\\driver/");

		assertEquals(new VCSComponentLocation("url", "components/driver"), repository.getComponentLocation());
		assertEquals("url", repository.getUrl());
		assertEquals("components/driver", repository.getSubfolder());
	}

	@Test
	public void testSubfolderAffectsEquality() {
		VCSRepository first = repository("components/first");
		VCSRepository second = repository("components/second");

		assertNotEquals(first, second);
	}

	@Test
	public void testEquivalentSubfoldersHaveTheSameIdentity() {
		assertEquals(repository(null), repository(""));
		assertEquals(repository("components/driver"), repository("components/driver/"));
		assertEquals(repository("components\\driver"), repository("components/driver"));
	}

	@Test
	public void testComponentPathWithoutSubfolder() {
		assertEquals("version", repository(null).getComponentPath("version"));
		assertEquals("mdeps", repository("").getComponentPath("mdeps"));
	}

	@Test
	public void testComponentPathUsesNormalizedSubfolder() {
		assertEquals("components/driver/version",
				repository("components/driver").getComponentPath("version"));
		assertEquals("components/driver/mdeps",
				repository("components/driver///").getComponentPath("mdeps"));
		assertEquals("components/driver/metadata/version",
				repository("components\\driver\\").getComponentPath("metadata/version"));
	}

	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier
				.forClass(VCSRepository.class)
				.withOnlyTheseFields("componentLocation")
				.usingGetClass()
				.verify();
	}

	private VCSRepository repository(String subfolder) {
		return new VCSRepository("name", "url", subfolder, null, null, null, null, null, null);
	}
}
