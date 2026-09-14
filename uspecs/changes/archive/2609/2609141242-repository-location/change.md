---
change_id: 2609141101-repository-location
type: refactor
issue_url: https://untill.atlassian.net/browse/PRIME-156
scope: [releaser, tests]
breaking: true
---

# Change request: Centralized component location state

## Why

A repository URL and its optional monorepo subfolder together describe a component's location, but this value is currently presented as an identifier and duplicated by the full repository configuration. Giving the component location a clear role avoids duplicated state and makes monorepo behavior easier to understand.

## What

- Component locations consistently represent a VCS URL and an optional normalized monorepo subfolder.
- Component-relative path joining is owned by the same component location used for equality and lookup.
- Status caching, delayed tags, and processed-repository tracking continue to distinguish components by both URL and subfolder.
- Existing repository configuration files, delayed-tag persistence, and release workflow behavior remain unchanged; Java types and accessors adopt component-location terminology.

## How

Decisions:

- Retain a small immutable component-location value object, renamed to `VCSComponentLocation`, instead of using the full runtime repository configuration as an identity key.
- Make the component location the sole owner of the repository URL, normalized optional subfolder, equality, hashing, display value, and component-relative path joining; the runtime repository delegates location-related access.
- Use component locations consistently as keys for status caching, delayed tags, and processed-repository tracking, replacing identifier terminology throughout those boundaries.
- Replace the old identifier type and accessors rather than maintaining a parallel compatibility API.
- Preserve the delayed-tag YAML fields and legacy URL-only input while changing only the in-memory location type.

Assumptions:

- Consumers of the Java types can migrate with this repository and do not require a binary-compatibility shim for the renamed identifier type.

Out of scope:

- Canonicalizing or otherwise changing repository URLs.
- Changing existing subfolder normalization rules.

References:

- [component location value semantics](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- [repository configuration ownership](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
- [component location construction](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- [status cache key contract](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/CachedStatuses.java)
- [delayed-tag persistence compatibility](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/DelayedTagsFile.java)
- [component location behavior tests](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryTest.java)

## Construction

### Tests

- [x] create: [conf/VCSComponentLocationTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSComponentLocationTest.java)
  - verify that null, empty, trailing-slash, and backslash subfolders have the existing normalized representation
  - verify equality and hashing use the URL and normalized subfolder
  - verify display values for root repositories and monorepo components
  - verify relative paths remain unchanged at the repository root and are prefixed for a monorepo component

- [x] update: [conf/VCSRepositoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryTest.java)
  - verify the repository exposes its location and delegates URL, normalized subfolder, and component-relative path access
  - update repository equality verification to use the location field as its sole identity state

- [x] update: [conf/VCSRepositoryFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryFactoryTest.java)
  - verify component locations are created from component URL and subfolder configuration
  - update root-repository expectations to the normalized empty subfolder representation

- [x] update: [conf/DelayedTagsFileTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/DelayedTagsFileTest.java)
  - use component locations for delayed-tag lookup, writing, and removal
  - retain coverage for URL-and-subfolder isolation and legacy URL-only content

- [x] update: [actions/ActionAbstractTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/actions/ActionAbstractTest.java)
  - use component locations when verifying processed-repository tracking

- [x] update: [releaser/WorkflowTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
  - derive component file paths through the component location boundary
  - use component locations for delayed-tag fixture lookup

- [x] update: [releaser/WorkflowBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowBuildTest.java)
  - verify cache invalidation with component-location keys

- [x] update: [releaser/WorkflowDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)
  - use component locations for monorepo-aware delayed-tag assertions

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - seed cached status by component location

### Component location

- [x] rename: [conf/VCSComponentLocation.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java) from `VCSRepositoryId.java`
  - rename the class and constructor to `VCSComponentLocation`
  - retain immutable URL and normalized optional subfolder state
  - retain equality, hashing, and display semantics
  - add component-relative path joining based on the normalized subfolder

- [x] update: [conf/VCSRepository.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
  - replace the identifier field with a `componentLocation` value
  - remove the duplicate URL and subfolder fields
  - expose the location through `getComponentLocation` and delegate URL, subfolder, and component-relative path access to it
  - base repository equality and hashing on the location
  - accept the factory's prebuilt component location without reconstructing it

- [x] update: [conf/VCSRepositoryFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
  - replace identifier factory methods with `getVCSComponentLocation` overloads for components and component names
  - construct runtime repositories and standalone location keys from the same URL and subfolder configuration
  - pass the configured component-location value directly into the runtime repository

### Location consumers

- [x] update: [releaser/CachedStatuses.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/CachedStatuses.java)
  - use component locations as cache keys

- [x] update: [conf/DelayedTagsFile.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/DelayedTagsFile.java)
  - replace identifier types and variables with component locations
  - keep the persisted `url` and optional `subfolder` fields and legacy URL-only reader unchanged

- [x] update: [actions/IAction.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/actions/IAction.java)
  - accept component locations for processed-repository operations

- [x] update: [actions/ActionAbstract.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/actions/ActionAbstract.java)
  - track processed repositories by component location and use the component location accessor

- [x] update: [releaser/ActionTreeBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ActionTreeBuilder.java)
  - use component locations for delayed-tag checks

- [x] update: [releaser/ExtendedStatusBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
  - replace identifier variables, cache access, and delayed-tag access with component locations
  - obtain dependency locations through the renamed factory boundary

- [x] update: [branch/DevelopBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
  - obtain the normalized history subfolder from the repository
  - resolve the development version path through the component-relative path boundary

- [x] update: [scmactions/SCMActionRelease.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionRelease.java)
  - obtain cached status by component location

- [x] update: [scmactions/SCMActionTag.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
  - use the component location for delayed-tag lookup and removal

- [x] update: [procs/SCMProcForkBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcForkBranch.java)
  - obtain cached status by component location

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - use component locations for cached status and delayed-tag operations

- [x] update: [procs/SCMProcLockMDeps.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcLockMDeps.java)
  - use component locations for current and dependency status lookup

- [x] update: [procs/SCMProcActualizePatches.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcActualizePatches.java)
  - use component locations for current and dependency status lookup
