---
change_id: 2609141402-read-mdeps-from-develop-branch
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-157
scope: [releaser, tests]
---

# Change request: Component dependencies from configured develop branches

Refs:

- [PRIME-157: migrate-drivers: scm4j: read component dependencies from the configured develop branch](./issue-PRIME-157.md)

## Why

Release planning must derive a component's dependency graph from the same configured development branch that supplies its version and history. Reading dependencies from the default branch can produce an incorrect release plan when a component uses a custom development branch.

## What

Symptom: A component configured with a custom development branch can resolve dependencies from the default branch instead of its configured branch.

```text
release status is requested for a component
      |
      v
ReleaseBranchFactory.getMDepsDevelop(repo)
      |
      v
getMDepsRelease(null, repo)              <-- fault: null selects the default VCS branch
      |
      v
component mdeps is read from the wrong branch
      |
      v
the release plan contains stale or incorrect dependencies   (symptom)
```

Corrected behavior: Development dependencies are read from the component's configured development branch for both root and subfolder components, while default-branch behavior remains unchanged when no custom branch is configured.

## How

Decisions:

- Reuse the existing branch-parameterized dependency reader so dependency parsing, optional-file handling, and component-relative path resolution remain centralized; the development dependency path supplies the repository's configured development branch.
- Preserve `null` as the configured value that selects each VCS adapter's established default branch, avoiding a compatibility translation or a new branch-selection convention.
- Keep branch interpretation inside the Git and SVN adapters and verify the correction at the releaser boundary against real temporary repositories for both backends.
- Cover root and subfolder components with different dependency content on the default and custom development branches so the tests prove both branch selection and legacy path behavior.

Assumptions:

- None

Out of scope:

- Changing the component configuration schema, dependency-file syntax, component-relative path rules, or VCS branch APIs.
- Extending this fix into broader shared-repository workflow acceptance coverage.

References:

- [development and release dependency selection](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/ReleaseBranchFactory.java)
- [configured development branch and component-path boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
- [dual-backend temporary repository fixture](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/testutils/TestEnvironment.java)
- [existing custom-development-branch integration coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/DevelopBranchTest.java)

## Construction

- [x] create: [branch/ReleaseBranchFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/branch/ReleaseBranchFactoryTest.java)
  - exercise development dependency selection through `ReleaseBranchFactory` against real temporary Git and SVN repositories
  - seed distinguishable dependency files on the default and custom development branches and verify the configured branch wins for both root and subfolder components
  - retain regression coverage for the null development-branch default, including component-relative dependency paths and conversion of development dependencies to unlocked versions

- [x] update: [branch/ReleaseBranchFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/ReleaseBranchFactory.java)
  - pass the repository's configured development branch to the existing dependency reader
  - retain centralized component-path resolution, dependency parsing, missing-file handling, and development-version unlocking
