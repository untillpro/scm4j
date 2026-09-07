# migrate-drivers: scm4j: support subfolders in cc.yml

- URL: https://untill.atlassian.net/browse/PRIME-118
- ID: PRIME-118
- State: In Progress
- Author: Denis Gribanov
- Assignees: Denis Gribanov
- Labels: none

## Description

need to have ability to specify in `cc.yml`:

```
- eu\.untill\.sdk\.drivers:(.*):
    url: https://dev.untill.com/git/untill/untill-drivers
    subfolder: components/$1
```

so we will know that a component with coords `eu.untill.sdk.drivers:vmax-fiscal-printer-driver:7.0@zip # drivers` should be taken not from the root of repository [https://dev.untill.com/git/untill/untill-drivers](https://dev.untill.com/git/untill/untill-drivers) but from the folder  `components/vmax-fiscal-printer-driver` in that repository
