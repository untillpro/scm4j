# scm4j-monorepo: make untillpro/scm4j subprojects be just source files subfolders

- URL: https://untill.atlassian.net/browse/PRIME-95
- ID: PRIME-95
- State: In Progress
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov

## Description

instead make the root be a gradle project that manages dependencies for all subfolders. Also make so that build command build the `scm4j-releaser`, test command run all tests
