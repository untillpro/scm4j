---
change_id: 2609171405-upgrade-jgit-5-13-5
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-190
---

# Change request: Upgrade JGit while retaining Java 8 compatibility

## Why

The current JGit 4.3 dependency does not expose a remote's symbolic `HEAD` through its public API, forcing default-branch discovery to depend on JGit internals. JGit 5.13.5 is the final release line compatible with Java 8 and provides the public symbolic-reference behavior needed to remove that coupling without raising scm4j's runtime requirement.

## What

Update scm4j's Git dependency and compatibility guarantees:

- Contributors and consumers use JGit 5.13.5 while scm4j remains compatible with Java 8.
- Git default-branch discovery relies only on supported public JGit APIs, without reflective access to implementation details.
- Release workflows continue to support repositories whose default branch is `main`, `master`, or another advertised branch.
- Existing explicit branch configuration and SVN behavior remain unchanged.

## How

Decisions:

- Use the exact Maven artifact version `5.13.5.202508271544-r`, whose release repairs Java 8 runtime compatibility, rather than a dynamic JGit version.
- Resolve a missing Git branch from the remote's advertised symbolic `HEAD` with JGit's public `ls-remote` API, validate that it targets `refs/heads/*`, and retain the resolved name for the lifetime of the Git VCS instance.
- Reject remotes whose default branch cannot be established from a symbolic `HEAD`; do not guess conventional branch names or inspect JGit implementation fields.

Assumptions:

- Supported Git servers advertise their default branch as a symbolic remote `HEAD`.

Out of scope:

- Adding fallback discovery for Git servers that omit or detach the advertised remote `HEAD`.
- Upgrading unrelated build dependencies.

References (internal):

- [centralized Git dependency declaration](../../../../../build.gradle)
- [Git branch resolution boundary](../../../../../scm4j-vcs-git/src/main/java/org/scm4j/vcs/GitVCS.java)
- [Git adapter behavior coverage](../../../../../scm4j-vcs-git/src/test/java/org/scm4j/vcs/GitVCSTest.java)

References (external):

- [JGit Java runtime requirements](https://github.com/eclipse-jgit/jgit)
- [JGit 5.13.5 Java 8 compatibility fix](https://projects.eclipse.org/projects/technology.jgit/releases/5.13.5)

## Provisioning and configuration

- [x] update: [centralized Git dependency declaration](../../../../../build.gradle): replace JGit `4.3.0.201604071810-r` with the exact Java 8-compatible release `5.13.5.202508271544-r` for `scm4j-vcs-git` (manual edit; the build has no dependency-update command)
