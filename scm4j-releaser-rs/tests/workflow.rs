use std::ffi::OsStr;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static NEXT_TEMP: AtomicU64 = AtomicU64::new(0);

struct TempDir(PathBuf);

impl TempDir {
    fn new(test: &str) -> Self {
        let nonce = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let sequence = NEXT_TEMP.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!(
            "scm4j-workflow-{test}-{}-{nonce}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&path).unwrap();
        Self(path)
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[derive(Clone)]
struct Repository {
    bare: PathBuf,
    component_root: String,
}

struct SvnRepository {
    url: String,
}

impl SvnRepository {
    fn cat(&self, relative: &str) -> String {
        successful_command(
            Path::new("."),
            "svn",
            ["cat", &format!("{}/{relative}", self.url)],
        )
    }

    fn exists(&self, relative: &str) -> bool {
        command(
            Path::new("."),
            "svn",
            ["info", &format!("{}/{relative}", self.url)],
        )
        .status
        .success()
    }
}

impl Repository {
    fn show(&self, revision: &str, file: &str) -> String {
        let path = if self.component_root.is_empty() {
            file.to_owned()
        } else {
            format!("{}/{file}", self.component_root)
        };
        git_bare(&self.bare, ["show", &format!("{revision}:{path}")])
    }

    fn has_ref(&self, reference: &str) -> bool {
        command(
            &self.bare,
            "git",
            ["show-ref", "--verify", "--quiet", reference],
        )
        .status
        .success()
    }
}

struct Harness {
    temp: TempDir,
    work: PathBuf,
    config: PathBuf,
    rules: Vec<String>,
}

impl Harness {
    fn new(test: &str) -> Self {
        let temp = TempDir::new(test);
        let work = temp.0.join("work");
        fs::create_dir_all(&work).unwrap();
        let config = temp.0.join("cc.yml");
        Self {
            temp,
            work,
            config,
            rules: Vec::new(),
        }
    }

    fn add_git(
        &mut self,
        component: &str,
        version: &str,
        mdeps: Option<&str>,
        subfolder: Option<&str>,
        build_command: Option<&str>,
    ) -> Repository {
        let name = component.rsplit(':').next().unwrap();
        let bare = self.temp.0.join(format!("{name}.git"));
        let seed = self.temp.0.join(format!("{name}-seed"));
        git(&self.temp.0, ["init", "--bare", path(&bare).as_str()]);
        git(
            &self.temp.0,
            ["init", "--initial-branch=main", path(&seed).as_str()],
        );
        git(&seed, ["config", "user.name", "scm4j test"]);
        git(&seed, ["config", "user.email", "scm4j@example.test"]);

        let component_root = subfolder.unwrap_or_default().replace('\\', "/");
        let root = if component_root.is_empty() {
            seed.clone()
        } else {
            seed.join(&component_root)
        };
        fs::create_dir_all(&root).unwrap();
        fs::write(root.join("version"), format!("{version}\n")).unwrap();
        if let Some(mdeps) = mdeps {
            fs::write(root.join("mdeps"), mdeps).unwrap();
        }
        fs::write(root.join("content.txt"), format!("initial {component}\n")).unwrap();
        git(&seed, ["add", "."]);
        git(&seed, ["commit", "-m", "initial"]);
        git(&seed, ["remote", "add", "origin", path(&bare).as_str()]);
        git(&seed, ["push", "-u", "origin", "main"]);
        git_bare(&bare, ["symbolic-ref", "HEAD", "refs/heads/main"]);

        let mut rule = format!(
            "'{component}':\n  url: '{}'\n  type: git\n  developBranch: main\n",
            path(&bare)
        );
        if !component_root.is_empty() {
            rule.push_str(&format!("  subfolder: '{component_root}'\n"));
        }
        if let Some(build_command) = build_command {
            rule.push_str(&format!("  releaseCommand: '{build_command}'\n"));
        }
        self.rules.push(rule);
        self.write_config();

        Repository {
            bare,
            component_root,
        }
    }

    fn add_monorepo(&mut self, components: &[(&str, &str, &str)]) -> PathBuf {
        let bare = self.temp.0.join("monorepo.git");
        let seed = self.temp.0.join("monorepo-seed");
        git(&self.temp.0, ["init", "--bare", path(&bare).as_str()]);
        git(
            &self.temp.0,
            ["init", "--initial-branch=main", path(&seed).as_str()],
        );
        git(&seed, ["config", "user.name", "scm4j test"]);
        git(&seed, ["config", "user.email", "scm4j@example.test"]);
        for (component, folder, version) in components {
            let root = seed.join(folder);
            fs::create_dir_all(&root).unwrap();
            fs::write(root.join("version"), format!("{version}\n")).unwrap();
            fs::write(root.join("content.txt"), format!("initial {component}\n")).unwrap();
            self.rules.push(format!(
                "'{component}':\n  url: '{}'\n  type: git\n  developBranch: main\n  subfolder: '{folder}'\n  releaseCommand: 'git --version'\n",
                path(&bare)
            ));
        }
        git(&seed, ["add", "."]);
        git(&seed, ["commit", "-m", "initial"]);
        git(&seed, ["remote", "add", "origin", path(&bare).as_str()]);
        git(&seed, ["push", "-u", "origin", "main"]);
        git_bare(&bare, ["symbolic-ref", "HEAD", "refs/heads/main"]);
        self.write_config();
        bare
    }

    fn add_svn(&mut self, component: &str, version: &str, mdeps: Option<&str>) -> SvnRepository {
        let name = component.rsplit(':').next().unwrap();
        let repository = self.temp.0.join(format!("{name}-svn"));
        let seed = self.temp.0.join(format!("{name}-svn-seed"));
        successful_command(
            &self.temp.0,
            "svnadmin",
            ["create", path(&repository).as_str()],
        );
        let url = file_url(&repository);
        successful_command(
            &self.temp.0,
            "svn",
            [
                "mkdir",
                &format!("{url}/trunk"),
                &format!("{url}/branches"),
                &format!("{url}/tags"),
                "-m",
                "layout",
            ],
        );
        successful_command(
            &self.temp.0,
            "svn",
            ["checkout", &format!("{url}/trunk"), path(&seed).as_str()],
        );
        fs::write(seed.join("version"), format!("{version}\n")).unwrap();
        fs::write(seed.join("content.txt"), format!("initial {component}\n")).unwrap();
        if let Some(mdeps) = mdeps {
            fs::write(seed.join("mdeps"), mdeps).unwrap();
        }
        successful_command(&seed, "svn", ["add", "--force", "."]);
        successful_command(&seed, "svn", ["commit", "-m", "initial"]);

        self.rules.push(format!(
            "'{component}':\n  url: '{url}'\n  type: svn\n  developBranch: trunk\n  releaseBranchPrefix: release/\n  releaseCommand: 'svn --version --quiet'\n"
        ));
        self.write_config();
        SvnRepository { url }
    }

    fn write_config(&self) {
        fs::write(&self.config, self.rules.join("\n")).unwrap();
    }

    fn run(&self, args: &[&str]) -> Output {
        Command::new(env!("CARGO_BIN_EXE_scm4j-releaser"))
            .args(args)
            .current_dir(&self.work)
            .env("SCM4J_CC", &self.config)
            .env("SCM4J_CREDENTIALS", "")
            .env("GIT_AUTHOR_NAME", "scm4j test")
            .env("GIT_AUTHOR_EMAIL", "scm4j@example.test")
            .env("GIT_COMMITTER_NAME", "scm4j test")
            .env("GIT_COMMITTER_EMAIL", "scm4j@example.test")
            .output()
            .unwrap()
    }

    fn succeeds(&self, args: &[&str]) -> String {
        let output = self.run(args);
        assert_success(args, &output);
        String::from_utf8_lossy(&output.stdout).into_owned()
    }

    fn fails(&self, args: &[&str]) -> String {
        let output = self.run(args);
        assert!(
            !output.status.success(),
            "command {args:?} unexpectedly succeeded:\n{}",
            String::from_utf8_lossy(&output.stdout)
        );
        String::from_utf8_lossy(&output.stderr).into_owned()
    }
}

#[test]
fn fork_and_build_release() {
    let mut harness = Harness::new("fork-build");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );

    harness.succeeds(&["fork", "org.example:service"]);
    assert_eq!(
        repository.show("refs/heads/main", "version"),
        "1.1.0-SNAPSHOT"
    );
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.0"
    );

    harness.succeeds(&["build", "org.example:service"]);
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.1"
    );
    assert!(repository.has_ref("refs/tags/1.0.0"));
    let output = harness.succeeds(&["build", "org.example:service"]);
    assert!(output.contains("already built"));
}

#[test]
fn component_can_release_the_next_minor_version() {
    let mut harness = Harness::new("next-minor");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service"]);

    commit_to_remote_branch(
        &harness.temp.0,
        &repository.bare,
        "main",
        "next-minor.txt",
        "valuable feature",
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service"]);

    assert!(repository.has_ref("refs/tags/1.0.0"));
    assert!(repository.has_ref("refs/tags/1.1.0"));
    assert_eq!(
        repository.show("refs/heads/main", "version"),
        "1.2.0-SNAPSHOT"
    );
    assert_eq!(
        repository.show("refs/heads/release/1.1", "version"),
        "1.1.1"
    );
}

#[test]
fn build_requires_a_release_branch_and_builder() {
    let mut harness = Harness::new("build-errors");
    harness.add_git(
        "org.example:no-branch",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.add_git("org.example:no-builder", "2.0.0-SNAPSHOT", None, None, None);

    assert!(harness
        .fails(&["build", "org.example:no-branch"])
        .contains("no release branch"));
    harness.succeeds(&["fork", "org.example:no-builder"]);
    assert!(harness
        .fails(&["build", "org.example:no-builder"])
        .contains("releaseCommand is not configured"));
}

#[test]
fn dependency_graph_is_released_first_and_mdeps_are_locked() {
    let mut harness = Harness::new("dependencies");
    let dependency = harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    let root = harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency # keep this comment\n"),
        None,
        Some("git --version"),
    );

    harness.succeeds(&["fork", "org.example:root"]);
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:dependency:2.3.0 # keep this comment"
    );

    harness.succeeds(&["build", "org.example:root"]);
    assert!(dependency.has_ref("refs/tags/2.3.0"));
    assert!(root.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:dependency:2.3.0 # keep this comment"
    );
}

#[test]
fn patch_can_be_built_on_a_previous_release_line() {
    let mut harness = Harness::new("patch");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service"]);

    commit_to_remote_branch(
        &harness.temp.0,
        &repository.bare,
        "release/1.0",
        "patch.txt",
        "patch feature",
    );
    harness.succeeds(&["build", "org.example:service:1.0.0"]);

    assert!(repository.has_ref("refs/tags/1.0.1"));
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.2"
    );
}

#[test]
fn delayed_tag_preserves_revision_until_tag_command() {
    let mut harness = Harness::new("delayed-tag");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service", "--delayed-tag"]);

    assert!(!repository.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.0"
    );
    let state = harness
        .work
        .join(".scm4j-releaser/components/org.example_service/delayed-tag");
    assert!(state.is_file());

    harness.succeeds(&["tag", "org.example:service"]);
    assert!(repository.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.1"
    );
    assert!(!state.exists());
}

#[test]
fn delayed_tag_rejects_an_advanced_release_branch() {
    let mut harness = Harness::new("delayed-tag-advanced");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service", "--delayed-tag"]);
    commit_to_remote_branch(
        &harness.temp.0,
        &repository.bare,
        "release/1.0",
        "late-change.txt",
        "release moved",
    );

    let error = harness.fails(&["tag", "org.example:service"]);
    assert!(error.contains("advanced from delayed commit"));
    assert!(!repository.has_ref("refs/tags/1.0.0"));
}

#[test]
fn monorepo_components_use_scoped_branches_and_tags() {
    let mut harness = Harness::new("monorepo");
    let bare = harness.add_monorepo(&[
        ("org.example:alpha", "components/alpha", "1.0.0-SNAPSHOT"),
        ("org.example:beta", "components/beta", "2.0.0-SNAPSHOT"),
    ]);

    harness.succeeds(&["fork", "org.example:alpha", "org.example:beta"]);
    harness.succeeds(&["build", "org.example:alpha", "org.example:beta"]);

    assert_eq!(
        git_bare(
            &bare,
            [
                "show",
                "refs/heads/alpha/release/1.0:components/alpha/version"
            ]
        ),
        "1.0.1"
    );
    assert_eq!(
        git_bare(
            &bare,
            [
                "show",
                "refs/heads/beta/release/2.0:components/beta/version"
            ]
        ),
        "2.0.1"
    );
    assert!(has_bare_ref(&bare, "refs/tags/alpha/1.0.0"));
    assert!(has_bare_ref(&bare, "refs/tags/beta/2.0.0"));
}

#[test]
fn svn_component_can_be_forked_and_built() {
    let mut harness = Harness::new("svn");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);

    harness.succeeds(&["fork", "org.example:legacy"]);
    assert_eq!(repository.cat("trunk/version"), "3.5.0-SNAPSHOT");
    assert_eq!(repository.cat("branches/release/3.4/version"), "3.4.0");

    harness.succeeds(&["build", "org.example:legacy"]);
    assert_eq!(repository.cat("branches/release/3.4/version"), "3.4.1");
    assert!(repository.exists("tags/3.4.0"));
}

#[test]
fn git_component_locks_an_svn_dependency() {
    let mut harness = Harness::new("git-svn-dependency");
    let dependency = harness.add_svn("org.example:legacy", "2.3.0-SNAPSHOT", None);
    let root = harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:legacy\n"),
        None,
        Some("git --version"),
    );

    harness.succeeds(&["fork", "org.example:root"]);
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:legacy:2.3.0"
    );

    harness.succeeds(&["build", "org.example:root"]);
    assert!(dependency.exists("tags/2.3.0"));
    assert!(root.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:legacy:2.3.0"
    );
}

fn commit_to_remote_branch(root: &Path, bare: &Path, branch: &str, file: &str, content: &str) {
    let clone = root.join(format!("patch-{}", branch.replace('/', "-")));
    git(root, ["clone", path(bare).as_str(), path(&clone).as_str()]);
    git(&clone, ["config", "user.name", "scm4j test"]);
    git(&clone, ["config", "user.email", "scm4j@example.test"]);
    git(&clone, ["checkout", branch]);
    fs::write(clone.join(file), content).unwrap();
    git(&clone, ["add", file]);
    git(&clone, ["commit", "-m", "patch feature"]);
    git(&clone, ["push", "origin", branch]);
}

fn has_bare_ref(bare: &Path, reference: &str) -> bool {
    command(bare, "git", ["show-ref", "--verify", "--quiet", reference])
        .status
        .success()
}

fn git<I, S>(directory: &Path, args: I) -> String
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    successful_command(directory, "git", args)
}

fn git_bare<I, S>(bare: &Path, args: I) -> String
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let mut complete = vec!["--git-dir".to_owned(), path(bare)];
    complete.extend(
        args.into_iter()
            .map(|value| value.as_ref().to_string_lossy().into_owned()),
    );
    successful_command(bare.parent().unwrap(), "git", complete)
}

fn successful_command<I, S>(directory: &Path, program: &str, args: I) -> String
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let output = command(directory, program, args);
    assert!(
        output.status.success(),
        "{program} failed in {}:\nstdout: {}\nstderr: {}",
        directory.display(),
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8_lossy(&output.stdout).trim().to_owned()
}

fn command<I, S>(directory: &Path, program: &str, args: I) -> Output
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    Command::new(program)
        .args(args)
        .current_dir(directory)
        .output()
        .unwrap()
}

fn assert_success(args: &[&str], output: &Output) {
    assert!(
        output.status.success(),
        "command {args:?} failed:\nstdout: {}\nstderr: {}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

fn file_url(path_value: &Path) -> String {
    format!("file:///{}", path(path_value).trim_start_matches('/'))
}
