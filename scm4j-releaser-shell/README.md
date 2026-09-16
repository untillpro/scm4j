# Overview

Shell runner for [scm4j-releaser](../scm4j-releaser).

# Usage

- Install `jdk8`, `git`, `sh` (on Windows `sh` normally comes with `git`, use `chocolatey install git jdk8`)
- Clone the scm4j monorepo
- Run `releaser.cmd` or `releaser` to build and run scm4j-releaser
- Use Git in the monorepo checkout to select the branch, tag, or commit to run
- For more details, see [scm4j-releaser](../scm4j-releaser)

# Under the Hood

- `releaser` resolves the monorepo root from its own location, regardless of the current working directory
- On every invocation, `releaser` uses the root Gradle wrapper to build the scm4j-releaser fat JAR from the current checkout; Gradle skips unchanged build work
- After a successful build, `releaser` runs the generated fat JAR and passes through the supplied CLI arguments
- `releaser.cmd` on Windows has a single purpose - find `sh`, `git` and launch `sh releaser`

The legacy `releaser pull` commands are no longer supported. Update or switch the monorepo checkout with Git instead.

# Historical version

[scm4j-releaser-shell standalone repository](https://github.com/scm4j/scm4j-releaser-shell)
