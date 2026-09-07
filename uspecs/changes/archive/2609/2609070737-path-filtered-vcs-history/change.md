---
change_id: 2609070652-path-filtered-vcs-history
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-110
scope: [vcs-api, vcs-git, vcs-svn, vcs-test]
---

# Change request: Repository path-filtered history

## Why

Release planning for independently versioned monorepo components must inspect only changes that affect the selected component. A folder therefore needs its own filtered log so that it behaves like a logical subrepository for history queries, without changes from sibling components or unrelated paths.

## What

Version-control history gains repository-relative path filtering with consistent behavior across Git and SVN:

- Callers can request directional commit history limited to a selected file or directory.
- Empty or unspecified paths retain the existing whole-repository history behavior.
- Direction, ordering, inclusive starting revisions, and result limits remain consistent for filtered and unfiltered history.
- Slash-delimited release tag namespaces can be created, listed, found by revision, and removed consistently across supported version-control systems.

## How

Decisions:

- Add path filtering as a required directional-history contract and retain the existing directional method as a default whole-branch convenience entry point.
- Implement non-empty path filtering with each adapter's native history primitive: JGit path logs for Git and path-targeted repository logs for SVN, without in-memory or silent whole-history fallbacks.
- Keep Git's existing slash-delimited ref handling and make SVN create intermediate tag directories and recursively enumerate only actual tag copies beneath them.
- Deliver the contract, both maintained adapters, API documentation, and shared conformance coverage together so no supported implementation has partial behavior.

Assumptions:

- None

Out of scope:

- Deriving component paths or release policy in consuming applications.
- Adding path filtering to the separate start-revision/end-revision history operation.

References:

- [version-control operation contract](../../../../../scm4j-vcs-api/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [Git history and ref handling](../../../../../scm4j-vcs-git/src/main/java/org/scm4j/vcs/GitVCS.java)
- [SVN history and tag handling](../../../../../scm4j-vcs-svn/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [shared adapter conformance suite](../../../../../scm4j-vcs-test/src/main/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)

## Construction

### Tests

- [x] update: [abstracttest/VCSAbstractTest.java](../../../../../scm4j-vcs-test/src/main/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
  - verify path-filtered history includes changes below the selected directory and excludes sibling directories, direct parent-directory files, and unrelated root paths
  - cover both walk directions, inclusive cursors, result limits, and unchanged null/empty-path behavior
  - verify slash-delimited branch and tag creation, enumeration, revision lookup, and removal through the shared adapter contract

- [x] update: [vcs/svn/SVNVCSTest.java](../../../../../scm4j-vcs-svn/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - retain SVN-specific exception and missing-tag-directory coverage with recursive tag namespaces

### API contract

- [x] update: [vcs/api/IVCS.java](../../../../../scm4j-vcs-api/src/main/java/org/scm4j/vcs/api/IVCS.java) and [scm4j-vcs-api/README.md](../../../../../scm4j-vcs-api/README.md)
  - add the required repository-relative path parameter to directional history
  - retain the existing four-argument call as a default whole-branch convenience method delegating with an empty path
  - document filtered-history, null/empty-path, ordering, cursor, direction, and limit semantics

### Adapters

- [x] update: [scm4j/vcs/GitVCS.java](../../../../../scm4j-vcs-git/src/main/java/org/scm4j/vcs/GitVCS.java)
  - use native JGit path history while preserving existing range traversal, ordering, cursor, and limit behavior
  - keep existing slash-delimited branch and tag mechanics unchanged

- [x] update: [vcs/svn/SVNVCS.java](../../../../../scm4j-vcs-svn/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - query history at the selected path below the resolved branch
  - create intermediate directories for slash-delimited tags and recursively enumerate only actual tag copies
  - preserve existing exception wrapping and nested-branch behavior

## Quick start

Request commits affecting one repository folder:

```java
List<VCSCommit> commits = vcs.getCommitsRange(
        branchName, startRevision, WalkDirection.DESC, 10, "components/postgres");
```

Continue using the four-argument overload, or pass an empty path, to request whole-branch history.
