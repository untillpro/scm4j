# migrate-drivers: scm4j: implement integration test for component from monorepo usage

- URL: https://untill.atlassian.net/browse/PRIME-167
- ID: PRIME-167
- State: In Progress⚒️
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov

## Description

Proposed test name: `testMonorepoComponents`

## End-to-end release workflow test flow

Uses:

- The existing `unTill` application.
- A monorepo containing `postgres` and `sqlite`.
- The existing standalone, non-monorepo `UBL` component.

`unTill` depends on `postgres` and `UBL`. Prepare `UBL` with an existing released version and do not modify it during the test.

Test the workflow for both Git and SVN:

1. Create one valuable commit for `postgres` and two for `sqlite`.
2. Fork and build `unTill`.
3. Verify that:
   - `postgres` is selected as the monorepo dependency.
   - `postgres` is built from its latest component revision rather than the newer repository-wide `sqlite` revision.
   - The correct `postgres` and existing `UBL` versions are locked in the `unTill` release `mdeps`.
   - The `postgres` build is tagged at the revision used for the build.
   - `sqlite` is neither built nor tagged.
   - `UBL` is not rebuilt or retagged.
4. Add another commit affecting only `sqlite`.
5. Verify that `postgres`, `UBL`, and `unTill` do not require a release.
6. Add another commit affecting `postgres`.
7. Verify that:
   - `postgres` requires a new release.
   - `unTill` requires a new version because its `postgres` dependency changed.
   - The new `postgres` and `unTill` versions are built and correctly tagged.
   - The new `unTill` release locks the new `postgres` version.
   - The locked `UBL` version remains unchanged.
   - `UBL` is not built or tagged again.
   - `sqlite` remains unreleased.
