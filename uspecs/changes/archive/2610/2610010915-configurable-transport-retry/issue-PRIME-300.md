# migrate-drivers: scm4j: implement util for retry with backoff and use it on transport operation in both git and svn implementations

- URL: https://untill.atlassian.net/browse/PRIME-300
- ID: PRIME-300
- State: in-progress
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov
- Linked issues: PRIME-77 (parent)

## Description

* move `runWithTransportRetry`from git implementation a a dedicated util tool to somewhere `vcs/api`
* make decisions whether to retyr or not be configurable (because git anv svn eceptions differs)
