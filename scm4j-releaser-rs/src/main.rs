mod catalog;
mod config;
mod git;
mod svn;
mod version;

use config::{Config, ScmType};
use git::Git;
use std::collections::HashSet;
use std::env;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use version::Version;

const WORK_DIR: &str = ".scm4j-releaser";
const LOCK_FILE: &str = ".scm4j-releaser.lock";

fn main() {
    if env::var_os("SCM4J_ASKPASS").is_some() {
        let prompt = env::args().nth(1).unwrap_or_default().to_ascii_lowercase();
        let variable = if prompt.contains("username") {
            "SCM4J_ASKPASS_USERNAME"
        } else {
            "SCM4J_ASKPASS_PASSWORD"
        };
        println!("{}", env::var(variable).unwrap_or_default());
        return;
    }
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
    let delayed = args.iter().skip(1).any(|arg| arg == "--delayed-tag");
    if delayed && command != "build" {
        return Err("--delayed-tag is valid for build only".to_owned());
    }
    if let Some(option) = args
        .iter()
        .skip(1)
        .find(|arg| arg.starts_with('-') && *arg != "--delayed-tag")
    {
        return Err(format!("unknown option `{option}`"));
    }
    let components: Vec<_> = args
        .iter()
        .skip(1)
        .filter(|arg| !arg.starts_with('-'))
        .collect();

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
    let work = home.join(WORK_DIR);
    fs::create_dir_all(&work).map_err(|e| format!("cannot create {}: {e}", work.display()))?;
    if components.is_empty() {
        return Err("component coordinates are required; use group:artifact".to_owned());
    }

    let catalog = Catalog::load(&home)?;
    let configs = resolve_component_graph(&catalog, &components, &work)?;
    for config in configs {
        let component_work = work.join("components").join(safe_name(&config.component));
        fs::create_dir_all(&component_work)
            .map_err(|e| format!("cannot create {}: {e}", component_work.display()))?;
        println!("=== {} ===", display_component(&config));
        if command == "tag" && !component_work.join("delayed-tag").is_file() {
            println!("No delayed tag for {}", config.component);
            continue;
        }
        if command == "build" {
            lock_git_mdeps(&catalog, &config, &work)?;
        }
        execute_config(command, &config, &work, &component_work, delayed)?;
        if command == "fork" {
            lock_git_mdeps(&catalog, &config, &work)?;
        }
    }
    Ok(())
}

fn resolve_component_graph(
    catalog: &Catalog,
    roots: &[&String],
    shared_work: &Path,
) -> Result<Vec<Config>, String> {
    let mut visiting = HashSet::new();
    let mut completed = HashSet::new();
    let mut result = Vec::new();
    for root in roots {
        visit_component(
            catalog,
            root,
            shared_work,
            &mut visiting,
            &mut completed,
            &mut result,
        )?;
    }
    Ok(result)
}

fn visit_component(
    catalog: &Catalog,
    coordinates: &str,
    shared_work: &Path,
    visiting: &mut HashSet<String>,
    completed: &mut HashSet<String>,
    result: &mut Vec<Config>,
) -> Result<(), String> {
    let config = catalog.resolve(coordinates)?;
    if completed.contains(&config.component) {
        return Ok(());
    }
    if !visiting.insert(config.component.clone()) {
        return Err(format!("cyclic mdeps dependency at `{}`", config.component));
    }
    let mdeps = read_develop_mdeps(&config, shared_work)?;
    for dependency in parse_mdeps(&mdeps) {
        visit_component(
            catalog,
            &dependency,
            shared_work,
            visiting,
            completed,
            result,
        )?;
    }
    visiting.remove(&config.component);
    completed.insert(config.component.clone());
    result.push(config);
    Ok(())
}

fn read_develop_mdeps(config: &Config, shared_work: &Path) -> Result<String, String> {
    match config.scm_type {
        ScmType::Git => {
            let git = prepare_repository(config, shared_work)?;
            let revision = match &config.release_line {
                Some(line) => format!("origin/{}", release_branch_name_for_line(config, line)),
                None => format!("origin/{}", discover_develop_branch(&git, config)?),
            };
            let path = component_path(config, "mdeps");
            if git.succeeds(["cat-file", "-e", &format!("{revision}:{path}")]) {
                git.show_file(&revision, &path)
            } else {
                Ok(String::new())
            }
        }
        ScmType::Svn => svn::read_develop_mdeps(config, shared_work),
    }
}

fn parse_mdeps(content: &str) -> Vec<String> {
    content
        .lines()
        .filter_map(|line| {
            let value = line.split('#').next().unwrap_or_default().trim();
            (!value.is_empty()).then(|| value.to_owned())
        })
        .collect()
}

fn replace_coordinate_version(coordinates: &str, version: &str) -> Result<String, String> {
    let extension_pos = coordinates.find('@').unwrap_or(coordinates.len());
    let base = &coordinates[..extension_pos];
    let extension = &coordinates[extension_pos..];
    let first_colon = base
        .find(':')
        .ok_or_else(|| format!("invalid component coordinates `{coordinates}`"))?;
    let second_colon = base[first_colon + 1..]
        .find(':')
        .map(|index| first_colon + 1 + index);
    let (name, classifier) = match second_colon {
        Some(second_colon) => {
            let version_and_classifier = &base[second_colon + 1..];
            let classifier = version_and_classifier
                .find(':')
                .map_or("", |index| &version_and_classifier[index..]);
            (&base[..second_colon], classifier)
        }
        None => (base, ""),
    };
    Ok(format!("{name}:{version}{classifier}{extension}"))
}

fn lock_git_mdeps(catalog: &Catalog, config: &Config, shared_work: &Path) -> Result<(), String> {
    if config.scm_type != ScmType::Git {
        return Ok(());
    }
    let git = prepare_repository(config, shared_work)?;
    let Ok((_, branch)) = latest_release(&git, config) else {
        return Ok(());
    };
    let revision = format!("origin/{branch}");
    let path = component_path(config, "mdeps");
    if !git.succeeds(["cat-file", "-e", &format!("{revision}:{path}")]) {
        return Ok(());
    }
    let original = git.show_file(&revision, &path)?;
    let mut changed = false;
    let mut output = Vec::new();
    for line in original.lines() {
        let (value, comment) = match line.split_once('#') {
            Some((value, comment)) => (value.trim(), Some(comment)),
            None => (line.trim(), None),
        };
        if value.is_empty() {
            output.push(line.to_owned());
            continue;
        }
        let dependency = catalog.resolve(value)?;
        if dependency.scm_type != ScmType::Git {
            return Err(format!(
                "Git component `{}` cannot lock non-Git dependency `{}`",
                config.component, dependency.component
            ));
        }
        let dependency_git = prepare_repository(&dependency, shared_work)?;
        let (_, dependency_branch) = latest_release(&dependency_git, &dependency)?;
        let dependency_revision = format!("origin/{dependency_branch}");
        let current = read_version(&dependency_git, &dependency_revision, &dependency)?;
        let dependency_head = dependency_git.run(["rev-parse", &dependency_revision])?;
        let locked = if release_is_done(&dependency_git, &dependency, &current, &dependency_head)? {
            current.previous_patch().unwrap_or(current)
        } else {
            current
        };
        let coords = replace_coordinate_version(value, &locked.to_string())?;
        let replacement = match comment {
            Some(comment) => format!("{coords} #{comment}"),
            None => coords,
        };
        changed |= replacement != line;
        output.push(replacement);
    }
    if !changed {
        return Ok(());
    }
    git.checkout_remote(&branch)?;
    let root = PathBuf::from(git.run(["rev-parse", "--show-toplevel"])?);
    let file = root.join(&path);
    fs::write(&file, format!("{}\n", output.join("\n")))
        .map_err(|e| format!("cannot write {}: {e}", file.display()))?;
    git.run(["add", "--", &path])?;
    git.run(["commit", "-m", "#scm-mdeps"])?;
    git.run(["push", "origin", &format!("{branch}:{branch}")])?;
    println!("Locked mdeps in {branch}");
    Ok(())
}

fn execute_config(
    command: &str,
    config: &Config,
    shared_work: &Path,
    component_work: &Path,
    delayed: bool,
) -> Result<(), String> {
    match config.scm_type {
        ScmType::Git => {
            let git = prepare_repository(config, shared_work)?;
            let develop = discover_develop_branch(&git, config)?;
            match command {
                "status" => status(&git, config, &develop),
                "fork" => fork(&git, config, &develop),
                "build" => build(&git, config, component_work, delayed),
                "tag" => tag(&git, config, component_work),
                _ => Err(format!("unknown command `{command}`; use --help")),
            }
        }
        ScmType::Svn => svn::execute(command, config, component_work, delayed),
    }
}

fn print_help() {
    println!("scm4j-releaser - multi-component Git/SVN release tool\n\n\
Usage:\n  scm4j-releaser init\n  scm4j-releaser status group:artifact [...]\n  scm4j-releaser fork group:artifact [...]\n  scm4j-releaser build group:artifact [...] [--delayed-tag]\n  scm4j-releaser tag group:artifact [...]\n  scm4j-releaser unlock\n\n\
Configuration and all working data are stored beside the executable.\n\
Only run `unlock` after making sure no other releaser process is active.");
}

fn init(home: &Path) -> Result<(), String> {
    let templates = [
        ("cc.yml", catalog::CC_TEMPLATE),
        ("cc", catalog::CC_LIST_TEMPLATE),
        ("credentials.yml", catalog::CREDENTIALS_TEMPLATE),
    ];
    let mut created = 0;
    for (name, content) in templates {
        let path = home.join(name);
        if path.exists() {
            continue;
        }
        fs::write(&path, content).map_err(|e| format!("cannot create {}: {e}", path.display()))?;
        println!("Created {}", path.display());
        created += 1;
    }
    if created == 0 {
        println!("Configuration files already exist");
    }
    Ok(())
}

fn prepare_repository(config: &Config, work: &Path) -> Result<Git, String> {
    let repositories = work.join("repositories");
    let directory = repositories.join(repository_key(&config.repository));
    fs::create_dir_all(
        directory
            .parent()
            .expect("repository directory has a parent"),
    )
    .map_err(|e| format!("cannot create repository workspace: {e}"))?;
    let git = if directory.join(".git").is_dir() {
        let git = Git::new(directory, config.username.clone(), config.password.clone());
        let origin = git.run(["remote", "get-url", "origin"])?;
        if origin != config.repository {
            return Err(format!("managed clone belongs to `{origin}`, configured repository is `{}`; remove {} to re-clone",
                config.repository, git.directory().display()));
        }
        git
    } else {
        Git::clone(
            &config.repository,
            &directory,
            config.username.clone(),
            config.password.clone(),
        )?
    };
    git.fetch()?;
    Ok(git)
}

fn repository_key(repository: &str) -> String {
    let normalized = repository.trim_end_matches(['/', '\\']);
    let repository_name = normalized
        .rsplit(['/', '\\', ':'])
        .next()
        .unwrap_or_default();
    let repository_name = repository_name
        .strip_suffix(".git")
        .unwrap_or(repository_name);
    let repository_name: String = safe_name(repository_name).chars().take(48).collect();
    let repository_name = repository_name.trim_matches('_');
    let repository_name = if repository_name.is_empty() {
        "repository"
    } else {
        repository_name
    };
    format!("{repository_name}-{}", repository_hash(repository))
}

fn repository_hash(repository: &str) -> String {
    let mut hash = 0xcbf29ce484222325_u64;
    for byte in repository.as_bytes() {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    format!("{hash:016x}")
}

fn safe_name(value: &str) -> String {
    value
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || matches!(ch, '.' | '-') {
                ch
            } else {
                '_'
            }
        })
        .collect()
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
    git.show_file(revision, &component_path(config, &config.version_file))?
        .parse()
}

fn component_path(config: &Config, relative: &str) -> String {
    if config.subfolder.is_empty() {
        relative.to_owned()
    } else {
        format!("{}/{relative}", config.subfolder)
    }
}

fn component_log(
    git: &Git,
    revision: &str,
    config: &Config,
    count: usize,
    format: &str,
) -> Result<String, String> {
    if config.subfolder.is_empty() {
        git.run([
            "log",
            &format!("-{count}"),
            &format!("--format={format}"),
            revision,
        ])
    } else {
        git.run([
            "log",
            &format!("-{count}"),
            &format!("--format={format}"),
            revision,
            "--",
            &config.subfolder,
        ])
    }
}

fn component_head(git: &Git, revision: &str, config: &Config) -> Result<String, String> {
    let commit = component_log(git, revision, config, 1, "%H")?;
    if commit.is_empty() {
        Err(format!(
            "no commits found for component `{}`",
            config.subfolder
        ))
    } else {
        Ok(commit)
    }
}

fn latest_release(git: &Git, config: &Config) -> Result<(Version, String), String> {
    let release = match &config.release_line {
        Some(line) => format!("{line}.0").parse::<Version>()?,
        None => {
            let develop = discover_develop_branch(git, config)?;
            read_version(git, &format!("origin/{develop}"), config)?
                .previous_minor_release()
                .ok_or_else(|| "no release branch; run `fork`".to_owned())?
        }
    };
    let branch = release_branch_name(config, &release);
    if git.succeeds([
        "show-ref",
        "--verify",
        "--quiet",
        &format!("refs/remotes/origin/{branch}"),
    ]) {
        Ok((release, branch))
    } else {
        Err("no release branch; run `fork`".to_owned())
    }
}

fn status(git: &Git, config: &Config, develop: &str) -> Result<(), String> {
    if config.release_line.is_some() {
        let (_, branch) = latest_release(git, config)?;
        let version = read_version(git, &format!("origin/{branch}"), config)?;
        let head = git.run(["rev-parse", &format!("origin/{branch}")])?;
        println!("release: {branch} ({version})");
        println!(
            "next action: {}",
            if release_is_done(git, config, &version, &head)? {
                "DONE"
            } else {
                "BUILD"
            }
        );
        return Ok(());
    }
    let dev_ref = format!("origin/{develop}");
    let dev_version = read_version(git, &dev_ref, config)?;
    if !dev_version.snapshot {
        return Err(format!("develop version must be a SNAPSHOT: {dev_version}"));
    }
    if !dev_version.has_zero_patch() {
        return Err(format!("develop SNAPSHOT patch must be 0: {dev_version}"));
    }
    let expected_branch = release_branch_name(config, &dev_version);
    let last_message = component_log(git, &dev_ref, config, 1, "%B")?;
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
                if release_is_done(git, config, &version, &head)? {
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
    if config.release_line.is_some() {
        return Err("fork does not accept a locked release version".to_owned());
    }
    let dev_ref = format!("origin/{develop}");
    let version = read_version(git, &dev_ref, config)?;
    if !version.snapshot {
        return Err(format!(
            "develop version must end with -SNAPSHOT: {version}"
        ));
    }
    if !version.has_zero_patch() {
        return Err(format!("develop SNAPSHOT patch must be 0: {version}"));
    }
    let release = version.release_zero();
    let branch = release_branch_name(config, &release);
    if git.succeeds([
        "show-ref",
        "--verify",
        "--quiet",
        &format!("refs/remotes/origin/{branch}"),
    ]) {
        println!("Release branch `{branch}` already exists");
        return Ok(());
    }
    let last_message = component_log(git, &dev_ref, config, 1, "%B")?;
    if last_message.contains("#scm-ver") && latest_release(git, config).is_ok() {
        println!("No fork needed for {}", display_component(config));
        return Ok(());
    }

    git.checkout_remote(develop)?;
    // The remote branch was proven absent above. -B also recovers cleanly from a
    // local branch left by an earlier failed atomic push.
    git.run(["checkout", "-B", &branch])?;
    write_and_commit_version(git, config, &release)?;
    git.checkout_remote(develop)?;
    let next = version.next_minor_snapshot();
    write_and_commit_version(git, config, &next)?;
    git.run([
        "push",
        "--atomic",
        "origin",
        &format!("{develop}:{develop}"),
        &format!("{branch}:{branch}"),
    ])?;
    println!("Forked {branch} at {release}; develop is now {next}");
    Ok(())
}

fn write_and_commit_version(git: &Git, config: &Config, version: &Version) -> Result<(), String> {
    let root = PathBuf::from(git.run(["rev-parse", "--show-toplevel"])?);
    let relative_path = component_path(config, &config.version_file);
    let path = root.join(&relative_path);
    fs::write(&path, format!("{version}\n"))
        .map_err(|e| format!("cannot write {}: {e}", path.display()))?;
    git.run(["add", "--", &relative_path])?;
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
    let branch_head = git.run(["rev-parse", "HEAD"])?;
    if release_is_done(git, config, &version, &branch_head)? {
        println!("{branch} is already built");
        return Ok(());
    }
    let tag_name = git_tag_name(config, &version);
    if git.succeeds([
        "rev-parse",
        "--verify",
        "--quiet",
        &format!("refs/tags/{tag_name}"),
    ]) {
        return Err(format!("tag `{tag_name}` already exists"));
    }
    if delayed && work.join("delayed-tag").exists() {
        return Err("a delayed tag is already pending; run `tag` first".to_owned());
    }
    let commit = component_head(git, "HEAD", config)?;
    run_build(git, config, work, &branch, &version, &commit)?;
    if delayed {
        let state = format!("scm=git\nbranch={branch}\nversion={version}\ncommit={commit}\n");
        fs::write(work.join("delayed-tag"), state)
            .map_err(|e| format!("cannot save delayed tag: {e}"))?;
        println!("Built {version}; tag for {commit} was delayed");
    } else {
        tag_and_bump(git, config, &branch, &version, &commit)?;
        run_after_tag(config, work, &version)?;
        println!("Built and tagged {version}");
    }
    Ok(())
}

fn release_is_done(
    git: &Git,
    config: &Config,
    version: &Version,
    head: &str,
) -> Result<bool, String> {
    let Some(previous) = version.previous_patch() else {
        return Ok(false);
    };
    let message = component_log(git, head, config, 1, "%B")?;
    if !message.contains("#scm-ver")
        || !git.succeeds([
            "rev-parse",
            "--verify",
            "--quiet",
            &format!("refs/tags/{}", git_tag_name(config, &previous)),
        ])
    {
        return Ok(false);
    }
    let commits = component_log(git, head, config, 2, "%H")?;
    let parent = match commits.lines().nth(1) {
        Some(commit) => commit,
        None => return Ok(false),
    };
    let tagged = git.run([
        "rev-list",
        "-n",
        "1",
        &format!("refs/tags/{}", git_tag_name(config, &previous)),
    ])?;
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
    if config.build_command.trim().is_empty() {
        return Err(format!(
            "releaseCommand is not configured for `{}`",
            if config.component.is_empty() {
                "component"
            } else {
                &config.component
            }
        ));
    }
    let builds = work.join("builds");
    fs::create_dir_all(&builds).map_err(|e| format!("cannot create build directory: {e}"))?;
    let directory = builds.join(version.to_string());
    let directory_arg = directory.to_string_lossy().into_owned();
    // This is normally a registered worktree. The fallback also handles build
    // directories created by older releaser versions that used local clones.
    let _ = git.succeeds(["worktree", "remove", "--force", &directory_arg]);
    if directory.exists() {
        fs::remove_dir_all(&directory)
            .map_err(|e| format!("cannot clear {}: {e}", directory.display()))?;
    }
    // Worktrees reuse the managed clone's object database, so a monorepo is
    // fetched once even when many releases are built.
    git.run(["worktree", "prune"])?;
    git.run(["worktree", "add", "--detach", &directory_arg, commit])?;
    let build_directory = if config.subfolder.is_empty() {
        directory.clone()
    } else {
        directory.join(&config.subfolder)
    };
    if !build_directory.is_dir() {
        return Err(format!(
            "subfolder `{}` does not exist at revision {commit}",
            config.subfolder
        ));
    }
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
        .current_dir(&build_directory)
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
    if let Ok(scm) = get("scm") {
        if scm != "git" {
            return Err("delayed tag belongs to a different SCM type".to_owned());
        }
    }
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
    run_after_tag(config, work, &version)?;
    fs::remove_file(path).map_err(|e| format!("cannot remove delayed tag state: {e}"))?;
    println!("Applied delayed tag {version}");
    Ok(())
}

fn tag_and_bump(
    git: &Git,
    config: &Config,
    branch: &str,
    version: &Version,
    commit: &str,
) -> Result<(), String> {
    let tag_name = git_tag_name(config, version);
    git.run([
        "tag",
        "-a",
        &tag_name,
        "-m",
        &format!("{version} release"),
        commit,
    ])?;
    let next = version.next_patch();
    write_and_commit_version(git, config, &next)?;
    git.run([
        "push",
        "--atomic",
        "origin",
        &format!("{branch}:{branch}"),
        &format!("refs/tags/{tag_name}"),
    ])?;
    Ok(())
}

fn run_after_tag(config: &Config, work: &Path, version: &Version) -> Result<(), String> {
    let Some(hook) = config.after_tag.as_deref() else {
        return Ok(());
    };
    let root = work.join("builds").join(version.to_string());
    let directory = if config.subfolder.is_empty() {
        root
    } else {
        root.join(&config.subfolder)
    };
    let mut command = if cfg!(windows) {
        let mut command = Command::new("cmd");
        command.args(["/D", "/S", "/C", hook]);
        command
    } else {
        let mut command = Command::new("sh");
        command.args(["-c", hook]);
        command
    };
    let status = command
        .current_dir(&directory)
        .env("SCM4J_VERSION", version.to_string())
        .status()
        .map_err(|e| format!("cannot start afterTag command: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("afterTag command failed with {status}"))
    }
}

fn release_branch_name(config: &Config, version: &Version) -> String {
    release_branch_name_for_line(config, &version.release_line())
}

fn release_branch_name_for_line(config: &Config, release_line: &str) -> String {
    format!(
        "{}{}{}",
        config.reference_namespace, config.release_branch_prefix, release_line
    )
}

fn git_tag_name(config: &Config, version: &Version) -> String {
    format!("{}{version}", config.reference_namespace)
}

fn display_component(config: &Config) -> String {
    match &config.release_line {
        Some(line) => format!("{}:{line}", config.component),
        None if config.component.is_empty() => "component".to_owned(),
        None => config.component.clone(),
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
use catalog::Catalog;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repository_key_contains_readable_name_and_unique_hash() {
        let first = repository_key("https://example.org/team/project.git");
        let second = repository_key("ssh://git@example.net/other/project.git");

        assert!(first.starts_with("project-"));
        assert!(second.starts_with("project-"));
        assert_ne!(first, second);
    }

    #[test]
    fn repository_key_supports_scp_style_urls() {
        assert!(repository_key("git@example.org:team/product.git").starts_with("product-"));
    }

    #[test]
    fn replacing_mdep_version_preserves_classifier_and_extension() {
        assert_eq!(
            replace_coordinate_version("org.example:archive:2.3@zip", "2.4").unwrap(),
            "org.example:archive:2.4@zip"
        );
        assert_eq!(
            replace_coordinate_version("org.example:archive:2.3:all@zip", "2.4").unwrap(),
            "org.example:archive:2.4:all@zip"
        );
    }
}
