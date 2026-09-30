# migrate-drivers: scm4j: optimize multi-file reads with IVCS.getFilesContent

- URL: https://untill.atlassian.net/browse/PRIME-290
- ID: PRIME-290
- State: in-progress
- Author: Denis Gribanov
- Labels: none

### Why

Reading related files one by one requires `getFileContent()` calls. For Git, every call performs a fetch, adding approximately one second of network latency per file.

The files should also be read from the same repository revision to ensure a consistent snapshot.

### What

Add `IVCS.getFilesContent` method that:

* Accepts a branch name, a list of file paths, and an optional revision.
* Returns a map where each file path is associated with its content.
* Performs as less underlying vcs operations as possible
* Resolves the commit once and reads all requested files from the same commit tree.
* Preserves the existing branch-not-found and file-not-found behavior.
* Keeps the existing single-file `getFileContent()` API for compatibility.

Example signature:

```
Map<String, String> getFilesContent(
        String branchName,
        List<String> filePaths,
        String revision);
```
