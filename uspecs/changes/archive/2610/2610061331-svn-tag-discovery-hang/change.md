---
change_id: 2610061242-svn-tag-discovery-hang
type: fix
scope: [vcs]
issue_url: https://untill.atlassian.net/browse/PRIME-330
---

# Change request: Reliable SVN tag discovery below repository root

## Why

When an SVN project URL points below the actual repository root, tag queries mistake copied tags for namespace directories and recursively scan their contents. This makes tag discovery take minutes or appear to hang while callers wait on a large series of remote log requests.

## What

Symptom: Reading SVN tags can appear to hang when the configured project URL is below the SVN repository root.

```text
caller invokes getTags() or getTagsOnRevision()
      |
      v
SVN returns a repository-root-relative changed path
      |
      v
findCopyEntry() constructs a project-relative path   <-- fault: paths cannot match
      |
      v
collectTags() mistakes the copied tag for a namespace
      |
      v
tag contents are recursively queried with SVN log requests
      |
      v
tag discovery takes minutes or appears to hang   (symptom)
```

Corrected behavior: SVN tag discovery matches copy entries relative to the actual repository root, traverses only genuine namespace directories, and returns ordinary and namespaced tags without scanning copied tag contents.

## How

Decisions:

- Resolve each candidate tag through SVNKit's repository-relative path API before comparing it with changed-path keys, avoiding suffix matching and assumptions that the configured project URL is the repository root.
- Preserve recursive tag namespaces, but classify a directory only from its exact creation-path change: a copied addition is a tag, an addition without copy history is a namespace, and missing or contradictory metadata fails without descending into the directory contents.
- Request only the oldest relevant history entry with a server-side limit of one while retaining changed-path discovery and strict node history, so tag and branch boundary lookup does not transfer unused history.
- Verify tag discovery with the project located below a local SVN repository root while retaining the shared ordinary-tag and slash-delimited namespace behavior.

Assumptions:

- The earliest strict node-history entry for a current tag or namespace directory contains that directory's exact added path and copy metadata, when applicable.

Out of scope:

- Changing global SVN connection or socket timeouts and expanding transport retry to repository metadata reads.
- Removing slash-delimited tag namespace support as a workaround.

References:

- [SVN tag discovery and repository session boundary](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [SVN adapter repository setup and exception coverage](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
- [shared tag and namespace behavior](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java)
- [existing SVN transport retry boundary](../../../archive/2610/2610010915-configurable-transport-retry/change.md)
- [SVNKit repository-path and bounded-log API](https://svnkit.com/javadoc/org/tmatesoft/svn/core/io/SVNRepository.html)

## Construction

### Tests

- [x] update: [svn/SVNVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - create a local SVN fixture whose configured project URL is below the repository root, reproducing the path form returned by the affected server topology
  - cover ordinary and slash-delimited tags through both complete listing and revision lookup, including correct names and related revisions
  - verify copied tag directories are classified as tags without querying their payload directories, while added directories without copy history remain traversable namespaces
  - verify absent or contradictory creation-path metadata fails through the existing SVN adapter exception boundary instead of triggering recursive discovery
  - verify first-commit history requests retain changed paths and strict node history while applying a server-side limit of one

### SVN adapter

- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - resolve candidate paths with SVNKit's repository-relative path facility and normalize them to the absolute key form used by changed-path maps
  - classify only the exact candidate creation path as a copied tag or non-copied namespace and reject missing or inconsistent metadata without recursion
  - preserve recursive enumeration for genuine namespace containers and the existing tag names, related commits, and public exception translation
  - replace unbounded collection-based first-commit lookup with the handler-based log operation limited to one result, retaining ascending traversal, changed-path discovery, and strict node history
