# migrate-drivers: scm4j: read component dependencies from the configured develop branch

- URL: https://untill.atlassian.net/browse/PRIME-157
- ID: PRIME-157
- State: In Progress
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov
- Parent: [PRIME-77: Migrate prime drivers to a GitHub monorepo](https://untill.atlassian.net/browse/PRIME-77)

## Description

`ReleaseBranchFactory.getMDepsDevelop()` reads `mdeps` from the default VCS branch by passing `null` to `getMDepsRelease()`. Use `repo.getDevelopBranch()` so dependency discovery reads `<subfolder>/mdeps` from the configured development branch. Add Git and SVN coverage for root and subfolder components using a custom development branch.

Note that the existing logic with no monorepo used should not break
