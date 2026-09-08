package org.scm4j.releaser;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Supplier;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.releaser.conf.VCSRepository;

public class UtilsTest {

	private static final Version TEST_VERSION = new Version("1.2.3");

	@Test
	public void testReleaseBranchNameWithSubfolder() {
		VCSRepository repository = repository("driver", "components/driver", "release/");

		assertEquals("driver/release/1.2", Utils.getReleaseBranchName(repository, TEST_VERSION));
	}

	@Test
	public void testReleaseBranchNameWithTrailingSubfolderSeparators() {
		VCSRepository repository = repository("driver", "components/driver///", "release/");

		assertEquals("driver/release/1.2", Utils.getReleaseBranchName(repository, TEST_VERSION));
	}

	@Test
	public void testReleaseBranchNameWithCustomPrefixAndSubfolder() {
		VCSRepository repository = repository("vmax", "components/vmax", "B");

		assertEquals("vmax/B1", Utils.getReleaseBranchName(repository, new Version("1.4")));
	}

	@Test
	public void testReleaseBranchNameWithSlashTerminatedCustomPrefixAndSubfolder() {
		VCSRepository repository = repository("vmax", "components/vmax", "B/");

		assertEquals("vmax/B/1", Utils.getReleaseBranchName(repository, new Version("1.4")));
	}

	@Test
	public void testReleaseBranchNameWithoutSubfolder() {
		assertEquals("release/1.2", Utils.getReleaseBranchName(repository(null, "release/"), TEST_VERSION));
		assertEquals("B1.2", Utils.getReleaseBranchName(repository("", "B"), TEST_VERSION));
	}

	@Test
	public void testTagDescWithSubfolder() {
		TagDesc tagDesc = Utils.getTagDesc(repository("driver", "components/driver", "release/"), TEST_VERSION.toString());

		assertEquals("driver/1.2.3", tagDesc.getName());
		assertEquals("1.2.3 release", tagDesc.getMessage());
	}

	@Test
	public void testTagDescWithTrailingSubfolderSeparators() {
		TagDesc tagDesc = Utils.getTagDesc(repository("driver", "components/driver///", "release/"), TEST_VERSION.toString());

		assertEquals("driver/1.2.3", tagDesc.getName());
		assertEquals("1.2.3 release", tagDesc.getMessage());
	}

	@Test
	public void testTagDescWithSubfolderIgnoresReleaseBranchPrefix() {
		assertTagDesc(Utils.getTagDesc(repository("vmax", "components/vmax", "B"), "1.4"),
				"vmax/1.4", "1.4 release");
		assertTagDesc(Utils.getTagDesc(repository("vmax", "components/vmax", "B/"), "1.4"),
				"vmax/1.4", "1.4 release");
	}

	@Test
	public void testTagDescWithoutSubfolder() {
		assertTagDesc(Utils.getTagDesc(repository(null, "release/"), TEST_VERSION.toString()), "1.2.3", "1.2.3 release");
		assertTagDesc(Utils.getTagDesc(repository("", "release/"), TEST_VERSION.toString()), "1.2.3", "1.2.3 release");
		assertTagDesc(Utils.getTagDesc(TEST_VERSION.toString()), "1.2.3", "1.2.3 release");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testReportDurationNoIProgress() {
		@SuppressWarnings("rawtypes")
		Supplier mockedSup = mock(Supplier.class);
		Utils.reportDuration(mockedSup, "test", new Component("test:test"), null);
		verify(mockedSup).get();
	}

	private VCSRepository repository(String subfolder, String releaseBranchPrefix) {
		return repository("name", subfolder, releaseBranchPrefix);
	}

	private VCSRepository repository(String name, String subfolder, String releaseBranchPrefix) {
		return new VCSRepository(name, "url", subfolder, null, null, null, releaseBranchPrefix, null, null);
	}

	private void assertTagDesc(TagDesc tagDesc, String name, String message) {
		assertEquals(name, tagDesc.getName());
		assertEquals(message, tagDesc.getMessage());
	}
}
