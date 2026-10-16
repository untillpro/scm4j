# scm4j-releaser-rs

This is a compact replacement for the Java `scm4j-releaser` workflow. It supports multiple Git or Subversion
components, `mdeps`, and the main project's `cc.yml`/`credentials.yml` configuration model. It is
single-threaded and never uses a shared home-directory workspace.

Supported workflow:

1. `status` inspects the develop branch and its corresponding release branch.
2. `fork` creates a release branch for the current release line, writes a non-snapshot version with patch
   zero there, and advances develop to the next minor `-SNAPSHOT` version.
3. `build` checks the release revision out into an isolated build directory, runs the configured command,
   creates a version tag, and increments the patch version.
4. `build --delayed-tag` delays tagging only for components explicitly named on the command line;
   dependencies are tagged immediately. `tag` later applies the saved root tags and patch increments.

Managed dependencies from `mdeps` are discovered recursively, deduplicated, checked for cycles, and processed
dependency-first. Versions in develop-branch `mdeps` are ignored with a warning: those dependencies
are resolved without a locked version. Release-branch `mdeps` and versions explicitly supplied on the
command line still select their specified release lines. Classifiers and extensions are preserved.
Before a Git parent component is built, dependency versions in its release-branch `mdeps`
are locked to the released versions. Git components may live in a configured `subfolder`; SVN components must
remain at their configured branch root. Execution is intentionally sequential.

Commits whose message contains `#scm-ignore` are skipped when deciding whether a fork or build is
needed, in both Git and SVN. Add this marker manually for changes that do not affect the build result.
Ignored commits do not hide earlier changes that still require a release. Their files are included
when a subsequent release is built. Git's configured `subfolder` filter still applies.

Fork detection inspects develop commits after the release boundary: the merge base with the
release branch in Git, or the release branch's actual `copyfrom-rev` in SVN. Both `#scm-ignore`
and `#scm-ver` commits are skipped; a version marker does not hide earlier changes in that
range. Git's `subfolder` filter applies. SVN release branches must have been copied directly
from the configured develop path.

In both Git and SVN, if develop's release line already has a release branch, `status`, `fork`,
and `build` report an error asking you to increase develop's minor version. An explicitly
selected release line can still be used independently of develop's version.

## Configuration

Configuration is searched first in `<home_dir>/.scm4j`, matching the Java releaser. For compatibility,
the executable directory is searched afterwards. Run `scm4j-releaser init` to create `cc.yml`, `cc`, and
`credentials.yml` in `<home_dir>/.scm4j`. Component coordinates are required for all workflow commands:

```text
scm4j-releaser status org.example:product
scm4j-releaser fork org.example:product
scm4j-releaser build org.example:product
```

Add `--verbose` to describe release actions such as cloning, building, forking, changing versions, tagging,
and locking `mdeps`. Add `--trace` to print every external Git, SVN, build, and `afterTag` command to stderr
before it is executed. Password arguments are redacted from trace output. The two options can be combined.

To inspect or build a service release from a specific release line, append a locked version to the
coordinates. For example, `status org.example:product:152` inspects `release/152` instead of develop;
`build org.example:product:152` builds the next patch from that branch. A full locked version such as
`1.2.7` selects release line `1.2`.

Multiple roots can be passed explicitly; dependencies found in their `mdeps` files are added automatically:

```text
scm4j-releaser build org.example:service-a org.example:service-b
```

`status` prints the dependency tree and the planned action for each visible component. Components whose
action is `DONE` are hidden by default; use `status org.example:product --show-done` to include them.
Coordinates that differ only by version, extension, or classifier are shown once as the same
`group:artifact` component.

Example `cc.yml`:

```yaml
'org.example:(.*)':
  url: https://example.org/company/monorepo.git
  type: git
  subfolder: components/$1
  developBranch: main
  releaseCommand: ./gradlew publish
  afterTag: ./gradlew notifyRelease

'org.example:legacy':
  url: https://svn.example.org/legacy
  type: svn
  developBranch: trunk
  releaseCommand: ./gradlew publish

'~':
  releaseBranchPrefix: release/
```

Rules are regular expressions evaluated in declaration order. As in the Java implementation, `$1`, `$2`,
etc. are substituted in `url` and `subfolder`. `releaseCommand` is required only by `build`; `afterTag` is
optional. The `cc` file lists additional local paths or HTTP(S) YAML URLs, one per line. `SCM4J_CC` overrides
those files; the deprecated `SCM4J_VCS_REPOS` name is also accepted. Sources are separated with `;`.

Example `credentials.yml`:

```yaml
'https://example\.org/.*':
  name: release-user
  password: token-or-password
```

Credential rules match repository URLs in declaration order. `SCM4J_CREDENTIALS` may point to alternative
local or HTTP(S) YAML sources. Git credentials are supplied through an isolated `GIT_ASKPASS` invocation;
SVN credentials use non-interactive CLI arguments and are not cached by the releaser.

For Git, `version_file` is resolved relative to `subfolder`, component status only considers commits touching
that subfolder, and the build command runs there. Repositories are keyed by URL and cloned once even when
several components use different subfolders of the same monorepo. Release checkouts use `git worktree` and
share its object database. Branches and tags are namespaced by `subfolder` to prevent components in one
repository from colliding.

SVN repositories use the conventional `trunk`, `branches`, and `tags` layout. A `releaseBranchPrefix` of
`release/` therefore produces `branches/release/1.5`; a prefix of `B` produces `branches/B1.5`.
`subfolder` must be empty for SVN. Standard SVN authentication is used.

## Build and install

```text
cargo build --release
```

Windows releases are published from tags named `scm4j-releaser-rs-<version>`. The latest x86-64 build is
available at
`https://github.com/untillpro/scm4j/releases/latest/download/scm4j-releaser-windows-x86_64.zip`.
The Cargo package is named `scm4j-releaser-rs`, while the executable remains `scm4j-releaser.exe` for
command-line compatibility.

Copy the resulting executable into a dedicated directory. Configuration is stored in `<home_dir>/.scm4j`:

- `cc.yml`, `cc`, `credentials.yml` — compatible multi-component configuration;

Working data is stored in the current directory from which the command is run:

- `.scm4j-releaser/lock` — OS-managed exclusive process lock;
- `.scm4j-releaser/repositories/<repository-name>-<url-hash>` — deduplicated managed Git clones;
- `.scm4j-releaser/components/<coords>/builds/<version>` — isolated build checkouts;
- `.scm4j-releaser/components/<coords>/delayed-tag` — optional delayed-tag state.

Initialize and edit the generated configuration:

```text
scm4j-releaser init
scm4j-releaser status org.example:product
scm4j-releaser fork org.example:product
scm4j-releaser build org.example:product
```

Each component must contain a `version` file in `[major-prefix]minor.patch[-SNAPSHOT]` form; surrounding
whitespace is accepted when reading, while releaser writes the value without spaces or line breaks. `mdeps`
is optional. The major prefix is kept verbatim and is never incremented, so both `3.0-SNAPSHOT` and
`1.2.3.0-SNAPSHOT` are valid. A develop snapshot must have patch `0`. For example, `1.2.3.0-SNAPSHOT`
forks `release/1.2.3` and advances develop to `1.2.4.0-SNAPSHOT`. Git commits require `user.name` and
`user.email` to be configured.

The operating system releases the process lock automatically when the releaser exits, including
after forced termination. The empty lock file itself remains in the working directory.
