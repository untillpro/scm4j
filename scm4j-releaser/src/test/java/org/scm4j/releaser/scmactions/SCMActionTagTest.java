/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.releaser.scmactions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.scm4j.releaser.Constants;
import org.scm4j.releaser.Utils;
import org.scm4j.releaser.Version;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTag;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSType;
import org.scm4j.releaser.exceptions.EReleaserException;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.IVCSMutationSession;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SCMActionTagTest {
	private static final Version VERSION = new Version("1.2.3");
	private static final String REVISION = "0123456789abcdef";
	private Fixture fixture;

	@Before
	public void setUp() throws Exception {
		new DelayedTagsFile().delete();
		fixture = fixture();
		new DelayedTagsFile().writeDelayedTag(
				fixture.repository.getComponentLocation(), VERSION, REVISION);
	}

	@After
	public void tearDown() {
		new DelayedTagsFile().delete();
	}

	@Test
	public void testDelayedTagReusesOneSessionForTagAndPatchBumpThenCloses() {
		String branch = Utils.getReleaseBranchName(fixture.repository, VERSION);
		String versionPath = fixture.repository.getComponentPath(Constants.VER_FILE_NAME);
		TagDesc tag = Utils.getTagDesc(fixture.repository, VERSION.toString());
		when(fixture.session.getFileContent(branch, versionPath, null)).thenReturn(VERSION.toString());

		fixture.action.execute(fixture.progress);

		InOrder order = inOrder(fixture.vcs, fixture.session);
		order.verify(fixture.vcs).startModify();
		order.verify(fixture.session).createTag(branch, tag.getName(), tag.getMessage(), REVISION);
		order.verify(fixture.session).getFileContent(branch, versionPath, null);
		order.verify(fixture.session).setFileContent(branch, versionPath, VERSION.toNextPatch().toString(),
				Constants.SCM_VER + " " + VERSION.toNextPatch());
		order.verify(fixture.session).close();
		verify(fixture.session, never()).setCorrupted(true);
		assertNull(new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation()));
	}

	@Test
	public void testPatchBumpIsOptionalButUsesTheSameSession() {
		String branch = Utils.getReleaseBranchName(fixture.repository, VERSION);
		String versionPath = fixture.repository.getComponentPath(Constants.VER_FILE_NAME);
		when(fixture.session.getFileContent(branch, versionPath, null))
				.thenReturn(VERSION.toNextPatch().toString());

		fixture.action.execute(fixture.progress);

		verify(fixture.vcs).startModify();
		verify(fixture.session).getFileContent(branch, versionPath, null);
		verify(fixture.session, never()).setFileContent(anyString(), anyString(), anyString(), anyString());
		verify(fixture.session).close();
		assertNull(new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation()));
	}

	@Test
	public void testFailedTagMarksSessionCorruptAndKeepsDelayedRecord() {
		RuntimeException failure = new RuntimeException("tag failed");
		doThrow(failure).when(fixture.session)
				.createTag(anyString(), anyString(), anyString(), anyString());

		assertFailureKeepsDelayedTag(failure);

		verify(fixture.session).setCorrupted(true);
		verify(fixture.session).close();
		verify(fixture.session, never()).getFileContent(anyString(), anyString(), anyString());
	}

	@Test
	public void testFailedPatchBumpMarksSessionCorruptAndKeepsDelayedRecord() {
		String branch = Utils.getReleaseBranchName(fixture.repository, VERSION);
		String versionPath = fixture.repository.getComponentPath(Constants.VER_FILE_NAME);
		RuntimeException failure = new RuntimeException("patch bump failed");
		when(fixture.session.getFileContent(branch, versionPath, null)).thenReturn(VERSION.toString());
		doThrow(failure).when(fixture.session).setFileContent(
				branch, versionPath, VERSION.toNextPatch().toString(), Constants.SCM_VER + " " + VERSION.toNextPatch());

		assertFailureKeepsDelayedTag(failure);

		verify(fixture.session).setCorrupted(true);
		verify(fixture.session).close();
	}

	private void assertFailureKeepsDelayedTag(RuntimeException failure) {
		try {
			fixture.action.execute(fixture.progress);
			fail("Expected delayed-tag action failure");
		} catch (EReleaserException actual) {
			assertSame(failure, actual.getCause());
		}
		DelayedTag retained = new DelayedTagsFile().getDelayedTag(fixture.repository.getComponentLocation());
		assertNotNull(retained);
		assertEquals(VERSION, retained.getVersion());
		assertEquals(REVISION, retained.getRevision());
	}

	private Fixture fixture() {
		IVCS vcs = mock(IVCS.class);
		IVCSMutationSession session = mock(IVCSMutationSession.class);
		when(vcs.startModify()).thenReturn(session);
		VCSRepository repository = new VCSRepository("component", "https://example.test/repository.git",
				"components/component", null, VCSType.GIT, null, "release/", vcs, null);
		SCMActionTag action = new SCMActionTag(new Component("test:component:" + VERSION), repository);
		return new Fixture(repository, action, vcs, session, mock(IProgress.class));
	}

	private static final class Fixture {
		private final VCSRepository repository;
		private final SCMActionTag action;
		private final IVCS vcs;
		private final IVCSMutationSession session;
		private final IProgress progress;

		private Fixture(VCSRepository repository, SCMActionTag action, IVCS vcs,
				IVCSMutationSession session, IProgress progress) {
			this.repository = repository;
			this.action = action;
			this.vcs = vcs;
			this.session = session;
			this.progress = progress;
		}
	}
}
