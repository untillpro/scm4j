---
change_id: 2609181231-migrate-to-github-namespace
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-203
---

# Change request: Migrate publishing coordinates to GitHub namespace

Refs:

- [PRIME-203: Migrate scm4j publishing coordinates](./issue-PRIME-203.md)

## Why

The current Maven group cannot be verified without ownership of the corresponding domain. A GitHub-based namespace provides coordinates that can be attributed to the Untill GitHub organization and prepared for publication through Maven Central and the Gradle Plugin Portal.

## What

Publishing and consumption use a verifiable GitHub-based namespace:

- The releaser is published as `io.github.untillpro:scm4j-releaser:<version>`.
- The Gradle plugin publication uses the `io.github.untillpro` namespace consistently for its group and plugin identity.
- Consumers can resolve and use both publications through the documented coordinates.
- Previously published `org.untillpro` artifacts remain separate and are identified as legacy coordinates.
- Namespace eligibility is confirmed for Maven Central and the Gradle Plugin Portal.

## How

Decisions:

- Limit the coordinate migration to the two externally published deliverables, the releaser Maven artifact and the Gradle plugin; retain the existing Gradle identities of unpublished support modules.
- Keep module metadata as the source of truth for publication groups: the releaser Maven publication continues to derive its group from the project, while the Gradle plugin's project group and plugin ID move together under the same `io.github.untillpro` prefix.
- Make a one-way identity cutover without deleting, republishing, relocating, or dual-publishing releases under the legacy Maven group or Gradle plugin ID.
- Retain existing Java package names and implementation class names because publication coordinates do not require a source or binary package migration.
- Point ownership metadata at the current `untillpro/scm4j` repository and preserve the namespace rationale alongside the publication identity so later changes do not reintroduce an unverifiable domain-based group.
- Verify the releaser coordinates through a local Maven publication and consumer resolution, verify the new plugin ID through Gradle TestKit, and use live registry publication and consumption as the final integration checks.
- Treat Maven Central namespace approval and Gradle Plugin Portal approval as external release gates: a GitHub organization namespace is not granted automatically by Central, and changing a plugin group or ID triggers Plugin Portal review.

Assumptions:

- Untill can demonstrate control of the `untillpro` GitHub organization and obtain the required Central and Plugin Portal approvals for `io.github.untillpro`.
- Consumers can migrate by replacing the Maven group or Gradle plugin ID without a compatibility alias.

Out of scope:

- Renaming Java packages or moving source directories to match the publication namespace.
- Adding a Maven Central deployment pipeline or managing registry credentials.

References (internal):

- [central module metadata and publication wiring](../../../../../build.gradle)
- [tag-driven GitHub Packages release flow](../../../../../.github/workflows/publish-releaser.yml)
- [releaser consumer guidance](../../../../../README.md)
- [Gradle plugin consumer and publishing guidance](../../../../../scm4j-releaser-gradle-plugin/README.md)
- [Gradle plugin ID integration coverage](../../../../../scm4j-releaser-gradle-plugin/src/test/java/io/github/scm4j/releaser/gradle/ReleaserGradlePluginIntegrationTest.java)

References (external):

- [Maven Central namespace registration and GitHub organization limitation](https://central.sonatype.org/register/namespace/)
- [Gradle Plugin Portal namespace and review requirements](https://plugins.gradle.org/docs/publish-plugin)

## Provisioning and configuration

### Publication metadata

- [x] update: [central Gradle publication configuration](../../../../../build.gradle) (manual edit; no Gradle CLI command updates project publication identities)
  - set the `scm4j-releaser` module group to `io.github.untillpro` while continuing to derive its Maven publication group from `project.group`
  - set the `scm4j-releaser-gradle-plugin` module group to `io.github.untillpro` and its plugin ID to `io.github.untillpro.scm4j-releaser-gradle-plugin`
  - point the plugin website and VCS metadata to the current `untillpro/scm4j` repository
  - add adjacent comments explaining that `io.github.untillpro` is the verifiable GitHub-based namespace and that domain-based groups require control of the corresponding domain
