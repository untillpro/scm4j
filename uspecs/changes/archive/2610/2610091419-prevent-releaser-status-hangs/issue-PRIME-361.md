# migrate-drivers: scm4j: prevent releaser status hangs by replacing DUMMY cache polling with CompletableFuture

- URL: https://untill.atlassian.net/browse/PRIME-361
- ID: `PRIME-361`
- State: In Progress⚒️
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov

### Why

Status calculation uses a `DUMMY` cache value to indicate that another thread is computing a component. Dependent threads poll this value every 500 ms until it is replaced.

If the owner terminates with an `Error`, such as `OutOfMemoryError`, the current `catch (Exception)` cleanup is bypassed. The `DUMMY` entry remains indefinitely, and all dependent threads wait forever without receiving the original failure.

The current approach also:

* Uses repeated polling instead of completion notification.
* Does not preserve or propagate the owner’s failure to waiting threads.
* Provides no information about the owner or computation state.
* Can cause multiple waiters to recompute concurrently after a failed entry is removed.
* Makes the original failure difficult to diagnose because the top-level operation may remain waiting.

Dependency-cycle detection is explicitly out of scope; dependency cycles are assumed not to occur.

### What

Replace cached `ExtendedStatus.DUMMY` values with cached `CompletableFuture<ExtendedStatus>` instances.

The implementation should:

* Atomically assign one thread as the computation owner using `putIfAbsent`.
* Make other threads await the same future instead of polling.
* Complete the future with `ExtendedStatus` when computation succeeds.
* Complete the future exceptionally for every terminal failure, including `Error`, before rethrowing it.
* Propagate the same failure to all waiting threads.
* Conditionally remove failed futures with `cache.remove(location, future)` so later requests may retry.
* Keep successful futures cached for reuse.
* Preserve thread interruption and propagate it correctly.
* Add tests covering successful sharing, ordinary exceptions, `Error` propagation, concurrent waiters, retry after failure, and interruption.
* Do not add dependency-cycle detection as part of this issue.
* Implement as simple as possible.
