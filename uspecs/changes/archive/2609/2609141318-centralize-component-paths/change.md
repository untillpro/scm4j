---
change_id: 2609140803-centralize-component-paths
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-155
scope: [releaser, tests]
---

# Change request: Centralized component-relative repository paths

## Why

Components stored in repository subfolders need every metadata operation to address the same component-relative path. Centralizing path resolution prevents release workflow steps from independently composing paths and accidentally reading or modifying repository-root metadata.

## What

Symptom: Release workflows for a subfolder-backed component can read or update root-level `version` and `mdeps` files instead of that component's files.

```text
release processing starts for a component with a configured subfolder
      |
      v
VCSComponentLocation normalizes the component subfolder
      |
      v
workflow steps pass hard-coded metadata names or compose paths independently
                                              <-- fault: component-relative path resolution is inconsistent
      |
      v
VCS reads or writes repository-root metadata instead of component metadata   (symptom)
```

Corrected behavior: All workflow metadata paths are resolved through one repository helper that combines the normalized `VCSComponentLocation` subfolder with the requested relative path and preserves root-relative behavior when no subfolder is configured.

## How

Decisions:

- Keep `VCSRepository` as the workflow-facing component-path boundary and delegate joining to `VCSComponentLocation`, where the normalized subfolder is owned.
- Compose VCS paths with forward slashes rather than platform filesystem APIs, returning the requested relative path unchanged when the normalized subfolder is empty and otherwise joining it with exactly one separator.
- Route all component-owned `version` and `mdeps` reads, writes, and batched change-list entries through the shared resolver across development, release, build, patch, and delayed-tag flows, without falling back to root metadata for a configured subfolder.
- Keep the VCS contract and Git and SVN adapters unchanged; releaser workflows remain responsible for supplying already-resolved repository-relative file paths.
- Verify the resolver directly and exercise complete fork, build, dependency-locking, and delayed-tag workflows for subfolder-backed components while retaining regression coverage for repositories without subfolders.

Assumptions:

- Component-owned metadata path arguments are non-empty relative VCS paths without a leading separator.

Out of scope:

- Changing release branch or tag namespaces, component history filtering, component-location equality, or build checkout layout.
- Migrating root-level metadata into configured component subfolders or providing compatibility fallback to root files.

References:

- [repository configuration and identity boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
- [normalized component location](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- [repository-relative VCS file contract](../../../../../scm4j-vcs-api/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [current development-path composition](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
- [release metadata access boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/ReleaseBranchFactory.java)
- [subfolder workflow fixture and assertions](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
- [prior development-branch isolation design](../../../archive/2609/2609100910-subfolder-aware-develop-branch/change.md)

## Construction

### Tests

- [x] update: [conf/VCSRepositoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryTest.java)
  - verify component-path resolution preserves a relative path for null and empty subfolders
  - verify normalized forward-slash, backslash, and trailing-separator subfolders produce the same component-relative path with exactly one separator
  - cover both metadata leaf names and an already nested relative path

- [x] update: [branch/DevelopBranchTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/DevelopBranchTest.java)
  - replace test-side path composition with the repository path boundary
  - retain component-local version, missing-version, path-filtered history, and root-repository regression coverage
  - verify a non-normalized configured subfolder resolves the same version path as its normalized component location

- [x] update: [releaser/WorkflowTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
  - seed component-local `version` and `mdeps` fixtures through the repository path boundary when subfolders are configured
  - make shared release-version and dependency assertions read component-relative metadata
  - retain distinguishable root metadata so subfolder workflow tests detect accidental root reads or writes

- [x] update: [releaser/WorkflowBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowBuildTest.java)
  - extend the subfolder-backed fork-and-build workflow to verify development and release version changes remain component-local
  - verify dependency locking reads and writes the component-local `mdeps` file and leaves root metadata unchanged
  - retain immediate tag and release-branch naming assertions

- [x] update: [releaser/WorkflowDelayedTagTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)
  - exercise delayed-tag version inspection and patch bumping against component-local release metadata
  - update manual version mutations and assertions to use the repository path boundary
  - verify delayed tagging does not modify the root `version` file for a subfolder-backed component

- [x] update: [releaser/WorkflowPatchesTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowPatchesTest.java)
  - cover a subfolder-backed patch workflow reading and bumping the component-local release version
  - verify dependency patch actualization reads and writes component-local `mdeps` metadata without changing the root file

### Repository path boundary

- [x] update: [conf/VCSComponentLocation.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
  - join relative paths using the normalized subfolder stored by the component location
  - preserve the supplied relative path for root repositories and join configured subfolders with one forward slash

- [x] update: [conf/VCSRepository.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
  - expose the workflow-facing component-relative path resolver
  - delegate joining to the component location instead of duplicating normalized-subfolder handling

### Metadata consumers

- [x] update: [releaser/Utils.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
  - resolve the development `version` read through the repository path boundary

- [x] update: [branch/DevelopBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
  - replace local `version` path composition with the repository path boundary
  - leave the existing normalized subfolder history selection and root-query optimization unchanged

- [x] update: [branch/ReleaseBranchFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/ReleaseBranchFactory.java)
  - resolve current and patch release-branch `version` reads through the repository path boundary
  - resolve development and release `mdeps` reads through the same boundary

- [x] update: [scmactions/SCMActionTag.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
  - use the component-relative `version` path when delayed tagging inspects and conditionally bumps a release branch

- [x] update: [procs/SCMProcForkBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcForkBranch.java)
  - use the component-relative `version` path for the release-branch change-list entry and development minor-version bump

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - bump the patch version at the component-relative release-branch path

- [x] update: [procs/SCMProcLockMDeps.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcLockMDeps.java)
  - read the current component-local `mdeps` file and write the locked content to the same path in the batched change list

- [x] update: [procs/SCMProcActualizePatches.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcActualizePatches.java)
  - read and update dependency patch versions through the component-relative `mdeps` path
