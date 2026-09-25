---
change_id: 2609250716-prime-253
type: fix
scope: [releaser, vcs, diagnostics]
issue_url: https://untill.atlassian.net/browse/PRIME-253
---

# Change request: Reliable concurrent monorepo status calculation

## Why

Release status calculation recursively processes component dependencies in parallel. Components that share a repository must not interfere through reused mutable working copies, and operators need to identify the component owned by each active worker when diagnosing a stalled calculation.

## What

Symptom: Status calculation can stall when multiple component subfolders from one repository are processed concurrently, while thread dumps expose only generic worker names.

```text
user requests release status
      |
      v
ExtendedStatusBuilder calculates dependency statuses in parallel
      |
      v
VCSRepositoryFactory enables reusable working copies for components sharing a repository URL
      |               <-- fault: monorepo component operations share a mutable working-copy pool
      v
parallel component VCS operations interfere through reused repository workspaces
      |
      v
status calculation stalls and its workers cannot be associated with components   (symptom)
```

Corrected behavior: Monorepo component operations use isolated non-reusable working copies while standalone repositories retain reuse, and thread dumps can locate the releaser CLI and identify each active status worker by component.

## How

Decisions:

- Model reuse as a repository-workspace policy selected from component topology, rather than adding monorepo branches to individual Git or SVN operations.
- Partition monorepo repository workspace folders by appending the normalized component subfolder to the URL-derived folder name; leave the folder name unchanged when no subfolder is configured.
- Preserve the existing one-argument workspace API as the reusable caller default and make the subfolder-aware overload the explicit implementation contract.
- Derive working-copy reuse from component topology: reuse repository-root working copies and dispose component-subfolder working copies after use.
- Apply the topology-derived behavior before constructing either VCS backend, keeping working-copy lifecycle behavior shared by Git and SVN.
- Scope a component name to the complete status-calculation call and restore the previous thread name in all outcomes so shared fork-join workers never retain stale ownership.
- Verify policy selection, reusable versus disposable lease lifecycle, scoped thread-name restoration, and concurrent monorepo status behavior at their owning unit and workflow boundaries.

Assumptions:

- A normalized non-empty component subfolder is a sufficient indication that its VCS operations require non-reusable working copies.
- A releaser launched through the shell runner remains visible to `jps -lv` with `org.scm4j.releaser.cli.CLI` as its main class.

Out of scope:

- Replacing the common fork-join pool or changing dependency traversal, status-cache waiting, and transport retry behavior.

References:

- [component repository and workspace policy selection](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- [backend-independent VCS construction](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSFactory.java)
- [workspace compatibility boundary](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/IVCSWorkspace.java)
- [repository working-copy policy](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
- [working-copy reuse and disposal lifecycle](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopy.java)
- [parallel status and thread ownership boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- [status thread-dump process discovery](../../../../../threads.sh)
- [working-copy lifecycle verification patterns](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopyTest.java)
- [status thread-name verification](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)

## Construction

### Tests

- [x] update: [workingcopy/VCSRepositoryWorkspaceTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspaceTest.java)
  - verify a repository without a subfolder keeps the existing URL-derived workspace folder name
  - verify a normalized monorepo subfolder is appended safely and separates components with the same repository URL
  - verify reusable leases persist for ordinary repositories while non-reusable monorepo leases are removed on close

- [x] update: [conf/VCSFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSFactoryTest.java)
  - cover the existing reusable default and forwarding of the monorepo subfolder that implies non-reuse with focused Git tests
  - leave SVN-specific nuances to the long-running workflow integration suite

- [x] update: [releaser/ExtendedStatusBuilderTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)
  - verify status work observes the component name and restores the previous thread name after successful and failed calculations

- [x] update: [releaser/WorkflowMonorepoForkAndBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
  - exercise one status graph containing multiple component subfolders from the same repository and verify it completes with independent component results

### VCS workspace

- [x] update: [workingcopy/IVCSWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/IVCSWorkspace.java)
  - add a repository-workspace creation path carrying an optional component subfolder
  - retain the existing single-argument caller contract as the reusable, unsuffixed default

- [x] update: [workingcopy/VCSWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSWorkspace.java)
  - implement subfolder-aware repository workspace construction and pass the optional subfolder through to folder derivation

- [x] update: [workingcopy/VCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
  - append a filesystem-safe normalized subfolder to the URL-derived repository folder only when the subfolder is non-empty
  - create disposable locked working copies for a component subfolder and preserve the existing reusable lifecycle for the repository root

### Releaser and diagnostics

- [x] update: [conf/VCSFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSFactory.java)
  - construct one component-configured repository workspace before selecting Git or SVN so both backends receive identical isolation behavior
  - preserve the existing overload as the reusable, unsuffixed compatibility path

- [x] update: [conf/VCSRepositoryFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
  - pass the normalized component subfolder into VCS construction so a non-empty subfolder selects disposable working copies

- [x] update: [releaser/ExtendedStatusBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
  - assign the current component name for the full cached status calculation
  - restore the caller or fork-join worker name in a `finally` path for every return and failure
