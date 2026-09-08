package org.scm4j.releaser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.commons.progress.IProgress;
import org.scm4j.releaser.branch.ReleaseBranchPatch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSRepositoryFactory;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

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

	@Test
	public void testOwnSubfolderTagMarksReleaseBoundary() {
		assertTrue(noValueableCommitsAfterLastTag("driver", "components/driver", "driver/1.2.3"));
	}

	@Test
	public void testOtherSubfolderTagsDoNotMarkReleaseBoundary() {
		assertFalse(noValueableCommitsAfterLastTag("driver", "components/driver",
				"components/sibling/1.2.3", "1.2.3"));
	}

	@Test
	public void testNestedComponentTagDoesNotMarkReleaseBoundary() {
		assertFalse(noValueableCommitsAfterLastTag("components", "components",
				"components/driver/1.2.3"));
	}

	@Test
	public void testAnyTagRemainsReleaseBoundaryWithoutSubfolder() {
		assertTrue(noValueableCommitsAfterLastTag("name", null, "other-tag"));
	}

	@Test
	public void testCommitTraversalUsesSubfolderForEveryPage() {
		String branchName = "driver/release/1.2";
		String subfolder = "components/driver";
		IVCS vcs = mock(IVCS.class);
		VCSRepository repository = repository("driver", subfolder, vcs);
		ReleaseBranchPatch releaseBranch = mock(ReleaseBranchPatch.class);
		when(releaseBranch.getName()).thenReturn(branchName);
		List<VCSCommit> firstPage = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			firstPage.add(new VCSCommit("revision-" + i, Constants.SCM_IGNORE, "author"));
		}
		when(vcs.getCommitsRange(branchName, null, WalkDirection.DESC, 10, subfolder)).thenReturn(firstPage);
		when(vcs.getCommitsRange(branchName, "revision-9", WalkDirection.DESC, 10, subfolder))
				.thenReturn(Collections.emptyList());
		when(vcs.getTagsOnRevision(any(String.class))).thenReturn(Collections.emptyList());

		assertTrue(new ExtendedStatusBuilder(mock(VCSRepositoryFactory.class))
				.noValueableCommitsAfterLastTag(repository, releaseBranch));

		verify(vcs).getCommitsRange(branchName, null, WalkDirection.DESC, 10, subfolder);
		verify(vcs).getCommitsRange(branchName, "revision-9", WalkDirection.DESC, 10, subfolder);
	}

	private VCSRepository repository(String subfolder) {
		return repository("name", subfolder, null);
	}

	private VCSRepository repository(String name, String subfolder, IVCS vcs) {
		return new VCSRepository(name, "url", subfolder, null, null, null, "release/", vcs, null);
	}

	private ExtendedStatus status(String version, Component component, VCSRepository repository) {
		return new ExtendedStatus(new Version(version), BuildStatus.BUILD, new LinkedHashMap<>(), component, repository);
	}

	private boolean noValueableCommitsAfterLastTag(String name, String subfolder, String... tagNames) {
		IVCS vcs = mock(IVCS.class);
		VCSRepository repository = new VCSRepository(name, "status-test-url", subfolder,
				null, null, null, "release/", vcs, null);
		ReleaseBranchPatch releaseBranch = mock(ReleaseBranchPatch.class);
		VCSCommit commit = new VCSCommit("revision", "valuable change", "author");
		List<VCSTag> tags = new ArrayList<>();
		for (String tagName : tagNames) {
			tags.add(new VCSTag(tagName, tagName + " message", "author", commit));
		}
		when(releaseBranch.getName()).thenReturn("release-branch");
		when(vcs.getCommitsRange("release-branch", null, WalkDirection.DESC, 10, subfolder))
				.thenReturn(Collections.singletonList(commit));
		when(vcs.getTagsOnRevision(commit.getRevision())).thenReturn(tags);

		ExtendedStatusBuilder builder = new ExtendedStatusBuilder(mock(VCSRepositoryFactory.class));
		return builder.noValueableCommitsAfterLastTag(repository, releaseBranch);
	}
}
