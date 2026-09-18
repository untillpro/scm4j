# migrate-drivers: scm4j: migrate Maven group ID from org.scm4j to org.untillpro

- URL: https://untill.atlassian.net/browse/PRIME-197
- ID: PRIME-197
- State: in-progress
- Author: Denis Gribanov
- Labels: none
- Assignees: Denis Gribanov

## Description

The `scm4j-releaser` Maven publication currently uses:

```
org.scm4j:scm4j-releaser
```

Change its Maven group ID to the organization-owned namespace:

```
org.untillpro:scm4j-releaser
```

Update the Gradle module metadata, publication configuration, tests, and consumer documentation accordingly.

Acceptance criteria:

* New releases are published as `org.untillpro:scm4j-releaser:<version>`.
* Existing version `org.scm4j:scm4j-releaser:35.0.0` remains unchanged.
* Client examples use the new coordinates.
* Publication is verified in GitHub Maven Packages.
* Java package names such as `org.scm4j.releaser` remain unchanged; migrating source packages is out of scope.

