# Git adapters
The Git adapters execute basic Git VCS operations such as merges and branch creation through the
[common VCS API](../README.md). The native command-line adapter is available for explicit
construction but is not wired into production call sites. Existing callers, including the
releaser, continue to use [GitVCS](../src/main/java/org/scm4j/vcs/git/GitVCS.java) and
[GitVCSUtils](../src/main/java/org/scm4j/vcs/git/GitVCSUtils.java), backed by JGit. Both
implementations are in the `org.scm4j.vcs.git` package.
Features:
- Working with branches: create, remove, browse
- Branch merge with result return (success or list of conflicted files)
- Summarized diff between branches
- File content getting and setting
- File create and remove
- Working with tags: create, remove, browse
- Directional commit history for the whole branch or a selected repository-relative file or directory

Use cases
- VCS server hooks
- Build machines
  - checking in\out, tagging
- Software project management systems
  - Create own branches from GUI, browse commits, product versions management, etc
- Product release automation
  - automatic merging, forking, tagging, version bumping, etc
  - Example: [scm4j-releaser](../../scm4j-releaser/README.md)


# Terms
- Workspace Home
  - Home local folder of all folders used by vcs-related operations. See the [common VCS API](../README.md) for details
- Locked Working Copy, LWC
  - Local folder where vcs-related operations are executed. Provides thread- and process-safe repository of working folders. See the [common VCS API](../README.md) for details
- Test Repository
  - Git repository which is used to execute functional tests
  - File-based repository is used
  - Generates new one before and deletes after each test
  - Named `scm4j-vcs-git-testrepo` under the shared test base directory

# Native CLI dependency matrix

| Category | Requirement | When required | Provisioning and ownership |
| --- | --- | --- | --- |
| Runtime | Java 8-compatible runtime | Always | SCM4J already requires Java 8; the native adapter does not launch a newer JVM. |
| Runtime | `git` 2.25 or later on `PATH` | Always | Provides every porcelain, plumbing, sparse-checkout, remote, and transport command used by the adapter. |
| HTTPS credentials | POSIX-compatible `sh` | Only when credentials are supplied through `IVCS.setCredentials` | Runs the short-lived adapter-owned ask-pass helper. Git commands themselves are never shell command strings. Git for Windows includes `sh`. |
| Ambient credentials | Configured Git credential helper | Optional when SCM4J credentials are not supplied | Owned by the host Git installation and user configuration. Git Credential Manager is bundled with Git for Windows. |
| SSH transport | OpenSSH-compatible `ssh` and valid host-key configuration | Only for SSH repository URLs | Owned by the host. Git for Windows includes OpenSSH. |
| SSH key management | `ssh-agent` and `ssh-add` | Optional for encrypted or agent-managed SSH keys | Owned by the host; both executables are included with Git for Windows. |
| Git transport internals | `git-upload-pack`, `git-receive-pack`, and Git HTTP remote helpers | Selected internally by Git for the configured remote transport | Bundled under Git's execution path; do not install or invoke them separately. |
| Java libraries | Common VCS API and the existing Failsafe retry dependency | Always | The native adapter adds no third-party Java library. |
| Existing adapter | JGit and the JGit-backed `GitVCS` adapter | Existing production call sites | Remains the wired implementation; the native adapter is not selected automatically. |
| Build and test | JDK 8, Git 2.25 or later, and the repository's Gradle wrapper | Contributor and CI builds | Run `gradlew` or `gradlew.bat`; do not install system Gradle. |

The native adapter does not depend on `git-lfs`, submodule initialization, the SVN command-line
client, `curl`, `rsync`, `sed`, `awk`, or `grep`. LFS materialization and submodule checkout are not
implemented by this change. Git for Windows bundles the required and conditional Git-side tools
described above.

# Native Git command contract

The templates below describe process argument lists, not shell command strings. Each token is
passed separately through Java's process API, repository paths follow a literal `--` separator
where the Git command accepts one, and standard output and standard error are drained concurrently.
`GIT_TERMINAL_PROMPT=0` prevents an unattended process from blocking. Explicit credentials use the
short-lived ask-pass environment described above; proxy values are also command-scoped and neither
kind of secret is written to arguments, remote URLs, repository configuration, or logs.

| VCS responsibility | Exact Git command family and selected options | Machine-readable result | Network and credentials |
| --- | --- | --- | --- |
| Executable discovery | `git --version` | Parse the numeric major and minor components from `git version <version>` and reject versions before 2.25. | No network or credentials. |
| Repository initialization | `git clone --origin origin --no-checkout -- <repository> <directory>` | Success is the exit status; diagnostics are retained only for a redacted failure. | Contacts the remote and may use ask-pass, a configured credential helper, SSH, and proxy configuration. |
| Remote default branch | `git ls-remote --symref <repository> HEAD` | Parse the documented `ref: refs/heads/<name>` tab-delimited record and matching `HEAD` object record; reject missing, non-symbolic, or non-branch targets. | Contacts the remote and uses the same transport configuration as clone. |
| Synchronization | `git fetch --prune --prune-tags origin +refs/heads/*:refs/remotes/origin/*` | Success is the exit status. `--prune-tags` supplies the tag refspec, so tags are fetched and stale local tags are removed without a duplicate command-line refspec. The adapter does not use `git pull`; branch state is selected explicitly after fetch. | Contacts the remote, may use credentials and proxy configuration, and is covered by transport retries. |
| Full checkout and cleanup | `git checkout -B <branch> refs/remotes/origin/<branch>`, `git checkout --detach <revision>`, `git reset --hard <revision-or-ref>`, and `git clean -fd` | Success is the exit status. A failed reset or cleanup marks an internal locked working copy as corrupted. | Local after synchronization. |
| Sparse component checkout | `git sparse-checkout init --cone` followed by `git sparse-checkout set --stdin`; `git sparse-checkout disable` restores a full checkout when required | The validated, normalized component directory is written to standard input as one newline-terminated directory record. Success is the exit status; no sparse-index mode or non-cone pattern parsing is used. `init --cone` enables cone interpretation for the following `set`. | Local after clone or fetch. `sparse-checkout` and cone mode establish the Git 2.25 minimum. |
| Ref discovery and validation | `git show-ref --verify --quiet <ref>` and `git for-each-ref --format=%(refname)%00%(objectname)%00%(symref)%00 <ref-prefix>` | `show-ref` uses documented exit status. `for-each-ref` uses NUL-delimited fields followed by Git's record newline; symbolic `origin/HEAD` is excluded from branch results. | Local. |
| Object and file lookup | `git rev-parse --verify <revision>^{commit}`, `git cat-file -e <revision>:<path>`, and `git cat-file blob <revision>:<path>` | Object IDs are single hexadecimal records. `cat-file -e` uses exit status for existence; blob bytes are decoded as UTF-8 for the VCS string API. Revisions are normalized or validated before they become command tokens. | Local after synchronization. |
| Branch lifecycle | `git branch --track <new-branch> refs/remotes/origin/<source-branch>`, `git branch -D <branch>`, and the push refspecs `git push --set-upstream origin refs/heads/<branch>:refs/heads/<branch>` or `git push origin :refs/heads/<branch>` | Local branch commands and push use exit status. Existence is checked through `show-ref`, allowing branch-exists failures to map to the VCS exception contract without parsing diagnostics. | Push contacts the remote and may use credentials, SSH, and proxy configuration; local branch commands do not. |
| Merge and conflict recovery | `git merge --no-edit -m <message> refs/remotes/origin/<source-branch>`, `git diff --name-only --diff-filter=U -z`, `git merge --abort`, and fallback `git reset --hard` | Merge success uses exit status. Conflict paths are NUL-delimited and produce an unsuccessful merge result; abort/reset output is not parsed. | Merge is local after fetch; the successful result is published with `git push origin HEAD:refs/heads/<destination-branch>`. |
| Branch differences | `git diff --name-status -z --no-renames <destination-ref> <source-ref>` plus `git diff --no-color --no-ext-diff --no-renames <destination-ref> <source-ref> -- <path>` for each entry | The summary uses NUL-delimited status/path records. Patch text is captured verbatim as UTF-8; add, delete, and modify statuses map to the VCS change types and other statuses map to unknown. | Local after synchronization. |
| File writes and removal | Java writes requested UTF-8 content, followed by `git add -- <paths>`, `git rm -- <path>`, `git commit --only -m <message> -- <paths>`, and `git push origin HEAD:refs/heads/<branch>` | Mutation commands use exit status. The resulting commit is resolved and formatted through the commit-history commands below. | Add, remove, and commit are local; push contacts the remote and may use transport credentials and proxy configuration. |
| Commit traversal and metadata | `git rev-list` selects revision ranges with `--reverse`, `--max-count=<limit>`, parent exclusions, and optional `-- <repository-relative-path>`; `git log --no-walk=unsorted --format=%H%x00%an%x00%B%x00 <object-ids>` formats the selected commits | `rev-list` emits one object ID per line. `log` emits NUL-delimited revision, author, and full-message fields, followed by the format record newline, in the requested traversal order; limits are applied to the path-filtered selection. | Local after synchronization. |
| Tag lifecycle and lookup | `git tag -a -m <message> <tag> <revision>`, `git tag -d <tag>`, `git for-each-ref [--points-at=<revision>] --format=%(refname)%00%(objecttype)%00%(objectname)%00%(*objectname)%00%(taggername)%00%(contents)%00 refs/tags/`, and push refspecs `git push origin refs/tags/<tag>:refs/tags/<tag>` or `git push origin :refs/tags/<tag>` | The native `IVCS` path creates annotated tags. Lookup records use NUL-delimited fields followed by Git's record newline, so existing annotated and lightweight tags can both be read without parsing display text. Existence is checked with `show-ref`; duplicate tags map to the VCS tag-exists exception. | Tag inspection and local mutation are local; push contacts the remote and may use transport credentials and proxy configuration. |
| Working-copy state | `git status --porcelain=v1 -z --untracked-files=all` | Porcelain v1 is a stable contract and `-z` leaves paths unquoted and NUL-terminated. It is used for cleanliness and recovery decisions, never for user-facing formatting. | Local. |

All listed options are available at the Git 2.25 baseline. The adapter intentionally avoids newer
`switch` and `restore` commands, sparse indexes, partial-clone filters, and human-oriented output.
Only clone, `ls-remote`, fetch, and push are network operations; only those commands may invoke
transport credentials or proxy handling. Git's transport helper programs are selected internally
by Git and are never launched directly by SCM4J.

# Using the existing JGit adapter
- Code snippet
	```java
	import java.io.File;
	import org.scm4j.vcs.api.IVCS;
	import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
	import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
	import org.scm4j.vcs.api.workingcopy.VCSWorkspace;
	import org.scm4j.vcs.git.GitVCS;

	final String WORKSPACE_DIR = new File(System.getProperty("java.io.tmpdir"), "git-workspaces").getPath();
	IVCSWorkspace workspace = new VCSWorkspace(WORKSPACE_DIR);
	String repoUrl = "https://github.com/MyUser/MyRepo";
	IVCSRepositoryWorkspace repoWorkspace = workspace.getVCSRepositoryWorkspace(repoUrl);
	IVCS vcs = new GitVCS(repoWorkspace);
	vcs.setCredentials("user", "password"); // if necessary
	```
- Use methods of the [IVCS interface](../src/main/java/org/scm4j/vcs/api/IVCS.java). See the [common VCS API](../README.md) for details
- Path-filtered history uses the `IVCS.getCommitsRange` repository-relative path contract. Use `/` separators and keep the path within the selected branch.
- Use `vcs.setProxy()` and `vcs.setCredentials()` if necessary
- `GitVCS` additionally exposes `VCSTag createUnannotatedTag(String branchName, String tagName, String revisionToTag)`, which is not part of `IVCS`. Use a `GitVCS` reference to create an unannotated tag named `tagName` at `revisionToTag`. If `branchName` is `null`, the branch targeted by the remote symbolic `HEAD` is used. If `revisionToTag` is `null`, the head of `branchName` is used.

# JGit implementation details
- [JGit](https://projects.eclipse.org/projects/technology.jgit) is used as the framework to work with Git repositories
- Repository operations use an LWC, except `checkout`, which uses the caller-provided target folder. Configuration methods do not allocate an LWC.
- `getLocalGit(IVCSLockedWorkingCopy wc)` method is used to create a Git implementation to execute vcs operations within `wc` Working Copy
  - If the local Git object database does not exist, the configured repository is cloned into the folder; otherwise the existing repository is opened. Fetching and branch switching are handled by the calling operation.
- `setProxy` installs JVM-wide `ProxySelector` and `Authenticator` defaults. The selector uses the configured proxy for URLs containing the repository URL, ignoring case, and delegates other URLs to the previous selector.

# JGit functional testing
- New local file-based Test Repository is created before each test and deletes automatically after each test
- Run [GitVCSTest](../src/test/java/org/scm4j/vcs/git/GitVCSTest.java) as a JUnit test, or run `./gradlew :scm4j-vcs:test --tests org.scm4j.vcs.git.GitVCSTest` from the repository root (`.\gradlew.bat` in Windows PowerShell).
- This includes inherited [VCSAbstractTest](../src/test/java/org/scm4j/vcs/api/abstracttest/VCSAbstractTest.java) tests. See [shared conformance tests](testing.md) for details.

# JGit limitations
- Commit messages can not be attached to branch create and delete operations because Git does not expose these operations as separate commits
- Default-branch lookup requires the remote to advertise a symbolic `HEAD` pointing to an existing branch. The resolved branch name is cached per `GitVCS` instance; there is no fallback to a hard-coded branch name.
- The three-argument `getCommitsRange(branchName, startRevision, endRevision)` excludes the starting revision. Use a `WalkDirection` overload for an inclusive starting cursor.

# Historical version

[scm4j-vcs-git standalone repository](https://github.com/scm4j/scm4j-vcs-git)
