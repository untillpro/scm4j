# scm4j

scm4j is a Java toolkit for source-control, release, build, and deployment automation. This repository is a Gradle monorepo containing the releaser CLI and its supporting modules.

For releaser usage and concepts, see [scm4j-releaser](scm4j-releaser/README.md).

## Release scm4j-releaser

Create and push to main branch a tag named:

```text
scm4j-releaser-<version>
```

The maving artifact with the following coords will be published to github maven repository:

```text
org.untillpro:scm4j-releaser:<version>
```

Example:

```shell
git switch main
git pull --ff-only origin main
git tag -a scm4j-releaser-36.0.1 -m "Release scm4j-releaser 36.0.1"
git push origin scm4j-releaser-36.0.1
```

## Consume scm4j-releaser

### Coordinates and repository

Use these Maven coordinates, replacing `<version>` with the required release:

```text
org.untillpro:scm4j-releaser:<version>
```

The GitHub Maven repository is:

```text
https://maven.pkg.github.com/untillpro/scm4j
```

### Gradle

Add the repository and credentials:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/untillpro/scm4j')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'org.untillpro:scm4j-releaser:36.0.0'
}
```

### Maven

Add credentials to `~/.m2/settings.xml`. The server ID must match the repository ID in `pom.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Add the repository and dependency to `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/untillpro/scm4j</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>org.untillpro</groupId>
    <artifactId>scm4j-releaser</artifactId>
    <version>36.0.0</version>
  </dependency>
</dependencies>
```

The published artifact is an executable fat JAR with `org.scm4j.releaser.cli.CLI` as its main class. After resolving or downloading it, run it with Java 8 or later:

```shell
java -jar scm4j-releaser-36.0.0.jar <arguments>
```
