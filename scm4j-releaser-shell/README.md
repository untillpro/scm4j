# Overview

Shell runner for https://github.com/scm4j/scm4j-releaser

# Usage

- Install `jdk8`, `git`, `sh` (on Windows `sh` normally comes with `git`, use `chocolatey install git jdk8`)
- Clone this repository
- Run `releaser.cmd` or `releaser`, it will pull scm4j-releaser/master build it and run
- Use `releaser pull` to update from `master`
- Use `releaser pull 23.0` to get particular version
- For more details about using releaser see https://github.com/scm4j/scm4j-releaser

# Under the Hood

- releaser.cmd on Windows has a single purpose - find `sh`, `git` and launch `sh releaser`

# Problems

- If any problems with git occurs drop a folder which is shown by `pull` command

