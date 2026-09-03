---
change_id: 2609021500-centralize-gradle-monorepo-build
type: build
issue_url: https://untill.atlassian.net/browse/PRIME-95
---

# Change request: Centralized Gradle build for the scm4j monorepo

Refs:

- [PRIME-95: scm4j-monorepo: make untillpro/scm4j subprojects be just source files subfolders](./issue-PRIME-95.md)

## Why

Each migrated scm4j project currently retains its own standalone Gradle project configuration, so contributors cannot build and test the monorepo as one unit. A root-managed build is needed to coordinate dependencies and make repository-level build and test commands meaningful.

## What

The monorepo gains a single contributor-facing Gradle build contract:

- The repository root is the Gradle build entry point for all source-only project subfolders.
- The root build is the sole source of truth for dependencies between project subfolders and for their third-party dependencies.
- Project subfolders retain their source, resources, and relevant descriptive or legal documents, but no standalone build, release, or CI configuration; any configuration still required is owned at the repository root.
- Dependencies between included scm4j projects resolve from their local source subfolders in the current checkout rather than from previously published scm4j artifacts.
- The standard build command produces `scm4j-releaser`, building only that project and the project subfolders in its dependency closure; unrelated deliverables are outside the command's scope.
- The centralized build preserves `scm4j-releaser`'s existing artifact formats and packaging behavior; changing its release format is outside this change.
- The standard test command executes every test in every migrated Gradle-based source project, including auxiliary projects such as `scm4j-template` and `scm4j-test-jitpack`, and fails when any test fails.
- Opening the repository root in Visual Studio Code imports the complete Gradle project model and exposes Run and Debug actions for test classes and methods in every project subfolder, without opening subfolders as separate workspaces.

## How

Decisions:

- Implement one native Gradle multi-project build, keeping the existing source-folder names as project paths and configuring every included project from root-owned Groovy build logic; do not use composite builds or invoke nested legacy wrappers.
- Add root-owned `.vscode/extensions.json` recommending the Extension Pack for Java and `.vscode/settings.json` selecting standard Java project mode, automatic Gradle model updates, and root-wrapper import. Rely on the Java Test Runner's generated test actions rather than per-project VS Code metadata or custom task and launch definitions.
- Standardize the root wrapper on Gradle 8.14.5, the latest patch on the final Gradle line that can run on Java 8, while preserving each project's existing Java 7 or Java 8 source and target compatibility.
- Replace `mdeps` parsing and published scm4j coordinates with standard Gradle project dependencies, and translate legacy dependency scopes to the corresponding Gradle 8 configurations.
- Move project identity and third-party dependency declarations to the root without aligning or upgrading existing dependency versions; preserve project-specific source sets, manifests, archives, and releaser packaging behavior.
- Express root build and test orchestration through Gradle project and task relationships within one invocation. The all-tests path includes the Gradle plugin's TestKit integration tests as well as ordinary project tests, while existing environment-based test skips remain effective.
- Resolve external libraries through Maven Central and JitPack rather than JCenter, and retain only plugins needed for the required compilation, testing, and artifact behavior.

Assumptions:

- The existing third-party coordinates and versions required by the source projects remain available from Maven Central or JitPack; an unavailable artifact will require an explicit follow-up decision instead of an implicit version upgrade.
- Tests guarded by optional external-service settings are expected to skip when those settings are absent; the aggregate test command does not provision those services or credentials.
- Contributors install the recommended Java extensions and make a compatible project JDK available to VS Code; machine-specific JDK installation paths remain user settings and are not committed to the repository.

Out of scope:

- Upgrading Java language levels, third-party libraries, or public APIs.
- Recreating standalone per-repository publishing, IDE, Coveralls, or Travis workflows that are not required by the centralized build contract.
- Provisioning Jenkins or other external systems used by conditionally enabled tests.

References (internal):

- [current releaser dependencies, source sets, and artifact behavior](../../../../../scm4j-releaser/build.gradle)
- [legacy releaser dependency relationships](../../../../../scm4j-releaser/mdeps)
- [Gradle plugin build and TestKit test boundary](../../../../../scm4j-releaser-gradle-plugin/build.gradle)
- [legacy Java 7 compatibility boundary](../../../../../scm4j-jenkins/build.gradle)
- [conditional Jenkins test requirements](../../../../../scm4j-jenkins/README.md)

References (external):

- [Gradle multi-project builds and project dependencies](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [Gradle 8.14.5 release notes](https://docs.gradle.org/8.14.5/release-notes.html)
- [JCenter sunset guidance](https://jfrog.com/blog/jcenter-sunset/)
- [VS Code Java project import behavior](https://code.visualstudio.com/docs/java/java-project)
- [VS Code Java test discovery and Run/Debug actions](https://code.visualstudio.com/docs/java/java-testing)
- [VS Code Extension Pack for Java contents](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)

## Provisioning and configuration

### Root Gradle build

- [x] create: [settings.gradle](../../../../../settings.gradle)
  - name the root project `scm4j`
  - include every existing Gradle-based source folder as a subproject under its current folder name
  - keep `scm4j-doc`, `scm4j-releaser-choco`, and `scm4j-releaser-shell` outside the Java project graph

- [x] create: [build.gradle](../../../../../build.gradle)
  - configure all subprojects from the root with their existing group, version, Java compatibility, source sets, manifests, archives, test behavior, and required packaging behavior
  - migrate every third-party source, runtime, and test dependency required by the centralized build without changing its version, using Gradle 8 dependency configurations and Maven Central or JitPack as appropriate
  - replace scm4j artifact coordinates and `mdeps` parsing with local project dependencies, preserving whether each relationship belongs to production or test classpaths
  - make the absolute root task `:build` select `:scm4j-releaser:build` and its project dependency closure, avoiding Gradle's unqualified task selector that would build every subproject
  - make the absolute root task `:test` aggregate every subproject test, including the Gradle plugin TestKit integration category

- [x] create: root Gradle 8.14.5 wrapper files, including [gradle/wrapper/gradle-wrapper.properties](../../../../../gradle/wrapper/gradle-wrapper.properties), [gradlew](../../../../../gradlew), and [gradlew.bat](../../../../../gradlew.bat)
  - bootstrap from the existing Gradle 7.4.2 wrapper because no system Gradle installation is present on Windows:
    - `.\scm4j-releaser-gradle-plugin\gradlew.bat wrapper --gradle-version 8.14.5 --distribution-type bin`
    - `.\gradlew.bat wrapper --gradle-version 8.14.5 --distribution-type bin`
  - retain the wrapper JAR generated by the second command so contributors use the declared distribution without installing Gradle globally

### Visual Studio Code workspace

- [x] create: [.vscode/extensions.json](../../../../../.vscode/extensions.json)
  - recommend `vscjava.vscode-java-pack`, which supplies Java language support, Gradle project import, and the Java Test Runner
  - do not pin machine-local extension installation paths or versions

- [x] create: [.vscode/settings.json](../../../../../.vscode/settings.json)
  - set `java.server.launchMode` to `Standard`
  - set `java.configuration.updateBuildConfiguration` to `automatic`
  - enable `java.import.gradle.wrapper.enabled` so project import uses the root wrapper
  - keep nested Gradle project discovery disabled so the root multi-project model is imported only once
  - do not commit machine-specific JDK paths; contributors configure their compatible project JDK in user settings

### Legacy configuration cleanup

- [x] update: [.gitignore](../../../../../.gitignore)
  - consolidate the still-relevant Gradle, Java, Eclipse, IntelliJ, test, and generated-output exclusions currently distributed across project-level ignore files
  - preserve source-tree `.gitignore` placeholders that intentionally keep otherwise-empty source directories

- [x] remove: standalone configuration from every included project after its required values have been migrated to the root
  - remove project build and settings files, nested Gradle wrapper directories and scripts, and Gradle/Eclipse launcher files
  - remove project-level `version` and `mdeps` files after migrating their identities and dependency relationships
  - remove obsolete Travis and project-level build, release, IDE, coverage, and ignore configuration superseded by the root files
  - retain source, resources, documentation, notices, and licenses
