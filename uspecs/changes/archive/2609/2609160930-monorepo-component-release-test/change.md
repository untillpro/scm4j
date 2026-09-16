---
change_id: 2609160717-monorepo-component-release-test
type: test
issue_url: https://untill.atlassian.net/browse/PRIME-167
scope: [releaser, tests]
---

# Change request: Monorepo dependency release workflow coverage

Refs:

- [PRIME-167: migrate-drivers: scm4j: implement integration test for component from monorepo usage](./issue-PRIME-167.md)

## Why

Monorepo release behavior is covered by isolated tests, but there is no end-to-end verification of an application release involving a used monorepo component, an unused sibling component, and an unchanged standalone dependency. This leaves the complete dependency selection, build, version-locking, and tagging workflow unverified across both supported version-control systems.

## What

Add end-to-end test coverage for the following release behavior in both Git and SVN:

- Releasing `unTill` selects `postgres` from a shared `postgres` and `sqlite` monorepo while retaining its existing standalone `UBL` dependency.
- The initial release builds and tags `postgres` at its latest component revision, even when a newer repository-wide revision belongs only to `sqlite`, and locks the correct `postgres` and `UBL` versions in `unTill`.
- A change affecting only `sqlite` leaves `postgres`, `UBL`, and `unTill` with nothing to release.
- A subsequent `postgres` change releases both `postgres` and `unTill`, updates the locked `postgres` version, and leaves `sqlite` and `UBL` unreleased.

## How

Decisions:

- Exercise the scenario through the existing status, fork, and build action graph against real temporary repositories, rather than mocking release collaborators.
- Use a dedicated four-component test fixture while reusing topology-neutral workflow helpers, leaving the default three-repository fixture unchanged for existing tests.
- Represent `postgres` and `sqlite` as separate component mappings that resolve to one repository URL and distinct normalized subfolders, so repository identity, status caching, path-filtered history, and component-relative metadata participate in the test.
- Run the same scenario with the real Git and SVN adapters instead of treating one adapter as representative of both.
- Make component-revision selection observable by advancing the shared release-branch head with a `sqlite`-only change after the `postgres` release branch exists, then verify outcomes from recorded build metadata and durable repository state.

Assumptions:

- None

Out of scope:

- Changing production release behavior uncovered by the new coverage.
- Extending the scenario to patch releases or delayed tagging.

References:

- [shared workflow action and assertion harness](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
- [temporary repository and component fixture setup](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)
- [existing end-to-end fork and build workflow](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowForkAndBuildTest.java)
- [cross-adapter component revision verification](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
- [component mapping and repository construction boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)

## Construction

- [x] create: [testutils/MonorepoTestEnvironment.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/MonorepoTestEnvironment.java)
  - provide a disposable four-component fixture for a selected VCS type, with standalone `unTill` and `UBL` repositories plus one physical repository shared by `postgres` and `sqlite`
  - generate the complete ordered YAML component configuration explicitly, assigning distinct shared-repository subfolders while retaining the existing test builder and release naming conventions
  - seed component-relative development metadata, distinguishable monorepo-root sentinels, `unTill` dependencies, and an already released and tagged `UBL` baseline that remains unchanged throughout the scenario
  - expose the repositories, component versions, and cleanup operations needed to run identical scenarios against Git and SVN

- [x] create: [releaser/WorkflowMonorepoTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoTest.java)
  - run `testMonorepoComponents` through the real status, fork, and build workflow for both Git and SVN
  - create the initial `postgres` and `sqlite` histories, then move the shared release-branch head with a `sqlite`-only commit so the selected `postgres` build and tag revisions can be distinguished from repository head
  - verify the first `unTill` release locks the released `postgres` and existing `UBL` versions while `sqlite` and `UBL` are neither built nor tagged again
  - verify directly and through the `unTill` action tree that a subsequent `sqlite`-only development change produces no action for unchanged `postgres`, `UBL`, or `unTill`
  - verify a subsequent `postgres` change releases and tags new `postgres` and `unTill` versions, updates only the locked `postgres` dependency, and leaves `sqlite` and `UBL` unchanged
  - document the tested dependency topology, repository ownership, initial versions, and expected release versions beside the workflow scenario
