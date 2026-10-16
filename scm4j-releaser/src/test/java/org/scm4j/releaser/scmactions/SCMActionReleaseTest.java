/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.releaser.scmactions;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.Test;
import org.mockito.InOrder;
import org.scm4j.releaser.BuildStatus;
import org.scm4j.releaser.CachedStatuses;
import org.scm4j.releaser.ExtendedStatus;
import org.scm4j.releaser.Version;
import org.scm4j.releaser.actions.ActionSet;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSComponentLocation;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.conf.VCSRepositoryFactory;
import org.scm4j.releaser.exceptions.EReleaserException;
import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.scmactions.procs.ISCMProc;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.IVCSMutationSession;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SCMActionReleaseTest {

	@Test
	public void testOneMutationSessionIsPassedToEveryProcedureAndClosedAfterSuccess() {
		Fixture fixture = fixture(BuildStatus.BUILD, ActionSet.FULL);
		ISCMProc first = mock(ISCMProc.class);
		ISCMProc second = mock(ISCMProc.class);
		fixture.action.getProcs().clear();
		fixture.action.getProcs().add(first);
		fixture.action.getProcs().add(second);

		fixture.action.execute(fixture.progress);

		InOrder order = inOrder(fixture.vcs, first, second, fixture.session);
		order.verify(fixture.vcs).startModify();
		order.verify(first).execute(fixture.progress, fixture.session);
		order.verify(second).execute(fixture.progress, fixture.session);
		order.verify(fixture.session).close();
		verify(fixture.session, never()).setCorrupted(true);
	}

	@Test
	public void testProcedureFailureMarksSessionCorruptClosesItAndSkipsLaterProcedures() {
		Fixture fixture = fixture(BuildStatus.BUILD, ActionSet.FULL);
		ISCMProc failing = mock(ISCMProc.class);
		ISCMProc skipped = mock(ISCMProc.class);
		RuntimeException failure = new RuntimeException("procedure failed");
		org.mockito.Mockito.doThrow(failure).when(failing).execute(fixture.progress, fixture.session);
		fixture.action.getProcs().clear();
		fixture.action.getProcs().add(failing);
		fixture.action.getProcs().add(skipped);

		try {
			fixture.action.execute(fixture.progress);
			fail("Expected release failure");
		} catch (EReleaserException actual) {
			assertSame(failure, actual.getCause());
		}

		InOrder order = inOrder(fixture.vcs, failing, fixture.session);
		order.verify(fixture.vcs).startModify();
		order.verify(failing).execute(fixture.progress, fixture.session);
		order.verify(fixture.session).setCorrupted(true);
		order.verify(fixture.session).close();
		verify(skipped, never()).execute(any(IProgress.class), any(IVCSMutationSession.class));
	}

	@Test
	public void testActionWithoutExecutableProceduresDoesNotOpenMutationSession() {
		Fixture fixture = fixture(BuildStatus.BUILD, ActionSet.FORK_ONLY);
		fixture.action.getProcs().clear();

		fixture.action.execute(fixture.progress);

		verify(fixture.vcs, never()).startModify();
		verify(fixture.session, never()).close();
	}

	private Fixture fixture(BuildStatus status, ActionSet actionSet) {
		Component component = new Component("test:component:1.0.0");
		VCSComponentLocation location = new VCSComponentLocation(
				"https://example.test/repository.git", "components/component");
		IVCS vcs = mock(IVCS.class);
		IVCSMutationSession session = mock(IVCSMutationSession.class);
		when(vcs.startModify()).thenReturn(session);
		VCSRepository repository = mock(VCSRepository.class);
		when(repository.getVCS()).thenReturn(vcs);
		when(repository.getComponentLocation()).thenReturn(location);
		when(repository.getUrl()).thenReturn(location.getUrl());
		when(repository.getSubfolder()).thenReturn(location.getSubfolder());
		CachedStatuses cache = new CachedStatuses();
		cache.put(location, new ExtendedStatus(new Version("1.0.0"), status,
				new LinkedHashMap<Component, ExtendedStatus>(), component, repository));
		SCMActionRelease action = new SCMActionRelease(component, new ArrayList<>(), cache,
				mock(VCSRepositoryFactory.class), actionSet, false, repository);
		return new Fixture(action, vcs, session, mock(IProgress.class));
	}

	private static final class Fixture {
		private final SCMActionRelease action;
		private final IVCS vcs;
		private final IVCSMutationSession session;
		private final IProgress progress;

		private Fixture(SCMActionRelease action, IVCS vcs, IVCSMutationSession session, IProgress progress) {
			this.action = action;
			this.vcs = vcs;
			this.session = session;
			this.progress = progress;
		}
	}
}
