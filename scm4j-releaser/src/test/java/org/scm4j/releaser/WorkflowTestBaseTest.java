package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.releaser.conf.VCSType;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class WorkflowTestBaseTest {

	@Test
	public void testSelectsOnlyGitForNormalExecution() {
		assertEquals(Collections.singletonList(VCSType.GIT),
				WorkflowTestBase.selectVcsTypes(Collections.<String>emptyList(), null));
	}

	@Test
	public void testSelectsGitAndSvnWhenAllVcsAreRequested() {
		assertEquals(Arrays.asList(VCSType.GIT, VCSType.SVN),
				WorkflowTestBase.selectVcsTypes(Collections.<String>emptyList(), "true"));
	}

	@Test
	public void testSelectsOnlyGitForDebugExecution() {
		assertEquals(Collections.singletonList(VCSType.GIT), WorkflowTestBase.selectVcsTypes(Arrays.asList(
				"-Xmx128m",
				"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"), "true"));
	}

	@Test
	public void testIgnoresUnrelatedJvmAgents() {
		assertEquals(Collections.singletonList(VCSType.GIT), WorkflowTestBase.selectVcsTypes(Arrays.asList(
				"-javaagent:coverage.jar",
				"-agentpath:profiler.dll",
				"-Xmx128m"), null));
	}
}
