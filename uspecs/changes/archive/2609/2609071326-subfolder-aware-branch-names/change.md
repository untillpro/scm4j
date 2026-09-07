---
change_id: 2609071147-subfolder-aware-branch-names
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-120
scope: [releaser, tests]
---

# Change request: Subfolder-aware release branch names

Refs:

- [PRIME-120: migrate-drivers: scm4j: consider subfolder on branches naming](./issue-PRIME-120.md)

## Why

Components located in subfolders of a shared repository need distinct release branch namespaces. Including the configured subfolder in release branch names prevents one component's release branches from being mistaken for another component's branches.

## What

- A component with a configured subfolder has that subfolder prepended to its release branch names.
- For example, a component in `vmax-fiscal-printer-driver` uses release branch names such as `vmax-fiscal-printer-driver/release/<ver>`.
- Components without a configured subfolder retain the existing release branch naming convention.
- Release workflows consistently derive and recognize branch names using the component's applicable naming convention.
- Resolved repositories with the same URL but different configured subfolders are treated as distinct component repository locations.

## How

Decisions:

- Keep release branch naming centralized in the releaser's repository-aware naming boundary so branch creation, discovery, building, delayed tagging, and status reporting all receive the same name without VCS-specific changes.
- Compose a configured name as `<subfolder>/<release-branch-prefix><release-version>`, inserting exactly one forward slash between the subfolder and the existing name; preserve the existing name unchanged when the subfolder is `null` or empty.
- Apply the subfolder as both a release branch namespace and part of component repository identity; leave develop branch names, tag names, repository content paths, history filtering, and physical workspace identity unchanged.
- Cover the centralized naming boundary with focused tests for configured, absent, and empty subfolders, while retaining existing workflow coverage as the compatibility check for repositories without subfolders.

Assumptions:

- Configured subfolder values are valid repository-relative paths that can also serve as Git and SVN branch namespaces.

Out of scope:

- Discovering, migrating, or falling back to legacy unprefixed release branches after a component is configured with a subfolder.

References:

- [repository-aware release branch naming](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
- [release branch discovery](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/ReleaseBranchFactory.java)
- [release branch creation](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcForkBranch.java)
- [repository subfolder configuration](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
- [releaser utility coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/UtilsTest.java)

## Construction

### Tests

- [x] update: [conf/VCSRepositoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryTest.java)
  - verify repositories with the same URL and different subfolders are unequal
  - verify equality and hash-code contracts use both URL and subfolder

- [x] update: [releaser/UtilsTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/UtilsTest.java)
  - verify a configured subfolder prefixes the existing release branch name with one forward slash separator
  - verify a trailing separator in the configured subfolder does not produce a doubled separator
  - verify `null` and empty subfolders preserve the existing release branch name, including custom release branch prefixes

### Component repository identity

- [x] update: [conf/VCSRepository.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
  - include the configured subfolder alongside URL in equality and hash-code calculations

### Release branch naming

- [x] update: [releaser/Utils.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
  - derive the existing release branch name from the configured release branch prefix and release version
  - prepend the repository subfolder when present, inserting a forward slash only when the configured value does not already end with one
  - keep all release workflow consumers on the centralized naming method without changing VCS operations or unrelated names
