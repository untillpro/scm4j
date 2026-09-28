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
fn version_flags_print_the_package_version_without_configuration() {
    let harness = Harness::new("version");

    for flag in ["--version", "-V"] {
        let output = harness.run(&[flag]);
        assert_success(&[flag], &output);
        assert_eq!(
            String::from_utf8_lossy(&output.stdout),
            format!("scm4j-releaser {}\n", env!("CARGO_PKG_VERSION"))
        );
    }
}

#[test]
fn trace_prints_external_commands_to_stderr() {
    let mut harness = Harness::new("trace-commands");
    harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );

    let quiet = harness.run(&["status", "org.example:service"]);
    assert_success(&["status", "org.example:service"], &quiet);
    assert!(!String::from_utf8_lossy(&quiet.stderr).contains("+ ["));

    let traced = harness.run(&["status", "org.example:service", "--trace"]);
    assert_success(&["status", "org.example:service", "--trace"], &traced);
    let stderr = String::from_utf8_lossy(&traced.stderr);
    assert!(stderr.contains("+ ["), "missing command trace:\n{stderr}");
    assert!(
        stderr.contains("git fetch origin --prune --tags"),
        "{stderr}"
    );
    assert!(
        !stderr.contains("* "),
        "unexpected verbose output:\n{stderr}"
    );
}

#[test]
fn verbose_describes_release_actions_without_command_trace() {
    let mut harness = Harness::new("verbose-actions");
    harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );

    let output = harness.run(&["fork", "org.example:service", "--verbose"]);
    assert_success(&["fork", "org.example:service", "--verbose"], &output);
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains("* Cloning Git repository"), "{stderr}");
    let cloning = stderr
        .lines()
        .find(|line| line.starts_with("* Cloning Git repository"))
        .expect("verbose output must describe cloning");
    assert!(!cloning.contains(" into "), "{cloning}");
    assert!(
        stderr.contains("* Forking Git branch `main` into `release/1.0` at version `1.0.0`"),
        "{stderr}"
    );
    assert!(
        stderr.contains("* Updating version file `version` to `1.0.0`"),
        "{stderr}"
    );
    assert!(
        !stderr.contains("+ ["),
        "unexpected command trace:\n{stderr}"
    );

    let output = harness.run(&["status", "org.example:service", "--verbose"]);
    assert_success(&["status", "org.example:service", "--verbose"], &output);
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert_eq!(
        stderr.matches("* Fetching branches and tags from").count(),
        1,
        "{stderr}"
    );
    assert!(!stderr.contains("* Using Git repository"), "{stderr}");

    let output = harness.run(&["build", "org.example:service", "--verbose"]);
    assert_success(&["build", "org.example:service", "--verbose"], &output);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let building = stderr
        .lines()
        .find(|line| line.starts_with("* Building version"))
        .expect("verbose output must describe building");
    assert!(!building.contains(" in "), "{building}");
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
fn version_file_accepts_line_breaks_when_read() {
    let mut harness = Harness::new("version-whitespace");
    harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );

    harness.succeeds(&["status", "org.example:service"]);
}

#[test]
fn status_accepts_coordinates_with_an_empty_version() {
    let mut harness = Harness::new("status-empty-version");
    harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );

    harness.succeeds(&["status", "org.example:service:"]);
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
        Some("org.example:dependency#keep this comment\n"),
        None,
        Some("git --version"),
    );

    harness.succeeds(&["fork", "org.example:root"]);
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:dependency:2.3.0#keep this comment"
    );

    harness.succeeds(&["build", "org.example:root"]);
    assert!(dependency.has_ref("refs/tags/2.3.0"));
    assert!(root.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:dependency:2.3.0#keep this comment"
    );

    let output = harness.succeeds(&["build", "org.example:root"]);
    assert!(output.contains("already built"), "{output}");
    assert!(!output.contains("Locked mdeps"), "{output}");
}

#[test]
fn status_prints_dependency_tree_with_planned_actions() {
    let mut harness = Harness::new("status-tree");
    harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency\n"),
        None,
        Some("git --version"),
    );

    let output = harness.succeeds(&["status", "org.example:root"]);
    let tree = output
        .split_once("Dependency tree (dependencies execute first):\n")
        .map(|(_, tree)| tree)
        .expect("status output must contain a dependency tree");
    assert!(tree.starts_with("org.example:root [FORK]\n`-- org.example:dependency [FORK]\n"));
}

#[test]
fn status_marks_done_parent_for_rebuild_when_dependency_will_build() {
    let mut harness = Harness::new("status-build-mdeps");
    let dependency = harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency\n"),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    harness.succeeds(&["build", "org.example:root"]);
    commit_to_remote_branch(
        &harness.temp.0,
        &dependency.bare,
        "release/2.3",
        "patch.txt",
        "dependency patch",
    );

    let output = harness.succeeds(&["status", "org.example:root"]);
    let tree = output
        .split_once("Dependency tree (dependencies execute first):\n")
        .map(|(_, tree)| tree)
        .expect("status output must contain a dependency tree");
    assert!(
        tree.starts_with("org.example:root [BUILD_MDEPS]\n`-- org.example:dependency [BUILD]\n")
    );
}

#[test]
fn status_hides_done_nodes_unless_requested() {
    let mut harness = Harness::new("status-show-done");
    harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency\n"),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    harness.succeeds(&["build", "org.example:root"]);

    let hidden = harness.succeeds(&["status", "org.example:root"]);
    assert!(hidden.contains("Dependency tree (dependencies execute first):\n(no actions)\n"));
    assert!(!hidden.contains("[DONE]"));

    let shown = harness.succeeds(&["status", "org.example:root", "--show-done"]);
    assert!(shown.contains("org.example:root [DONE]\n`-- org.example:dependency [DONE]\n"));
}

#[test]
fn status_deduplicates_dependencies_with_different_classifiers() {
    let mut harness = Harness::new("status-classifiers");
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
        Some("org.example:dependency\n"),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    harness.succeeds(&["build", "org.example:root"]);
    assert!(dependency.has_ref("refs/heads/release/2.3"));
    commit_to_remote_branch(
        &harness.temp.0,
        &root.bare,
        "main",
        "mdeps",
        "org.example:dependency:2.3.0:linux\norg.example:dependency:2.3.0:windows\n",
    );

    let output = harness.succeeds(&["status", "org.example:root", "--show-done"]);
    let tree = output
        .split_once("Dependency tree (dependencies execute first):\n")
        .map(|(_, tree)| tree)
        .expect("status output must contain a dependency tree");
    assert_eq!(tree.matches("org.example:dependency").count(), 1);
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
fn delayed_tag_applies_only_to_command_line_roots() {
    let mut harness = Harness::new("delayed-tag-root-only");
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
        Some("org.example:dependency\n"),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    harness.succeeds(&["build", "org.example:root", "--delayed-tag"]);

    assert!(dependency.has_ref("refs/tags/2.3.0"));
    assert_eq!(
        dependency.show("refs/heads/release/2.3", "version"),
        "2.3.1"
    );
    assert!(!root.has_ref("refs/tags/1.0.0"));
    assert_eq!(root.show("refs/heads/release/1.0", "version"), "1.0.0");

    let components = harness.work.join(".scm4j-releaser/components");
    assert!(!components
        .join("org.example_dependency/delayed-tag")
        .exists());
    assert!(components.join("org.example_root/delayed-tag").is_file());

    harness.succeeds(&["tag", "org.example:root"]);
    assert!(root.has_ref("refs/tags/1.0.0"));
    assert_eq!(root.show("refs/heads/release/1.0", "version"), "1.0.1");
}

#[test]
fn delayed_tag_tags_the_saved_revision_after_the_release_branch_advances() {
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
    let delayed_commit = git_bare(&repository.bare, ["rev-parse", "refs/heads/release/1.0"]);
    commit_to_remote_branch(
        &harness.temp.0,
        &repository.bare,
        "release/1.0",
        "late-change.txt",
        "release moved",
    );

    harness.succeeds(&["tag", "org.example:service"]);

    assert_eq!(
        git_bare(&repository.bare, ["rev-list", "-n", "1", "refs/tags/1.0.0"]),
        delayed_commit
    );
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.1"
    );
    assert_eq!(
        repository.show("refs/heads/release/1.0", "late-change.txt"),
        "release moved"
    );
}

#[test]
fn delayed_tag_does_not_downgrade_an_already_bumped_release_version() {
    let mut harness = Harness::new("delayed-tag-version-advanced");
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
        "version",
        "1.0.2",
    );

    harness.succeeds(&["tag", "org.example:service"]);

    assert!(repository.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.2"
    );
}

#[test]
fn delayed_tag_rejects_a_release_branch_that_was_rewritten() {
    let mut harness = Harness::new("delayed-tag-rewritten");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service", "--delayed-tag"]);
    git_bare(
        &repository.bare,
        ["update-ref", "refs/heads/release/1.0", "refs/heads/main"],
    );

    let error = harness.fails(&["tag", "org.example:service"]);

    assert!(error.contains("no longer contains delayed commit"));
    assert!(!repository.has_ref("refs/tags/1.0.0"));
}

#[test]
fn monorepo_components_with_the_same_artifact_use_subfolder_scoped_refs() {
    let mut harness = Harness::new("monorepo");
    let bare = harness.add_monorepo(&[
        ("com.acme:api", "components/acme-api", "1.0.0-SNAPSHOT"),
        (
            "org.example:api",
            "components/example-api",
            "2.0.0-SNAPSHOT",
        ),
    ]);

    harness.succeeds(&["fork", "com.acme:api", "org.example:api"]);
    harness.succeeds(&["build", "com.acme:api", "org.example:api"]);

    assert_eq!(
        git_bare(
            &bare,
            [
                "show",
                "refs/heads/components/acme-api/release/1.0:components/acme-api/version"
            ]
        ),
        "1.0.1"
    );
    assert_eq!(
        git_bare(
            &bare,
            [
                "show",
                "refs/heads/components/example-api/release/2.0:components/example-api/version"
            ]
        ),
        "2.0.1"
    );
    assert!(has_bare_ref(&bare, "refs/tags/components/acme-api/1.0.0"));
    assert!(has_bare_ref(
        &bare,
        "refs/tags/components/example-api/2.0.0"
    ));
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
