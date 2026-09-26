---
change_id: 2609260815-implement-sparse-checkout
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-260
scope: [vcs]
breaking: true
---

# Change request: IVCS sparse checkout

Refs:

- [PRIME-260: migrate-drivers: scm4j: implement sparse checkout](./issue-PRIME-260.md)

## Why

The monorepo migration needs version-control operations to work efficiently when callers require only a subset of a repository. Sparse checkout support will avoid materializing unrelated content while keeping the IVCS checkout workflow simple.

## What

IVCS gains a dedicated sparse checkout operation:

- Callers can check out one repository directory without materializing the entire working tree.
- Git and SVN sparse checkout preserve branch and revision selection.
- Existing full checkout and every other Git operation remain JGit-based and unchanged.
- Backend failures are exposed to the caller without pre-validating the requested directory.

## How

Decisions:

- Add `sparseCheckout` as a separate IVCS operation taking the same branch, target, and revision inputs as full checkout plus one repository-relative directory; do not overload or alter the existing `checkout` behavior.
- Declare `sparseCheckout` as a required IVCS operation without a default implementation; every IVCS implementation must define its backend behavior.
- Keep repository access, credentials, proxy behavior, default-branch resolution, and all existing Git operations in the JGit-backed adapter; use native Git only inside `GitVCS.sparseCheckout` to configure and materialize the sparse working tree.
- Keep native process execution local to the existing Git adapter and invoke argument tokens directly without a shell; do not introduce a general native Git adapter or reusable Git CLI subsystem.
- Implement SVN sparse checkout through the existing SVNKit adapter by creating an empty-depth working-copy root and expanding the requested directory recursively with sticky depth and parent creation; do not invoke a native SVN command.
- Pass the requested directory directly to each backend without normalization, existence checks, directory-kind checks, pattern checks, or other validation; propagate backend failures for the caller to handle.
- Attempt the native sparse commands only when `sparseCheckout` is called; do not probe for a Git executable, inspect its version, or add installation and compatibility checks.
- Verify the shared sparse-checkout contract for Git and SVN, plus regression protection for the unchanged JGit-backed operations.

Assumptions:

- The caller environment provides a native `git` command capable of the sparse-checkout commands used by the adapter.
- Callers provide an appropriate repository-relative directory and handle any failure returned by the selected backend.

Out of scope:

- Passing component subfolders from the releaser into the sparse checkout operation; that adoption belongs to PRIME-255.
- Creating or adopting a general native Git adapter, or moving any existing Git operation away from JGit.
- Validating, normalizing, or otherwise interpreting the requested sparse-checkout directory.
- Supporting multiple directories, arbitrary include/exclude patterns, sparse indexes, or submodule initialization.
- Invoking or provisioning a native SVN command; SVN sparse checkout remains SVNKit-based.
- Detecting, installing, or enforcing a version of the native Git command.

References (internal):

- [common checkout contract](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [JGit-backed Git adapter boundary](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [SVNKit-backed SVN adapter boundary](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [cross-adapter checkout verification](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
- [Git adapter verification](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)

References (external):

- [releaser sparse-checkout adoption (PRIME-255)](https://untill.atlassian.net/browse/PRIME-255)
- [native Git sparse-checkout behavior](https://git-scm.com/docs/git-sparse-checkout)
- [SVNKit working-copy depth operations](https://svnkit.com/javadoc/org/tmatesoft/svn/core/wc/SVNUpdateClient.html)

## Construction

### Tests

- [x] update: [abstracttest/VCSAbstractTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
  - verify sparse checkout at branch head and an explicit revision materializes the requested directory while leaving an unrelated sibling directory absent

- [x] update: [git/GitVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)
  - verify process launch and non-zero-exit failures are propagated to the caller
  - retain the existing shared suite and Git-specific coverage as regression protection for all JGit-backed operations

- [x] update: [svn/SVNVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - verify SVNKit failures are propagated without adapter-side directory validation

### VCS API and adapters

- [x] update: [api/IVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
  - add `sparseCheckout(String branchName, String targetPath, String revision, String repositoryRelativeDirectory)` as a required operation without a default implementation
  - document the single-directory contract, branch and revision behavior, caller-owned validation and failure handling, and the source/binary compatibility impact for external IVCS implementations

- [x] update: [git/GitVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
  - override `sparseCheckout` in the existing JGit-backed adapter without changing `checkout` or any other operation
  - use JGit for repository access, credentials, proxy behavior, and default-branch or revision resolution without performing a full working-tree checkout
  - clone without checkout on first sparse use and refresh remote-tracking branch refs through the existing JGit transport and retry behavior
  - invoke only the local native sparse-checkout setup and materialization commands through direct process argument tokens, keeping the process seam inside `GitVCS`
  - configure cone-mode sparse checkout, then materialize a branch head on its named local branch or an explicit revision in detached-head mode
  - pass `repositoryRelativeDirectory` unchanged to native Git and propagate process launch or non-zero-exit failures without directory validation
  - do not probe the executable, check its version, create a native Git adapter, or add a general command gateway

- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - implement `sparseCheckout` through SVNKit without invoking a native SVN process
  - check out the selected branch or trunk at empty depth, or switch an existing working copy to that URL and depth, then update the requested directory to infinite sticky depth at the same revision while creating its parent working-copy nodes
  - pass `repositoryRelativeDirectory` through without pre-validation and translate SVNKit failures through the existing VCS exception boundary

### Documentation

- [x] update: [scm4j-vcs/README.md](../../../../../scm4j-vcs/README.md)
  - document the required sparse checkout operation, its single-directory argument, caller-owned validation and failure handling, and unchanged full-checkout behavior
  - describe Git and SVN support and add a backend-neutral IVCS usage example

- [x] update: [docs/git.md](../../../../../scm4j-vcs/docs/git.md)
  - explain that only `sparseCheckout` invokes native Git while all existing Git operations remain JGit-backed
  - state that the method performs no executable/version probe or directory validation and returns command failures to its caller

- [x] update: [docs/svn.md](../../../../../scm4j-vcs/docs/svn.md)
  - explain the SVNKit empty-depth checkout and recursive sticky-depth expansion used for sparse checkout
  - state that no native SVN executable or adapter-side directory validation is involved

## Quick start

Given an initialized `IVCS` instance, use the dedicated sparse checkout operation:

```java
vcs.sparseCheckout(
        "release/1.0",
        checkoutDirectory.getPath(),
        revision,
        "components/driver");
```

The selected adapter passes the directory to its backend without pre-validation. Callers are responsible for supplying a suitable value and handling failures. Continue using `checkout` for a full checkout.
