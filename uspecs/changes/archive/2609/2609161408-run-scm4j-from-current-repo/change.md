---
change_id: 2609161355-run-scm4j-from-current-repo
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-174
scope: [releaser-shell]
breaking: true
---

# Change request: Current-repository release driver execution

Refs:

- [PRIME-174: migrate-drivers: scm4j: adapt releaser-shell to run scm4j from the current repo](./issue-PRIME-174.md)

## Why

The monorepo migration requires the release shell to exercise the scm4j sources in the checkout where it is invoked. Running a separately cloned releaser prevents contributors from reliably validating and using the monorepo revision they are currently changing.

## What

The release shell will use scm4j from the current repository:

- Invoking the release shell from an scm4j checkout runs the releaser supplied by that checkout.
- The releaser execution no longer depends on cloning the legacy standalone releaser repository.
- Contributors can validate the current monorepo revision through the existing release-shell entry point.

## How

Decisions:

- Resolve the monorepo root relative to the release launcher's own location rather than the caller's working directory, so the launcher consistently selects the checkout that contains it.
- Build the releaser with the root Gradle wrapper and the releaser subproject's existing fat-JAR packaging, then invoke that artifact through the existing Java CLI while forwarding user arguments unchanged.
- Remove the shell-managed source clone, home-directory cache, and `pull` modes; checkout updates and revision selection remain explicit Git operations performed by the contributor.
- Keep build and execution behavior in the shared POSIX launcher, with the Windows command wrapper remaining a thin Git and shell discovery bridge.

Assumptions:

- Contributors continue to provide the documented JDK 8, Git, and POSIX-shell prerequisites; installing or locating a JDK remains outside the launcher.

Out of scope:

- Adding an automatic updater or a launcher-specific mechanism for selecting released scm4j revisions.
- Changing the releaser CLI, its argument semantics, or the contents of its fat JAR.

References:

- [legacy source clone, update, build, and execution flow](../../../../../scm4j-releaser-shell/releaser)
- [Windows delegation boundary](../../../../../scm4j-releaser-shell/releaser.cmd)
- [release-shell prerequisites and monorepo usage](../../../../../scm4j-releaser-shell/README.md)
- [centralized releaser build and packaging contract](../../../../../build.gradle)

## Construction

- [x] update: [scm4j-releaser-shell/releaser](../../../../../scm4j-releaser-shell/releaser)
  - derive the monorepo root from the launcher's directory so execution does not depend on the caller's working directory
  - run the root Gradle wrapper's releaser fat-JAR build on every invocation, allowing Gradle's incremental checks to avoid unnecessary rebuilds while keeping the artifact synchronized with the current checkout
  - launch the fat JAR produced under the monorepo releaser subproject and preserve user arguments when delegating to the Java CLI
  - remove the home-directory source cache, standalone repository URLs and cloning, and the releaser and shell `pull` modes
  - retain fail-fast behavior and quote checkout and artifact paths so repositories located beneath paths containing spaces remain usable

- [x] update: [scm4j-releaser-shell/README.md](../../../../../scm4j-releaser-shell/README.md)
  - explain that the launcher builds and runs the scm4j revision from the checkout containing it
  - remove the obsolete `pull` commands and cached-clone recovery guidance, directing contributors to manage checkout revisions with Git
  - retain the existing platform prerequisites and Windows delegation guidance
