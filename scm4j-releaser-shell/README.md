# Overview

Shell runner for [scm4j-releaser](../scm4j-releaser).

# Usage

- Install `jdk8`, `git`, `sh` (on Windows `sh` normally comes with `git`, use `chocolatey install git jdk8`)
- Clone the scm4j monorepo
- Run `releaser.cmd` or `releaser` to build and run scm4j-releaser
- Use `releaser pull` to update the releaser source
- Use `releaser pull 23.0` to get particular version
- For more details, see [scm4j-releaser](../scm4j-releaser)

# Under the Hood

- releaser.cmd on Windows has a single purpose - find `sh`, `git` and launch `sh releaser`

# Problems

- If any problems with git occurs drop a folder which is shown by `pull` command

# Historical version

[scm4j-releaser-shell standalone repository](https://github.com/scm4j/scm4j-releaser-shell)
