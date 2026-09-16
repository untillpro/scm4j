---
change_id: 2609161134-monorepo-patch-delayed-tests
type: test
issue_url: https://untill.atlassian.net/browse/PRIME-171
scope: [releaser, tests]
---

# Change request: Monorepo patch and delayed-tag workflow coverage

## Why

The existing integration scenario covers normal fork and build behavior for an application that consumes a monorepo component, but patch releases and delayed tagging are only covered when components live in separate physical repositories. Shared-repository sibling activity could therefore interfere with component history, versions, delayed-tag state, or tag targets without an end-to-end test detecting it.

## What

Add workflow-level coverage for components that share a monorepo:

- Patch releases use the target component's release-branch history and ignore newer sibling-only commits.
- Patch builds, version bumps, and namespaced tags remain scoped to the selected component.
- Delayed builds maintain independent records for components with the same repository URL and different subfolders.
- Delayed tag actions use each component's saved revision without changing sibling or repository-root metadata.
- Patch and delayed-tag behavior is verified with both Git and SVN.

## How

Decisions:

- Exercise both workflows through the real CLI action graph and disposable repositories for each supported VCS adapter rather than mocking release collaborators.
- Share the monorepo fixture and lifecycle infrastructure across workflow tests while keeping the existing normal fork/build scenario as an unchanged regression baseline.
- Keep patch and delayed-tag coverage as separate scenarios so each workflow's state transitions and durable outcomes remain independently diagnosable.
- Make repository-head divergence observable with sibling-only activity, then verify component identity through build metadata, delayed-tag records, component-local versions, and namespaced tags.
- Apply root-sentinel verification to the develop branch and every release namespace created for either component in the shared repository.

Assumptions:

- The production implementation is expected to satisfy the added workflows without code changes; defects exposed by the new coverage will be handled separately.

Out of scope:

- Propagating a monorepo component patch into an existing release of a consuming standalone application.
- Expanding topology-independent patch and delayed-tag error-path coverage already exercised by the general workflow suite.

References:

- [monorepo fork/build end-to-end baseline](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
- [shared physical-repository fixture](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/MonorepoTestEnvironment.java)
- [existing component-subfolder patch workflow](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowPatchesTest.java)
- [existing delayed-tag workflow](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)
- [cross-adapter component-revision behavior](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)

## Construction

- [x] create: [releaser/WorkflowMonorepoTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoTestBase.java)
  - provide the shared test harness for disposable `MonorepoTestEnvironment` setup, CLI configuration, Git/SVN scenario execution, and process-wide release-state cleanup
  - expose a scenario context containing the four components and their repositories so every workflow test uses the same physical repository topology and component identities
  - centralize reusable component-commit, build-revision, tag-revision, and component-tag assertions currently embedded in the normal workflow scenario
  - verify root `version` and `mdeps` sentinels on the develop branch and every release namespace created for either shared-repository component before environment deletion
  - document why the harness resets global state, runs both adapters, filters component history and tags, and checks root sentinels during teardown

- [x] delete: [releaser/WorkflowMonorepoTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoTest.java)
  - replace the monolithic test after extracting its fixture lifecycle and reusable assertions into the shared harness

- [x] create: [releaser/WorkflowMonorepoForkAndBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
  - adopt the shared monorepo harness and scenario context without changing the existing dependency release behavior or assertions
  - retain the normal fork/build, dependency propagation, sibling-only no-op, and second minor-release regression baseline
  - document the repository topology and the purpose of each state transition and isolation assertion

- [x] create: [releaser/WorkflowMonorepoPatchesTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoPatchesTest.java)
  - create and build the initial `postgres` release, then place a valuable `postgres` commit followed by a newer `sqlite`-only commit on its physical release branch
  - build a versioned `postgres` patch and verify the action, builder metadata, and namespaced patch tag use the component commit rather than repository head
  - verify the component-local release version advances by one patch while sibling metadata, the sibling tag namespace, and root sentinels remain unchanged
  - add sibling-only release-branch activity after the patch and verify a repeated patch build is a no-op with no additional `postgres` tag
  - execute the complete scenario with both Git and SVN
  - document the initial-release baseline, deliberate repository-head divergence, and component-scoped outcomes

- [x] create: [releaser/WorkflowMonorepoDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoDelayedTagTest.java)
  - fork `postgres` and `sqlite`, move each physical release-branch head with an opposite-sibling commit, and build both components with delayed tagging
  - verify records sharing one repository URL remain distinct by component subfolder and retain each component-selected revision and release version
  - verify delayed builds create no tags or patch-version bumps, then tag one component and confirm only its local version and delayed record change
  - tag the remaining component and verify both namespaced tags coexist at their saved revisions and the delayed-tag file becomes empty
  - execute the complete scenario with both Git and SVN
  - document why component revisions are captured, branch heads are moved, and delayed records are consumed independently

- [x] verify: monorepo workflow regression coverage
  - run `.\gradlew.bat :scm4j-releaser:test --tests org.scm4j.releaser.WorkflowMonorepoForkAndBuildTest --tests org.scm4j.releaser.WorkflowMonorepoPatchesTest --tests org.scm4j.releaser.WorkflowMonorepoDelayedTagTest`
  - run `.\gradlew.bat :scm4j-releaser:test`
