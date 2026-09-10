# migrate-drivers: scm4j: support monorepo components in DevelopBranch

- URL: https://untill.atlassian.net/browse/PRIME-146
- ID: PRIME-146
- State: In Progress⚒️
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov
- Parent issue: [PRIME-77: Migrate prime drivers to a GitHub monorepo](https://untill.atlassian.net/browse/PRIME-77)

## Description

`DevelopBranch` currently reads `version` from the repository root and determines whether development has changed using the latest repository-wide commit. For components with a configured `subfolder`, this can read another component’s version, trigger unnecessary releases, or miss changes when a sibling’s version bump is the latest commit.

Update `DevelopBranch` to use the configured component subfolder:

* `getVersion()` must read `<subfolder>/version` from the configured develop branch.
* `isModified()` must inspect the latest commit affecting that subfolder, using the existing path-filtered VCS history API.
* Apply the existing `#scm-ver` and `#scm-ignore` handling to that component’s history.
* Preserve existing behavior when no subfolder is configured.

**Acceptance criteria:**

* A component configured with `subfolder: components/driver` reads `components/driver/version`, even when a different root `version` exists.
* A missing component version produces the existing `ENoVersionFile` error without falling back to root metadata.
* A component feature followed by a sibling’s `#scm-ver` commit still reports modified.
* A component’s `#scm-ver` commit followed by a sibling feature reports unmodified.
* A component’s latest relevant `#scm-ignore` commit reports unmodified.
* No commits affecting the component returns unmodified.
* Reads and history queries respect a custom `developBranch`.
* Cover Git and SVN behavior and retain regression coverage for components without subfolders.


