---
change_id: 2609171302-support-git-default-branch
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-189
scope: [git-vcs, releaser]
breaking: true
---

# Change request: Support repository-defined Git default branches

## Why

Git repositories may use `main` or another branch as their default instead of `master`. Releaser workflows must follow repository configuration and remote Git metadata so release automation is not coupled to a legacy branch name.

## What

Symptom: A releaser workflow can fail for a Git repository whose default branch is not `master`, including a `main`-only repository with an explicitly configured development branch.

```text
releaser operates on a Git repository without a master branch
      |
      v
workflow reads development state or builds a release branch
      |
      v
GitVCS resolves null branches and internal checkouts as master   <-- fault: hard-coded default branch
      |
      v
Git checkout or reference lookup targets a missing master branch
      |
      v
release workflow fails   (symptom)
```

Corrected behavior: Releaser honors an explicitly configured development branch and otherwise resolves the Git remote's default branch, allowing complete workflows on `main`, `master`, or any other valid default branch name.

## How

Decisions:

- Preserve the VCS API's `null` branch value as the portable representation of a repository's primary branch, but define it as the Git remote's default branch for Git and continue mapping it to `trunk` for SVN.
- Treat an explicitly configured development branch as authoritative. Default-branch discovery applies only when callers use the portable `null` branch value; all explicit branch names continue to pass through unchanged.
- Determine the Git default branch from the remote's symbolic `HEAD`, using the same repository URL, credentials, and proxy configuration as other Git transport operations. Do not infer it from the current working-copy checkout or guess between conventional names such as `main` and `master`.
- Resolve the Git default branch lazily after transport configuration is available and retain it for the lifetime of the Git VCS instance, so every operation in one releaser run uses a consistent primary branch while reused working copies remain free to switch branches.
- If the remote does not advertise a usable symbolic `HEAD`, fail with a clear VCS error instead of silently targeting a guessed branch.
- Cover the portable primary-branch contract at the VCS adapter boundary and cover a complete releaser workflow against a Git repository whose default branch is `main`; retain coverage showing custom default-branch names remain supported.
- Update VCS terminology and usage documentation so `null` means the repository-defined primary branch rather than specifically Git `master`.

Assumptions:

- Supported Git servers advertise their configured default branch through symbolic `HEAD`.
- A repository's default branch does not change during one releaser process run.

Out of scope:

- Changing explicitly configured development branch names.
- Changing SVN's `trunk` convention.
- Upgrading the Git client library.

References:

- [VCS API branch conventions](../../../../../scm4j-vcs-api/README.md) — current cross-adapter meaning of the portable `null` branch value.
- [Git `ls-remote` documentation](https://git-scm.com/docs/git-ls-remote) — authoritative remote symbolic `HEAD` behavior used for default-branch discovery.

## Construction

- [x] update: [abstracttest/VCSAbstractTest.java](../../../../../scm4j-vcs-test/src/main/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
  - replace `master`-specific shared test descriptions and commit messages with portable primary-branch terminology

- [x] update: [vcs/GitVCSTest.java](../../../../../scm4j-vcs-git/src/test/java/org/scm4j/vcs/GitVCSTest.java)
  - run the shared VCS contract against a repository whose symbolic `HEAD` targets `main`
  - verify `null` branch operations consistently use the advertised default branch, including after a reusable working copy has checked out another branch
  - verify a custom `stable` default remains supported and an unusable remote `HEAD` produces a clear VCS failure
  - exclude the symbolic remote-tracking `HEAD` from branch listings

- [x] update: [testutils/TestEnvironment.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)
  - create Git workflow repositories with `main` as their default and without relying on a `master` branch
  - use the `main` fixture for both complete workflow tests and existing custom-development-branch integration scenarios, proving explicit configuration remains authoritative
  - keep SVN workflow fixtures unchanged

- [x] update: [testutils/MonorepoTestEnvironment.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/MonorepoTestEnvironment.java)
  - create Git monorepo workflow fixtures with the same `main` default-branch convention

- [x] update: [vcs/GitVCSUtils.java](../../../../../scm4j-vcs-git/src/main/java/org/scm4j/vcs/GitVCSUtils.java)
  - support creating test repositories with an explicit initial/default branch while retaining a conventional default for existing callers

- [x] update: [vcs/GitVCS.java](../../../../../scm4j-vcs-git/src/main/java/org/scm4j/vcs/GitVCS.java)
  - lazily resolve and retain the authenticated remote symbolic `HEAD` as the branch represented by `null`
  - apply the resolved branch consistently to checkout, reference lookup, merge, push, history, tagging, and branch-deletion safety operations
  - reject a missing, non-symbolic, or non-branch remote `HEAD` with a descriptive VCS error
  - leave explicitly supplied branch names unchanged

- [x] update: [scm4j-vcs-api/README.md](../../../../../scm4j-vcs-api/README.md)
  - redefine the portable `null` branch convention as the repository's primary/default branch and document the Git and SVN mappings

- [x] update: [scm4j-vcs-git/README.md](../../../../../scm4j-vcs-git/README.md)
  - replace `master`-specific operation guidance with remote-default-branch terminology

- [x] update: [config-templates/cc.yml](../../../../../scm4j-releaser/src/main/resources/org/scm4j/releaser/cli/config-templates/cc.yml)
  - explain that an omitted development branch follows the Git remote default or SVN `trunk`

- [x] update: [scm4j-releaser/README.md](../../../../../scm4j-releaser/README.md)
  - describe the default development branch as the repository-defined Git default or SVN `trunk`

- [x] update: [docs/data-structure.md](../../../../../scm4j-releaser/docs/data-structure.md)
  - replace the legacy JitPack branch example with `main-SNAPSHOT`

- [x] update: [docs/data-structure-SCM4J_CC.md](../../../../../scm4j-releaser/docs/data-structure-SCM4J_CC.md)
  - describe an omitted Git development branch using repository-defined default-branch terminology

- [x] update: [scm4j-releaser-choco.nuspec](../../../../../scm4j-releaser-choco/scm4j-releaser-choco.nuspec)
  - update the example repository asset URL from the legacy branch name to `main`
