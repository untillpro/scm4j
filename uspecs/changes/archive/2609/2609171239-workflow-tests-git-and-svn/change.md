---
change_id: 2609170912-workflow-tests-git-and-svn
type: test
issue_url: https://untill.atlassian.net/browse/PRIME-170
scope: [releaser-tests, ci]
---

# Change request: Run workflow integration tests against Git and SVN

## Why

Release workflows depend on behavior shared by the Git and SVN implementations, but backend coverage should be concentrated where complete workflows are exercised. Running every lower-level test against both backends adds duplicate work, while missing a consistent backend matrix in workflow integration tests can leave backend-specific release regressions undetected.

## What

Backend coverage is aligned with each test level:

- Normal workflow integration test runs exercise release behavior against Git only.
- A daily GitHub Actions run exercises every workflow integration test against both Git and SVN at 04:00 Europe/Moscow time.
- Tests outside the workflow integration suite use Git as their default VCS backend and are not duplicated for SVN.
- A workflow test launched in debug mode executes once against Git, allowing a single predictable debugging session.
- Scheduled Git/SVN execution reports backend-specific failures independently so the failing VCS implementation is identifiable.

## How

Decisions:

- Make the shared workflow-test harness the single owner of backend selection using the existing JUnit 4 parameterized runner. Each workflow test method is discovered as a separately named invocation for its selected backend, rather than looping over backends inside the test body.
- Use Git as the default parameter set and enable the explicit Git-and-SVN set only when `SCM4J_WORKFLOW_TEST_ALL_VCS=true`; adding another VCS implementation therefore cannot silently expand the workflow suite.
- Detect an attached JDWP debugger before applying the environment override and keep the parameter set on Git, avoiding a second invocation that would repeat breakpoints and fixture setup.
- Pass the selected backend through the existing standard and monorepo fixture constructors and give every parameterized invocation its own setup and teardown lifecycle. Existing scenario helpers consume that backend instead of creating an inner backend loop.
- Treat use of the shared workflow harness as the integration-test boundary. Tests outside that boundary use the existing Git-default fixture and remove ad hoc multi-backend loops; dedicated Git and SVN adapter suites remain backend-specific.
- Keep the existing JUnit 4 dependency and Gradle test task, verify both parameter discovery modes, and use the existing GitHub Actions test workflow to run the workflow-test slice daily at 04:00 Europe/Moscow time.

Assumptions:

- IDE and Gradle debug launches expose the standard JDWP agent in the test JVM input arguments.
- Workflow tests continue to run without parallel test forks because their fixture directories and release state are process-wide resources.
- GitHub Actions evaluates the scheduled workflow from the default branch and supports the configured `Europe/Moscow` IANA timezone.

Out of scope:

- Changing production Git or SVN behavior or consolidating their dedicated adapter test suites.
- Parallelizing workflow backend variants or splitting them into separate CI or Gradle tasks.

References:

- [shared workflow fixture and scenario lifecycle](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
- [backend-aware test repository fixture](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)
- [representative backend loop outside the workflow boundary](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/DevelopBranchTest.java)
- [existing JUnit 4 test configuration](../../../../../build.gradle)

## Construction

- [x] create: [releaser/WorkflowTestBaseTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBaseTest.java)
  - provide focused regression coverage for workflow backend selection without creating VCS repositories
  - exercise the shared harness's parameter-selection boundary with synthetic test JVM arguments
  - verify that normal execution and unrelated JVM agents select only the Git workflow parameter
  - verify that the scheduled-run environment override selects distinct Git and SVN workflow parameters
  - verify that a JDWP agent argument selects only Git even when the scheduled-run override is present

- [x] update: [releaser/WorkflowTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
  - make the JUnit 4 parameterized runner and backend-labelled parameter source apply to every workflow integration subclass
  - use Git by default, derive the fixed Git/SVN parameter set from the scheduled-run environment override, and keep Git when JDWP debugging is active
  - construct standard and monorepo fixtures from the current parameter and preserve isolated setup, teardown, builders, delayed tags, and release directories for each invocation
  - replace scenario-internal backend iteration with execution against the current parameter

- [x] update: [GitHub Actions test workflow](../../../../../.github/workflows/test.yml)
  - run the normal test suite with Git-only workflow parameters on pull requests and pushes to `main`
  - schedule the workflow-test slice daily at 04:00 in the `Europe/Moscow` timezone
  - enable explicit Git/SVN workflow parameters only for the scheduled run
  - rely on the executable mode committed for `gradlew` and remove the redundant permission-setting step

- [x] update: [releaser/WorkflowMonorepoDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoDelayedTagTest.java)
  - execute its scenario once using the backend selected by the shared workflow harness

- [x] update: [releaser/WorkflowMonorepoForkAndBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
  - execute its scenario once using the backend selected by the shared workflow harness

- [x] update: [releaser/WorkflowMonorepoPatchesTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoPatchesTest.java)
  - execute its scenario once using the backend selected by the shared workflow harness

- [x] update: [actions/ActionAbstractTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/actions/ActionAbstractTest.java)
  - stop inheriting the workflow integration harness and initialize only the Git-default component and repository fixtures required by these action-level tests

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - stop inheriting the workflow integration harness and give its tests scoped Git-default fixtures and cleanup
  - remove the local Git/SVN iteration while preserving revision selection, tagging, working-directory, and missing-branch assertions on Git

- [x] update: [branch/DevelopBranchTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/DevelopBranchTest.java)
  - replace the local backend loop with the default Git fixture while preserving component-subfolder isolation coverage

- [x] update: [branch/ReleaseBranchFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/ReleaseBranchFactoryTest.java)
  - replace the local backend loop with the default Git fixture while preserving custom develop-branch coverage for root and subfolder components
