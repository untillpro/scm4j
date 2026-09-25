---
change_id: 2609251356-native-git-cli-driver
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-256
scope: [vcs]
---

# Change request: Native Git CLI driver

## Why

The existing JGit integration is slow, does not support sparse checkout, and cannot gain newer JGit capabilities without requiring JRE 17 or later. SCM4J needs a Git integration path that preserves its current Java runtime compatibility while using the capabilities and performance of the native Git client.

## What

The SCM4J VCS integration gains a Git driver backed by the native Git command-line client:

- Git repository and working-copy operations can run through a locally installed Git executable.
- Sparse checkout is supported so callers can materialize only the required repository paths.
- The native driver preserves the project's existing Java runtime compatibility instead of requiring JRE 17 for newer Git capabilities.
- Native Git execution provides a faster alternative to the existing JGit-backed workflow.

## How

Decisions:

- Add the native implementation as a separate adapter behind the existing VCS contract without wiring it into the releaser factory or any other production call site; keep the existing JGit adapter behavior unchanged.
- Isolate process execution behind one adapter-owned command gateway that invokes Git with argument arrays and an explicit working directory, drains both output streams, treats non-zero exits as structured command failures, and never relies on a platform shell for quoting.
- Consume stable machine-oriented Git output, using explicit formats and NUL-delimited records for paths and commit fields; determine domain conditions through ref, object, and porcelain-status queries instead of localized diagnostic text.
- Supply explicit credentials through a short-lived ask-pass channel, disable terminal prompting, and apply proxy settings per invocation; do not place secrets in command arguments, remote URLs, persistent Git configuration, diagnostics, or retry reports.
- Preserve remote-default-branch resolution, VCS result and exception semantics, locked-working-copy ownership, corruption handling, and transport retry reporting so switching adapters does not change the shared API contract.
- Apply directory-based cone-mode sparse checkout only when the native adapter is explicitly constructed with a component subfolder; keep repository-root checkouts and internal working copies full, and retain Git's documented ancestor-file inclusion rather than using deprecated non-cone patterns.
- Accept the optional normalized component subfolder directly in native-adapter construction without extending the cross-VCS checkout signature or changing SVN behavior.
- Require native Git 2.25 or newer, validate availability and version before repository work, and fail with an actionable VCS error when the executable is missing or unsupported.
- Run the native adapter through the shared VCS conformance suite and add only the native-command coverage needed for process execution, parsing, failures, authentication redaction, and sparse materialization; keep existing JGit tests unchanged.

Assumptions:

- Environments that explicitly construct the native adapter can provide Git 2.25 or newer on `PATH` on Windows and Unix-like hosts.
- Component subfolders passed directly to the native adapter identify directories present at the selected revision, and cone-mode inclusion of files immediately below ancestor directories is acceptable.
- Existing host credential helpers and SSH configuration remain authoritative when explicit username and password credentials are not supplied through the VCS API.

Out of scope:

- Removing the JGit adapter or dependency.
- Integrating the native adapter into `VCSFactory`, the releaser, or any other production call site, including adding configuration to switch between native Git and JGit.
- Supporting arbitrary sparse-checkout patterns, sparse indexes, partial clones, Git LFS materialization, or submodule initialization.
- Changing the VCS API contract or the SVN adapter.

References (internal):

- [shared VCS behavior contract](../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [existing Git behavior and working-copy lifecycle](../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [cross-adapter conformance coverage](../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
- [Java 8 build contract and VCS dependencies](../../../build.gradle)

References (external):

- [Git sparse-checkout modes and behavior](https://git-scm.com/docs/git-sparse-checkout)
- [Git stable porcelain and NUL-delimited status formats](https://git-scm.com/docs/git-status)
- [Git structured commit output formats](https://git-scm.com/docs/git-log)
- [Git symbolic remote reference discovery](https://git-scm.com/docs/git-ls-remote)
- [Git credential and ask-pass mechanisms](https://git-scm.com/docs/gitcredentials)

## Provisioning and configuration

### Continuous integration

- [x] update: [test workflow](../../../.github/workflows/test.yml): verify native Git 2.25 or later before Gradle tests and add a Windows job for the native Git adapter alongside the existing Ubuntu suite (manual edit; the hosted runners supply Git)

### Dependency and command-line tool documentation

- [x] update: [VCS library overview](../../../scm4j-vcs/README.md) and [Git adapter guide](../../../scm4j-vcs/docs/git.md): publish a complete dependency matrix for the native adapter
  - identify Java 8 and `git` 2.25 or later on `PATH` as the only unconditional runtime tools
  - identify a POSIX-compatible `sh` as conditional infrastructure for the adapter-owned ask-pass helper, and an OpenSSH-compatible `ssh` plus host key configuration as conditional infrastructure for SSH remotes; document `ssh-agent` and `ssh-add` as optional key-management tools and note that Git for Windows supplies these executables
  - identify a configured Git credential helper as optional when SCM4J credentials are not supplied, with Git Credential Manager documented as the bundled Windows option
  - identify Git's bundled remote and transport helpers, including `git-upload-pack`, `git-receive-pack`, and the HTTP transport helpers, as part of the Git installation rather than separately provisioned commands
  - distinguish the native adapter's Java/API dependencies and existing Failsafe retry dependency from the unchanged JGit adapter; state that the native adapter adds no third-party Java library
  - document JDK 8, the repository Gradle wrapper, and Git 2.25 or later as build/test prerequisites, with no system Gradle installation required
  - state that `git-lfs`, submodule initialization, the SVN CLI, `curl`, `rsync`, `sed`, `awk`, and `grep` are not native-driver dependencies; LFS materialization and submodule checkout remain unsupported by this change
- [x] update: [Git adapter guide](../../../scm4j-vcs/docs/git.md): list every Git command family exercised by the implementation and map each family to its VCS responsibility
  - cover executable/version discovery; clone, fetch, checkout/reset, sparse checkout, and working-copy cleanup; symbolic remote HEAD and ref discovery; branch, merge, push, and tag lifecycle; file read/write/remove and commit creation; status, diff, and directional/path-filtered history
  - name the exact porcelain and plumbing subcommands selected during implementation, their minimum-version-sensitive options, their machine-readable output mode, and whether each command may access the network or credentials
  - record that Git commands are launched directly through Java process APIs and do not require `cmd.exe`, PowerShell, Bash, or another shell for argument parsing
- [x] update: [releaser shell prerequisites](../../../scm4j-releaser-shell/README.md): add a Windows operator checklist for `java`, `git`, `sh`, and conditional `ssh` availability
  - verify installations with `java -version`, `git --version`, `Get-Command git`, `Get-Command sh`, and `ssh -V`; document which checks are mandatory for HTTPS-only and SSH-based repositories
  - state that Git 2.25 and conditional OpenSSH checks apply to direct native-driver testing and do not imply releaser integration
  - explain that the checked-in `gradlew.bat` is used for builds and that a separately installed `gradle` command is not required

## Construction

### Tests

- [x] create: [git/GitCliTest.java](../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitCliTest.java)
  - verify executable discovery and parsing of supported, unsupported, malformed, and missing Git versions
  - verify direct argument-token execution, working-directory and standard-input handling, concurrent standard-output and standard-error capture, and structured non-zero-exit failures
  - verify command-scoped proxy and ask-pass environments, terminal-prompt suppression, secret redaction from failures and retry reports, and deletion of temporary ask-pass files
- [x] create: [git/NativeGitVCSTest.java](../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/NativeGitVCSTest.java)
  - make the native adapter run the complete shared `VCSAbstractTest` contract against a local Git remote with a symbolic default branch while leaving `GitVCSTest` on JGit
  - cover native parsing with spaces, Unicode paths, multiline commit and tag messages, annotated and lightweight tags, NUL-delimited status/diff output, and missing ref, file, and default-branch cases
  - cover transport-only retries and status reporting, cleanup and merge-recovery corruption handling, and preservation of domain exception/result semantics without parsing localized diagnostics
  - cover full root checkout and Git 2.25 cone-mode sparse checkout at an explicit revision, including ancestor files, excluded sibling directories, and transitions between reusable internal full working copies and explicit sparse checkouts

### Native Git adapter

- [ ] create: [git/GitCli.java](../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitCli.java)
  - provide the adapter-owned Java 8 process gateway for direct Git argument arrays, explicit working directories, optional standard input, concurrent output draining, exit status, and structured failures
  - validate `git --version` against the 2.25 minimum and produce actionable errors for a missing or unsupported executable
  - apply command-scoped credentials, proxy settings, `GIT_TERMINAL_PROMPT=0`, and the short-lived `sh` ask-pass helper without persisting or reporting secrets
  - centralize redacted diagnostics and transport retry execution with the existing Failsafe dependency while leaving local commands non-retrying
- [ ] create: [git/NativeGitVCS.java](../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/NativeGitVCS.java)
  - implement `IVCS` as the native counterpart to `GitVCS`, preserving default-branch caching, results, exceptions, retry reporting, and locked-working-copy ownership
  - implement clone/fetch/reset/clean synchronization, ref and object validation, branch and merge lifecycle, file mutations, directional and path-filtered history, diffs, and annotated tag lifecycle with the documented Git 2.25 commands
  - parse only exit statuses and documented machine formats, including explicit NUL-delimited ref, status, diff, commit, and tag records
  - keep internal working copies full and reusable, mark them corrupted when recovery fails, and apply cone-mode sparse checkout only to explicit component-subfolder checkouts at the requested revision
  - accept the normalized component subfolder at construction without changing `IVCS.checkout` or SVN behavior
