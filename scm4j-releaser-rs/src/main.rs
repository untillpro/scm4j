mod catalog;
mod config;
mod external;
mod git;
mod svn;
mod version;

use config::{Config, ScmType};
use git::Git;
use std::collections::{HashMap, HashSet};
use std::env;
use std::fs::{self, OpenOptions};
use std::path::{Path, PathBuf};
use std::process::Command;
use version::Version;

const WORK_DIR: &str = ".scm4j-releaser";
const LOCK_FILE: &str = "lock";

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
    if matches!(command, "--version" | "-V") {
        println!("scm4j-releaser {}", env!("CARGO_PKG_VERSION"));
        return Ok(());
    }
    let delayed = args.iter().skip(1).any(|arg| arg == "--delayed-tag");
    let show_done = args.iter().skip(1).any(|arg| arg == "--show-done");
    let verbose = args.iter().skip(1).any(|arg| arg == "--verbose");
    let trace = args.iter().skip(1).any(|arg| arg == "--trace");
    if delayed && command != "build" {
        return Err("--delayed-tag is valid for build only".to_owned());
    }
    if show_done && command != "status" {
        return Err("--show-done is valid for status only".to_owned());
    }
    if let Some(option) = args.iter().skip(1).find(|arg| {
        arg.starts_with('-')
            && *arg != "--delayed-tag"
            && *arg != "--show-done"
            && *arg != "--verbose"
            && *arg != "--trace"
    }) {
        return Err(format!("unknown option `{option}`"));
    }
    let components: Vec<_> = args
        .iter()
        .skip(1)
        .filter(|arg| !arg.starts_with('-'))
        .collect();

    external::set_modes(verbose, trace);

    let executable = env::current_exe().map_err(|e| format!("cannot locate executable: {e}"))?;
    let installation_dir = executable
        .parent()
        .ok_or_else(|| "executable has no parent directory".to_owned())?
        .to_owned();
    let config_dir = env::home_dir()
        .map(|home| home.join(".scm4j"))
        .unwrap_or_else(|| installation_dir.clone());
    let working_dir =
        env::current_dir().map_err(|e| format!("cannot locate current working directory: {e}"))?;
    if command == "init" {
        return init(&config_dir);
    }
    let work = working_dir.join(WORK_DIR);
    fs::create_dir_all(&work).map_err(|e| format!("cannot create {}: {e}", work.display()))?;
    let _lock = Lock::acquire(&work.join(LOCK_FILE))?;
    if components.is_empty() {
        return Err("component coordinates are required; use group:artifact".to_owned());
    }

    let mut config_dirs = vec![config_dir];
    if config_dirs[0] != installation_dir {
        config_dirs.push(installation_dir);
    }
    let catalog = Catalog::load_search(&config_dirs)?;
    let mut fetched_repositories = HashSet::new();
    let (configs, dependencies) = if command == "tag" {
        let mut seen = HashSet::new();
        let mut configs = Vec::new();
        for coordinates in &components {
            let config = catalog.resolve(coordinates)?;
            if seen.insert(config.component.clone()) {
                configs.push(config);
            }
        }
        (configs, HashMap::new())
    } else {
        resolve_component_graph(&catalog, &components, &work, &mut fetched_repositories)?
    };
    let mut actions = HashMap::new();
    let mut dependency_forks = HashSet::new();
    if matches!(command, "status" | "fork" | "build") {
        // Plan before changing dependencies, so their actions remain visible to parents.
        for config in &configs {
            let component_work = work.join("components").join(safe_name(&config.component));
            fs::create_dir_all(&component_work)
                .map_err(|e| format!("cannot create {}: {e}", component_work.display()))?;
            let own = inspect_status(config, &work, &component_work, &mut fetched_repositories)?;
            let dependency_changes = dependencies
                .get(&config.component)
                .into_iter()
                .flatten()
                .any(|dependency| actions.get(dependency).copied() != Some("DONE"));
            let action = if own == "DONE"
                && (dependency_changes
                    || lock_git_mdeps(&catalog, config, &work, &mut fetched_repositories, true)?)
            {
                if config.release_line.is_none() {
                    dependency_forks.insert(config.component.clone());
                    "FORK"
                } else {
                    "BUILD_MDEPS"
                }
            } else {
                own
            };
            actions.insert(config.component.clone(), action);
        }
    }
    if command == "status" {
        print_dependency_tree(&catalog, &components, &dependencies, &actions, show_done)?;
        return Ok(());
    }
    if command == "build" {
        if let Some(config) = configs
            .iter()
            .find(|config| dependency_forks.contains(&config.component))
        {
            return Err(format!(
                "dependency changes require a new release branch for `{}`; run `fork` first",
                config.component
            ));
        }
    }
    let root_components = components
        .iter()
        .map(|coordinates| catalog.resolve(coordinates).map(|config| config.component))
        .collect::<Result<HashSet<_>, _>>()?;
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
            lock_git_mdeps(&catalog, &config, &work, &mut fetched_repositories, false)?;
        }
        let delay_this_component = delayed && root_components.contains(&config.component);
        execute_config(
            command,
            &config,
            &work,
            &component_work,
            delay_this_component,
            dependency_forks.contains(&config.component),
            &mut fetched_repositories,
        )?;
        if command == "fork" {
            lock_git_mdeps(&catalog, &config, &work, &mut fetched_repositories, false)?;
        }
    }
    Ok(())
}

fn resolve_component_graph(
    catalog: &Catalog,
    roots: &[&String],
    shared_work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<(Vec<Config>, HashMap<String, Vec<String>>), String> {
    let selected = select_component_configs(catalog, roots, shared_work, fetched_repositories)?;
    let mut visiting = HashSet::new();
    let mut completed = HashSet::new();
    let mut result = Vec::new();
    let mut dependencies = HashMap::new();
    for root in roots {
        let component = catalog.resolve(root)?.component;
        visit_component(
            catalog,
            &component,
            &selected,
            shared_work,
            &mut visiting,
            &mut completed,
            &mut result,
            &mut dependencies,
            fetched_repositories,
        )?;
    }
    for root in roots {
        let config = catalog.resolve(root)?;
        if config.release_line.is_some() {
            validate_locked_dependency_tree(&config.component, &selected, &dependencies)?;
        }
    }
    Ok((result, dependencies))
}

fn validate_locked_dependency_tree(
    root: &str,
    selected: &HashMap<String, Config>,
    dependencies: &HashMap<String, Vec<String>>,
) -> Result<(), String> {
    let mut visited = HashSet::new();
    let mut pending: Vec<_> = dependencies
        .get(root)
        .into_iter()
        .flatten()
        .cloned()
        .collect();
    while let Some(component) = pending.pop() {
        if !visited.insert(component.clone()) {
            continue;
        }
        let config = selected
            .get(&component)
            .ok_or_else(|| format!("component `{component}` was not resolved"))?;
        if config.release_line.is_none() {
            return Err(format!(
                "dependency `{component}` is not locked in release tree of `{root}`"
            ));
        }
        pending.extend(dependencies.get(&component).into_iter().flatten().cloned());
    }
    Ok(())
}

fn select_component_configs(
    catalog: &Catalog,
    roots: &[&String],
    shared_work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<HashMap<String, Config>, String> {
    let mut selected: HashMap<String, Config> = HashMap::new();
    let mut pending: Vec<String> = roots.iter().rev().map(|root| (*root).clone()).collect();
    while let Some(coordinates) = pending.pop() {
        let config = catalog.resolve(&coordinates)?;
        let scan = match selected.get(&config.component) {
            None => true,
            Some(existing) => match (&existing.release_line, &config.release_line) {
                (Some(existing), Some(candidate)) if existing != candidate => {
                    return Err(format!(
                        "conflicting locked release lines for `{}`: `{existing}` and `{candidate}`",
                        config.component
                    ));
                }
                (None, Some(_)) => true,
                _ => false,
            },
        };
        if !scan {
            continue;
        }
        selected.insert(config.component.clone(), config.clone());
        let mdeps = read_develop_mdeps(&config, shared_work, fetched_repositories)?;
        pending.extend(component_mdeps(&config, &mdeps, true)?.into_iter().rev());
    }
    Ok(selected)
}

fn visit_component(
    catalog: &Catalog,
    component: &str,
    selected: &HashMap<String, Config>,
    shared_work: &Path,
    visiting: &mut HashSet<String>,
    completed: &mut HashSet<String>,
    result: &mut Vec<Config>,
    dependencies: &mut HashMap<String, Vec<String>>,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<(), String> {
    if completed.contains(component) {
        return Ok(());
    }
    if !visiting.insert(component.to_owned()) {
        return Err(format!("cyclic mdeps dependency at `{component}`"));
    }
    let config = selected
        .get(component)
        .cloned()
        .ok_or_else(|| format!("component `{component}` was not resolved"))?;
    let mdeps = read_develop_mdeps(&config, shared_work, fetched_repositories)?;
    let dependency_coordinates = component_mdeps(&config, &mdeps, false)?;
    let mut dependency_names = Vec::new();
    for dependency in dependency_coordinates {
        let dependency_component = catalog.resolve(&dependency)?.component;
        if dependency_names.contains(&dependency_component) {
            continue;
        }
        visit_component(
            catalog,
            &dependency_component,
            selected,
            shared_work,
            visiting,
            completed,
            result,
            dependencies,
            fetched_repositories,
        )?;
        dependency_names.push(dependency_component);
    }
    dependencies.insert(component.to_owned(), dependency_names);
    visiting.remove(component);
    completed.insert(component.to_owned());
    result.push(config);
    Ok(())
}

fn inspect_status(
    config: &Config,
    shared_work: &Path,
    component_work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<&'static str, String> {
    match config.scm_type {
        ScmType::Git => {
            let git = prepare_repository(config, shared_work, fetched_repositories)?;
            let develop = discover_develop_branch(&git, config)?;
            status(&git, config, &develop, false)
        }
        ScmType::Svn => svn::status_action(config, component_work),
    }
}

fn print_dependency_tree(
    catalog: &Catalog,
    roots: &[&String],
    dependencies: &HashMap<String, Vec<String>>,
    actions: &HashMap<String, &'static str>,
    show_done: bool,
) -> Result<(), String> {
    println!("Dependency tree (dependencies execute first):");
    let mut printed = HashSet::new();
    for root in roots {
        let component = catalog.resolve(root)?.component;
        if !printed.insert(component.clone()) {
            continue;
        }
        if show_done || planned_action(&component, actions) != "DONE" {
            print_dependency_node(&component, "", true, true, dependencies, actions, show_done);
        }
    }
    if printed
        .iter()
        .all(|component| planned_action(component, actions) == "DONE")
        && !show_done
    {
        println!("(no actions)");
    }
    Ok(())
}

fn print_dependency_node(
    component: &str,
    prefix: &str,
    last: bool,
    root: bool,
    dependencies: &HashMap<String, Vec<String>>,
    actions: &HashMap<String, &'static str>,
    show_done: bool,
) {
    let connector = if root {
        ""
    } else if last {
        "`-- "
    } else {
        "|-- "
    };
    let action = planned_action(component, actions);
    println!("{prefix}{connector}{component} [{action}]");
    let children: Vec<_> = dependencies
        .get(component)
        .map(Vec::as_slice)
        .unwrap_or(&[])
        .iter()
        .filter(|child| show_done || planned_action(child, actions) != "DONE")
        .collect();
    let child_prefix = if root {
        prefix.to_owned()
    } else if last {
        format!("{prefix}    ")
    } else {
        format!("{prefix}|   ")
    };
    for (index, child) in children.iter().enumerate() {
        print_dependency_node(
            child,
            &child_prefix,
            index + 1 == children.len(),
            false,
            dependencies,
            actions,
            show_done,
        );
    }
}

fn planned_action(component: &str, actions: &HashMap<String, &'static str>) -> &'static str {
    actions.get(component).copied().unwrap_or("UNKNOWN")
}

fn read_develop_mdeps(
    config: &Config,
    shared_work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<String, String> {
    match config.scm_type {
        ScmType::Git => {
            let git = prepare_repository(config, shared_work, fetched_repositories)?;
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

fn component_mdeps(config: &Config, content: &str, warn: bool) -> Result<Vec<String>, String> {
    parse_mdeps(content).into_iter().map(|coordinate| {
        if config.release_line.is_some() {
            return Ok(coordinate);
        }
        let unversioned = unversioned_mdep(&coordinate)?;
        if warn && unversioned != coordinate {
            eprintln!(
                "WARNING: ignoring dependency version in develop mdeps of `{}`: `{coordinate}`; resolving the dependency without a locked version",
                config.component
            );
        }
        Ok(unversioned)
    }).collect()
}

fn unversioned_mdep(coordinate: &str) -> Result<String, String> {
    let base = coordinate.split('@').next().unwrap_or(coordinate);
    match base.split(':').nth(2) {
        Some(version) if !version.trim().is_empty() => replace_coordinate_version(coordinate, ""),
        _ => Ok(coordinate.to_owned()),
    }
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

fn replace_mdep_coordinate(line: &str, coordinate: &str) -> String {
    let coordinate_end = line.find('#').unwrap_or(line.len());
    let coordinate_field = &line[..coordinate_end];
    let value = coordinate_field.trim();
    let value_start = coordinate_field.len() - coordinate_field.trim_start().len();
    let value_end = value_start + value.len();
    format!("{}{coordinate}{}", &line[..value_start], &line[value_end..])
}

fn lock_git_mdeps(
    catalog: &Catalog,
    config: &Config,
    shared_work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
    dry_run: bool,
) -> Result<bool, String> {
    if config.scm_type != ScmType::Git {
        return Ok(false);
    }
    let git = prepare_repository(config, shared_work, fetched_repositories)?;
    let Ok((_, branch)) = latest_release(&git, config) else {
        return Ok(false);
    };
    let revision = format!("origin/{branch}");
    let path = component_path(config, "mdeps");
    if !git.succeeds(["cat-file", "-e", &format!("{revision}:{path}")]) {
        return Ok(false);
    }
    let original = git.show_file(&revision, &path)?;
    let mut changed = false;
    let mut changes = Vec::new();
    let mut output = Vec::new();
    // Keep line endings and trailing blank lines exactly as stored in Git.
    for line in original.split_inclusive('\n') {
        let coordinate_end = line.find('#').unwrap_or(line.len());
        let coordinate_field = &line[..coordinate_end];
        let value = coordinate_field.trim();
        if value.is_empty() {
            output.push(line.to_owned());
            continue;
        }
        // An unlocked parent follows develop dependencies, including when its mdeps
        // has just been copied into a new release branch with stale versions.
        let coordinates = if config.release_line.is_none() {
            unversioned_mdep(value)?
        } else {
            value.to_owned()
        };
        let dependency = catalog.resolve(&coordinates)?;
        let locked = match dependency.scm_type {
            ScmType::Git => {
                let dependency_git =
                    prepare_repository(&dependency, shared_work, fetched_repositories)?;
                let (_, dependency_branch) = latest_release(&dependency_git, &dependency)?;
                let dependency_revision = format!("origin/{dependency_branch}");
                let current = read_version(&dependency_git, &dependency_revision, &dependency)?;
                let dependency_head = dependency_git.run(["rev-parse", &dependency_revision])?;
                if release_is_done(&dependency_git, &dependency, &current, &dependency_head)? {
                    current.previous_patch().unwrap_or(current)
                } else {
                    current
                }
            }
            ScmType::Svn => svn::released_version(&dependency, shared_work)?,
        };
        let coords = replace_coordinate_version(value, &locked.to_string())?;
        // Replace only the coordinate itself. Whitespace and comments are not
        // part of dependency locking and must not create a release commit.
        let replacement = replace_mdep_coordinate(line, &coords);
        if replacement != line {
            changes.push(format!("Locking mdep `{value}` to version `{locked}`"));
        }
        changed |= replacement != line;
        output.push(replacement);
    }
    if !changed {
        return Ok(false);
    }
    if dry_run {
        return Ok(true);
    }
    for change in changes {
        external::action(format_args!("{change}"));
    }
    external::action(format_args!("Updating mdeps `{path}` on branch `{branch}`"));
    git.checkout_remote(&branch)?;
    let root = PathBuf::from(git.run(["rev-parse", "--show-toplevel"])?);
    let file = root.join(&path);
    fs::write(&file, output.concat())
        .map_err(|e| format!("cannot write {}: {e}", file.display()))?;
    git.run(["add", "--", &path])?;
    git.run(["commit", "-m", "#scm-mdeps"])?;
    git.run(["push", "origin", &format!("{branch}:{branch}")])?;
    println!("Locked mdeps in {branch}");
    Ok(true)
}

fn execute_config(
    command: &str,
    config: &Config,
    shared_work: &Path,
    component_work: &Path,
    delayed: bool,
    dependency_changes: bool,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<(), String> {
    match config.scm_type {
        ScmType::Git => {
            let git = prepare_repository(config, shared_work, fetched_repositories)?;
            let develop = discover_develop_branch(&git, config)?;
            match command {
                "status" => status(&git, config, &develop, true).map(|_| ()),
                "fork" => fork(&git, config, &develop, dependency_changes),
                "build" => build(&git, config, component_work, delayed),
                "tag" => tag(&git, config, component_work),
                _ => Err(format!("unknown command `{command}`; use --help")),
            }
        }
        ScmType::Svn => svn::execute(command, config, component_work, delayed, dependency_changes),
    }
}

fn print_help() {
    println!("scm4j-releaser {} - multi-component Git/SVN release tool\n\n\
Usage:\n  scm4j-releaser --version\n  scm4j-releaser init\n  scm4j-releaser status group:artifact [...] [--show-done]\n  scm4j-releaser fork group:artifact [...]\n  scm4j-releaser build group:artifact [...] [--delayed-tag]\n  scm4j-releaser tag group:artifact [...]\n\n\
Options:\n  --verbose       Describe release actions as they are performed\n  --trace         Print every external command before it is executed\n\n\
Configuration is read from <home_dir>/.scm4j; the executable directory is also searched for compatibility.\n\
Working data is stored in the current directory.", env!("CARGO_PKG_VERSION"));
}

fn init(home: &Path) -> Result<(), String> {
    fs::create_dir_all(home).map_err(|e| format!("cannot create {}: {e}", home.display()))?;
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

fn prepare_repository(
    config: &Config,
    work: &Path,
    fetched_repositories: &mut HashSet<PathBuf>,
) -> Result<Git, String> {
    let repositories = work.join("repositories");
    let directory = repositories.join(repository_key(&config.repository));
    fs::create_dir_all(
        directory
            .parent()
            .expect("repository directory has a parent"),
    )
    .map_err(|e| format!("cannot create repository workspace: {e}"))?;
    let exists = directory.join(".git").is_dir();
    let git = if exists {
        let git = Git::new(directory, config.username.clone(), config.password.clone());
        let origin = git.run(["remote", "get-url", "origin"])?;
        if origin != config.repository {
            return Err(format!("managed clone belongs to `{origin}`, configured repository is `{}`; remove {} to re-clone",
                config.repository, git.directory().display()));
        }
        git
    } else {
        external::action(format_args!(
            "Cloning Git repository `{}`",
            config.repository
        ));
        Git::clone(
            &config.repository,
            &directory,
            config.username.clone(),
            config.password.clone(),
        )?
    };
    if exists && !fetched_repositories.contains(git.directory()) {
        external::action(format_args!(
            "Fetching branches and tags from `{}`",
            config.repository
        ));
        git.fetch()?;
    }
    fetched_repositories.insert(git.directory().to_owned());
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
    git_component_log(git, revision, config, count, format, true)
}

fn git_component_log(
    git: &Git,
    revision: &str,
    config: &Config,
    count: usize,
    format: &str,
    ignore_marked: bool,
) -> Result<String, String> {
    let mut args = vec![
        "log".to_owned(),
        format!("-{count}"),
        format!("--format={format}"),
    ];
    if ignore_marked {
        args.extend(["--fixed-strings", "--invert-grep", "--grep=#scm-ignore"].map(str::to_owned));
    }
    args.push(revision.to_owned());
    if !config.subfolder.is_empty() {
        args.extend(["--".to_owned(), config.subfolder.clone()]);
    }
    git.run(args)
}

fn component_head(git: &Git, revision: &str, config: &Config) -> Result<String, String> {
    let commit = git_component_log(git, revision, config, 1, "%H", false)?;
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
            let version = read_version(git, &format!("origin/{develop}"), config)?;
            validate_develop_release_line(git, config, &develop, &version)?;
            version
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

fn validate_develop_release_line(
    git: &Git,
    config: &Config,
    develop: &str,
    version: &Version,
) -> Result<(), String> {
    let branch = release_branch_name(config, version);
    if git.succeeds([
        "show-ref",
        "--verify",
        "--quiet",
        &format!("refs/remotes/origin/{branch}"),
    ]) {
        return Err(format!(
            "develop `{develop}` version `{version}` has not advanced past existing release branch `{branch}`; increase the develop minor version (normally to `{}`)",
            version.next_minor_snapshot()
        ));
    }
    Ok(())
}

fn develop_has_changes(git: &Git, config: &Config, develop: &str) -> Result<bool, String> {
    let Ok((_, branch)) = latest_release(git, config) else {
        return Ok(true);
    };
    let dev_ref = format!("origin/{develop}");
    let base = git.run(["merge-base", &dev_ref, &format!("origin/{branch}")])?;
    let mut args = vec![
        "log".to_owned(),
        "-1".to_owned(),
        "--format=%H".to_owned(),
        "--fixed-strings".to_owned(),
        "--invert-grep".to_owned(),
        "--grep=#scm-ignore".to_owned(),
        "--grep=#scm-ver".to_owned(),
        format!("{base}..{dev_ref}"),
    ];
    if !config.subfolder.is_empty() {
        args.extend(["--".to_owned(), config.subfolder.clone()]);
    }
    Ok(!git.run(args)?.is_empty())
}

fn status(
    git: &Git,
    config: &Config,
    develop: &str,
    verbose: bool,
) -> Result<&'static str, String> {
    if config.release_line.is_some() {
        let (_, branch) = latest_release(git, config)?;
        let version = read_version(git, &format!("origin/{branch}"), config)?;
        let head = git.run(["rev-parse", &format!("origin/{branch}")])?;
        if verbose {
            println!("release: {branch} ({version})");
        }
        let action = if release_is_done(git, config, &version, &head)? {
            "DONE"
        } else {
            "BUILD"
        };
        if verbose {
            println!("next action: {action}");
        }
        return Ok(action);
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
    validate_develop_release_line(git, config, develop, &dev_version)?;
    let needs_fork = develop_has_changes(git, config, develop)?;
    if verbose {
        println!("develop: {develop} ({dev_version})");
    }
    if needs_fork {
        if verbose {
            println!("next action: FORK -> {expected_branch}");
        }
        return Ok("FORK");
    }
    match latest_release(git, config) {
        Ok((_, branch)) => {
            let version = read_version(git, &format!("origin/{branch}"), config)?;
            let head = git.run(["rev-parse", &format!("origin/{branch}")])?;
            if verbose {
                println!("release: {branch} ({version})");
            }
            let action = if release_is_done(git, config, &version, &head)? {
                "DONE"
            } else {
                "BUILD"
            };
            if verbose {
                println!("next action: {action}");
            }
            Ok(action)
        }
        Err(_) => {
            if verbose {
                println!("next action: FORK");
            }
            Ok("FORK")
        }
    }
}

fn fork(git: &Git, config: &Config, develop: &str, dependency_changes: bool) -> Result<(), String> {
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
    validate_develop_release_line(git, config, develop, &version)?;
    if !dependency_changes && !develop_has_changes(git, config, develop)? {
        println!("No fork needed for {}", display_component(config));
        return Ok(());
    }

    external::action(format_args!(
        "Forking Git branch `{develop}` into `{branch}` at version `{release}`"
    ));
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
    external::action(format_args!(
        "Updating version file `{relative_path}` to `{version}`"
    ));
    fs::write(&path, version.to_string())
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
    let tagged = component_log(git, &tagged, config, 1, "%H")?;
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
    // Configure sparsity before populating the worktree to avoid briefly
    // checking out the entire monorepo for every component.
    git.run([
        "worktree",
        "add",
        "--detach",
        "--no-checkout",
        &directory_arg,
        commit,
    ])?;
    let build_git = Git::new(
        directory.clone(),
        config.username.clone(),
        config.password.clone(),
    );
    if !config.subfolder.is_empty() {
        // `set` enables worktree-specific configuration, keeping other builds
        // and the shared managed clone independent of this component's scope.
        build_git.run(["sparse-checkout", "set", "--cone", "--", &config.subfolder])?;
    }
    build_git.run(["reset", "--hard", commit])?;
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
    external::action(format_args!(
        "Building version `{version}` from Git branch `{branch}` at commit `{commit}`"
    ));
    let mut command = shell_command(&config.build_command);
    let status = external::status(
        command
            .current_dir(&build_directory)
            .env("GIT_COMMIT", commit)
            .env("GIT_BRANCH", branch)
            .env("GIT_URL", &config.repository),
    )
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
    if !git.succeeds(["merge-base", "--is-ancestor", &commit, &remote_head]) {
        return Err(format!(
            "{branch} no longer contains delayed commit {commit}; current head is {remote_head}"
        ));
    }
    git.checkout_remote(&branch)?;
    tag_and_bump_if_needed(git, config, &branch, &version, &commit)?;
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
    tag_and_bump_inner(git, config, branch, version, commit, false)
}

fn tag_and_bump_if_needed(
    git: &Git,
    config: &Config,
    branch: &str,
    version: &Version,
    commit: &str,
) -> Result<(), String> {
    tag_and_bump_inner(git, config, branch, version, commit, true)
}

fn tag_and_bump_inner(
    git: &Git,
    config: &Config,
    branch: &str,
    version: &Version,
    commit: &str,
    preserve_newer_version: bool,
) -> Result<(), String> {
    let tag_name = git_tag_name(config, version);
    let source = format!(
        "Git commit `{commit}` (branch `{branch}`, local repository `{}`)",
        git.directory().display()
    );
    external::action(format_args!(
        "Creating Git tag `{tag_name}` for commit `{commit}`"
    ));
    git.run([
        "tag",
        "-a",
        &tag_name,
        "-m",
        &format!("{version} release"),
        commit,
    ])
    .map_err(|e| tag_failure(&tag_name, &source, "creating the local Git tag", e))?;
    let next = version.next_patch();
    let branch_version = read_version(git, "HEAD", config).map_err(|e| {
        tag_failure(
            &tag_name,
            &source,
            "reading the release version after creating the local tag",
            e,
        )
    })?;
    if !preserve_newer_version || branch_version < next {
        write_and_commit_version(git, config, &next).map_err(|e| {
            tag_failure(
                &tag_name,
                &source,
                "updating the release version after creating the local tag",
                e,
            )
        })?;
    }
    external::action(format_args!(
        "Publishing Git tag `{tag_name}` and branch `{branch}`"
    ));
    git.run([
        "push",
        "--atomic",
        "origin",
        &format!("{branch}:{branch}"),
        &format!("refs/tags/{tag_name}"),
    ])
    .map_err(|e| {
        tag_failure(
            &tag_name,
            &source,
            "publishing the Git tag and release branch",
            e,
        )
    })?;
    Ok(())
}

pub(crate) fn tag_failure(tag: &str, source: &str, stage: &str, error: String) -> String {
    eprintln!(
        "WARNING: release tagging did not complete while {stage}. Expected tag `{tag}` at {source}. Verify the tag and release version and repair them manually before retrying."
    );
    error
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
    external::action(format_args!(
        "Running afterTag for version `{version}` in {}",
        directory.display()
    ));
    let mut command = shell_command(hook);
    let status = external::status(
        command
            .current_dir(&directory)
            .env("SCM4J_VERSION", version.to_string()),
    )
    .map_err(|e| format!("cannot start afterTag command: {e}"))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("afterTag command failed with {status}"))
    }
}

pub(crate) fn shell_command(command_line: &str) -> Command {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;

        let mut command = Command::new("cmd");
        command.args(["/D", "/S", "/C"]);
        // cmd.exe does not use the standard Windows argv decoding rules.
        command.raw_arg(format!("\"{command_line}\""));
        command
    }
    #[cfg(not(windows))]
    {
        let mut command = Command::new("sh");
        command.args(["-c", command_line]);
        command
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

#[derive(Debug)]
struct Lock {
    _file: fs::File,
}

impl Lock {
    fn acquire(path: &Path) -> Result<Self, String> {
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .open(path)
            .map_err(|e| format!("cannot open lock file {}: {e}", path.display()))?;
        match file.try_lock() {
            Ok(()) => Ok(Self { _file: file }),
            Err(fs::TryLockError::WouldBlock) => Err(format!(
                "another releaser is running in this working directory ({})",
                path.display()
            )),
            Err(fs::TryLockError::Error(error)) => Err(format!(
                "cannot lock working directory using {}: {error}",
                path.display()
            )),
        }
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

    #[test]
    fn replacing_mdep_coordinate_preserves_comment_spacing() {
        assert_eq!(
            replace_mdep_coordinate(
                "eu.untill:TPAPIJavaProxy:21.0#implementation",
                "eu.untill:TPAPIJavaProxy:21.0"
            ),
            "eu.untill:TPAPIJavaProxy:21.0#implementation"
        );
        assert_eq!(
            replace_mdep_coordinate(
                "  eu.untill:TPAPIJavaProxy:20.0  #implementation",
                "eu.untill:TPAPIJavaProxy:21.0"
            ),
            "  eu.untill:TPAPIJavaProxy:21.0  #implementation"
        );
    }

    #[test]
    fn working_directory_lock_is_released_with_the_file_handle() {
        let path = env::temp_dir().join(format!("scm4j-releaser-lock-test-{}", std::process::id()));
        let first = Lock::acquire(&path).unwrap();

        let error = Lock::acquire(&path).unwrap_err();
        assert!(error.contains("another releaser is running"), "{error}");

        drop(first);
        let second = Lock::acquire(&path).unwrap();
        drop(second);
        fs::remove_file(path).unwrap();
    }
}
