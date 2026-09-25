# scm4j-releaser (Rust, simplified)

This is a deliberately small replacement for the Java `scm4j-releaser` workflow. It supports one Git or
Subversion repository and one component. It is single-threaded and never uses a shared home-directory
workspace.

Supported workflow:

1. `status` inspects the develop and latest release branch.
2. `fork` creates `release/MAJOR.MINOR`, writes `MAJOR.MINOR.0` there, and advances develop to the next
   minor `-SNAPSHOT` version.
3. `build` checks the release revision out into an isolated build directory, runs the configured command,
   creates a version tag, and increments the patch version.
4. `build --delayed-tag` builds without tagging; `tag` later applies the saved tag and patch increment.

Managed dependencies (`mdeps`), dependency trees, credentials files, and parallel execution are intentionally
not included. Git components may live in a configured `subfolder`; SVN components must remain at their
configured branch root.

## Configuration

Git example:

```ini
type=git
repository=https://example.org/company/project.git
develop_branch=main
# Optional path to one component in a monorepo.
subfolder=components/service
release_branch_prefix=release/
version_file=version
build_command=./gradlew clean build publish
push=true
```

Subversion example:

```ini
type=svn
repository=https://svn.example.org/project
develop_branch=trunk
release_branch_prefix=release/
tag_prefix=tags/
version_file=version
build_command=./gradlew clean build publish
push=true
```

For Git, `version_file` is resolved relative to `subfolder`, component status only considers commits touching
that subfolder, and the build command runs there. The repository is cloned once into the managed clone;
release checkouts use `git worktree` and share its object database.

All SVN paths are relative to `repository`. A usual layout is therefore `trunk`, `release/1.5`, and
`tags/1.5.0`. `subfolder` must be empty for SVN. Standard SVN authentication and credential caching are used.
`push=false` is Git-only because SVN commits change the repository immediately.

## Build and install

```text
cargo build --release
```

Copy the resulting `scm4j-releaser` executable into a dedicated directory. The program stores everything
in that directory:

- `scm4j-releaser.conf` — configuration;
- `.scm4j-releaser.lock` — exclusive process lock;
- `.scm4j-releaser/repository` — private managed clone or checkout;
- `.scm4j-releaser/builds/<version>` — isolated build checkout (a shared-object Git worktree for Git);
- `.scm4j-releaser/delayed-tag` — optional delayed-tag state.

Initialize and edit the generated configuration:

```text
scm4j-releaser init
scm4j-releaser status
scm4j-releaser fork
scm4j-releaser build
```

The repository must contain a `version` file such as `1.5.0-SNAPSHOT`. Git credentials are handled by the
normal Git credential mechanism. Commits require `user.name` and `user.email` to be configured for Git.

Set `push=false` to exercise the flow without modifying the remote. Since those local commits remain only
in the managed clone, this mode is intended for testing one command at a time.

If the process was forcibly terminated, first make sure it is no longer running and then use
`scm4j-releaser unlock` to remove the stale lock.
