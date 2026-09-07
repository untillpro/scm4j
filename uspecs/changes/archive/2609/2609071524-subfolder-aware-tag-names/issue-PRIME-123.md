# migrate-drivers: scm4j: consider subfolder on tags naming

- URL: https://untill.atlassian.net/browse/PRIME-123
- ID: PRIME-123
- State: In Progress
- Author: Denis Gribanov
- Assignees: Denis Gribanov
- Labels: none

## Description

if the coords of a component relies on a repository that has `subfolder` specified then tags nams for that component should include its name. E.g. :

cc.yaml content:  

```
- eu\.untill\.sdk\.drivers:(.*):
    url: https://dev.untill.com/git/untill/untill-drivers
    subfolder: $1
```

and we’re releasing version 1.4.7 of a component with coords `eu.untill.sdk.drivers:vmax-fiscal-printer-driver:`

then the tag names for that component should be `vmax-fiscal-printer-driver/1.4.7`
