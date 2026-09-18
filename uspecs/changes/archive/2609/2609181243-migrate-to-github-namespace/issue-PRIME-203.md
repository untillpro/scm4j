# migrate-drivers: scm4j: migrate scm4j Maven and Gradle plugin coordinates to io.github.untillpro

- URL: https://untill.atlassian.net/browse/PRIME-203
- ID: PRIME-203
- State: in-progress
- Author: Denis Gribanov
- Labels: none
- Assignee: Denis Gribanov

## Description

The current `org.untillpro` group incorrectly implies ownership of the `untillpro.org` domain. This namespace cannot be verified for publishing to Maven Central or the Gradle Plugin Portal because the domain is not owned by Untill.

Migrate all scm4j artifacts to the GitHub-based namespace associated with the `untillpro` organization:

```
io.github.untillpro
```

Before publishing, confirm and verify that Maven Central and the Gradle Plugin Portal accept `io.github.untillpro` for the GitHub organization.

Required changes:

* Change the `scm4j-releaser` Maven `groupId` to `io.github.untillpro`.
* Change the `scm4j-releaser-gradle-plugin` publication group to `io.github.untillpro`.
* Change the Gradle plugin ID to use the same namespace, if required:

    ```
    io.github.untillpro.scm4j-releaser-gradle-plugin
    ```
* Update Gradle publication configuration, tests, workflows, and documentation.
* Update consumer examples with the new Maven coordinates and plugin ID.
* Document that artifacts previously published under `org.untillpro` remain separate and are not automatically relocated.
* Verify publication and consumption through GitHub Packages.
* Verify namespace ownership requirements for future Maven Central and Gradle Plugin Portal publication.
* provide comments describing why the group name must be `io.github.untillpro`

**Acceptance criteria**

* The releaser is published as:

    ```
    io.github.untillpro:scm4j-releaser:<version>
    ```
* The Gradle plugin publication uses the `io.github.untillpro` namespace.
* A test consumer project can resolve and use both publications.
* Documentation contains only the new coordinates.
* The namespace is verified or confirmed as eligible for Maven Central and Gradle Plugin Portal.

