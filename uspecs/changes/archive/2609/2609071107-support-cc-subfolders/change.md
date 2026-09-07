---
change_id: 2609070929-support-cc-subfolders
type: feat
issue_url: https://untill.atlassian.net/browse/PRIME-118
scope: [releaser, tests]
---

# Change request: Repository subfolder mappings for components

Refs:

- [PRIME-118: migrate-drivers: scm4j: support subfolders in cc.yml](./issue-PRIME-118.md)

## Why

Component mappings need to carry subfolder metadata for later monorepo work, but the resolved repository configuration currently exposes only repository-level properties. Parsing and retaining the optional value provides that configuration boundary without changing release behavior in this task.

## What

Extend the releaser's component configuration representation:

- Component mappings can declare an optional string-valued subfolder.
- Subfolder values can substitute capture groups from the component-coordinate mapping pattern.
- The resolved subfolder is exposed with the other repository configuration properties.
- Mappings without a subfolder retain the existing configuration and release behavior.
- Parsing and storage do not alter file paths, history, builds, state keys, branches, or tags.

## How

Decisions:

- Read `subfolder` through the same regex placeholder-expansion operation already used for `url`, so matching order and capture substitution remain consistent across string-valued mapping properties.
- Add the resolved value as immutable repository configuration data and expose it through a getter; represent an omitted property as `null`, matching existing optional configuration defaults.
- Pass the property from repository construction without interpreting, normalizing, validating, or consuming it in release workflows.

Assumptions:

- None

Out of scope:

- Treating the parsed value as a filesystem or repository-relative path, including normalization, validation, or containment checks.
- Applying subfolders to version or dependency files, history, checkout or command directories, release state, branches, or tags.
- Changing component coordinate syntax or VCS contracts.

References:

- [regex-backed repository construction](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java)
- [repository configuration properties](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
- [capture-substituted string lookup](../../../../../scm4j-commons/src/main/java/org/scm4j/commons/regexconfig/RegexConfig.java)
- [repository parsing coverage](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryFactoryTest.java)

## Provisioning and configuration

- [x] update: [cli/config-templates/cc.yml](../../../../../scm4j-releaser/src/main/resources/org/scm4j/releaser/cli/config-templates/cc.yml): document the optional `subfolder` property with a capture-substituted example

## Construction

### Tests

- [x] update: [conf/VCSRepositoryFactoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryFactoryTest.java) and [conf/urls-mapping.yml](../../../../../scm4j-releaser/src/test/resources/org/scm4j/releaser/conf/urls-mapping.yml)
  - parse a literal subfolder and a capture-substituted subfolder from matching component rules
  - verify mappings without `subfolder` expose `null` while all existing repository properties remain unchanged

- [x] update: [conf/VCSRepositoryTest.java](../../../../../scm4j-releaser/src/test/java/org/scm4j/releaser/conf/VCSRepositoryTest.java)
  - verify the immutable subfolder property is exposed by the repository model
  - verify subfolder values do not change existing URL-based equality and hash-code behavior

### Configuration model

- [x] update: [conf/VCSRepositoryFactory.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepositoryFactory.java) and [conf/VCSRepository.java](../../../../../scm4j-releaser/src/main/java/org/scm4j/releaser/conf/VCSRepository.java)
  - read `subfolder` with capture substitution and a `null` default when resolving a component rule
  - pass the resolved string into the repository configuration model and expose it through an immutable getter
  - retain the existing constructor as a compatibility overload that delegates with a `null` subfolder
  - leave every VCS, release, path, build, cache, branch, and tag operation unchanged
