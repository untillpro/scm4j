# migrate-drivers: scm4j: publish the releaser as a maven artifact on github

- URL: https://untill.atlassian.net/browse/PRIME-194
- ID: PRIME-194
- State: In Progress
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov

## Description

* keep `fatJar` gradle task
* implement `publish` gradle task that will publish the releaser to github maven artifact
* implement github action that will pubish the new version to github maven artifactory
