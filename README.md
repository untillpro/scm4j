# scm4j

For a small, single-component and Git-only release workflow, see the
[Rust scm4j-releaser](scm4j-releaser-rs/README.md). It is single-threaded and keeps its private clone,
build directories, configuration, and process lock beside the executable.

scm4j (Software Configuration Management for Java) is a Java toolkit for source-control, release, build, and deployment automation. This repository is a Gradle monorepo containing the releaser CLI and its supporting modules.

Configuration management is the practice of handling changes systematically so that a system maintains its integrity over time.

For releaser usage and concepts, see [scm4j-releaser](scm4j-releaser/README.md).

## Release scm4j-releaser

Create and push to main branch a tag named:

```text
scm4j-releaser-<version>
```

The Maven artifact with the following coordinates will be published to the GitHub Maven repository:

```text
io.github.untillpro:scm4j-releaser:<version>
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
io.github.untillpro:scm4j-releaser:<version>
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
    implementation 'io.github.untillpro:scm4j-releaser:36.0.0'
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
    <groupId>io.github.untillpro</groupId>
    <artifactId>scm4j-releaser</artifactId>
    <version>36.0.0</version>
  </dependency>
</dependencies>
```

The published artifact is an executable fat JAR with `org.scm4j.releaser.cli.CLI` as its main class. After resolving or downloading it, run it with Java 8 or later:

```shell
java -jar scm4j-releaser-36.0.0.jar <arguments>
```

## The SCM process

According to [SCMQuest](http://scmquest.com/software-configuration-management-scm/), the SCM process includes:

- Identification of configuration items and baseline management
- Documentation of characteristics
- Change control
- Configuration status accounting
- Auditing and reproducibility
- Build and deployment management
- Process and environment management
- Continuous integration and continuous deployment
- Defect tracking and traceability
- Ensuring integrity, visibility, project coordination, and project evolution

## Further reading

- [SCM versus DevOps](https://softwareengineering.stackexchange.com/questions/130850/difference-between-devops-and-software-configuration-management) — Software Engineering Stack Exchange
- [DevOps Defined](https://www.hashicorp.com/devops-defined) — HashiCorp
- [Software Configuration Management Patterns](https://dzone.com/storage/assets/7529578-rc-167-softwareconfigurationmanagementpatterns.pdf) — DZone
- [Configuration Management](https://en.wikipedia.org/wiki/Configuration_management) — Wikipedia
- [Конфигурационное управление](https://goo.gl/ReJrjH) — Russian-language reference

### Related material

- [ITIL, Information Technology Infrastructure Library](https://en.wikipedia.org/wiki/ITIL)
- [ITIL on Russian Wikipedia](https://ru.wikipedia.org/wiki/ITIL)
- [ITIL books in Russian](http://www.wikiitil.ru/books.html)
