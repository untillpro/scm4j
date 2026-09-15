---
change_id: 2609141502-build-tag-component-revision
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-159
scope: [releaser, tests]
---

# Change request: Build subfolder components from their latest component revision

## Why

A monorepo component must be built and tagged from the same component revision used to determine that it needs a release. Building from a later repository-wide revision makes the component's release boundary inconsistent with its path-filtered history and can cause an already released change to be reported as unreleased.

## What

Symptom: A subfolder component can be rebuilt even though its latest component change was already built and tagged.

```text
a component change schedules a release build
      |
      v
a sibling or repository-root commit becomes the release branch head
      |
      v
SCMProcBuild selects the unfiltered repository head   <-- fault: selects a revision outside the component history
      |
      v
the component is built and tagged at that repository-wide revision
      |
      v
the next component-path-filtered status check cannot see the tagged revision
      |
      v
the released component change is reported as unreleased   (symptom)
```

Corrected behavior: A subfolder component is checked out, built, and tagged at the latest release-branch revision affecting its subfolder, while a component without a subfolder continues to use the repository branch head and both immediate and delayed tags identify the selected build revision.

## How

Decisions:

- Use the existing path-aware VCS history operation as the source of the build revision for subfolder components, while retaining the optimized branch-head lookup for components without a subfolder.
- Use one selected revision consistently for checkout, build-time revision metadata, immediate tagging, and delayed-tag persistence.
- Keep the post-build version bump on the current release branch so unrelated commits already present at the branch head are preserved.
- Implement the selection above the VCS adapters and verify it through the shared release workflow against both Git and SVN, avoiding backend-specific release semantics.

Assumptions:

- None

Out of scope:

- Changing component tag names or component-specific tag matching.
- Treating sibling or repository-root changes as changes to a subfolder component.

References:

- [build, checkout, tag, and version-bump lifecycle](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [existing path-aware latest-revision selection](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/branch/DevelopBranch.java)
- [subfolder component release workflow](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowPatchesTest.java)
- [immediate and delayed component-tag behavior](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/WorkflowDelayedTagTest.java)

## Construction

- [x] update: [procs/SCMProcBuildTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/scmactions/procs/SCMProcBuildTest.java)
  - exercise build-revision selection against real temporary Git and SVN repositories
  - verify that a subfolder component change followed by a sibling or repository-root commit is checked out and exposed to the builder as the component revision
  - verify that immediate tags and delayed-tag records identify the selected component revision while the post-build version bump preserves later repository-head content
  - retain regression coverage that a component without a subfolder builds and tags the repository branch head

- [x] update: [procs/SCMProcBuild.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
  - select the latest release-branch commit affecting the configured component subfolder, using the existing descending path-filtered history operation with a one-commit limit
  - retain direct branch-head selection when the component subfolder is empty and preserve the existing missing-revision failure behavior
  - pass the single selected revision through checkout, build-time environment variables, immediate tagging, and delayed-tag persistence without changing the branch-head version bump
