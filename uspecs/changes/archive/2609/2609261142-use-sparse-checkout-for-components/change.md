---
change_id: 2609261051-use-sparse-checkout-for-components
type: perf
issue_url: https://untill.atlassian.net/browse/PRIME-261
scope: [releaser, tests]
---

# Change request: Sparse checkout for monorepo components

Refs:

- [PRIME-261: migrate-drivers: scm4j: use sparse checkout for components from monorepo](./issue-PRIME-261.md)

## Why

Building a component from a monorepo currently checks out the entire repository even though the releaser executes the build only from the configured component subfolder. As migrated driver repositories grow, materializing unrelated components adds avoidable checkout time and disk I/O to every component build.

## What

Monorepo component builds use sparse checkout while preserving release behavior:

- Checkout work for a component with a configured subfolder no longer scales with unrelated monorepo content.
- The selected component directory is materialized at the same release-branch revision and supplied to the builder at the same working path as before.
- Components without a configured subfolder continue to receive a full repository checkout.
- Build commands, version updates, tagging, progress reporting, and checkout failure handling remain unchanged.

## How

Decisions:

- Select sparse versus full checkout in release-build orchestration from the repository's normalized component subfolder; do not add a separate configuration switch or move that policy into the VCS adapters.
- For a subfolder component, pass the existing release branch, selected build revision, checkout root, and normalized subfolder directly to the IVCS sparse-checkout operation, preserving the current builder working-directory layout.
- Reuse the existing checkout timing, progress, and exception boundary, and do not fall back to a full checkout when sparse checkout fails.
- Verify the release-build contract through the real Git and SVN adapters while retaining regression coverage for root-component full checkout and the existing revision and tagging behavior.

Assumptions:

- Release builds for configured subfolder components require no repository-root or sibling-component files outside the selected subtree.
- Environments that build Git-backed subfolder components provide a native Git command compatible with the existing sparse-checkout adapter.

Out of scope:

- Adding a per-component opt-out or full-checkout fallback for builds that depend on files outside their configured subfolder.
- Applying sparse materialization to status calculation, branch management, tagging, version updates, or other non-build operations.
- Changing the IVCS sparse-checkout contract or either backend implementation.

References:

- [release-build checkout boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [normalized component location](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- [shared sparse-checkout contract](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [Git sparse-checkout runtime dependency](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [release-build regression coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)

## Construction

### Tests

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - exercise the release-build scenarios through default git adapter only
  - ensure files in unrelated top-level and sibling directories already exist at the selected component revision, then advance the repository head with later unrelated changes so the fixture distinguishes sparse materialization from revision selection
  - verify subfolder builds materialize the component directory only and preserve its builder working directory, while root components continue to receive the full checkout
  - retain coverage for missing release branches, immediate and delayed tagging, version updates, build-time environment values, and unrelated head content

### Release build

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - use the existing full checkout when the repository has no configured subfolder
  - use IVCS sparse checkout with the normalized subfolder when building a subfolder component, passing the same release branch, build directory, and selected revision
  - keep build-directory lifecycle, progress and duration reporting, build-time environment values, builder working-directory selection, and failure propagation unchanged
