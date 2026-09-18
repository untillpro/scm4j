---
change_id: 2609180953-migrate-maven-group-to-org-untillpro
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-197
---

# Change request: Migrate Maven group ID to org.untillpro

Refs:

- [PRIME-197: migrate-drivers: scm4j: migrate Maven group ID from org.scm4j to org.untillpro](./issue-PRIME-197.md)

## Why

The releaser package is currently published under the legacy `org.scm4j` Maven namespace rather than the organization-owned `org.untillpro` namespace. Aligning the publication identity makes ownership clear to consumers while retaining the already-published coordinates for version 35.0.0.

## What

The scm4j releaser's Maven publication identity will move to the organization namespace while preserving existing releases:

- New releaser versions are available as `org.untillpro:scm4j-releaser:<version>`.
- The existing `org.scm4j:scm4j-releaser:35.0.0` artifact remains unchanged.
- Consumer-facing examples and publication verification use the new coordinates.
- Java package names under `org.scm4j` remain unchanged.

## How

Decisions:

- Change only the releaser's central Gradle module group and keep the Maven publication identity derived from `project.group`, preserving one source of truth for its group ID.
- Make a one-way coordinate cutover for releases after 35.0.0; do not republish, delete, relocate, or dual-publish the existing artifact under either namespace.
- Retain the current Gradle groups of every other scm4j module, including the separately published Gradle plugin.
- Document the GitHub Packages repository and new dependency notation alongside the releaser's existing usage guidance as the canonical consumer example.
- Verify the generated GAV and POM through a local Maven publication together with the existing releaser test suite; keep the release workflow unchanged because it supplies only the version and delegates publication coordinates to Gradle.

Assumptions:

- Consumers can migrate by changing the dependency group directly and do not require a relocation POM or compatibility alias for the old coordinate.

References:

- [central module identity and Maven publication wiring](../../../../../build.gradle)
- [current releaser usage guidance](../../../../../scm4j-releaser/README.md)
- [release publication workflow](../../../../../.github/workflows/publish-releaser.yml)
- [original GitHub Packages publication design](../../../archive/2609/2609180848-publish-releaser-github-maven/change.md)

## Provisioning and configuration

### Gradle publication identity

- [x] update: [build.gradle](../../../../../build.gradle) (manual edit; no applicable Gradle CLI command updates project metadata)
  - set the metadata group of every module previously assigned `org.scm4j` to `org.untillpro`; retain the empty `scm4j-test-jitpack` group and the Gradle plugin's `io.github.scm4j` group
  - continue deriving the Maven publication group ID from `project.group` rather than duplicating the new namespace in the publication block
  - run `.\gradlew.bat :scm4j-releaser:test :scm4j-releaser:publishReleaserPublicationToMavenLocal "-PreleaserVersion=0.0.0-prime-197-test"`, then confirm its POM and JAR resolve under `org/untillpro/scm4j-releaser`, with no artifact for that test version under `org/scm4j/scm4j-releaser`
