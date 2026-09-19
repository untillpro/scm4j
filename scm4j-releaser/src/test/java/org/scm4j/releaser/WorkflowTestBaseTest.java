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
				WorkflowTestBase.selectVcsTypes(null));
	}

	@Test
	public void testSelectsGitAndSvnWhenAllVcsAreRequested() {
		assertEquals(Arrays.asList(VCSType.GIT, VCSType.SVN),
				WorkflowTestBase.selectVcsTypes("true"));
	}
}
