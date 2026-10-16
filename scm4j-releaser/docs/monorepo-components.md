# Monorepo component release model

A monorepo is one physical VCS repository exposed as several logical components. Physical
repository state is identified by normalized repository URL. A logical component also
retains its configured repository-relative subfolder:

```text
physical repository = repository URL
logical component   = (repository URL, component subfolder)
```

Example configuration:

```yaml
eu\.untill:(postgres|sqlite):
  url: https://example.test/drivers.git
  subfolder: components/$1
  releaseCommand: ./gradlew publish
```

```text
drivers.git
|-- components/
|   |-- postgres/
|   |   |-- version
|   |   |-- mdeps
|   |   `-- ...
|   `-- sqlite/
|       |-- version
|       |-- mdeps
|       `-- ...
`-- ...
```

There is no fallback from a component subfolder to repository-root `version` or `mdeps` files.
For example, postgres always reads `components/postgres/version` and
`components/postgres/mdeps`.

## Shared status snapshot

One command-scoped workspace owns one shared repository state for every normalized repository URL. Git
and SVN repository directories are direct children of the same workspace root; there are no
backend-specific `git` or `svn` parent directories or name prefixes. Each directory uses the
ordinary URL-derived repository name (for example, `example.test_project.git`). The component
subfolder is not appended to that name.

The first sibling that needs status initializes the backend snapshot. Other siblings for the same
repository wait for that initialization result. After it is published, all sibling status
calculations read the same generation concurrently; they do not lock one another for ordinary
reads. Git network operations have a 60-second inactivity timeout, so a stalled clone, fetch, or
remote-ref lookup fails instead of leaving every waiting sibling blocked indefinitely. If no
component release is scheduled, no component checkout is created.

The snapshot differs by backend:

- Git keeps one complete non-bare, no-checkout clone in `repository-cache/` and captures fetched
  branch and tag refs as immutable object IDs. Status queries use per-call readers and walks over
  commits, trees, blobs, and diffs;
  they do not fetch, checkout, reset, or modify the clone's refs, index, or working tree.
- SVN publishes only the repository's exact global revision; it does not create a prepared working
  copy. File content, history, branches, tags, diffs, and existence checks use independent
  read-only `SVNRepository` sessions pinned to that revision. Status-only SVN operations therefore
  create neither `repository-cache` nor `working-copies` directories.

Each backend uses one query implementation for both status and modify sessions. Status reads use
the captured Git refs or pinned SVN revision; reads inside a modify session use its current local
Git refs or SVN's current repository revision, so they observe earlier committed writes. Explicit
historical revisions remain supported. Neither mode checks out files, resets the index, or changes
the working tree merely to answer a query.

A successful repository mutation invalidates the prepared generation. A later status read outside
an active mutation session performs one refresh and publishes a new generation.

The workspace's in-process `repoLock` serializes snapshot initialization, refresh, invalidation, and
working-copy allocation and initialization. It is not held during ordinary snapshot reads or while
a release or build runs. Independent writable copies retain their own exclusive file leases;
multiple releaser processes sharing one workspace are not supported.

## Component mutation sessions

The shared clone in `repository-cache/` is never a release worktree. When a scheduled component
release starts, `startModify()` creates one isolated sparse mutation session backed by a leased
UUID-named directory in `working-copies/`. scm4j reuses it for branch creation, dependency updates,
the build, tagging, and version bumps. Procedures therefore observe writes performed by earlier
procedures without allocating another checkout.

- Git derives the session locally from the cached clone, seeds its captured refs, and points its
  remote at the canonical repository URL. Cone-mode sparse checkout includes the selected component
  subtree and may also include repository-root files and files in leading parent directories chosen
  by Git. Sibling component subtrees remain absent.
- SVN creates an isolated sparse working copy for the selected component at the published revision.
  A repository-root component receives a full isolated working copy.

The builder runs in the mutation session's component directory while the session remains on the
release-branch head. scm4j still selects the newest commit that affected the component as the build
identity used for reporting, build environment variables, and the tag target. A later commit that
only changes a sibling can therefore be the physical branch head without changing the component
content being built.

A delayed-tag action also owns one sparse mutation session because it can tag the saved revision and
then bump the release branch's patch-version file. Metadata-only commands outside release and
delayed-tag actions can use direct ref or repository-URL operations without creating a sparse
session.

Closing a session releases its lease and retains a healthy working-copy directory for reuse. A
corrupted copy is removed without deleting the shared cache.

## Branch and tag names

For a component with a non-empty `subfolder`, scm4j uses the Maven artifact ID as a namespace:

```text
component namespace = <artifactId>/
release branch      = <artifactId>/<releaseBranchPrefix><major>.<minor>
release tag         = <artifactId>/<major>.<minor>.<patch>
```

With the default `releaseBranchPrefix: release/`:

| Logical component | Develop branch | Release branch | Release tag |
|---|---|---|---|
| `eu.untill:postgres`, version `2.59.0` | `main` | `postgres/release/2.59` | `postgres/2.59.0` |
| `eu.untill:sqlite`, version `3.4.1` | `main` | `sqlite/release/3.4` | `sqlite/3.4.1` |
| Component without `subfolder`, version `1.7.0` | `main` | `release/1.7` | `1.7.0` |

Release branches are full repository branches. Component namespaces keep sibling branch and tag
names from colliding. `developBranch` normally resolves to the shared repository default branch and
is not automatically component-namespaced.

## Component-scoped status and build identity

Minor detection asks for the newest develop-branch commit that affects the component subfolder.
Sibling and unrelated root commits are excluded. `#scm-ignore` and `#scm-ver` mean that matching
component commit is unmodified; any other message is valuable.

Patch detection scans component-filtered release-branch history backward in pages. It skips
`#scm-ignore` and `#scm-ver` commits and uses the component tag, or a delayed-tag revision, as the
release boundary.

| Question | Minor release | Patch release |
|---|---|---|
| Source branch | Shared develop branch | Component-namespaced release branch |
| History inspected | Newest component commit | Component history scanned backward |
| Boundary | Newest component commit marker/message | Component tag or delayed-tag revision |
| Sibling/root commits | Excluded by component path | Excluded by component path |

## Source map

- Simplification validation and known pre-refactor test failures: [change request](../../uspecs/changes/2610131326-reuse-monorepo-clones/change.md#vcs-checkout-adapters)
- Workspace ownership and component mapping: [`VCSRepositoryFactory`](../src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- Component identity and path joining: [`VCSComponentLocation`](../src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- Shared snapshot and derivative lifecycle: `scm4j-vcs` working-copy APIs
- Release mutation-session lifecycle: [`SCMActionRelease`](../src/main/java/org/scm4j/releaser/scmactions/SCMActionRelease.java)
- Delayed-tag mutation-session lifecycle: [`SCMActionTag`](../src/main/java/org/scm4j/releaser/scmactions/SCMActionTag.java)
- Build identity and working directory: [`SCMProcBuild`](../src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- Branch and tag namespace formulas: [`Utils`](../src/main/java/org/scm4j/releaser/Utils.java)
- Marker definitions: [`data-structure.md`](data-structure.md#comment-tags)
