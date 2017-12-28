# Overview

Shell runner for https://github.com/scm4j/scm4j-releaser

# Usage

- Install `jdk8`, `git`, `sh` (on Windows `sh` normally comes with `git`, use `chocolatey install git`)
- Clone this repository
- Run `releaser.cmd` or `releaser`, it will pull scm4j-releaser/master build it and run
- Use `releaser pull` to update from `master`
- Use `releaser pull release/23` to update from `release/23` branch
- For more details about using releaser see https://github.com/scm4j/scm4j-releaser

# Under the Hood

- releaser.cmd on windows has a single purpose - find `sh`, `git` and launch `sh releaser`

# Problems

- If any problems with git occurs drop a folder which is shown by `pull` command

