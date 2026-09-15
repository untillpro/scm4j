---
change_id: 2609151324-component-build-context
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-164
scope: [releaser, tests]
breaking: true
---

# Change request: Build execution context for monorepo components

Refs:

- [PRIME-164: Define the build execution context for monorepo components](./issue-PRIME-164.md)

## Why

Subfolder components are checked out as part of an entire repository, but their release commands always execute at the checkout root. This supports centralized repository-wide builds while leaving component-local build commands without an explicit way to run from their component directory.

## What

Monorepo builds gain an explicit component execution context:

- If subfolder is defined then build command is always executed in the component's dir, otherwise - as before, in the root of the repository

## How

Decisions:

- Resolve the builder working directory in release-build orchestration and pass it through the existing builder contract, so command-line and class-backed builders follow the same component-context rule.
- Continue checking out the entire repository at the selected build revision; change only the directory supplied to the builder so shared and root files remain available to component builds.
- Derive the working directory from the normalized, regex-resolved component subfolder already held by the repository configuration; do not independently expand the release command or introduce a component-subfolder environment variable.
- Keep command parsing, build-time environment variables, tagging, and version updates unchanged because they do not determine the builder's working directory.
- Verify the working-directory contract at the release-build boundary for both Git and SVN, while retaining the command builder's existing guarantee that it applies the directory it receives to the launched process.

Assumptions:

- A configured subfolder is a repository-relative directory present in the selected checkout revision.
- Centralized builds associated with a subfolder can invoke root-owned tooling through a path relative to the component directory; no repository-root execution override is required.

Out of scope:

- Adding a per-component option that overrides subfolder-based working-directory selection.
- Expanding regex captures in release commands or adding a component-subfolder environment variable.
- Replacing the full-repository checkout with a component-only checkout.

References:

- [release checkout and builder invocation boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [shared builder working-directory contract](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/builders/IBuilder.java)
- [command process working-directory behavior](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/builders/CmdLineBuilder.java)
- [normalized component subfolder representation](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSComponentLocation.java)
- [regex-resolved component configuration](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- [cross-adapter release-build coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)

## Provisioning and configuration

- [x] update: [cli/config-templates/cc.yml](../../../../../scm4j-releaser/src/main/resources/org/scm4j/releaser/cli/config-templates/cc.yml): document that a release command runs from the resolved component `subfolder` when present and from the repository checkout root otherwise

## Construction

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - verify against real Git and SVN repositories that a subfolder component's builder receives its directory inside the full checkout as the working folder
  - verify that a component without a subfolder continues to receive the repository checkout root
  - update existing build-revision checks to locate sibling and root files from the checkout root while preserving immediate-tag, delayed-tag, and missing-release-branch coverage

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - keep the full repository checkout in the existing build directory
  - resolve the builder working folder beneath that checkout from the repository's normalized subfolder, falling back to the checkout root when it is empty
  - pass the resolved working folder through the existing builder interface without changing build-time environment variables, tagging, or version updates
