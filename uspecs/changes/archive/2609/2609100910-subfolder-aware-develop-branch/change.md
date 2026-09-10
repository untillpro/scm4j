---
change_id: 2609100754-subfolder-aware-develop-branch
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-146
scope: [releaser, tests]
---

# Change request: Component subfolder isolation on development branches

Refs:

- [PRIME-146: migrate-drivers: scm4j: support monorepo components in DevelopBranch](./issue-PRIME-146.md)

## Why

Components sharing a repository need their own development version and change status so releases reflect only their changes. Reading root metadata or a sibling component's latest commit can select the wrong version, trigger an unnecessary release, or hide a pending component release.

## What

Symptom: A component configured with a repository subfolder can read the root version and report an incorrect modification status because unrelated sibling commits determine whether it needs a release.

```text
release or status processing starts for a component with subfolder configured
      |
      v
DevelopBranch reads version and modification status on repo.getDevelopBranch()
      |
      v
DevelopBranch.getVersion() reads Constants.VER_FILE_NAME
      |               <-- fault: omits repo.getSubfolder() from the version path
      v
DevelopBranch.isModified() calls VCS.log(developBranch, 1)
      |               <-- fault: selects the latest repository-wide commit
      v
#scm-ver and #scm-ignore checks use that potentially unrelated commit
      |
      v
wrong component version or unnecessary/missed release   (symptom)
```

Corrected behavior: For Git and SVN, development version reads use `<subfolder>/version` with the existing `ENoVersionFile` error and no root fallback when missing, while modification checks use the latest commit affecting that subfolder through the existing path-filtered VCS history API, retain `#scm-ver`, `#scm-ignore`, and empty-history handling, respect the configured `developBranch`, and preserve existing behavior when no subfolder is configured.

## How

Decisions:

- Keep component path selection in the development-branch layer, with Git and SVN retaining responsibility for branch resolution and history filtering through the existing VCS contract; no new adapter interface or dependency is needed.
- Retain the existing repository-wide history call for null and empty subfolders, selecting the filtered history route only for a configured component path to minimize compatibility changes for existing callers.
- Verify component isolation at the releaser boundary against real temporary Git and SVN repositories, reusing the existing repository creation and workspace utilities with isolated fixtures so coverage for both backends does not depend on changing the suite's global backend selection.

Assumptions:

- None

Out of scope:

- Extending subfolder support to other metadata readers, dependency files, version writes, or release-branch and tagging operations elsewhere in the release workflow.

References:

- [development version and modification boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
- [existing VCS content and history contract](../../../../../scm4j-vcs-api/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [existing component-scoped history integration](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- [repository fixture utilities and global backend selection](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)

## Construction

- [x] update: [branch/DevelopBranchTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/DevelopBranchTest.java)
  - exercise the development-branch behavior with both Git and SVN through the selectable test environment
  - verify a subfolder-backed component reads its own `version` instead of an existing root version, and raises `ENoVersionFile` without falling back when the component file is absent
  - verify component and sibling commit order cannot hide a component feature or make a component `#scm-ver` commit appear modified
  - verify the latest component `#scm-ignore` commit and an empty component history report unmodified
  - verify version reads and history selection use a custom develop branch
  - retain regression coverage for null and empty subfolders, including existing repository-wide history and version behavior

- [x] update: [testutils/TestEnvironment.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)
  - allow a test to select Git or SVN while preserving Git as the default for existing callers
  - use the selected backend consistently when writing repository configuration and creating temporary repositories

- [x] update: [releaser/WorkflowTestBase.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowTestBase.java)
  - seed each configured component subfolder with its development version so existing subfolder workflow tests use component-local metadata

- [x] update: [branch/DevelopBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
  - read `version` relative to the configured component subfolder and preserve the current root path when the subfolder is null or empty
  - query the latest commit through the descending, path-filtered VCS history API when a subfolder is configured
  - preserve the existing repository-wide history route for components without subfolders and apply the existing empty-history, `#scm-ver`, and `#scm-ignore` rules to the selected history
  - document why the root query stays on `log(..., 1)`: Git range lookup performs pull, fetch, and checkout work, while SVN range lookup resolves both history boundaries before requesting the range
