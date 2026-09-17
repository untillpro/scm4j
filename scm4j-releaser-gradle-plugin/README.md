# Overview

Gradle plugin for [scm4j-releaser](../scm4j-releaser).

The plugin sets the Gradle project version from a `version` file and adds dependencies declared in an optional `mdeps` file.

# Usage

Apply the plugin from the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.scm4j.scm4j-releaser-gradle-plugin):

```groovy
plugins {
    id 'base'
    id 'io.github.scm4j.scm4j-releaser-gradle-plugin' version '0.3.1'
}
```

Add a required `version` file to the root directory of the client project. Its trimmed content becomes `project.version`:

```text
1.2.3-SNAPSHOT
```

Add an `mdeps` file when dependencies are managed by scm4j-releaser. Each non-comment line uses this format:

```text
group:name:version[:classifier][@extension] [# configuration]
```

For example:

```text
org.slf4j:slf4j-api:1.7.36
com.example:reporting::all@jar # runtimeOnly
```

The version may be left empty, in which case the plugin uses `latest.integration`. The configuration defaults to `implementation`. The client build must declare repositories for these dependencies, for example:

```groovy
repositories {
    mavenCentral()
}
```

The plugin fails configuration when `version` is absent or an `mdeps` line is malformed. Blank lines and lines beginning with `#` are ignored.

# Development and publishing

Build and test the plugin from the monorepo root:

```text
./gradlew :scm4j-releaser-gradle-plugin:build
```

The module is configured with the Gradle Plugin Development and Plugin Publish plugins. To publish a non-SNAPSHOT release to the Plugin Portal, set its release version in the root `moduleMetadata`, configure `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`, then run:

```text
./gradlew :scm4j-releaser-gradle-plugin:publishPlugins
```

# Historical version

[scm4j-releaser-gradle-plugin standalone repository](https://github.com/scm4j/scm4j-releaser-gradle-plugin)
