mod config;
mod git;
mod version;

use config::Config;
use git::{run_in, Git};
use std::env;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use version::Version;

const CONFIG_FILE: &str = "scm4j-releaser.conf";
const WORK_DIR: &str = ".scm4j-releaser";
const LOCK_FILE: &str = ".scm4j-releaser.lock";

fn main() {
    if let Err(error) = execute() {
        eprintln!("EXECUTION FAILED: {error}");
        std::process::exit(1);
    }
}

fn execute() -> Result<(), String> {
    let args: Vec<String> = env::args().skip(1).collect();
    let command = args.first().map(String::as_str).unwrap_or("help");
    if matches!(command, "help" | "--help" | "-h") {
        print_help();
        return Ok(());
    }
    if args.len() != 1 && !(args.len() == 2 && args[1] == "--delayed-tag" && command == "build") {
        return Err("invalid arguments; use --help".to_owned());
    }

    let executable = env::current_exe().map_err(|e| format!("cannot locate executable: {e}"))?;
    let home = executable
        .parent()
        .ok_or_else(|| "executable has no parent directory".to_owned())?
        .to_owned();
    let lock_path = home.join(LOCK_FILE);
    if command == "unlock" {
        if lock_path.exists() {
            fs::remove_file(&lock_path)
                .map_err(|e| format!("cannot remove {}: {e}", lock_path.display()))?;
            println!("Removed {}", lock_path.display());
        } else {
            println!("No lock exists");
        }
        return Ok(());
    }
    let _lock = Lock::acquire(&lock_path)?;

    if command == "init" {
        return init(&home);
    }
    let config = Config::load(&home.join(CONFIG_FILE))?;
    let work = home.join(WORK_DIR);
    fs::create_dir_all(&work).map_err(|e| format!("cannot create {}: {e}", work.display()))?;
    let git = prepare_repository(&config, &work)?;
    let develop = discover_develop_branch(&git, &config)?;

    match command {
        "status" => status(&git, &config, &develop),
        "fork" => fork(&git, &config, &develop),
        "build" => build(&git, &config, &work, args.get(1).is_some()),
        "tag" => tag(&git, &config, &work),
        _ => Err(format!("unknown command `{command}`; use --help")),
    }
}

fn print_help() {
    println!("scm4j-releaser - single-component Git release tool\n\n\
Usage:\n  scm4j-releaser init\n  scm4j-releaser status\n  scm4j-releaser fork\n  scm4j-releaser build [--delayed-tag]\n  scm4j-releaser tag\n  scm4j-releaser unlock\n\n\
Configuration and all working data are stored beside the executable.\n\
Only run `unlock` after making sure no other releaser process is active.");
}

fn init(home: &Path) -> Result<(), String> {
    let path = home.join(CONFIG_FILE);
    if path.exists() {
        return Err(format!("{} already exists", path.display()));
    }
    fs::write(&path, config::TEMPLATE)
        .map_err(|e| format!("cannot create {}: {e}", path.display()))?;
    println!("Created {}", path.display());
    Ok(())
}

fn prepare_repository(config: &Config, work: &Path) -> Result<Git, String> {
    let directory = work.join("repository");
    let git = if directory.join(".git").is_dir() {
        let git = Git::new(directory);
        let origin = git.run(["remote", "get-url", "origin"])?;
        if origin != config.repository {
            return Err(format!("managed clone belongs to `{origin}`, configured repository is `{}`; remove {} to re-clone",
                config.repository, git_path(work).display()));
        }
        git
    } else {
        Git::clone(&config.repository, &directory)?
    };
    git.fetch()?;
    Ok(git)
}

fn git_path(work: &Path) -> PathBuf {
    work.join("repository")
}

fn discover_develop_branch(git: &Git, config: &Config) -> Result<String, String> {
    if let Some(branch) = &config.develop_branch {
        return Ok(branch.clone());
    }
    let symbolic = git.run(["symbolic-ref", "--short", "refs/remotes/origin/HEAD"])?;
    symbolic
        .strip_prefix("origin/")
        .map(str::to_owned)
        .ok_or_else(|| "origin/HEAD does not name a remote branch; set develop_branch".to_owned())
}

fn read_version(git: &Git, revision: &str, config: &Config) -> Result<Version, String> {
    git.show_file(revision, &config.version_file)?.parse()
}

fn release_branches(git: &Git, config: &Config) -> Result<Vec<(Version, String)>, String> {
    let refs = git.run([
        "for-each-ref",
        "--format=%(refname:strip=3)",
        "refs/remotes/origin",
    ])?;
    let mut branches = Vec::new();
    for branch in refs.lines() {
        if let Some(line) = branch.strip_prefix(&config.release_branch_prefix) {
            let synthetic = format!("{line}.0");
            if let Ok(version) = synthetic.parse::<Version>() {
                branches.push((version, branch.to_owned()));
            }
        }
    }
    branches.sort_by(|a, b| a.0.cmp(&b.0));
    Ok(branches)
}

fn latest_release(git: &Git, config: &Config) -> Result<(Version, String), String> {
    release_branches(git, config)?
        .pop()
        .ok_or_else(|| "no release branch; run `fork`".to_owned())
}

fn status(git: &Git, config: &Config, develop: &str) -> Result<(), String> {
    let dev_ref = format!("origin/{develop}");
    let dev_version = read_version(git, &dev_ref, config)?;
    if !dev_version.snapshot {
        return Err(format!("develop version must be a SNAPSHOT: {dev_version}"));
    }
    let expected_branch = format!(
        "{}{}",
        config.release_branch_prefix,
        dev_version.release_line()
    );
    let last_message = git.run(["log", "-1", "--format=%B", &dev_ref])?;
    let needs_fork = !git.succeeds([
        "show-ref",
        "--verify",
        "--quiet",
        &format!("refs/remotes/origin/{expected_branch}"),
    ]) && !last_message.contains("#scm-ver");
    println!("develop: {develop} ({dev_version})");
    if needs_fork {
        println!("next action: FORK -> {expected_branch}");
        return Ok(());
    }
    match latest_release(git, config) {
        Ok((_, branch)) => {
            let version = read_version(git, &format!("origin/{branch}"), config)?;
            let head = git.run(["rev-parse", &format!("origin/{branch}")])?;
            println!("release: {branch} ({version})");
            println!(
                "next action: {}",
                if release_is_done(git, &version, &head)? {
                    "DONE"
                } else {
                    "BUILD"
                }
            );
        }
        Err(_) => println!("next action: FORK"),
    }
    Ok(())
}

fn fork(git: &Git, config: &Config, develop: &str) -> Result<(), String> {
    let dev_ref = format!("origin/{develop}");
    let version = read_version(git, &dev_ref, config)?;
    if !version.snapshot {
        return Err(format!(
            "develop version must end with -SNAPSHOT: {version}"
        ));
    }
    let release = version.release_zero();
    let branch = format!("{}{}", config.release_branch_prefix, release.release_line());
    if git.succeeds([
        "show-ref",
        "--verify",
        "--quiet",
        &format!("refs/remotes/origin/{branch}"),
    ]) {
        return Err(format!("release branch `{branch}` already exists"));
    }

    git.checkout_remote(develop)?;
    // The remote branch was proven absent above. -B also recovers cleanly from a
    // local branch left by an earlier failed atomic push.
    git.run(["checkout", "-B", &branch])?;
    write_and_commit_version(git, config, &release)?;
    git.checkout_remote(develop)?;
    let next = version.next_minor_snapshot();
    write_and_commit_version(git, config, &next)?;
    if config.push {
        git.run([
            "push",
            "--atomic",
            "origin",
            &format!("{develop}:{develop}"),
            &format!("{branch}:{branch}"),
        ])?;
    }
    println!(
        "Forked {branch} at {release}; develop is now {next}{}",
        local_suffix(config)
    );
    Ok(())
}

fn write_and_commit_version(git: &Git, config: &Config, version: &Version) -> Result<(), String> {
    let root = PathBuf::from(git.run(["rev-parse", "--show-toplevel"])?);
    let path = root.join(&config.version_file);
    fs::write(&path, format!("{version}\n"))
        .map_err(|e| format!("cannot write {}: {e}", path.display()))?;
    git.run(["add", "--", &config.version_file])?;
    git.run(["commit", "-m", &format!("#scm-ver {version}")])?;
    Ok(())
}

fn build(git: &Git, config: &Config, work: &Path, delayed: bool) -> Result<(), String> {
    let (_, branch) = latest_release(git, config)?;
    git.checkout_remote(&branch)?;
    let version = read_version(git, "HEAD", config)?;
    if version.snapshot {
        return Err(format!(
            "release branch contains snapshot version: {version}"
        ));
    }
    let commit = git.run(["rev-parse", "HEAD"])?;
    if release_is_done(git, &version, &commit)? {
        return Err(format!(
            "{branch} is already built; add a release commit before building another patch"
        ));
    }
    if git.succeeds([
        "rev-parse",
        "--verify",
        "--quiet",
        &format!("refs/tags/{version}"),
    ]) {
        return Err(format!("tag `{version}` already exists"));
    }
    if delayed && work.join("delayed-tag").exists() {
        return Err("a delayed tag is already pending; run `tag` first".to_owned());
    }
    run_build(git, config, work, &branch, &version, &commit)?;
    if delayed {
        let state = format!("branch={branch}\nversion={version}\ncommit={commit}\n");
        fs::write(work.join("delayed-tag"), state)
            .map_err(|e| format!("cannot save delayed tag: {e}"))?;
        println!("Built {version}; tag for {commit} was delayed");
    } else {
        tag_and_bump(git, config, &branch, &version, &commit)?;
        println!("Built and tagged {version}{}", local_suffix(config));
    }
    Ok(())
}

fn release_is_done(git: &Git, version: &Version, head: &str) -> Result<bool, String> {
    let Some(previous) = version.previous_patch() else {
        return Ok(false);
    };
    let message = git.run(["log", "-1", "--format=%B", head])?;
    if !message.contains("#scm-ver")
        || !git.succeeds([
            "rev-parse",
            "--verify",
            "--quiet",
            &format!("refs/tags/{previous}"),
        ])
    {
        return Ok(false);
    }
    let parent = git.run(["rev-parse", &format!("{head}^")])?;
    let tagged = git.run(["rev-list", "-n", "1", &format!("refs/tags/{previous}")])?;
    Ok(parent == tagged)
}

fn run_build(
    git: &Git,
    config: &Config,
    work: &Path,
    branch: &str,
    version: &Version,
    commit: &str,
) -> Result<(), String> {
    let builds = work.join("builds");
    fs::create_dir_all(&builds).map_err(|e| format!("cannot create build directory: {e}"))?;
    let directory = builds.join(version.to_string());
    if directory.exists() {
        fs::remove_dir_all(&directory)
            .map_err(|e| format!("cannot clear {}: {e}", directory.display()))?;
    }
    let source = git.run(["rev-parse", "--show-toplevel"])?;
    let directory_name = directory
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| "invalid build directory name".to_owned())?;
    run_in(
        &builds,
        "git",
        ["clone", "--no-checkout", &source, directory_name],
    )?;
    run_in(&directory, "git", ["checkout", "--detach", commit])?;
    let mut command = if cfg!(windows) {
        let mut c = Command::new("cmd");
        c.args(["/D", "/S", "/C", &config.build_command]);
        c
    } else {
        let mut c = Command::new("sh");
        c.args(["-c", &config.build_command]);
        c
    };
    let status = command
        .current_dir(&directory)
        .env("GIT_COMMIT", commit)
        .env("GIT_BRANCH", branch)
        .env("GIT_URL", &config.repository)
        .status()
        .map_err(|e| format!("cannot start build command: {e}"))?;
    if !status.success() {
        return Err(format!("build command failed with {status}"));
    }
    Ok(())
}

fn tag(git: &Git, config: &Config, work: &Path) -> Result<(), String> {
    let path = work.join("delayed-tag");
    let state =
        fs::read_to_string(&path).map_err(|e| format!("cannot read delayed tag state: {e}"))?;
    let get = |name: &str| {
        state
            .lines()
            .find_map(|line| line.strip_prefix(&format!("{name}=")))
            .map(str::to_owned)
            .ok_or_else(|| format!("invalid delayed tag state: missing {name}"))
    };
    let branch = get("branch")?;
    let version: Version = get("version")?.parse()?;
    let commit = get("commit")?;
    let remote_head = git.run(["rev-parse", &format!("origin/{branch}")])?;
    if remote_head != commit {
        return Err(format!(
            "{branch} advanced from delayed commit {commit} to {remote_head}"
        ));
    }
    git.checkout_remote(&branch)?;
    tag_and_bump(git, config, &branch, &version, &commit)?;
    fs::remove_file(path).map_err(|e| format!("cannot remove delayed tag state: {e}"))?;
    println!("Applied delayed tag {version}{}", local_suffix(config));
    Ok(())
}

fn tag_and_bump(
    git: &Git,
    config: &Config,
    branch: &str,
    version: &Version,
    commit: &str,
) -> Result<(), String> {
    git.run([
        "tag",
        "-a",
        &version.to_string(),
        "-m",
        &format!("{version} release"),
        commit,
    ])?;
    let next = version.next_patch();
    write_and_commit_version(git, config, &next)?;
    if config.push {
        git.run([
            "push",
            "--atomic",
            "origin",
            &format!("{branch}:{branch}"),
            &format!("refs/tags/{version}"),
        ])?;
    }
    Ok(())
}

fn local_suffix(config: &Config) -> &'static str {
    if config.push {
        ""
    } else {
        " (local only: push=false)"
    }
}

struct Lock {
    path: PathBuf,
}

impl Lock {
    fn acquire(path: &Path) -> Result<Self, String> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(path)
            .map_err(|e| {
                format!(
                    "another releaser may be running ({}: {e}); use `unlock` only for a stale lock",
                    path.display()
                )
            })?;
        writeln!(file, "pid={}", std::process::id())
            .map_err(|e| format!("cannot write lock: {e}"))?;
        Ok(Self {
            path: path.to_owned(),
        })
    }
}

impl Drop for Lock {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
    }
}
