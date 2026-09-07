package org.scm4j.releaser;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.commons.progress.IProgress;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSRepositoryFactory;

public class ExtendedStatusBuilderTest {

	@Test
	public void testStatusesAreCachedByRepositorySubfolder() {
		Component firstComponent = new Component("test:first:");
		Component secondComponent = new Component("test:second:");
		VCSRepository firstRepository = repository("first");
		VCSRepository secondRepository = repository("second");
		ExtendedStatus firstStatus = status("1.0", firstComponent, firstRepository);
		ExtendedStatus secondStatus = status("2.0", secondComponent, secondRepository);
		VCSRepositoryFactory repositoryFactory = mock(VCSRepositoryFactory.class);
		when(repositoryFactory.getVCSRepository(firstComponent)).thenReturn(firstRepository);
		when(repositoryFactory.getVCSRepository(secondComponent)).thenReturn(secondRepository);
		ExtendedStatusBuilder builder = spy(new ExtendedStatusBuilder(repositoryFactory));
		doReturn(firstStatus).when(builder).getMinorStatus(eq(firstComponent), any(CachedStatuses.class),
				any(IProgress.class), eq(firstRepository), any(DelayedTag.class));
		doReturn(secondStatus).when(builder).getMinorStatus(eq(secondComponent), any(CachedStatuses.class),
				any(IProgress.class), eq(secondRepository), any(DelayedTag.class));
		CachedStatuses cache = new CachedStatuses();

		ExtendedStatus firstResult = builder.getAndCacheMinorStatus(firstComponent, cache);
		ExtendedStatus secondResult = builder.getAndCacheMinorStatus(secondComponent, cache);

		assertEquals(new Version("1.0"), firstResult.getNextVersion());
		assertEquals(new Version("2.0"), secondResult.getNextVersion());
		assertEquals(2, cache.size());
	}

	private VCSRepository repository(String subfolder) {
		return new VCSRepository("name", "url", subfolder, null, null, null, null, null, null);
	}

	private ExtendedStatus status(String version, Component component, VCSRepository repository) {
		return new ExtendedStatus(new Version(version), BuildStatus.BUILD, new LinkedHashMap<>(), component, repository);
	}
}
