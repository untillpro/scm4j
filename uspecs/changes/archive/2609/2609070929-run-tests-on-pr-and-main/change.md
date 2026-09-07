---
change_id: 2609070818-run-tests-on-pr-and-main
type: ci
issue_url: https://untill.atlassian.net/browse/PRIME-111
---

# Change request: Automated tests for pull requests and main

Refs:

- [PRIME-111: migrate-drivers: scm4j: implement tests run on PR and commit to main](./issue-PRIME-111.md)

## Why

Changes to the scm4j monorepo are not automatically verified when proposed or integrated. Running the test suite for pull requests and commits to the main branch gives contributors timely feedback and protects the shared branch from undetected test regressions.

## What

The repository gains continuous test validation for contributed and integrated changes:

- Every pull request is automatically validated by the scm4j test suite.
- Every commit to the main branch is automatically validated by the scm4j test suite.
- A test failure makes the corresponding CI run fail so contributors can identify regressions before relying on the change.

## How

Decisions:

- Use one GitHub Actions workflow triggered by `pull_request` for all target branches and by `push` only for `main`; use `pull_request`, not `pull_request_target`, so contributed code runs without privileged base-repository context.
- Run one GitHub-hosted Ubuntu job with a Temurin JDK 8, matching the centralized build toolchain while keeping the initial CI contract to one portable baseline rather than introducing an operating-system or JDK matrix; Temurin is used because `setup-java` supports Oracle JDK only from version 17 and Temurin provides a maintained OpenJDK 8 build.
- Invoke the checked-in root Gradle Wrapper and its aggregate `test` task as the sole authority for test selection; do not enumerate subprojects or substitute the narrower root `build` task.
- Use the maintained checkout and Java setup actions together with Gradle's setup action, select the latest stable exact release tag for each action rather than a moving major-version tag, and use the open-source basic Gradle cache provider with cache writes limited to the default branch.
- Grant the workflow token read-only repository-content access and pass no repository secrets or external-service credentials into pull request test runs.

Assumptions:

- GitHub Actions and GitHub-hosted runners are enabled for the repository.
- Hosted runners can reach the Gradle distribution, Maven Central, and JitPack endpoints required by the existing build.

Out of scope:

- Configuring branch protection or making the workflow check mandatory for merging.
- Provisioning a Jenkins service or credentials for the conditionally enabled Jenkins functional tests.
- Publishing coverage, build artifacts, packages, or releases from the test workflow.

References (internal):

- [centralized Java toolchain and aggregate test orchestration](../../../../../build.gradle)
- [pinned Gradle distribution](../../../../../gradle/wrapper/gradle-wrapper.properties)
- [conditional Jenkins functional-test requirements](../../../../../scm4j-jenkins/README.md)

References (external):

- [GitHub guidance for Java and Gradle CI](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle)
- [GitHub Actions event filters and token permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [setup-java distribution and version support](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md#oracle)
- [Gradle action setup, wrapper validation, and caching](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)
- [Gradle and Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)

## Provisioning and configuration

- [x] create: [.github/workflows/test.yml](../../../../../.github/workflows/test.yml)
  - define one test workflow triggered for every pull request and for pushes to `main`
  - grant the workflow token only `contents: read` permission and do not expose repository secrets to the test job
  - run one job on `ubuntu-latest` and provision the latest available Temurin 8 JDK
  - use `actions/checkout@v7.0.1`, the latest stable exact release
  - use `actions/setup-java@v6.0.0`, the latest stable exact release
  - use `gradle/actions/setup-gradle@v6.3.0`, the latest stable exact release; select its open-source basic cache provider and retain its default policy in which only the default branch writes cache entries
  - make the checked-out root Gradle wrapper executable and run its aggregate `test` task so the job status follows the test command's exit status
