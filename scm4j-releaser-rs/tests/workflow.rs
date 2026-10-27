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
fn develop_mdeps_ignore_versions_with_one_warning_per_dependency() {
    for svn_root in [false, true] {
        let mut harness = Harness::new("develop-mdeps-version");
        harness.add_git(
            "org.example:dependency",
            "2.3.0-SNAPSHOT",
            None,
            None,
            Some("git --version"),
        );
        let mdeps = "org.example:dependency:0.0.0-SNAPSHOT:all@jar # runtimeOnly\n";
        if svn_root {
            harness.add_svn("org.example:root", "1.0.0-SNAPSHOT", Some(mdeps));
        } else {
            harness.add_git(
                "org.example:root",
                "1.0.0-SNAPSHOT",
                Some(mdeps),
                None,
                Some("git --version"),
            );
        }
        let output = harness.run(&["status", "org.example:root"]);
        assert_success(&["status", "org.example:root"], &output);
        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        assert!(stdout.contains("org.example:dependency [FORK]"), "{stdout}");
        assert_eq!(
            stderr
                .matches("WARNING: ignoring dependency version")
                .count(),
            1,
            "{stderr}"
        );
        assert!(stderr.contains("org.example:root"), "{stderr}");
        assert!(
            stderr.contains("org.example:dependency:0.0.0-SNAPSHOT:all@jar"),
            "{stderr}"
        );
    }
}

#[test]
fn fork_replaces_stale_develop_mdeps_versions_with_current_releases() {
    let mut harness = Harness::new("stale-develop-mdeps");
    let dependency = harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    let original = "org.example:dependency:0.0.0:all@jar # runtimeOnly\n";
    let root = harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some(original),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    assert_eq!(root.show("refs/heads/main", "mdeps"), original.trim());
    assert_eq!(
        root.show("refs/heads/release/1.0", "mdeps"),
        "org.example:dependency:2.3.0:all@jar # runtimeOnly"
    );
    harness.succeeds(&["build", "org.example:root"]);
    assert!(dependency.has_ref("refs/tags/2.3.0"));
    assert!(root.has_ref("refs/tags/1.0.0"));
    let output = harness.run(&["status", "org.example:root:1.0.0", "--show-done"]);
    assert_success(&["status", "org.example:root:1.0.0"], &output);
    assert!(!String::from_utf8_lossy(&output.stderr).contains("ignoring dependency version"));
    assert!(String::from_utf8_lossy(&output.stdout).contains("org.example:dependency [DONE]"));
}

#[test]
fn fork_preserves_mdeps_whitespace_and_trailing_lines() {
    for ending in ["", "\n", "\n\n", "\n\n  \t\n"] {
        let mut harness = Harness::new("mdeps-formatting");
        harness.add_git(
            "org.example:dependency",
            "2.3.0-SNAPSHOT",
            None,
            None,
            Some("git --version"),
        );
        let original = format!("\n  org.example:dependency  # runtimeOnly{ending}");
        let root = harness.add_git(
            "org.example:root",
            "1.0.0-SNAPSHOT",
            Some(&original),
            None,
            Some("git --version"),
        );
        harness.succeeds(&["fork", "org.example:root"]);
        // Read raw stdout: the usual show helper trims away this regression.
        let output = command(&root.bare, "git", ["show", "refs/heads/release/1.0:mdeps"]);
        assert!(output.status.success());
        assert_eq!(
            String::from_utf8(output.stdout).unwrap(),
            original.replace("org.example:dependency", "org.example:dependency:2.3.0"),
            "file ending: {ending:?}"
        );
    }
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
fn dependency_changes_fork_unlocked_parent_but_rebuild_locked_release() {
    for dependency_already_built in [false, true] {
        let mut harness = Harness::new("dependency-parent-fork");
        let dependency = harness.add_git(
            "org.example:dependency",
            "2.3.0-SNAPSHOT",
            None,
            None,
            Some("git --version"),
        );
        let middle = harness.add_git(
            "org.example:middle",
            "4.0.0-SNAPSHOT",
            Some("org.example:dependency\n"),
            None,
            Some("git --version"),
        );
        let root = harness.add_git(
            "org.example:root",
            "1.0.0-SNAPSHOT",
            Some("org.example:middle\n"),
            None,
            Some("git --version"),
        );
        harness.succeeds(&["fork", "org.example:root"]);
        harness.succeeds(&["build", "org.example:root"]);
        let old_root = git_bare(&root.bare, ["rev-parse", "release/1.0"]);
        let old_middle = git_bare(&middle.bare, ["rev-parse", "release/4.0"]);
        commit_to_remote_branch(
            &harness.temp.0,
            &dependency.bare,
            "release/2.3",
            "patch.txt",
            "dependency patch",
        );
        if dependency_already_built {
            harness.succeeds(&["build", "org.example:dependency"]);
        }
        let output = harness.succeeds(&["status", "org.example:root", "--show-done"]);
        assert!(output.contains("org.example:root [FORK]"), "{output}");
        assert!(output.contains("org.example:middle [FORK]"), "{output}");
        let locked = harness.succeeds(&["status", "org.example:root:1.0.0", "--show-done"]);
        assert!(
            locked.contains("org.example:root [BUILD_MDEPS]"),
            "{locked}"
        );
        assert!(
            locked.contains("org.example:middle [BUILD_MDEPS]"),
            "{locked}"
        );
        assert!(!locked.contains("[FORK]"), "{locked}");
        assert!(harness
            .fails(&["build", "org.example:root"])
            .contains("run `fork` first"));
        assert_eq!(git_bare(&root.bare, ["rev-parse", "release/1.0"]), old_root);
        assert_eq!(
            git_bare(&middle.bare, ["rev-parse", "release/4.0"]),
            old_middle
        );
        harness.succeeds(&["fork", "org.example:root"]);
        assert!(root.has_ref("refs/heads/release/1.1"));
        assert!(middle.has_ref("refs/heads/release/4.1"));
        // Repeating fork before build must not create yet another minor release.
        harness.succeeds(&["fork", "org.example:root"]);
        assert!(!root.has_ref("refs/heads/release/1.2"));
        harness.succeeds(&["build", "org.example:root"]);
        assert!(root.has_ref("refs/tags/1.1.0"));
        assert_eq!(git_bare(&root.bare, ["rev-parse", "release/1.0"]), old_root);
        assert_eq!(
            git_bare(&middle.bare, ["rev-parse", "release/4.0"]),
            old_middle
        );
        assert!(harness
            .succeeds(&["status", "org.example:root", "--show-done"])
            .contains("org.example:root [DONE]"));
    }
}

#[test]
fn dependency_changes_build_patch_when_parent_release_is_explicit() {
    let mut harness = Harness::new("locked-dependency-patch");
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
    commit_to_remote_branch(
        &harness.temp.0,
        &dependency.bare,
        "release/2.3",
        "patch.txt",
        "dependency patch",
    );
    harness.succeeds(&["build", "org.example:root:1.0.0"]);
    assert!(dependency.has_ref("refs/tags/2.3.1"));
    assert!(root.has_ref("refs/tags/1.0.1"));
    assert!(!root.has_ref("refs/heads/release/1.1"));
    assert_eq!(
        root.show("refs/tags/1.0.1", "mdeps"),
        "org.example:dependency:2.3.1"
    );
    assert!(harness
        .succeeds(&["status", "org.example:root:1.0.0", "--show-done"])
        .contains("org.example:root [DONE]"));
}

#[test]
fn dependency_changes_fork_svn_parent_without_parent_commits() {
    let mut harness = Harness::new("svn-dependency-parent-fork");
    let dependency = harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    let root = harness.add_svn(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency\n"),
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
    assert!(harness
        .succeeds(&["status", "org.example:root"])
        .contains("org.example:root [FORK]"));
    assert!(harness
        .fails(&["build", "org.example:root"])
        .contains("run `fork` first"));
    harness.succeeds(&["fork", "org.example:root"]);
    assert!(root.exists("branches/release/1.1"));
    assert_eq!(root.cat("branches/release/1.0/version"), "1.0.1");
    assert_eq!(root.cat("trunk/version"), "1.2.0-SNAPSHOT");
}

#[test]
fn status_prefers_a_locked_version_shared_elsewhere_in_the_tree() {
    let mut harness = Harness::new("status-shared-locked-version");
    harness.add_git(
        "org.example:shared",
        "1.5.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    let first = harness.add_git(
        "org.example:first",
        "1.0.0-SNAPSHOT",
        Some("org.example:shared:\n"),
        None,
        Some("git --version"),
    );
    let second = harness.add_git(
        "org.example:second",
        "1.0.0-SNAPSHOT",
        Some("org.example:shared:\n"),
        None,
        Some("git --version"),
    );

    harness.succeeds(&["fork", "org.example:shared"]);
    harness.succeeds(&["build", "org.example:shared"]);
    harness.succeeds(&["fork", "org.example:shared"]);
    harness.succeeds(&["fork", "org.example:first"]);
    harness.succeeds(&["fork", "org.example:second"]);
    commit_to_remote_branch(
        &harness.temp.0,
        &first.bare,
        "release/1.0",
        "mdeps",
        "org.example:shared:",
    );
    commit_to_remote_branch(
        &harness.temp.0,
        &second.bare,
        "release/1.0",
        "mdeps",
        "org.example:shared:1.5.0",
    );

    let output = harness.succeeds(&[
        "status",
        "org.example:first:1.0.0",
        "org.example:second:1.0.0",
        "--show-done",
    ]);

    assert_eq!(
        output.matches("org.example:shared [DONE]").count(),
        2,
        "{output}"
    );
    assert!(!output.contains("org.example:shared [BUILD]"), "{output}");
    assert!(!output.contains("org.example:shared [FORK]"), "{output}");

    commit_to_remote_branch(
        &harness.temp.0,
        &first.bare,
        "release/1.0",
        "mdeps",
        "org.example:shared:1.6.0",
    );
    let error = harness.fails(&[
        "status",
        "org.example:first:1.0.0",
        "org.example:second:1.0.0",
    ]);
    assert!(
        error
            .contains("conflicting locked release lines for `org.example:shared`: `1.6` and `1.5`"),
        "{error}"
    );
}

#[test]
fn status_rejects_an_unlocked_dependency_in_a_locked_release_tree() {
    let mut harness = Harness::new("status-unlocked-release-dependency");
    harness.add_git(
        "org.example:dependency",
        "2.3.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    let root = harness.add_git(
        "org.example:root",
        "1.0.0-SNAPSHOT",
        Some("org.example:dependency:\n"),
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:root"]);
    commit_to_remote_branch(
        &harness.temp.0,
        &root.bare,
        "release/1.0",
        "mdeps",
        "org.example:dependency:",
    );

    let error = harness.fails(&["status", "org.example:root:1.0.0"]);

    assert!(
        error.contains(
            "dependency `org.example:dependency` is not locked in release tree of `org.example:root`"
        ),
        "{error}"
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

    let output = harness.run(&["tag", "org.example:root", "--trace"]);
    assert_success(&["tag", "org.example:root", "--trace"], &output);
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(!stdout.contains("org.example:dependency"), "{stdout}");
    assert_eq!(
        stderr.matches("git fetch origin --prune --tags").count(),
        1,
        "{stderr}"
    );
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
fn monorepo_builds_checkout_only_the_component_and_ancestor_files() {
    let mut harness = Harness::new("sparse-builds");
    harness.add_monorepo(&[
        ("com.acme:api", "components/acme-api", "1.0.0-SNAPSHOT"),
        (
            "org.example:api",
            "components/example-api",
            "2.0.0-SNAPSHOT",
        ),
    ]);
    let seed = harness.temp.0.join("monorepo-seed");
    fs::write(seed.join("version"), "3.0.0-SNAPSHOT\n").unwrap();
    fs::write(seed.join("README.md"), "shared root file\n").unwrap();
    fs::write(
        seed.join("components/build.config"),
        "shared ancestor file\n",
    )
    .unwrap();
    fs::create_dir_all(seed.join("unrelated")).unwrap();
    fs::write(seed.join("unrelated/large.txt"), "unrelated project\n").unwrap();
    git(&seed, ["add", "."]);
    git(
        &seed,
        ["commit", "-m", "add shared files and root component"],
    );
    git(&seed, ["push"]);
    harness.rules.push(format!(
        "'org.example:root':\n  url: '{}'\n  type: git\n  developBranch: main\n  releaseCommand: 'git --version'\n",
        path(&harness.temp.0.join("monorepo.git"))
    ));
    harness.write_config();

    harness.succeeds(&[
        "fork",
        "com.acme:api",
        "org.example:api",
        "org.example:root",
    ]);
    harness.succeeds(&[
        "build",
        "com.acme:api",
        "org.example:api",
        "org.example:root",
    ]);

    let workspace = harness.work.join(".scm4j-releaser");
    for (component, version, included, excluded) in [
        ("com.acme_api", "1.0.0", "acme-api", "example-api"),
        ("org.example_api", "2.0.0", "example-api", "acme-api"),
    ] {
        let build = workspace
            .join("components")
            .join(component)
            .join("builds")
            .join(version);
        assert!(build
            .join(format!("components/{included}/content.txt"))
            .is_file());
        assert!(!build.join(format!("components/{excluded}")).exists());
        assert!(!build.join("unrelated").exists());
        assert!(build.join("README.md").is_file());
        assert!(build.join("components/build.config").is_file());
        assert_eq!(git(&build, ["status", "--porcelain"]), "");
        // Excluded paths remain tracked, rather than being deleted from Git.
        assert!(!git(
            &build,
            ["ls-files", &format!("components/{excluded}/version")]
        )
        .is_empty());
    }
    let root_build = workspace.join("components/org.example_root/builds/3.0.0");
    assert!(root_build.join("components/acme-api/content.txt").is_file());
    assert!(root_build
        .join("components/example-api/content.txt")
        .is_file());
    assert!(root_build.join("unrelated/large.txt").is_file());
    assert_eq!(git(&root_build, ["status", "--porcelain"]), "");
    let repositories: Vec<_> = fs::read_dir(workspace.join("repositories"))
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .collect();
    assert_eq!(repositories.len(), 1);
    assert!(repositories[0]
        .join("components/acme-api/content.txt")
        .is_file());
    assert!(repositories[0]
        .join("components/example-api/content.txt")
        .is_file());
    assert!(repositories[0].join("unrelated/large.txt").is_file());
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
fn git_tag_failure_warns_with_tag_and_commit_for_manual_repair() {
    let mut harness = Harness::new("git-tag-failure");
    let repository = harness.add_git(
        "org.example:service",
        "1.0.0-SNAPSHOT",
        None,
        None,
        Some("git --version"),
    );
    harness.succeeds(&["fork", "org.example:service"]);
    harness.succeeds(&["build", "org.example:service", "--delayed-tag"]);
    let commit = git_bare(&repository.bare, ["rev-parse", "refs/heads/release/1.0"]);
    let hook = repository.bare.join("hooks/pre-receive");
    fs::write(
        &hook,
        "#!/bin/sh\necho 'test: publication rejected' >&2\nexit 1\n",
    )
    .unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&hook, fs::Permissions::from_mode(0o755)).unwrap();
    }
    let error = harness.fails(&["tag", "org.example:service"]);
    assert!(
        error.contains("WARNING: release tagging did not complete"),
        "{error}"
    );
    assert!(
        error.contains("publishing the Git tag and release branch"),
        "{error}"
    );
    assert!(error.contains("Expected tag `1.0.0`"), "{error}");
    assert!(error.contains(&format!("Git commit `{commit}`")), "{error}");
    assert!(error.contains("repair them manually"), "{error}");
    assert!(error.contains("test: publication rejected"), "{error}");
    assert!(!repository.has_ref("refs/tags/1.0.0"));
    assert_eq!(
        repository.show("refs/heads/release/1.0", "version"),
        "1.0.0"
    );

    // The leftover local tag also produces repair context on a retry.
    let error = harness.fails(&["tag", "org.example:service"]);
    assert!(error.contains("creating the local Git tag"), "{error}");
    assert!(error.contains("Expected tag `1.0.0`"), "{error}");
    assert!(error.contains(&commit), "{error}");
    assert!(error.contains("already exists"), "{error}");
}

#[test]
fn svn_tag_failure_warns_with_tag_and_revision_for_manual_repair() {
    let mut harness = Harness::new("svn-tag-failure");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    harness.succeeds(&["fork", "org.example:legacy"]);
    let revision = successful_command(
        &harness.temp.0,
        "svn",
        [
            "info",
            "--show-item",
            "last-changed-revision",
            &format!("{}/branches/release/3.4", repository.url),
        ],
    );
    let hooks = harness.temp.0.join("legacy-svn/hooks");
    if cfg!(windows) {
        fs::write(
            hooks.join("pre-commit.bat"),
            "@echo off\r\necho test: commit rejected 1>&2\r\nexit /b 1\r\n",
        )
        .unwrap();
    } else {
        let hook = hooks.join("pre-commit");
        fs::write(
            &hook,
            "#!/bin/sh\necho 'test: commit rejected' >&2\nexit 1\n",
        )
        .unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&hook, fs::Permissions::from_mode(0o755)).unwrap();
        }
    }
    let error = harness.fails(&["build", "org.example:legacy"]);
    assert!(
        error.contains("WARNING: release tagging did not complete"),
        "{error}"
    );
    assert!(error.contains("creating the SVN tag"), "{error}");
    assert!(error.contains("Expected tag `tags/3.4.0`"), "{error}");
    assert!(
        error.contains(&format!("SVN revision `r{revision}`")),
        "{error}"
    );
    assert!(error.contains("branch `branches/release/3.4`"), "{error}");
    assert!(error.contains("repair them manually"), "{error}");
    assert!(error.contains("test: commit rejected"), "{error}");
    assert!(!repository.exists("tags/3.4.0"));
    assert_eq!(repository.cat("branches/release/3.4/version"), "3.4.0");
}

#[test]
fn git_manual_branch_uses_common_ancestor_instead_of_version_messages() {
    for subfolder in [None, Some("components/legacy")] {
        let mut harness = Harness::new("git-manual-boundary");
        let repository = harness.add_git(
            "org.example:legacy",
            "3.4.0-SNAPSHOT",
            None,
            subfolder,
            Some("git --version"),
        );
        let branch = subfolder.map_or("release/3.4", |_| "components/legacy/release/3.4");
        git_bare(&repository.bare, ["branch", branch, "main"]);
        let commit = |branch: &str, file: &str, content: &str, message: &str| {
            let file =
                subfolder.map_or_else(|| file.to_owned(), |folder| format!("{folder}/{file}"));
            commit_to_remote_branch_with_message(
                &harness.temp.0,
                &repository.bare,
                branch,
                &file,
                content,
                message,
            );
        };
        commit(branch, "version", "3.4.0", "manual release version");
        // The manual version bump has no scm-ver marker, and initial history is unmarked.
        commit(
            "main",
            "version",
            "3.5.0-SNAPSHOT",
            "manual version bump #scm-ignore",
        );
        commit(
            "main",
            "content.txt",
            "already merged",
            "#scm-ignore merged change",
        );
        commit("main", "content.txt", "formatting", "#scm-ver formatting");
        if subfolder.is_some() {
            commit_to_remote_branch_with_message(
                &harness.temp.0,
                &repository.bare,
                "main",
                "sibling.txt",
                "sibling feature",
                "feature",
            );
        }
        assert!(harness
            .succeeds(&["status", "org.example:legacy"])
            .contains("BUILD"));
        assert!(harness
            .succeeds(&["fork", "org.example:legacy"])
            .contains("No fork needed"));
        let next_branch = subfolder.map_or("release/3.5", |_| "components/legacy/release/3.5");
        assert!(!repository.has_ref(&format!("refs/heads/{next_branch}")));

        commit("main", "content.txt", "new feature", "feature");
        commit(
            "main",
            "version",
            "3.5.0-SNAPSHOT\n",
            "#scm-ver 3.5.0-SNAPSHOT",
        );
        commit(
            "main",
            "content.txt",
            "new feature with formatting",
            "#scm-ignore formatting",
        );
        // Neither marker may hide the earlier real change in the range.
        assert!(harness
            .succeeds(&["status", "org.example:legacy"])
            .contains("FORK"));
        harness.succeeds(&["fork", "org.example:legacy"]);
        assert!(repository.has_ref(&format!("refs/heads/{next_branch}")));
    }
}

#[test]
fn git_existing_current_release_reports_missing_develop_version_bump() {
    for subfolder in [None, Some("components/legacy")] {
        let mut harness = Harness::new("git-missing-bump");
        let repository = harness.add_git(
            "org.example:legacy",
            "3.4.0-SNAPSHOT",
            None,
            subfolder,
            Some("git --version"),
        );
        let prefix = subfolder.map_or(String::new(), |folder| format!("{folder}/"));
        let branch = format!("{prefix}release/3.4");
        git_bare(&repository.bare, ["branch", &branch, "main"]);
        // An older release exists too, so silently falling back to it would be possible.
        git_bare(
            &repository.bare,
            ["branch", &format!("{prefix}release/3.3"), "main"],
        );
        commit_to_remote_branch_with_message(
            &harness.temp.0,
            &repository.bare,
            &branch,
            &format!("{prefix}version"),
            "3.4.0",
            "manual release version",
        );
        let develop_before = git_bare(&repository.bare, ["rev-parse", "main"]);
        let release_before = git_bare(&repository.bare, ["rev-parse", &branch]);
        for command in ["status", "fork", "build"] {
            let output = harness.run(&[command, "org.example:legacy"]);
            assert!(
                !output.status.success(),
                "{command} must reject the unchanged develop version"
            );
            let stderr = String::from_utf8_lossy(&output.stderr);
            assert!(stderr.contains("has not advanced"), "{stderr}");
            assert!(stderr.contains(&branch), "{stderr}");
            assert!(stderr.contains("3.5.0-SNAPSHOT"), "{stderr}");
        }
        assert_eq!(
            git_bare(&repository.bare, ["rev-parse", "main"]),
            develop_before
        );
        assert_eq!(
            git_bare(&repository.bare, ["rev-parse", &branch]),
            release_before
        );
        assert!(git_bare(&repository.bare, ["tag", "--list"]).is_empty());
        assert!(harness
            .succeeds(&["status", "org.example:legacy:3.4.0"])
            .contains("BUILD"));
    }
}

#[test]
fn git_scm_ignore_commits_do_not_trigger_releases() {
    for subfolder in [None, Some("components/legacy")] {
        let mut harness = Harness::new("git-ignore");
        let repository = harness.add_git(
            "org.example:legacy",
            "3.4.0-SNAPSHOT",
            None,
            subfolder,
            Some("git --version"),
        );
        let component_file = |file: &str| {
            if let Some(folder) = subfolder {
                format!("{folder}/{file}")
            } else {
                file.to_owned()
            }
        };
        let commit = |branch: &str, file: &str, content: &str, message: &str| {
            commit_to_remote_branch_with_message(
                &harness.temp.0,
                &repository.bare,
                branch,
                &component_file(file),
                content,
                message,
            );
        };
        harness.succeeds(&["fork", "org.example:legacy"]);
        // Ignored changes between the tag and bump must not require a rebuild.
        let branch = if subfolder.is_some() {
            "components/legacy/release/3.4"
        } else {
            "release/3.4"
        };
        let tag = if subfolder.is_some() {
            "components/legacy/3.4.0"
        } else {
            "3.4.0"
        };
        git_bare(&repository.bare, ["tag", tag, branch]);
        commit(
            branch,
            "content.txt",
            "documentation",
            "docs\n\n#scm-ignore",
        );
        commit(branch, "version", "3.4.1", "#scm-ver 3.4.1");
        commit(
            branch,
            "content.txt",
            "formatting",
            "#scm-ignore formatting",
        );
        commit("main", "content.txt", "docs on develop", "#scm-ignore docs");
        assert!(harness
            .succeeds(&["status", "org.example:legacy", "--show-done"])
            .contains("DONE"));
        assert!(harness
            .succeeds(&["fork", "org.example:legacy"])
            .contains("No fork needed"));
        assert!(harness
            .succeeds(&["build", "org.example:legacy"])
            .contains("already built"));

        // Ignored commits cannot hide a real change; the next build includes them.
        commit(branch, "content.txt", "new feature", "feature");
        commit(
            branch,
            "content.txt",
            "new feature with formatting",
            "#scm-ignore formatting",
        );
        assert!(harness
            .succeeds(&["status", "org.example:legacy"])
            .contains("BUILD"));
        harness.succeeds(&["build", "org.example:legacy"]);
        let next_tag = if subfolder.is_some() {
            "refs/tags/components/legacy/3.4.1"
        } else {
            "refs/tags/3.4.1"
        };
        assert_eq!(
            repository.show(next_tag, "content.txt"),
            "new feature with formatting"
        );
        assert!(harness
            .succeeds(&["status", "org.example:legacy", "--show-done"])
            .contains("DONE"));
    }
}

#[test]
fn svn_scm_ignore_commits_do_not_trigger_releases() {
    let mut harness = Harness::new("svn-ignore");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    harness.succeeds(&["fork", "org.example:legacy"]);
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "copy",
            &format!("{}/branches/release/3.4", repository.url),
            &format!("{}/tags/3.4.0", repository.url),
            "-m",
            "3.4.0 release",
        ],
    );
    let checkout = harness.temp.0.join("release-checkout");
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "checkout",
            &format!("{}/branches/release/3.4", repository.url),
            path(&checkout).as_str(),
        ],
    );
    let commit = |file: &str, content: &str, message: &str| {
        fs::write(checkout.join(file), content).unwrap();
        successful_command(&checkout, "svn", ["commit", "-m", message]);
    };
    commit("content.txt", "documentation", "docs\n\n#scm-ignore");
    commit("version", "3.4.1", "#scm-ver 3.4.1");
    // More than one log page of ignored commits must still reach the version bump.
    for index in 0..33 {
        commit(
            "content.txt",
            &format!("formatting {index}"),
            "#scm-ignore formatting",
        );
    }
    let develop = harness.temp.0.join("legacy-svn-seed");
    successful_command(&develop, "svn", ["update"]);
    fs::write(develop.join("content.txt"), "develop docs").unwrap();
    successful_command(&develop, "svn", ["commit", "-m", "#scm-ignore docs"]);
    assert!(harness
        .succeeds(&["status", "org.example:legacy", "--show-done"])
        .contains("DONE"));
    assert!(harness
        .succeeds(&["fork", "org.example:legacy"])
        .contains("No fork needed"));
    assert!(harness
        .succeeds(&["build", "org.example:legacy"])
        .contains("already built"));

    commit("content.txt", "new feature", "feature");
    commit(
        "content.txt",
        "new feature with formatting",
        "#scm-ignore formatting",
    );
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("BUILD"));
    harness.succeeds(&["build", "org.example:legacy"]);
    assert_eq!(
        repository.cat("tags/3.4.1/content.txt"),
        "new feature with formatting"
    );
    assert!(harness
        .succeeds(&["status", "org.example:legacy", "--show-done"])
        .contains("DONE"));
}

#[test]
fn svn_manual_branch_uses_copy_boundary_instead_of_version_messages() {
    let mut harness = Harness::new("svn-manual-boundary");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    let develop = harness.temp.0.join("legacy-svn-seed");
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "copy",
            "--parents",
            &format!("{}/trunk", repository.url),
            &format!("{}/branches/release/3.4", repository.url),
            "-m",
            "manual release branch",
        ],
    );
    // There is no scm-ver marker anywhere in this legacy history.
    fs::write(develop.join("version"), "3.5.0-SNAPSHOT").unwrap();
    successful_command(
        &develop,
        "svn",
        ["commit", "-m", "manual version bump #scm-ignore"],
    );
    for index in 0..34 {
        fs::write(
            develop.join("content.txt"),
            format!("merged change {index}"),
        )
        .unwrap();
        successful_command(
            &develop,
            "svn",
            ["commit", "-m", "already merged #scm-ignore"],
        );
    }
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("BUILD"));
    assert!(harness
        .succeeds(&["fork", "org.example:legacy"])
        .contains("No fork needed"));
    assert!(!repository.exists("branches/release/3.5"));

    fs::write(develop.join("content.txt"), "new feature").unwrap();
    successful_command(&develop, "svn", ["commit", "-m", "feature"]);
    fs::write(develop.join("version"), "3.5.0-SNAPSHOT\n").unwrap();
    successful_command(&develop, "svn", ["commit", "-m", "#scm-ver 3.5.0-SNAPSHOT"]);
    // A newer version marker must not hide an earlier real change.
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("FORK"));
    harness.succeeds(&["fork", "org.example:legacy"]);
    assert!(repository.exists("branches/release/3.5"));
}

#[test]
fn svn_existing_current_release_reports_missing_develop_version_bump() {
    let mut harness = Harness::new("svn-missing-bump");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "copy",
            "--parents",
            &format!("{}/trunk", repository.url),
            &format!("{}/branches/release/3.4", repository.url),
            "-m",
            "manual release branch",
        ],
    );
    let release = harness.temp.0.join("manual-release");
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "checkout",
            &format!("{}/branches/release/3.4", repository.url),
            path(&release).as_str(),
        ],
    );
    fs::write(release.join("version"), "3.4.0").unwrap();
    successful_command(&release, "svn", ["commit", "-m", "manual release version"]);
    for command in ["status", "fork", "build"] {
        let output = harness.run(&[command, "org.example:legacy"]);
        assert!(
            !output.status.success(),
            "{command} must reject the unchanged develop version"
        );
        let stderr = String::from_utf8_lossy(&output.stderr);
        assert!(stderr.contains("has not advanced"), "{stderr}");
        assert!(stderr.contains("branches/release/3.4"), "{stderr}");
        assert!(stderr.contains("3.5.0-SNAPSHOT"), "{stderr}");
    }
    assert!(!repository.exists("tags/3.4.0"));
    assert_eq!(repository.cat("trunk/version"), "3.4.0-SNAPSHOT");
    assert_eq!(repository.cat("branches/release/3.4/version"), "3.4.0");
    // An explicitly selected release line remains usable independently of trunk's version.
    assert!(harness
        .succeeds(&["status", "org.example:legacy:3.4.0"])
        .contains("BUILD"));
}

#[test]
fn svn_branch_point_uses_copied_revision_not_copy_commit_revision() {
    let mut harness = Harness::new("svn-old-copy");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    let develop = harness.temp.0.join("legacy-svn-seed");
    let copied = successful_command(
        &harness.temp.0,
        "svn",
        [
            "info",
            "--show-item",
            "last-changed-revision",
            &format!("{}/trunk", repository.url),
        ],
    );
    fs::write(develop.join("content.txt"), "not included in release").unwrap();
    successful_command(
        &develop,
        "svn",
        ["commit", "-m", "feature before copy commit"],
    );
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "copy",
            "--parents",
            "-r",
            copied.trim(),
            &format!("{}/trunk", repository.url),
            &format!("{}/branches/release/3.4", repository.url),
            "-m",
            "copy earlier revision",
        ],
    );
    fs::write(develop.join("version"), "3.5.0-SNAPSHOT").unwrap();
    successful_command(&develop, "svn", ["commit", "-m", "#scm-ver 3.5.0-SNAPSHOT"]);
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("FORK"));
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
    assert!(harness
        .succeeds(&["status", "org.example:legacy", "--show-done"])
        .contains("DONE"));
}

#[test]
fn svn_changes_between_tag_and_version_bump_require_build() {
    let mut harness = Harness::new("svn-intervening-commit");
    let repository = harness.add_svn("org.example:legacy", "3.4.0-SNAPSHOT", None);
    harness.succeeds(&["fork", "org.example:legacy"]);
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "copy",
            &format!("{}/branches/release/3.4", repository.url),
            &format!("{}/tags/3.4.0", repository.url),
            "-m",
            "3.4.0 release",
        ],
    );
    let checkout = harness.temp.0.join("release-checkout");
    successful_command(
        &harness.temp.0,
        "svn",
        [
            "checkout",
            &format!("{}/branches/release/3.4", repository.url),
            path(&checkout).as_str(),
        ],
    );

    // A tag without a version bump is not DONE, but rebuilding cannot reuse it.
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("BUILD"));
    assert!(harness
        .fails(&["build", "org.example:legacy"])
        .contains("already exists"));

    fs::write(checkout.join("content.txt"), "intervening change\n").unwrap();
    successful_command(&checkout, "svn", ["commit", "-m", "feature after tag"]);
    // The latest commit has no service marker: the new change needs a build.
    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("BUILD"));
    fs::write(checkout.join("version"), "3.4.1").unwrap();
    successful_command(&checkout, "svn", ["commit", "-m", "#scm-ver 3.4.1"]);

    assert!(harness
        .succeeds(&["status", "org.example:legacy"])
        .contains("BUILD"));
    harness.succeeds(&["build", "org.example:legacy"]);
    assert_eq!(
        repository.cat("tags/3.4.1/content.txt"),
        "intervening change"
    );
    assert!(harness
        .succeeds(&["status", "org.example:legacy", "--show-done"])
        .contains("DONE"));
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
    commit_to_remote_branch_with_message(root, bare, branch, file, content, "patch feature");
}

fn commit_to_remote_branch_with_message(
    root: &Path,
    bare: &Path,
    branch: &str,
    file: &str,
    content: &str,
    message: &str,
) {
    let repository = bare.file_stem().unwrap().to_string_lossy();
    let sequence = NEXT_TEMP.fetch_add(1, Ordering::Relaxed);
    let clone = root.join(format!(
        "patch-{repository}-{}-{sequence}",
        branch.replace('/', "-")
    ));
    git(root, ["clone", path(bare).as_str(), path(&clone).as_str()]);
    git(&clone, ["config", "user.name", "scm4j test"]);
    git(&clone, ["config", "user.email", "scm4j@example.test"]);
    git(&clone, ["checkout", branch]);
    fs::write(clone.join(file), content).unwrap();
    git(&clone, ["add", file]);
    git(&clone, ["commit", "-m", message]);
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
