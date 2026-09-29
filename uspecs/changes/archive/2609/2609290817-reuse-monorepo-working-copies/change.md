---
change_id: 2609290746-reuse-monorepo-working-copies
type: perf
scope: [vcs, releaser-tests]
issue_url: https://untill.atlassian.net/browse/PRIME-276
---

# Change request: Reuse monorepo component working copies

## Why

Release status currently discards each monorepo component working copy, so successive repository reads repeatedly clone, check out, and delete the same repository. For large dependency graphs and high parallelism, this redundant work saturates storage and JGit resources until status calculation appears to stall. Component workspaces are already partitioned and locked, so retaining them also lets subsequent status and release-workflow VCS operations reuse the repository prepared during the initial calculation.

## What

Improve status and release-workflow VCS performance without changing calculated statuses, CLI output, release decisions, or the public VCS API contract:

- Regular working copies remain available after their lease is released and are reused by later operations for the same repository component.
- Monorepo components in different subfolders remain isolated, and a working copy is never leased to concurrent operations.
- Follow-on release-workflow VCS operations can reuse a working copy prepared during status calculation.
- Explicitly temporary or corrupted working copies continue to be removed when released.
- Repository data remains refreshed before an operation so reuse does not expose stale branch or tag state.

## How

Decisions:

- Make ordinary repository-workspace leases reusable for both repository-root and component-subfolder locations; reserve disposable leases for explicit temporary use and corrupted working copies.
- Retain the repository URL plus normalized component subfolder as the working-copy pool's isolation key, so sibling monorepo components never draw from the same mutable pool.
- Keep lease lifecycle policy in the backend-independent working-copy layer so Git and SVN receive identical reuse behavior without changing the public workspace or VCS contracts.
- Preserve the existing file-lock pool behavior: reuse an unlocked copy and allocate a distinct retained copy when every existing copy is leased.
- Preserve each VCS adapter's existing refresh, branch-switching, cleanup, and corruption handling when a retained copy is acquired again.
- Verify the policy at the working-copy lifecycle boundary and through concurrent monorepo status and release workflows that protect component independence.

Assumptions:

- Subfolder-partitioned workspace paths and exclusive leases are sufficient to prevent the cross-component interference addressed by PRIME-253 without disposing successful working copies.
- Existing Git and SVN operations leave successfully released working copies reusable and identify the failure paths that require corruption cleanup.

Out of scope:

- Changing dependency traversal, common-pool parallelism, or transport retry behavior.
- Replacing the release build's separate full or sparse checkout with the internal status working copy.
- Adding age-, count-, or size-based eviction for retained working-copy pools.

References:

- [repository and component workspace isolation policy](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
- [working-copy locking, reuse, and disposal lifecycle](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopy.java)
- [workspace lifecycle verification](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspaceTest.java)
- [concurrent monorepo status and release behavior](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
- [release build checkout boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [prior monorepo isolation decision](../../../archive/2609/2609251021-prime-253/change.md)

## Construction

### Tests

- [x] update: [workingcopy/VCSRepositoryWorkspaceTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspaceTest.java)
  - replace the disposable component-workspace expectation with persistence and reuse after a lease is released
  - verify repository-root and component-subfolder workspaces follow the same reusable lease lifecycle while their pool directories remain distinct

- [x] verify: [workingcopy/VCSLockedWorkingCopyTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopyTest.java)
  - run the existing coverage that simultaneous leases use different working-copy folders and that explicit temporary or corrupted copies are removed

- [x] update: [releaser/WorkflowMonorepoForkAndBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
  - repeat the shared-repository status scenario with fresh status caches so the second calculation exercises retained component working copies
  - verify both runs preserve independent statuses and versions for sibling components processed concurrently

### VCS workspace

- [x] update: [workingcopy/VCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
  - obtain a reusable ordinary lease for component-subfolder workspaces instead of forcing every lease to be temporary
  - retain subfolder-derived pool partitioning and the explicit temporary-working-copy path
