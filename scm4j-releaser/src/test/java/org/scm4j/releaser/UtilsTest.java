package org.scm4j.releaser;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Supplier;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;

public class UtilsTest {

	private static final Version TEST_VERSION = new Version("1.2.3");

	@Test
	public void testReleaseBranchNameWithSubfolder() {
		VCSRepository repository = repository("components/driver", "release/");

		assertEquals("components/driver/release/1.2", Utils.getReleaseBranchName(repository, TEST_VERSION));
	}

	@Test
	public void testReleaseBranchNameWithTrailingSubfolderSeparator() {
		VCSRepository repository = repository("components/driver/", "release/");

		assertEquals("components/driver/release/1.2", Utils.getReleaseBranchName(repository, TEST_VERSION));
	}

	@Test
	public void testReleaseBranchNameWithoutSubfolder() {
		assertEquals("release/1.2", Utils.getReleaseBranchName(repository(null, "release/"), TEST_VERSION));
		assertEquals("B1.2", Utils.getReleaseBranchName(repository("", "B"), TEST_VERSION));
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
		return new VCSRepository("name", "url", subfolder, null, null, null, releaseBranchPrefix, null, null);
	}
}
