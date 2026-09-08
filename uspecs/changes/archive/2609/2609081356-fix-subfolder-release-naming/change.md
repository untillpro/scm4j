---
change_id: 2609080727-fix-subfolder-release-naming
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-127
scope: [releaser, tests]
---

# Change request: Subfolder-aware release references and history

## Why

Components can share a repository while keeping their sources in distinct subfolders. Their release references must identify the component independently of its storage path, while status calculation must inspect only that component's history and release boundaries.

## What

Symptom: For a component configured with a repository subfolder, release branches and tags include the storage path, commit history is not scoped to the component, and nested component tags can be mistaken for the component's own release boundary.

```text
release or status processing starts for a subfolder-backed component
      |
      v
VCSRepositoryFactory expands the configured subfolder
      |
      v
Utils.getReleaseBranchName() and Utils.getTagDesc()
      |               <-- fault: prefixes release references with the storage subfolder
      v
ExtendedStatusBuilder.walkOnCommits() and Utils.isTagForRepository()
      |               <-- fault: uses repository-wide history and accepts nested tag prefixes
      v
branches and tags expose the storage path, while unrelated commits or tags affect status   (symptom)
```

Corrected behavior: For subfolder-backed components, scm4j names release branches and tags without the repository storage prefix, reads commits from the configured subfolder, and recognizes only that component's tags as release boundaries.

## How

Decisions:

- Separate the logical release namespace from the repository-relative source path: derive release references from the component artifact identifier and reserve the full configured subfolder for VCS content and history selection.
- For a subfolder-backed component, compose release branches as `<artifact-id>/<release-branch-prefix><release-version-without-patch>` and tags as `<artifact-id>/<release-version>`; preserve the existing branch and tag conventions when no subfolder is configured.
- Preserve `releaseBranchPrefix` exactly, including any trailing slash: component `vmax` version `1.4` uses branch `vmax/B1` with prefix `B` or `vmax/B/1` with prefix `B/`. Both configurations use tag `vmax/1.4`.
- Keep branch and tag construction and tag ownership matching in the shared repository-aware naming boundary, and use a delimiter-aware `<artifact-id>/` namespace so sibling or nested component tags cannot act as release boundaries.
- Scope every page of release-status commit traversal through the existing repository-relative-path argument of the VCS history contract, leaving Git and SVN adapter interfaces unchanged.
- Verify the shared naming boundary directly and cover branch discovery and creation, immediate and delayed tagging, component-specific tag recognition, and subfolder-filtered status history through the existing releaser workflow test layers.

Assumptions:

- Artifact identifiers are unique among components sharing a repository and are valid as one segment of Git and SVN branch and tag names.

Out of scope:

- Migrating or recognizing release branches and tags created with the previous full-subfolder namespace.
- Changing repository identity, develop-branch naming, workspace layout, or build checkout behavior.

References:

- [central release reference naming](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
- [component artifact identity](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/Component.java)
- [release-status history and tag traversal](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- [path-scoped VCS history contract](../../../../../scm4j-vcs-api/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [release naming regression coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/UtilsTest.java)
- [prior subfolder branch design](../../../archive/2609/2609071326-subfolder-aware-branch-names/change.md)
- [prior subfolder tag design](../../../archive/2609/2609071524-subfolder-aware-tag-names/change.md)
- [nested tag boundary review finding](https://github.com/untillpro/scm4j/pull/6#discussion_r3951051596)

## Construction

### Tests

- [x] update: [releaser/UtilsTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/UtilsTest.java)
  - verify a repository named `driver` with subfolder `components/driver` produces `driver/release/1.2` and `driver/1.2.3` instead of names containing `components`
  - verify component `vmax` version `1.4` produces branch `vmax/B1` with prefix `B` and `vmax/B/1` with prefix `B/`, placing the artifact namespace before the unchanged prefix
  - verify both custom prefix configurations produce tag `vmax/1.4` with message `1.4 release`
  - preserve branch and tag naming for `null` and empty subfolders

- [x] update: [conf/VCSRepositoryFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryFactoryTest.java)
  - verify component-based repository resolution uses the full coordinate name for configuration lookup but exposes the artifact identifier as the logical release namespace
  - preserve the bare-name YAML fixtures and exercise their mapping rules using component stubs with matching configuration names
  - verify the package-private string overload uses `<name>:<name>` for fallback URL expansion while keeping the artifact identifier as the repository name

- [x] update: [releaser/ExtendedStatusBuilderTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)
  - verify release-status traversal passes the full configured subfolder to each paged `getCommitsRange` request
  - verify only tags in the exact artifact namespace mark a release boundary, including rejection of nested-component tags that merely share a path prefix
  - preserve repository-wide tag recognition and history traversal when no subfolder is configured

- [x] update: [releaser/WorkflowBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowBuildTest.java)
  - exercise immediate branch creation and tagging with a `components/$1` mapping
  - assert the release branch places the artifact identifier before the configured prefix, while the tag uses the artifact identifier and full version; neither includes the parent storage folder

- [x] update: [releaser/WorkflowDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)
  - exercise delayed tag creation and existing-tag detection with a `components/$1` mapping
  - assert both paths use the same artifact-based tag namespace

### Component release namespace

- [x] update: [conf/VCSRepositoryFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
  - keep configuration matching based on the full component coordinate name
  - provide the coordinate artifact identifier to `VCSRepository` as the logical name used by release-reference helpers
  - consolidate repository construction in the `Component` overload, deriving the configuration name and artifact identifier from that component
  - make the string overload package-private for tests and delegate through `new Component(componentName + ":" + componentName)`

### Release reference naming

- [x] update: [releaser/Utils.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
  - derive the component namespace from the repository's logical name only when a subfolder is configured
  - compose release branches with the component namespace before the configured branch prefix and release version, preserving the prefix exactly
  - compose tags and match tag ownership with the same delimiter-aware component namespace
  - preserve legacy naming and tag recognition for repositories without subfolders

### Component history

- [x] update: [releaser/ExtendedStatusBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
  - pass the configured repository-relative subfolder to every paged release-branch history request
  - preserve the existing traversal limit, direction, and pagination behavior
