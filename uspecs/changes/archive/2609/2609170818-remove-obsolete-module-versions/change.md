---
change_id: 2609170743-remove-obsolete-module-versions
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-179
---

# Change request: Remove obsolete monorepo module versions

## Why

The source projects now build together through direct project dependencies in one monorepo, so their historical standalone versions no longer identify resolved dependencies. Retaining those versions creates unnecessary release metadata and maintenance, while the Gradle plugin still requires a version for Plugin Portal publication.

## What

The monorepo exposes version information only where an external publication contract requires it:

- All subprojects except the scm4j-releaser Gradle plugin build without an explicitly managed project version.
- Non-plugin archives, manifests, and generated resources do not embed obsolete per-module versions.
- The scm4j-releaser fat JAR has a stable, versionless name and remains directly executable.
- The scm4j-releaser CLI no longer prints a version banner.
- The Gradle plugin remains independently versioned and publishable through the Gradle Plugin Portal.

## How

### Decisions

- Treat a Gradle project version as external publication metadata rather than as the identity of a source project inside the monorepo. Keep an explicit project version only for the Gradle plugin, while retaining group and Java compatibility configuration independently.
- Remove version-derived metadata from every non-plugin output instead of replacing the removed module versions with a shared root version or a placeholder. This includes archive version suffixes, manifest version attributes, and generated resources whose only purpose is to expose another monorepo module's version.
- Publish the releaser fat JAR under the stable name `fat-scm4j-releaser.jar`, while preserving its executable entry point and bundled runtime dependencies. Consumers maintained in this repository will address that stable artifact directly.
- Remove the releaser CLI startup version banner without substituting another inferred build identifier, so CLI output begins with the selected command's behavior or diagnostics.
- Continue to use the Gradle plugin project's version as the sole versioned publication contract, including its plugin marker and implementation artifacts.
- Verify the change across the aggregate build, tests, archive metadata, the executable fat JAR, and plugin publication metadata so that an obsolete version cannot leak from an indirect Gradle default.

### Assumptions

- No repository-external consumer requires the historical versioned filenames or manifest version attributes of non-plugin artifacts.
- No supported runtime integration consumes the generated deployer API version resource; the repository contains no reader for it.
- The Gradle plugin is the only module in this monorepo that is independently published and therefore needs an explicit semantic version.

### Out of scope

- Changing the Gradle plugin's version number, release policy, or Plugin Portal publication workflow.
- Changing dependency versions, Java compatibility, package names, entry points, or artifact contents except where version metadata or version-derived naming is removed.
- Reintroducing standalone release/version files or independent publication workflows for the other modules.

### References

- [Centralized Gradle project and packaging configuration](../../../../../build.gradle)
- [Releaser CLI startup output](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/cli/CLI.java)
- [Releaser fat JAR launcher contract](../../../../../scm4j-releaser-shell/releaser)
- [Original monorepo centralization decisions](../../../archive/2609/2609030923-centralize-gradle-monorepo-build/change.md)

## Provisioning and configuration

- [x] update: [build.gradle](../../../../../build.gradle) (manual edit; no applicable Gradle CLI operation)
  - remove the `version` entry from every module's metadata except `scm4j-releaser-gradle-plugin`, and assign `project.version` only when that entry is present
  - configure every non-plugin archive, including binary, source, Javadoc, and releaser fat JAR outputs, to omit Gradle's version suffix rather than expose the default `unspecified` value
  - remove version attributes from non-plugin JAR manifests while retaining the executable main-class attributes used by the installer and releaser
  - remove generation and packaging of the unused `scm4j-deployer-api-version` resource
  - retain the Gradle plugin's explicit project version and its versioned publication metadata for Plugin Portal releases
  - configure the releaser fat JAR's stable output name as `fat-scm4j-releaser.jar` while preserving its bundled runtime classpath and signature exclusions
