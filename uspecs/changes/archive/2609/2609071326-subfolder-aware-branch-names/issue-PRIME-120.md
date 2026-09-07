# migrate-drivers: scm4j: consider subfolder on branches naming

- URL: https://untill.atlassian.net/browse/PRIME-120
- ID: PRIME-120
- State: in-progress
- Author: Denis Gribanov
- Assignees: Denis Gribanov
- Labels: none

## Description

if the coords of a component relies on a repository that has `subfolder` specified then the release branch name for that component should include its name. E.g. :

cc.yaml content:

```
- eu\.untill\.sdk\.drivers:(.*):
    url: https://dev.untill.com/git/untill/untill-drivers
    subfolder: $1
```

and coords of a component is `eu.untill.sdk.drivers:vmax-fiscal-printer-driver:`

then the release branch name for that component should be `vmax-fiscal-printer-driver/release/<ver>`
