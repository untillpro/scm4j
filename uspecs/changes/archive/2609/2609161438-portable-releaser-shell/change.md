---
change_id: 2609161421-portable-releaser-shell
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-175
scope: [releaser-shell]
---

# Change request: Portable Windows release launcher

Refs:

- [PRIME-175: migrate-drivers: scm4j: refactor releaser-shell](./issue-PRIME-175.md)

## Why

The Windows launcher falls back to an English-language `Program Files` path when Git is not already available on `PATH`, so it can fail on localized installations even when Git for Windows is installed. Its indirect tool lookup also makes missing Git or `sh` difficult to detect and report reliably.

## What

Symptom: On Windows installations where Git for Windows is outside the initial `PATH` or the assumed English installation path, the release launcher reports Git or `sh` missing and does not start the releaser.

```text
user runs releaser.cmd
      |
      v
launcher searches for git.exe and sh.exe through argument and PATH expansion
      |
      v
:try_git injects c:/Program Files/Git/Cmd and :try_sh derives ../usr/bin   <-- fault: locale-dependent and indirect tool discovery
      |
      v
installed tools outside the assumed layout are not found
      |
      v
launcher reports Git or sh missing and exits   (symptom)
```

Corrected behavior: The Windows launcher discovers Git and `sh` without locale-specific installation paths, reports missing prerequisites clearly, and starts the shared release shell when both tools are available.

## How

Decisions:

- Replace recursive sentinel-argument control flow with a single-pass Windows wrapper that treats every original argument as releaser CLI input and returns the delegated process's exit code.
- Discover executables through Windows command lookup first, then retry Git through environment-resolved Program Files locations without assuming a drive letter or localized directory name.
- Accept `sh` directly from `PATH` when available; otherwise resolve the standard Git for Windows `usr/bin` location relative to the discovered Git executable and verify it before launch.
- Keep prerequisite discovery and delegation in the Windows wrapper while leaving build and Java execution in the shared POSIX launcher.

Assumptions:

- A Git for Windows installation that is absent from `PATH` uses its standard `Git` subdirectory beneath a Program Files location exposed by Windows environment variables.
- Git for Windows retains its bundled `sh` in the standard installation layout; installations with a custom shell layout make `sh` available on `PATH`.

Out of scope:

- Installing prerequisites or exhaustively discovering arbitrary Git and shell installation roots through the registry or filesystem scans.
- Changing the POSIX launcher, Gradle build, or Java releaser CLI behavior.

References:

- [current Windows prerequisite discovery and delegation](../../../../../scm4j-releaser-shell/releaser.cmd)
- [release-shell platform prerequisites](../../../../../scm4j-releaser-shell/README.md)
- [Windows `where` command lookup and exit codes](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/where)

## Construction

- [x] update: [scm4j-releaser-shell/releaser.cmd](../../../../../scm4j-releaser-shell/releaser.cmd)
  - replace the recursive self-call and reserved sentinel arguments with one local execution scope that preserves every user-supplied releaser argument
  - locate `git.exe` through Windows command lookup, retrying standard Git for Windows locations through Program Files environment variables without embedding a drive or localized directory name
  - locate `sh.exe` independently on `PATH` or in the standard `usr/bin` directory relative to the discovered Git executable
  - validate each prerequisite before delegation, print a specific actionable error to stderr, and return a nonzero status when discovery fails
  - quote executable and launcher paths, then propagate the shared shell launcher's exit code unchanged
  - verify discovery with tools on `PATH`, Git in an environment-resolved Program Files location, missing Git, missing `sh`, a checkout path containing spaces, and multiple releaser arguments

- [x] update: [scm4j-releaser-shell/README.md](../../../../../scm4j-releaser-shell/README.md)
  - describe the Windows discovery order for Git and `sh` without referring to a locale-specific installation path
  - explain how to resolve prerequisite errors by making custom installations available on `PATH`
  - retain the existing launcher commands and monorepo build behavior
