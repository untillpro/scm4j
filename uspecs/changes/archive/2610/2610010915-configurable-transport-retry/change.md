---
change_id: 2610010813-configurable-transport-retry
type: feat
scope: [vcs]
issue_url: https://untill.atlassian.net/browse/PRIME-300
---

# Change request: Configurable transport retry for Git and SVN

Refs:

- [PRIME-300: migrate-drivers: scm4j: implement util for retry with backoff and use it on transport operation in both git and svn implementations](./issue-PRIME-300.md)

## Why

Git and SVN transport operations encounter different retryable failures, while retry with backoff is currently limited to the Git implementation. SCM clients need consistent transport resilience without forcing both backends to use the same retry decision rules.

## What

Transport retry behavior becomes shared and configurable across the VCS domain:

- Git and SVN transport operations retry transient failures with backoff.
- Each backend can decide which of its transport failures are retryable.
- Non-retryable failures are propagated without exhausting the retry schedule.

## How

Decisions:

- Use a stateless utility in the VCS API package rather than an adapter base class; the utility accepts a backend failure predicate, operation label, status reporter, and checked operation so adapters retain their existing inheritance and exception boundaries.
- Continue using Failsafe and preserve the existing Git retry schedule of exponential backoff from 500 to 2000 milliseconds, 25 percent jitter, and at most 10 retries rather than introducing a second retry mechanism.
- Preserve backend exception contracts by unwrapping and rethrowing the original checked failure after retries are exhausted, while reporting only retries that are actually scheduled.
- Apply SVN retry to idempotent working-copy synchronization operations such as checkout, switch, and update; do not blindly retry remote mutations whose completion may be ambiguous after a transport failure.
- Keep the existing optional retry-status callback contract so current callers remain compatible and SVN retries become visible through the same reporting path as Git retries.
- Verify the shared policy independently from adapter integration, using synthetic backend-specific failures without depending on live networks or real backoff delays.

Assumptions:

- SVNKit exposes transient connection and I/O failures through stable exception error codes or cause chains that can be classified without treating authentication, repository-state, or path errors as retryable.
- Repeating an interrupted SVN checkout, switch, or update is safe for both repository-managed working copies and caller-provided target paths.

Out of scope:

- Making retry counts, delay bounds, or jitter configurable by library callers.
- Retrying SVN commits, copies, deletes, or other remote mutations.

References:

- [shared VCS extension and retry-reporting contract](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/IVCS.java)
- [current Git transport retry policy and failure classification](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
- [SVN working-copy synchronization and mutation boundaries](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
- [retry dependency configuration](../../../../../build.gradle)
- [retry status consumer behavior](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)

## Construction

### Tests

- [x] create: [api/UtilsTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/api/UtilsTest.java)
  - cover the shared utility's retryable and non-retryable paths, retry-status callback arguments, maximum-attempt behavior, and propagation of the original checked failure
  - exercise the fixed production policy through a package-level backoff-configurer seam that omits delays in tests so verification does not wait for real backoff intervals
- [x] update: [git/GitVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/git/GitVCSTest.java)
  - add regression coverage that socket and end-of-stream failures, including nested causes, remain retryable after orchestration moves out of the Git adapter
  - verify unrelated Git failures remain single-attempt failures and retry notifications retain their operation label and triggering exception
- [x] update: [svn/SVNVCSTest.java](../../../../../scm4j-vcs/src/test/java/org/scm4j/vcs/svn/SVNVCSTest.java)
  - cover retry and reporting for transient checkout, switch, and sparse-update failures using mocked SVNKit clients
  - verify the SVN transport-error allowlist and nested socket/end-of-stream causes retry, while authentication, missing-path, repository-state, and other non-transient errors do not
  - confirm mutating commit, copy, and delete calls are not routed through transport retry

### Shared retry utility

- [x] create: [api/Utils.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/api/Utils.java)
  - provide the generic stateless `runWithRetry` checked-operation runner described in `## How`, parameterized by operation label, failure predicate, and retry-status callback
  - define a library-owned generic checked functional interface for adapter commands so the public utility signature does not expose the implementation-only Failsafe dependency
  - build the existing Failsafe policy with 500-to-2000-millisecond backoff, 25 percent jitter, and 10 retries, and notify the callback only when a retry is scheduled
  - unwrap exhausted executions so callers receive their original checked backend exception; include package-level backoff configuration for deterministic unit tests

### VCS adapters

- [x] update: [git/GitVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/git/GitVCS.java)
  - delegate pull and fetch retries to the shared utility while retaining Git's socket/end-of-stream cause-chain classifier, operation labels, and configured status reporter
  - remove the adapter-local Failsafe policy and wrapper-exception handling without changing the public Git exception behavior
- [x] update: [svn/SVNVCS.java](../../../../../scm4j-vcs/src/main/java/org/scm4j/vcs/svn/SVNVCS.java)
  - retain the retry-status reporter configured through the existing VCS interface and pass it to the shared utility for SVN synchronization operations
  - classify transient failures with an explicit SVNKit allowlist for connection timeout, socket/request setup, connection-closed, and network I/O error codes, plus nested socket/end-of-stream causes
  - route checkout, switch, and update operations through retry with distinct operation labels, preserving current exception translation and excluding remote mutation operations
