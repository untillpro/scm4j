package org.scm4j.releaser.conf;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VCSRepositoryTest {

	@Test
	public void testSubfolderProperty() {
		VCSRepository repository = repository("components/driver");

		assertEquals("components/driver", repository.getSubfolder());
	}

	@Test
	public void testSubfolderAffectsEquality() {
		VCSRepository first = repository("components/first");
		VCSRepository second = repository("components/second");

		assertNotEquals(first, second);
	}

	@Test
	public void testEqualsAndHashCode() {
		EqualsVerifier
				.forClass(VCSRepository.class)
				.withOnlyTheseFields("url", "subfolder")
				.usingGetClass()
				.verify();
	}

	private VCSRepository repository(String subfolder) {
		return new VCSRepository("name", "url", subfolder, null, null, null, null, null, null);
	}
}
