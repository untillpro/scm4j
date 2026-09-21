# Monorepo component release model

This document shows the current scm4j behavior. A monorepo is one physical VCS repository exposed as several logical components. Each component is identified by:

```text
(repository URL, component subfolder)
```

## One repository, several components

Example configuration:

```yaml
eu\.untill:(postgres|sqlite):
  url: https://example.test/drivers.git
  subfolder: components/$1
  releaseCommand: ./gradlew publish
```

```text
drivers.git
├── components/
│   ├── postgres/
│   │   ├── version       ← postgres version
│   │   ├── mdeps         ← postgres managed dependencies
│   │   └── ...           ← postgres sources
│   └── sqlite/
│       ├── version       ← sqlite version
│       ├── mdeps         ← sqlite managed dependencies
│       └── ...           ← sqlite sources
└── ...                   ← not part of either component
```

There is no fallback from a component subfolder to repository-root `version` or `mdeps` files. For example, postgres always reads `components/postgres/version` and `components/postgres/mdeps`.

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

`developBranch` is configured once per matching component rule, and normally resolves to the repository default branch, such as `main`. It is not automatically component-namespaced.

The release branches above are full repository branches, not subfolder-only branches. Separate component names keep their branch and tag names from colliding.

## Example: interleaved monorepo Git log

One physical repository contains interleaved commits for every component:

```text
$ git log --all --graph --decorate --oneline

* c9 (HEAD -> main) [postgres] regenerate bindings #scm-ignore
* c8                [postgres] add JSONB support
* c7                [sqlite] add WAL support
* c6                [postgres] #scm-ver 2.60.0-SNAPSHOT
| * p4 (postgres/release/2.59) [postgres] #scm-ver 2.59.1
| * p3                            [sqlite] repair CI fixture
| * p2 (tag: postgres/2.59.0)     [postgres] #scm-mdeps
| * p1                            [postgres] #scm-ver 2.59.0
|/
* c5                [postgres] add binary COPY support
* c4                [sqlite] improve busy timeout
* c3                [root] update CI image
```

scm4j path-filters that graph and requests only one commit for minor detection:

```text
$ git log -1 main -- components/postgres
c9 [postgres] regenerate bindings #scm-ignore  → unmodified; c8 is not examined

$ git log -1 main -- components/sqlite
c7 [sqlite] add WAL support                     → modified
```

On the postgres release branch, component filtering also removes sibling commit `p3`:

```text
$ git log --oneline --decorate postgres/release/2.59 -- components/postgres
p4 [postgres] #scm-ver 2.59.1
p2 (tag: postgres/2.59.0) [postgres] #scm-mdeps
p1 [postgres] #scm-ver 2.59.0
c5 [postgres] add binary COPY support
```

At build time the physical head was sqlite commit `p3`, but scm4j selected postgres commit `p2`, checked out the full repository there, and tagged it `postgres/2.59.0`.

## Minor-release decision: one component commit

For a component in a subfolder, `DevelopBranch.isModified()` requests:

```text
develop branch + component subfolder + descending history + limit 1
```

Sibling and unrelated root commits are filtered out by the VCS history query. scm4j then examines exactly the newest commit that touched the component subfolder.

## Commit markers

`#scm4j-*` does not contain the component name

### Which release-branch commit is built?

A component release branch can contain commits outside the component folder. scm4j starts at the physical branch head, finds the newest commit affecting the component subfolder, and builds that revision.

The checkout is still the full repository because Git branches and revisions describe the repository as a whole. Only the build working directory is narrowed to the component subfolder.

## Working-copy reuse and checkout count

A single monorepo working copy can be reused while **determining status**, but not as the simultaneous build checkout for components released from different component branches.

## Minor detection versus patch detection

- Minor detection:
  - Reads the shared develop branch.
  - Filters history to the component subfolder.
  - Examines only the newest matching commit.
  - Treats `#scm-ignore` and `#scm-ver` as unmodified; any other message is valuable.

- Patch detection:
  - Reads the component-namespaced release branch.
  - Filters history to the component subfolder.
  - Scans backward in pages.
  - Skips `#scm-ignore` and `#scm-ver` commits and uses the component tag or delayed-tag revision as the release boundary.

| Question | Minor release | Patch release |
|---|---|---|
| Source branch | Shared develop branch | Component-namespaced release branch |
| History inspected | Only newest component commit | Component history scanned backward in pages |
| Boundary | Implicit result of the newest commit marker/message | Component-specific tag on a revision (or delayed-tag revision) |
| Sibling/root commits | Excluded by component path | Excluded by component path |

## Source map

- Component mapping and workspace creation: [`VCSRepositoryFactory`](../src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- Component identity and path joining: [`VCSComponentLocation`](../src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- Branch/tag namespace formulas: [`Utils`](../src/main/java/org/scm4j/releaser/Utils.java)
- One-commit minor check: [`DevelopBranch`](../src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
- Full minor status and dependency propagation: [`ExtendedStatusBuilder`](../src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- Fork behavior: [`SCMProcForkBranch`](../src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcForkBranch.java)
- Build revision, checkout, tag, and patch bump: [`SCMProcBuild`](../src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- Marker definitions: [`data-structure.md`](data-structure.md#comment-tags)
