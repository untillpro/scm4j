---
change_id: 2609071421-subfolder-aware-tag-names
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-123
scope: [releaser, tests]
---

# Change request: Subfolder-aware tag names

Refs:

- [PRIME-123: migrate-drivers: scm4j: consider subfolder on tags naming](./issue-PRIME-123.md)

## Why

Components located in subfolders of a shared repository need distinct tag namespaces. Including the configured subfolder in tag names prevents one component’s releases from being mistaken for another component’s releases.

## What

- Release tags for a component with a configured repository subfolder are prefixed with that subfolder.
- For example, version `1.4.7` of a component in `vmax-fiscal-printer-driver` uses the tag name `vmax-fiscal-printer-driver/1.4.7`.
- Components without a configured repository subfolder retain the existing tag naming convention.
- Release workflows consistently create and recognize tags using the component’s applicable naming convention.

## How

Decisions:

- Keep component tag naming in the releaser's repository-aware naming boundary and pass repository context through both immediate and delayed tagging; leave version-control adapter contracts unchanged.
- Compose a configured tag name as `<subfolder>/<release-version>`, inserting exactly one forward slash between the subfolder and version; preserve the version-only name for a `null` or empty subfolder and keep the tag message based on the unqualified version.
- Retain the existing version-only tag descriptor entry point for source compatibility while routing internal release workflows through the repository-aware form.
- When release status logic treats tags as release boundaries, accept the current component's namespace and ignore sibling component namespaces; preserve existing recognition behavior for repositories without a subfolder.
- Verify the naming boundary directly and cover immediate tagging, delayed tagging, and release-status isolation at workflow level while relying on existing cross-adapter coverage for slash-delimited tags.

Assumptions:

- Configured subfolder values are valid repository-relative paths that can also serve as Git and SVN tag namespaces.

Out of scope:

- Renaming existing unprefixed tags or falling back to them after a repository is configured with a subfolder.

References:

- [central release naming utilities](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
- [immediate build tagging flow](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [delayed tagging flow](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
- [release-status tag recognition](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- [slash-delimited tag adapter coverage](../../../../../scm4j-vcs-test/src/main/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)

## Construction

### Tests

- [x] update: [releaser/UtilsTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/UtilsTest.java)
  - verify a configured subfolder prefixes the release version in the tag name with exactly one forward slash
  - verify `null` and empty subfolders preserve the version-only tag name
  - verify tag messages remain based on the unqualified release version and the existing version-only descriptor remains compatible

- [x] update: [releaser/ExtendedStatusBuilderTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)
  - verify a subfolder-backed component recognizes tags in its own namespace as release boundaries
  - verify sibling and legacy unprefixed tag namespaces do not affect that component's release status
  - verify repositories without subfolders retain the existing tag-boundary behavior

- [x] update: [releaser/WorkflowTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java) and [releaser/WorkflowBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowBuildTest.java)
  - make shared tag assertions repository-aware, including version handling for namespaced tag names
  - verify an immediate build creates the expected subfolder-prefixed tag on the release commit
  - retain the existing workflow coverage for repositories without subfolders

- [x] update: [releaser/WorkflowDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)
  - use repository-aware tag descriptions when arranging and asserting delayed-tag scenarios
  - verify delayed tagging creates the expected subfolder-prefixed tag
  - verify an existing tag in the component namespace is reported and skipped without creating a duplicate

### Releaser

- [x] update: [releaser/Utils.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
  - add repository-aware tag description construction that prefixes configured subfolders and preserves version-only names for absent subfolders
  - reuse the existing one-separator namespace composition and leave the version-based tag message unchanged
  - retain the version-only tag description entry point for source compatibility
  - centralize repository namespace matching for tags used as release boundaries

- [x] update: [releaser/ExtendedStatusBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
  - filter tags found on a revision through the current repository namespace before treating the revision as the component's last release
  - preserve the current any-tag boundary behavior when no subfolder is configured

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - use the repository-aware tag description for immediate release tagging and status messages

- [x] update: [scmactions/SCMActionTag.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
  - use the repository-aware tag description for delayed tag creation, existing-tag handling, and status messages
