# migrate-drivers: scm4j: Define the build execution context for monorepo components

- URL: https://untill.atlassian.net/browse/PRIME-164
- ID: PRIME-164
- State: To Do
- Author: Denis Gribanov
- Labels: none

## Problem

Build contract that must be decided  
[SCMProcBuild.java (line 76)](/C:/workspace/scm4j/scm4j-releaser/src/main/java/org/scm4j/releaser/scmactions/procs/SCMProcBuild.java:76) checks out the entire repository and invokes the builder at the checkout root. That is correct for scm4j’s centralized Gradle design from [PRIME-95](https://untill.atlassian.net/browse/PRIME-95). However, generic subfolder components may need to build from their component directory.  
Also, releaseCommand does not expand regex captures and CmdLineBuilder receives no explicit component-subfolder environment variable.

## Solution

Define that whether a subfolder component’s release command runs from the component folder. Preserve root execution for centralized builds.
