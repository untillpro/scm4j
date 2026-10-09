---
change_id: 2610091257-prevent-releaser-status-hangs
type: fix
issue_url: https://untill.atlassian.net/browse/PRIME-361
scope: [releaser, tests]
breaking: true
---

# Change request: Failure-safe concurrent releaser status caching

Refs:

- [PRIME-361: migrate-drivers: scm4j: prevent releaser status hangs by replacing DUMMY cache polling with CompletableFuture](./issue-PRIME-361.md)

## Why

Concurrent releaser status calculations must finish or fail consistently for every dependent thread. Preventing a failed owner from leaving waiters blocked indefinitely makes failures observable, diagnosable, and safely retryable.

## What

Symptom: Dependent releaser status calculations can wait forever when the thread that owns a shared calculation terminates with an `Error`.

```text
multiple threads request status for the same component
      |
      v
ExtendedStatusBuilder.getAndCacheStatus inserts ExtendedStatus.DUMMY
      |
      v
owner terminates with an Error
      |
      v
catch (Exception) is bypassed and DUMMY remains cached   <-- fault: terminal failure is neither published nor cleaned up
      |
      v
dependent threads keep polling DUMMY forever   (symptom)
```

Corrected behavior: All callers share one completion-aware status calculation, receive its result or terminal failure, and a failed cached calculation is conditionally removed so a later request can retry.

## How

Decisions:

- Keep status computation inline on the caller that wins the cache reservation; use the future only as a single-flight result carrier rather than introducing another executor or asynchronous scheduling layer.
- Keep `CachedStatuses` as the concurrency boundary and centralize resolved-status reads and replacements there, so downstream action construction and execution do not duplicate future unwrapping and post-build status updates remain completed cache entries.
- Normalize an owner's failure once before publishing it: preserve `Error` and existing `EReleaserException` instances, wrap other exceptions in `EBuildStatus`, and have both the owner and its waiters rethrow that same normalized failure object.
- Treat interruption as local to the waiting caller: restore its interrupted flag and propagate the interruption through the existing unchecked status-building boundary without cancelling or evicting the owner's shared calculation.
- Exercise concurrency with deterministic coordination primitives; use bounded waits only as deadlock guards rather than using sleeps to drive test ordering.

Assumptions:

- Release action construction and execution consume the cache only after the status graph has completed successfully, so their status-oriented reads encounter completed successful entries.

References:

- [status calculation and cache reservation boundary](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
- [shared status cache representation](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/CachedStatuses.java)
- [parallel dependency traversal behavior](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/Utils.java)
- [post-build cached status replacement](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java)
- [status calculation test boundary](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)

## Construction

- [x] update: [releaser/ExtendedStatusBuilderTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/ExtendedStatusBuilderTest.java)
  - coordinate simultaneous requests deterministically and verify exactly one caller computes while all callers receive the successful result and later requests reuse it
  - verify an ordinary exception is normalized once and the owner and concurrent waiters receive the same `EBuildStatus` instance, while an existing `EReleaserException` is published unchanged
  - verify an `Error` is published and rethrown unchanged to the owner and concurrent waiters instead of leaving them blocked
  - verify conditional removal of a failed computation permits one later caller to retry successfully without causing the original waiters to recompute
  - verify interrupting a waiter restores its interrupt flag and fails only that caller while the owner continues and leaves its successful result cached
  - use latches or barriers for ordering and bounded waits only to fail fast if a regression hangs

- [x] update: [releaser/Coverage.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/Coverage.java)
  - remove coverage of the obsolete `ExtendedStatus.DUMMY` sentinel

- [x] update: [releaser/CachedStatuses.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/CachedStatuses.java)
  - replace inherited `ExtendedStatus` map storage with an encapsulated concurrent map of component locations to `CompletableFuture<ExtendedStatus>`
  - provide atomic future reservation and identity-checked removal operations for status calculation owners
  - preserve status-oriented reads, inserts, replacements, and size inspection for downstream release actions by representing supplied statuses as already-completed futures

- [x] update: [releaser/ExtendedStatusBuilder.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatusBuilder.java)
  - replace sentinel insertion and polling with a candidate future whose successful `putIfAbsent` caller computes inline and whose other callers wait interruptibly on the winning future
  - complete successful owner futures before returning and retain them for cache reuse, including the existing cache-hit rebinding to the requesting component and repository
  - normalize owner failures according to the existing releaser exception contract, complete the winning future exceptionally with that normalized object, conditionally remove that future, and rethrow the same object
  - unwrap completed failures for waiters without changing their identity; rethrow `Error` and `EReleaserException` directly and avoid a second `EBuildStatus` wrapper
  - restore the interrupted flag when a waiter is interrupted, propagate the interruption through the unchecked status-building API, and leave the shared computation untouched
  - remove the polling interval and all sentinel-specific control flow while retaining thread-name restoration for every exit path

- [x] update: [releaser/ExtendedStatus.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/ExtendedStatus.java)
  - remove the `DUMMY` singleton and its special string representation now that in-progress state is represented by a future
