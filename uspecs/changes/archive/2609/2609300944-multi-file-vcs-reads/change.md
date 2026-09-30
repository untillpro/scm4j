---
change_id: 2609300848-multi-file-vcs-reads
type: feat
scope: [vcs]
issue_url: https://untill.atlassian.net/browse/PRIME-290
---

# Change request: Multi-file VCS content reads

Refs:

- [PRIME-290: migrate-drivers: scm4j: optimize multi-file reads with IVCS.getFilesContent](./issue-PRIME-290.md)

## Why

Reading related repository files one at a time causes repeated Git fetch latency and can combine content from different revisions. SCM tooling needs a way to read several files from one consistent revision while reducing that overhead.

## What

The SCM VCS interface gains multi-file content retrieval:

- Callers can retrieve content for multiple paths from one branch and optional revision in a single request.
- Results map each path to content from one consistent repository revision, reducing repeated Git network operations for related files.
- Existing missing-branch and missing-file behavior remains intact, and callers can continue using single-file retrieval.

## How

Decisions:

- Implement the new method in all vcs implementations. Do not add default methods
- Keep Git's optimized path inside the current JGit adapter and its locked working-copy lifecycle, reusing existing repository access, credentials, and retry behavior without adding native Git commands or a separate transport layer.
- Use the shared VCS conformance suite for behavior across Git and SVN, do not add git- and svn-specific tests that would cover the actual implementation details

Assumptions:

- None

Out of scope:

- Updating downstream driver repositories to replace existing single-file call sequences; this change provides the library capability.

References:

- [shared VCS API contract](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [Git repository read lifecycle](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [SVN single-file content adapter](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [shared adapter conformance tests](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
- [Git shared-suite runner](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)
- [SVN shared-suite runner](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)

## Construction

### Tests

- [x] update: [abstracttest/VCSAbstractTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
  - add shared-suite cases for the multi-file contract and compatibility behavior in `## What`; run them against both Git and SVN without implementation-specific assertions
  - retain the existing single-file content cases

### VCS API and adapters

- [x] update: [api/IVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
  - declare the multi-file content operation as a required method with the branch, path list, and optional revision inputs; do not provide a default implementation
  - document the public contract from `## What` and state that external `IVCS` providers must implement the new method
- [x] update: [git/GitVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
  - implement the issue's reduced-fetch, shared-revision read path within the existing locked JGit lifecycle
  - preserve existing decoding and exception semantics; leave the single-file operation intact
- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - implement multi-file reads through the existing SVNKit repository access, using one selected repository revision for all requested paths
  - preserve the existing exception translation and leave the single-file operation intact

### Documentation

- [x] update: [scm4j-vcs/README.md](../../../../../scm4j-vcs/README.md)
  - document the required operation and its contract from `## What`, including that custom `IVCS` implementations must implement it
  - include the backend-neutral example shown in `## Quick start`

## Quick start

Read related repository files from the selected branch head or an explicit revision:

```java
Map<String, String> contents = vcs.getFilesContent(
        branchName,
        Arrays.asList("pom.xml", "drivers/mysql/pom.xml"),
        revision);
```
