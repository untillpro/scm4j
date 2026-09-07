package org.scm4j.releaser.conf;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.junit.Assert.assertEquals;

public class VCSRepositoryTest {

	@Test
	public void testSubfolderProperty() {
		VCSRepository repository = repository("components/driver");

		assertEquals("components/driver", repository.getSubfolder());
	}

	@Test
	public void testSubfolderDoesNotAffectEquality() {
		VCSRepository first = repository("components/first");
		VCSRepository second = repository("components/second");

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier
				.forClass(VCSRepository.class)
				.withOnlyTheseFields("url")
				.usingGetClass()
				.verify();
	}

	private VCSRepository repository(String subfolder) {
		return new VCSRepository("name", "url", subfolder, null, null, null, null, null, null);
	}
}
