# Decisions: Reuse one clone per monorepo

## Inconsistency: the request covers each monorepo but the plan limited shared snapshots to Git

Decision: Support both VCS backends with backend-specific snapshots. Git uses a complete clone and immutable ref/object-ID map. SVN uses a complete working copy pinned to one repository revision, reads current content from that snapshot where applicable, and uses independent read-only repository sessions pinned to the same revision for history and metadata. Both use isolated component-sparse working copies for release operations.

- Pros: honors the VCS-neutral change scope; preserves one initialization barrier and concurrent sibling reads; uses each backend's native consistency model.
- Cons: SVN history and metadata reads still contact the central repository because an SVN working copy does not contain complete history; implementation and test scope increase.
- Confidence: user-provided

Alternatives:

1. Limit shared snapshots to Git
   - Pros: smallest implementation; Git provides true local history and locally seeded sparse derivatives.
   - Cons: SVN monorepos retain repeated initialization and do not receive the requested optimization.
   - Confidence: high
2. Create a complete local SVN mirror
   - Pros: supplies SVN history locally and most closely matches Git clone behavior.
   - Cons: requires repository replication, more disk and operational complexity, and potentially additional server permissions.
   - Confidence: low

## Ambiguity: whether metadata-only branch and tag writes require sparse checkout

Decision: Create one component-sparse mutation session for each scheduled release and reuse it for every release procedure, including file changes, branch and tag operations, build execution, and version bumps. Give a delayed-tag action one session as well because it can also bump the patch-version file. Only genuinely metadata-only commands outside these actions use the backend's direct ref or repository-URL operation without creating a checkout.

- Pros: gives each mutating action one explicit workspace lifecycle; avoids repeated sparse checkouts between procedures; keeps related reads and writes consistent; preserves efficient direct metadata operations where no file write is possible.
- Cons: release-time branch and tag operations share a workspace even when they do not technically need files; actions, procedures, and VCS APIs must accept a common session.
- Confidence: user-provided

Alternatives:

1. Create sparse checkouts only for operations that need repository files
   - Pros: avoids checkout work for all branch and tag operations.
   - Cons: a release has different isolation mechanisms depending on operation type.
   - Confidence: high
2. Create a sparse checkout before every kind of write
   - Pros: applies one literal rule to every mutation.
   - Cons: repeats unnecessary checkout work and gives no shared lifecycle to an entire release.
   - Confidence: medium

## Inconsistency: protecting a shared SVN snapshot from another releaser process

Decision: Assume only one `scm4j-releaser` process accesses a configured workspace directory at a time. Coordinate initialization, maintenance, and concurrent status reads only between threads in that process; do not add cross-process snapshot leases.

- Pros: keeps sibling status reads fully concurrent; removes long-lived filesystem-lock lifecycle and cross-process revision coordination; matches the stated deployment model.
- Cons: simultaneous releaser processes pointed at the same workspace are unsupported and may race while refreshing or deriving from the prepared repository.
- Confidence: user-provided

Alternatives:

1. Hold one shared read lease for the complete SVN status phase
   - Pros: keeps the prepared SVN working copy stable across simultaneous processes while allowing concurrent readers.
   - Cons: requires command-lifecycle lock management and makes another process wait before refreshing.
   - Confidence: high
2. Read all SVN status data remotely at the pinned revision
   - Pros: avoids a long-lived working-copy lease.
   - Cons: loses much of the local prepared-working-copy benefit and increases network requests.
   - Confidence: medium
3. Copy the prepared SVN snapshot for each command
   - Pros: gives every command an immutable local snapshot.
   - Cons: duplicates the monorepo and adds substantial disk and copy overhead.
   - Confidence: low

## Ambiguity: building an older component commit in the single release workspace

Decision: Keep the sparse mutation session on the release-branch head and run the builder there. Continue selecting the last commit that affected the component as the reported build identity and tag target. Later branch-head commits affect only sibling paths, so the materialized component folder remains equivalent to that selected component commit.

- Pros: keeps one workspace for the whole release; avoids destructive cleanup and branch switching after the build; preserves component-scoped build identity and tagging.
- Cons: the repository `HEAD` present in the workspace can differ from the commit hash reported to the builder; root-level files would reflect branch head if a backend's sparse checkout materializes them.
- Confidence: user-provided

Alternatives:

1. Temporarily switch the same sparse workspace to the selected component commit
   - Pros: the checked-out repository revision exactly matches the reported build identity.
   - Cons: generated files must be removed and tracked files restored before switching back for later release writes.
   - Confidence: high
2. Keep a separate build checkout
   - Pros: isolates build artifacts and exactly materializes the selected component commit.
   - Cons: creates a second checkout and violates the selected one-workspace-per-release approach.
   - Confidence: medium

## Inconsistency: Git cone mode materializes files outside the component subtree

Decision: Keep Git cone-mode sparse checkout. A component mutation session may contain repository-root files and files in leading parent directories selected by Git, but it must exclude sibling component subtrees. SVN retains its backend-specific sparse-depth behavior.

- Pros: uses Git's standard optimized sparse mode; preserves existing sparse-checkout behavior; keeps root-level build configuration available.
- Cons: “component-only” does not mean a literally exclusive subtree; root files can reflect the release-branch head rather than the reported component commit.
- Confidence: user-provided

Alternatives:

1. Use strict component-only non-cone patterns
   - Pros: materializes only the selected component subtree and prevents accidental use of root files.
   - Cons: uses Git's less optimized and more error-prone non-cone pattern mode.
   - Confidence: high
2. Configure additional shared paths per component
   - Pros: permits strict defaults with explicit shared build inputs.
   - Cons: adds configuration, validation, and migration scope.
   - Confidence: medium
