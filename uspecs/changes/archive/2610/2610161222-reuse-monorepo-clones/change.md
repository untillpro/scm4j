---
change_id: 2610131326-reuse-monorepo-clones
type: perf
issue_url: https://untill.atlassian.net/browse/PRIME-427
scope: [vcs, releaser, tests, docs]
---

# Change request: Reuse one clone per monorepo

## Why

Components from the same monorepo currently use subfolder-specific repository workspaces and can repeat cloning and checkout work while their statuses are evaluated. Sharing one prepared Git repository or one pinned SVN revision per physical monorepo removes that duplication, but status calculation must remain parallel: siblings may wait for initialization to finish, then they must be able to read the published snapshot concurrently.

## What

- Reuse one complete prepared Git clone for all components that resolve to the same monorepo URL. Use the ordinary URL-derived workspace folder name without a VCS-type prefix. For SVN, publish only one pinned repository revision and perform status reads remotely without creating a prepared working copy.
- Let the first sibling initialize or refresh that local repository while the other siblings wait for the same readiness result.
- After initialization, obtain tags, versions, dependencies, diffs, and component-scoped commit history for all siblings concurrently from one stable repository snapshot.
- Keep Git's prepared repository read-only during status calculation. SVN status reads do not create, update, or switch any working copy.
- Do not create another checkout for a component when no release is scheduled.
- At the start of a scheduled component release, create one isolated sparse working copy and reuse it for all release procedures, including branch creation, merges, file changes, the build, tagging, and version bumps. Git uses cone-mode sparse checkout for the configured component subfolder, so the component subtree plus Git-selected root and parent-directory files are present while sibling component subtrees remain absent. SVN checks out the selected component path and pinned revision. Git derivatives are seeded from the prepared local clone.
- Give a delayed-tag action one component-sparse session as well, because it can tag the saved revision and then bump the component's patch-version file. Keep genuinely metadata-only commands outside these mutation actions checkout-free; execute them through the backend's ref or repository-URL API and invalidate the prepared snapshot after success.
- Keep a release mutation session on the release-branch head while building. Continue selecting the last commit that affected the component as the build identity used for reporting, environment values, and tagging, but do not detach or switch the workspace to that commit; later commits that affect only sibling paths leave the sparse component content unchanged.
- Keep URL-derived repository directory names directly under the common workspace root, without VCS-type prefixes, backend-specific parent directories, or component-subfolder suffixes. Store the shared Git clone in `repository-cache/` and isolated writable checkouts in `working-copies/`.
- Preserve externally observable release behavior: status results, dependency ordering, selected component commit used as build metadata and tag target, branch and tag naming, build commands and environment, tagging, and version bumps remain unchanged.

## How

Decisions:

- Separate physical repository identity from logical component identity. One command-scoped `VCSWorkspace`, owned by `VCSRepositoryFactory`, keeps a registry keyed by normalized repository URL and owns one shared repository state; each `IVCS` instance retains its own component subfolder for path qualification, status identity, delayed tags, and release decisions.
- Give each URL-derived Git repository directory a reserved canonical prepared-repository location that cannot be discovered as a pooled writable copy; keep UUID-named writable derivatives in a separate pool location. Create these directories lazily so SVN status-only work creates neither location. Put every backend's URL-derived repository directory directly under the common workspace root, without a component-subfolder suffix or a backend-specific parent directory.
- Model shared snapshot preparation as `UNINITIALIZED -> INITIALIZING -> READY` with a single completion object per repository key. The first sibling atomically becomes the initializer and clones or refreshes the complete Git repository, or queries SVN's latest global revision without a checkout. Other siblings await that completion object. An initialization failure is delivered to every waiter, leaves no published partial state, and permits a later attempt to replace the failed state.
- Use one in-process `repoLock` for repository-cache creation, refresh, repair, snapshot invalidation, and creation of a derivative. Do not hold that lock while status methods execute. After readiness, sibling threads read concurrently without acquiring a per-operation repository lock.
- Apply a 60-second inactivity timeout to Git clone, fetch, pull, push, and remote-ref discovery. A stalled initializer completes the shared snapshot future exceptionally so waiting siblings are released instead of remaining blocked indefinitely; socket timeouts use the existing transient-fetch retry handling where that handling already applies.
- Publish one immutable backend-specific snapshot when initialization completes. Git captures every fetched branch and tag ref as a name-to-object-ID map. SVN captures only the repository's latest global revision. Every sibling resolves its status reads against the published Git object IDs or SVN revision, so a status graph cannot mix repository generations.
- Implement Git status methods as object-database reads. Each call creates and closes its own reader/walk state and reads commits, trees, blobs, refs, and diffs without changing `HEAD`, the index, or the working tree. Component paths are qualified before tree and history queries. No Git status method performs a fetch or checkout; the one initialization refresh supplies all remote data for the status phase.
- Implement SVN status methods with a separate read-only `SVNRepository` session per concurrent call; never share the mutable session currently stored on `SVNVCS`. File, history, tag, diff, branch, historical-content, and existence queries use explicit URLs and the published global revision instead of `HEAD` or `-1`. Do not create a local SVN working copy during the status phase.
- Use one implementation of each read operation per backend for both status and modify sessions. Resolve the data source once: status reads use the captured shared Git refs or pinned SVN revision; reads inside `startModify()` use the session's current local Git refs or current SVN revision. Remove the parallel legacy query implementations rather than adding another reader hierarchy. Keep reads free of checkout, reset, merge, or working-tree changes in both modes.
- Keep modify-session ownership explicit and small: allocate once, initialize once, and use one failure-cleanup path before transferring the lease to the session. Preserve the original failure and attach a cleanup failure as suppressed. Keep the readiness future, `repoLock`, and existing file leases; do not introduce new lifecycle abstractions or general-purpose locking frameworks.
- Treat the prepared repository as a read snapshot, never as a release worktree. When a release action begins, open one component mutation session. For Git, create an isolated local repository without checking out files, seed its object database and captured branch and tag refs from the shared clone, point its remote at the canonical remote URL, and enable cone-mode sparse checkout for the normalized component subfolder. Cone-selected root and parent-directory files are allowed, but sibling component subtrees are excluded. For SVN, create an isolated sparse working copy at the published repository revision and component path. A repository-root component uses a full isolated checkout. Each session has independent mutable metadata and files, so release operations cannot disturb concurrent status readers or sibling components.
- Pass the same mutation session through every procedure in one `SCMActionRelease`. The session follows the release branch, exposes its sparse component directory as the builder's working directory, and serves release-time reads so later procedures observe branches, commits, and tags created earlier in the action. The build runs from the release-branch head without a detached checkout; the path-filtered component commit remains the reported build identity and tag target. Close the session through the existing successful/corrupt working-copy lifecycle after the final procedure or on failure. Session creation may briefly take the repository maintenance lock, but running the release or build does not lock the prepared repository.
- Give `SCMActionTag` its own component mutation session and reuse it for the saved-revision tag plus the conditional patch-version read and write. Perform genuinely metadata-only commands that are not part of `SCMActionRelease` or `SCMActionTag` directly through the backend's ref or repository-URL API without allocating a sparse mutation session. All successful remote mutations invalidate the prepared snapshot.
- Complete the initial status graph from the published snapshot before executing release actions. A successful remote mutation invalidates the repository key's published snapshot. A later read outside the component's active mutation session runs the single-initializer refresh path before it can publish or use a new generation; never mix pre-write and post-write refs or revisions in one status graph.
- Keep ordinary non-monorepo pooling unchanged. Both Git and SVN monorepos use the shared initialization barrier and concurrent snapshot reads, with the backend-specific preparation and derivative behavior described above.
- Verify both synchronization phases: unit tests prove one initializer plus concurrent readers, and workflow tests prove one URL-named prepared repository, overlapping sibling status reads, no status-only derivative, and component-scoped release materialization without sibling subtrees.

Snapshot readiness simplification (follow-up):

- Limit the refactor to `VCSRepositoryWorkspace` and its focused tests. Keep the existing repository-scoped snapshot and action-scoped working-copy ownership; do not change public interfaces, Git/SVN query implementations, release procedures, `startModify()`, clone retry handling, or the `repository-cache/` and `working-copies/` layout.
- Replace `AtomicReference<CompletableFuture<PreparedRepositorySnapshot>>` with `AtomicReference<FutureTask<PreparedRepositorySnapshot>>`. Atomically publish one task before initialization so siblings retain the same generation. Call `run()` on the selected task: `FutureTask` permits its initializer to execute only once, synchronously in a caller thread, without an executor or another lifecycle abstraction.
- Run the task's initializer through the existing `withRepoLock()`, including the existing rejection of null snapshot metadata. Let `FutureTask` capture success or failure automatically; remove `initializePreparedRepository()` and its manual `complete()`, `completeExceptionally()`, and `catch (Throwable)` handling. A completed task stays cached, and ready reads return through `get()` without acquiring `repoLock`.
- Preserve interruptible waiting and original failure identity. On `InterruptedException`, restore the thread's interrupt flag without cancelling or evicting the task. On `ExecutionException`, clear only that failed generation with `compareAndSet(task, null)` and rethrow its original `Exception` or `Error`; waiters already holding the failed task still receive the same failure, while a later call can retry. An older waiter must not clear a newer task.
- Keep successful-write invalidation under `repoLock`: clear the cached task reference without cancelling tasks already held by readers. Retain the existing workspace concurrency tests, supplement their failure-path coverage where needed, and validate the refactor with the Git clone-retry tests. No adapter or releaser changes are part of this follow-up.

Assumptions:

- Components configured with the same normalized repository URL refer to the same physical repository and use compatible VCS type and credentials.
- Only one `scm4j-releaser` process accesses a configured workspace directory at a time; synchronization is required between threads in that process, not between independent releaser processes.
- Status building has a distinct read phase that completes before scheduled release actions begin; current cached-status orchestration already provides this boundary.
- Component subfolders are normalized repository-relative directories and do not escape the repository root.
- The shared Git cache is not pruned or garbage-collected while a command can retain captured object IDs.

Out of scope:

- Changing dependency traversal or status-cache parallelism.
- Coordinating simultaneous `scm4j-releaser` processes that share one workspace directory.
- Automatically migrating or deleting existing subfolder-suffixed workspace directories.
- Changing component identity, release branch and tag namespaces, or build-directory naming.

References:

- [current component workspace partitioning](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
- [working-copy allocation and locking lifecycle](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopy.java)
- [repository workspace API](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/IVCSRepositoryWorkspace.java)
- [component-to-workspace construction](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- [release build checkout boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [Git clone and sparse-checkout behavior](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [concurrent monorepo release workflow](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
- [current monorepo release semantics](../../../../../scm4j-releaser/docs/monorepo-components.md)
- [prior component-isolation decision](../../../archive/2609/2609251021-prime-253/change.md)
- [prior retained working-copy decision](../../../archive/2609/2609290817-reuse-monorepo-working-copies/change.md)
- [Git local-clone and no-checkout behavior](https://git-scm.com/docs/git-clone)
- [Git sparse-checkout behavior](https://git-scm.com/docs/git-sparse-checkout)
- [current SVN repository and working-copy behavior](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [Apache Subversion repository and working-copy model](https://subversion.apache.org/quick-start)

## Construction

### Tests

- [x] update: [workingcopy/VCSRepositoryWorkspaceTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspaceTest.java)
  - retain latch-based coverage for one initializer, shared failure delivery, retry after failure, interruptible waiting without cancellation, lock-free ready reads, and sibling invalidation
  - supplement coverage for original `Exception` and `Error` propagation and rejection of null snapshot metadata; each failed attempt must permit a later successful generation
  - keep assertions expressed through the workspace API rather than depending on `CompletableFuture` or `FutureTask` internals

- [x] update: [workingcopy/VCSRepositoryWorkspaceTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspaceTest.java)
  - retain one-initializer behavior, shared failure delivery, retry after initialization failure, interruption handling, and snapshot invalidation coverage while simplifying readiness code
  - use latches rather than sleeps to verify that sibling reads proceed concurrently after initialization

- [x] update: [git/GitVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)
  - exercise the same file, history, branch, diff, existence, and tag APIs against the published cache and after writes inside one `startModify()` session
  - verify session reads observe new branches, commits, and tags without switching the build branch, resetting files, or allocating another checkout
  - retain sparse sibling isolation and the unchanged shared-cache fingerprint checks
  - adapter consolidation fixes the three regression failures: head reads keep the release branch checked out, and single-file and batch historical reads preserve uncommitted build changes

- [x] update: [svn/SVNVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - verify status reads stay at the published revision while modify-session reads observe preceding writes through the same query implementations
  - retain independent per-call read sessions, historical-revision and nested-tag behavior, sparse checkout isolation, and failed-session cleanup coverage

- [x] update: [workingcopy/VCSLockedWorkingCopyTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopyTest.java)
  - retain pooled allocation, release, interruption, and corruption coverage for standalone and isolated mutation working copies
  - verify mutation working copies for sibling components are separate and never resolve to the prepared status repository
  - verify a corrupted derivative is removed without deleting or marking the prepared repository corrupt

- [x] update: [conf/VCSFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSFactoryTest.java)
  - verify all components with one URL receive wrappers over the same shared repository state while retaining distinct component subfolders
  - verify adapter selection does not add a VCS-type prefix or change URL-based repository identity

- [x] update: [conf/VCSRepositoryFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryFactoryTest.java)
  - verify the factory reuses one `VCSWorkspace` and one URL-scoped repository state for sibling component repositories
  - verify different URLs do not share initialization or local clone folders

- [x] update: [abstracttest/VCSAbstractTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
  - preserve head and historical-revision sparse checkout behavior for Git and SVN
  - verify the selected component directory is materialized and sibling component content remains absent
  - allow backend-specific root-file behavior: Git cone mode includes root and parent-directory files, while SVN retains its current sparse-depth behavior
  - preserve all externally observable read, branch, merge, commit, and tag behavior

- [x] update: [git/GitVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)
  - verify concurrent calls for different component paths wait for one delayed clone or fetch and overlap after the prepared snapshot is published
  - verify read APIs use captured refs and repository objects without checkout, fetch, index writes, or working-tree changes
  - verify component checkout is locally derived from the prepared clone, receives captured branch and tag refs, uses the canonical remote for pushes, and performs no second remote clone or fetch
  - verify two component mutation sessions have independent refs, indexes, and sparse worktrees and leave the shared clone unchanged
  - verify one session is reused across branch, content, build-checkout, tag, and version-bump operations and its reads observe earlier writes
  - verify a successful push invalidates the old snapshot and the next read performs one refresh before publishing the new generation
  - preserve native sparse-checkout failure, interruption, and option-like path handling
  - verify cone mode includes the selected component subtree and Git-selected root or parent files but excludes sibling component subtrees

- [x] update: [svn/SVNVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - verify concurrent siblings wait for one pinned-revision query and their reads overlap after the revision is published
  - verify each concurrent history, tag, diff, branch, and historical-content call owns a separate read-only `SVNRepository` session
  - verify all implicit HEAD reads use the same published revision even when the remote repository advances during status calculation
  - verify current-revision component files are read remotely at the pinned revision without a checkout or update
  - verify status-only reads create neither prepared nor working-copy directories
  - verify one release mutation session contains only the selected component path and is reused for content, merge, build, and tag operations
  - preserve SVN authentication, transport retry, and sparse-path validation behavior

- [x] update: [releaser/WorkflowMonorepoForkAndBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowMonorepoForkAndBuildTest.java)
  - gate prepared-repository completion and read operations with test latches to prove siblings wait only for the first initialization and their status reads overlap afterward
  - verify concurrently evaluated siblings use one retained prepared repository while producing independent statuses, versions, tags, and path-filtered history
  - verify a status-only sibling creates no isolated checkout
  - verify a scheduled component release creates exactly one mutation workspace, excludes sibling component subtrees, reuses the workspace through all procedures, and preserves existing branch, tag, build, and version-bump results
  - verify a later sibling-only commit may be the repository head while the builder sees unchanged component files from that head and the earlier component commit remains the reported build identity and tag target

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - verify the build uses the release mutation session's component directory instead of allocating a separate checkout
  - verify the session remains on the release-branch head while the path-filtered component commit supplies build environment values and the tag target
  - retain coverage for component commit selection, delayed tagging, ordinary tagging, and patch bumps

- [x] new: `scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/SCMActionReleaseTest.java`
  - verify one mutation session is opened before the first release procedure, passed to every procedure in order, and closed after success
  - verify failures close and mark an unusable session corrupt without executing later procedures
  - verify an action with no executable release procedures does not open a session

- [x] new: `scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/SCMActionTagTest.java`
  - verify delayed tagging opens one component mutation session, reuses it for tag creation and an optional patch bump, and closes it deterministically
  - verify a failed tag or patch bump marks an unusable session corrupt and preserves the delayed-tag record for retry

### VCS workspace

- [x] update: [workingcopy/VCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
  - implement the FutureTask-based readiness design above using the existing atomic reference and `repoLock`; remove the manual initialization-completion helper
  - preserve null-result rejection, original failure propagation, interrupt flags, generation-specific failure cleanup, successful-write invalidation, and lock-free ready reads
  - keep public APIs, adapter/session behavior, working-copy leases, folder layout, and transport retries unchanged
  - validate with the working-copy package tests and `GitCloneRetryTest` using a fresh run against the FutureTask implementation
  - validation: the FutureTask implementation passes all 26 tests in the working-copy package and `GitCloneRetryTest`, including original exception/error propagation, null-result rejection, shared failure recovery, and waiter interruption

- [x] update: [workingcopy/VCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
  - simplify the readiness code around one completion object and `repoLock`; remove redundant control flow and exception forwarding only where the concurrency guarantees remain unchanged
  - retain URL-based sharing, lazy directory creation, snapshot invalidation, and the `repository-cache/` and `working-copies/` layout

- [x] update: [workingcopy/IVCSWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/IVCSWorkspace.java)
  - preserve the existing overloads while defining component-subfolder input as logical scope rather than physical clone identity
  - expose URL-scoped repository-state reuse within one workspace and document initialization-barrier semantics

- [x] update: [workingcopy/VCSWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSWorkspace.java)
  - cache shared repository state in a thread-safe map keyed by normalized URL
  - return component-scoped repository wrappers over that state instead of constructing independent physical workspaces

- [x] update: [workingcopy/IVCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/IVCSRepositoryWorkspace.java)
  - distinguish acquisition of the prepared read snapshot from allocation of an isolated writable working copy
  - expose component scope and snapshot invalidation without leaking Git or SVN implementation types through the common API

- [x] update: [workingcopy/VCSRepositoryWorkspace.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSRepositoryWorkspace.java)
  - derive every repository folder name from URL alone
  - keep the fixed canonical clone path separate from UUID-named writable working copies and their lock files
  - coordinate one asynchronous initialization result per repository key and publish only a complete repository snapshot
  - retain component subfolder only as logical scope on the wrapper
  - use an in-process maintenance lock for initialization, refresh, repair, and derivative creation, but not for snapshot reads
  - publish backend-owned immutable snapshot metadata for each prepared generation and invalidate the generation after successful writes

- [x] update: [workingcopy/VCSLockedWorkingCopy.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/workingcopy/VCSLockedWorkingCopy.java)
  - keep exclusive leases for standalone and isolated writable copies only
  - preserve pooled allocation and deletion of corrupted derivatives
  - do not use working-copy leases to guard read access to a prepared monorepo snapshot

### VCS checkout adapters

- [x] update: [git/GitVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
  - consolidate duplicate snapshot and retained-working-copy read implementations into the existing object-database query code
  - select captured cache refs for status and current session refs for modify-time reads; preserve branch, revision, error, and component-history semantics
  - collapse repeated allocation and initialization exception handlers into one cleanup path, with deterministic lease release and Windows pack-handle cleanup
  - validation: all five focused Git session tests pass, including the three regressions for branch preservation and uncommitted changes; modify sessions retain the captured default branch

- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - consolidate duplicate snapshot and legacy read implementations into revision-parameterized remote queries using one independent repository session per call
  - use the published revision for status and current revision for modify-session reads; preserve explicit historical revisions and existing tag and diff behavior
  - remove private helpers made unused by consolidation, simplify failed initialization cleanup, and keep session-owned mutation clients separate from concurrent readers
  - validation: the final focused run passes 44 tests across workspace, Git/SVN session, release/tag action, and both-backend shared-status workflow checks; construction self-review stages A and B are complete
  - broader validation: 167 VCS tests report 21 failures, and 11 release/workflow checks report two failures; all 23 failures also reproduce with the pre-refactor adapters compiled into an isolated diagnostic classpath. Existing failures cover stale snapshot expectations, legacy mocks, the SVN added/deleted-file diff, and the Windows native-Git path-length limit; do not claim a clean full suite

- [x] update: [api/IVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
  - document which methods are read-only and which require an isolated writable checkout
  - add a release-scoped mutation-session entry point while preserving checkout methods used outside release orchestration

- [x] new: `scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCSMutationSession.java`
  - expose the VCS operations required by release procedures plus the materialized component working directory
  - own branch/revision transitions, snapshot invalidation, corruption marking, and deterministic cleanup through `AutoCloseable`

- [x] update: [git/GitVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
  - route all status reads through the prepared snapshot and replace checkout-based reads with ref, commit, tree, blob, diff, and path-filtered history queries
  - allocate a separate reader or walk per call so sibling status methods can execute concurrently
  - create a release mutation session by deriving an isolated writable repository and explicit branch/tag refs from the local clone without a second remote clone or fetch, then configure the canonical remote and component sparse checkout
  - route branch, merge, file, and tag operations through the session; keep the build on the release branch, keep subsequent release reads in the session, and invalidate the prepared snapshot after successful pushes
  - keep direct checkout-free metadata operations available outside action-scoped mutation sessions
  - retain existing error translation, credentials, interruption handling, and process cleanup

- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - publish the exact repository revision without creating or updating a prepared working copy
  - replace the shared `SVNRepository` field on concurrent read paths with independently created, authenticated, and closed read sessions pinned to the published revision
  - use explicit-revision remote queries for current and historical files, history, and metadata
  - create one component-sparse mutation session per release and route content, merge, build, and release metadata operations through it
  - keep direct URL-based metadata operations available outside action-scoped mutation sessions
  - invalidate the prepared snapshot after successful repository mutations and preserve authentication, retry, exception, and cleanup behavior

### Releaser and documentation

- [x] update: [docs/monorepo-components.md](../../../../../scm4j-releaser/docs/monorepo-components.md)
  - describe one query implementation per backend with captured status state and current modify-session state
  - keep the accepted names `repository-cache/`, `repoLock`, and `startModify()` and the existing release-session contract
  - validate the simplification against workspace, Git/SVN session, release/tag action, and monorepo workflow tests; record any failures also present before the refactor rather than claiming an unverified clean suite

- [x] update: [conf/VCSRepositoryFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
  - own one reusable `VCSWorkspace` for the factory lifetime instead of constructing one per component
  - retain component subfolders for status identity and path qualification while routing siblings to the URL-scoped state

- [x] update: [scmactions/SCMActionRelease.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionRelease.java)
  - open one component mutation session for an executable release action, pass it to every procedure, and close it deterministically
  - avoid creating a session when the action contains no executable procedures

- [x] update: [procs/ISCMProc.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/ISCMProc.java)
  - execute release procedures against the action-owned mutation session instead of independently acquiring working copies

- [x] update: [procs/SCMProcForkBranch.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcForkBranch.java)
  - use the release mutation session for branch creation and development-version changes

- [x] update: [procs/SCMProcLockMDeps.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcLockMDeps.java)
  - use the release mutation session for dependency-file changes

- [x] update: [procs/SCMProcActualizePatches.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcActualizePatches.java)
  - use the release mutation session for patch dependency changes

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - select the existing path-filtered component commit for build metadata and tagging, but run the builder from the action-owned session at the release-branch head
  - retain build environment, delayed tagging, ordinary tagging, and patch-bump behavior through the same session

- [x] update: [scmactions/SCMActionTag.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
  - open one component mutation session and reuse it for saved-revision tagging, the current patch-version read, and any required patch bump
  - close the session before removing the delayed-tag record and retain the record when the action fails

- [x] update: [docs/monorepo-components.md](../../../../../scm4j-releaser/docs/monorepo-components.md)
  - document the single URL-named prepared repository, one-time initialization wait, concurrent sibling status reads, and Git/SVN snapshot differences
  - distinguish the immutable status snapshot from the single component-scoped mutation session created for a scheduled release
  - document that delayed-tag actions use one sparse session because they can also bump the patch version, while genuinely metadata-only commands remain checkout-free
  - document that Git cone mode may include repository-root and parent-directory files even though sibling component subtrees remain absent
  - remove the obsolete statement that each monorepo build checkout materializes the full repository
