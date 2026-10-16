use crate::config::Config;
use crate::git::run_in;
use crate::version::Version;
use std::ffi::{OsStr, OsString};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

pub fn execute(
    command: &str,
    config: &Config,
    work: &Path,
    delayed: bool,
    dependency_changes: bool,
) -> Result<(), String> {
    if !config.subfolder.is_empty() {
        return Err("subfolder is supported for Git only".to_owned());
    }
    validate_paths(config)?;
    let svn = Svn::new(
        &config.repository,
        work.join("repository"),
        config.username.clone(),
        config.password.clone(),
    );
    let develop = config.develop_branch.as_deref().unwrap_or("trunk");
    match command {
        "status" => status(&svn, config, develop, true).map(|_| ()),
        "fork" => fork(&svn, config, develop, dependency_changes),
        "build" => build(&svn, config, work, delayed),
        "tag" => tag(&svn, config, work),
        _ => Err(format!("unknown command `{command}`; use --help")),
    }
}

pub fn read_develop_mdeps(config: &Config, work: &Path) -> Result<String, String> {
    if !config.subfolder.is_empty() {
        return Err("subfolder is supported for Git only".to_owned());
    }
    let svn = Svn::new(
        &config.repository,
        work.join("repository"),
        config.username.clone(),
        config.password.clone(),
    );
    let source = config.release_line.as_ref().map_or_else(
        || {
            config
                .develop_branch
                .as_deref()
                .unwrap_or("trunk")
                .to_owned()
        },
        |line| release_path_for_line(config, line),
    );
    match svn.run(["cat", &format!("{}/mdeps", svn.url(&source))]) {
        Ok(content) => Ok(content),
        Err(_) => Ok(String::new()),
    }
}

pub fn status_action(config: &Config, work: &Path) -> Result<&'static str, String> {
    if !config.subfolder.is_empty() {
        return Err("subfolder is supported for Git only".to_owned());
    }
    validate_paths(config)?;
    let svn = Svn::new(
        &config.repository,
        work.join("repository"),
        config.username.clone(),
        config.password.clone(),
    );
    let develop = config.develop_branch.as_deref().unwrap_or("trunk");
    status(&svn, config, develop, false)
}

pub fn released_version(config: &Config, work: &Path) -> Result<Version, String> {
    validate_paths(config)?;
    let svn = Svn::new(
        &config.repository,
        work.join("repository"),
        config.username.clone(),
        config.password.clone(),
    );
    let (_, branch) = latest_release(&svn, config)?;
    let current = svn.read_version(&branch, &config.version_file)?;
    if release_is_done(&svn, config, &branch, &current)? {
        Ok(current.previous_patch().unwrap_or(current))
    } else {
        Ok(current)
    }
}

fn validate_paths(config: &Config) -> Result<(), String> {
    for (name, value) in [
        (
            "develop_branch",
            config.develop_branch.as_deref().unwrap_or("trunk"),
        ),
        (
            "release_branch_prefix",
            config.release_branch_prefix.as_str(),
        ),
        ("tag_prefix", config.tag_prefix.as_str()),
    ] {
        if value.starts_with('/')
            || value.starts_with('\\')
            || value.split(['/', '\\']).any(|p| p == "..")
        {
            return Err(format!(
                "{name} must be relative to the SVN repository root"
            ));
        }
    }
    Ok(())
}

fn status(
    svn: &Svn,
    config: &Config,
    develop: &str,
    verbose: bool,
) -> Result<&'static str, String> {
    if config.release_line.is_some() {
        let (_, branch) = latest_release(svn, config)?;
        let version = svn.read_version(&branch, &config.version_file)?;
        if verbose {
            println!("release: {branch} ({version})");
        }
        let action = if release_is_done(svn, config, &branch, &version)? {
            "DONE"
        } else {
            "BUILD"
        };
        if verbose {
            println!("next action: {action}");
        }
        return Ok(action);
    }
    let dev_version = svn.read_version(develop, &config.version_file)?;
    if !dev_version.snapshot {
        return Err(format!("develop version must be a SNAPSHOT: {dev_version}"));
    }
    if !dev_version.has_zero_patch() {
        return Err(format!("develop SNAPSHOT patch must be 0: {dev_version}"));
    }
    let expected = release_path(config, &dev_version);
    validate_develop_release_line(svn, config, develop, &dev_version)?;
    let needs_fork = develop_has_changes(svn, config, develop)?;
    if verbose {
        println!("develop: {develop} ({dev_version})");
    }
    if needs_fork {
        if verbose {
            println!("next action: FORK -> {expected}");
        }
        return Ok("FORK");
    }
    match latest_release(svn, config) {
        Ok((_, branch)) => {
            let version = svn.read_version(&branch, &config.version_file)?;
            if verbose {
                println!("release: {branch} ({version})");
            }
            let action = if release_is_done(svn, config, &branch, &version)? {
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

fn fork(svn: &Svn, config: &Config, develop: &str, dependency_changes: bool) -> Result<(), String> {
    if config.release_line.is_some() {
        return Err("fork does not accept a locked release version".to_owned());
    }
    let version = svn.read_version(develop, &config.version_file)?;
    if !version.snapshot {
        return Err(format!(
            "develop version must end with -SNAPSHOT: {version}"
        ));
    }
    if !version.has_zero_patch() {
        return Err(format!("develop SNAPSHOT patch must be 0: {version}"));
    }
    let release = version.release_zero();
    let branch = release_path(config, &release);
    validate_develop_release_line(svn, config, develop, &version)?;
    if !dependency_changes && !develop_has_changes(svn, config, develop)? {
        println!("No fork needed for {}", display_component(config));
        return Ok(());
    }
    crate::external::action(format_args!(
        "Forking SVN path `{develop}` into `{branch}` at version `{release}`"
    ));
    svn.copy(develop, &branch, None, "release branch created")?;
    svn.checkout(&branch, None, &svn.workspace)?;
    svn.write_and_commit_version(&svn.workspace, &config.version_file, &release)?;
    svn.checkout(develop, None, &svn.workspace)?;
    let next = version.next_minor_snapshot();
    svn.write_and_commit_version(&svn.workspace, &config.version_file, &next)?;
    println!("Forked {branch} at {release}; develop is now {next}");
    Ok(())
}

fn build(svn: &Svn, config: &Config, work: &Path, delayed: bool) -> Result<(), String> {
    let (_, branch) = latest_release(svn, config)?;
    let version = svn.read_version(&branch, &config.version_file)?;
    if version.snapshot {
        return Err(format!(
            "release branch contains snapshot version: {version}"
        ));
    }
    if release_is_done(svn, config, &branch, &version)? {
        println!("{branch} is already built");
        return Ok(());
    }
    let tag_path = tag_path(config, &version);
    if svn.exists(&tag_path) {
        return Err(format!("tag `{tag_path}` already exists"));
    }
    if delayed && work.join("delayed-tag").exists() {
        return Err("a delayed tag is already pending; run `tag` first".to_owned());
    }
    let revision = svn.head_revision(&branch)?;
    run_build(svn, config, work, &branch, &version, &revision)?;
    if delayed {
        let state = format!("scm=svn\nbranch={branch}\nversion={version}\nrevision={revision}\n");
        fs::write(work.join("delayed-tag"), state)
            .map_err(|e| format!("cannot save delayed tag: {e}"))?;
        println!("Built {version}; SVN revision {revision} was saved for delayed tagging");
    } else {
        tag_and_bump(svn, config, &branch, &version, &revision)?;
        run_after_tag(config, work, &version)?;
        println!("Built and tagged {version}");
    }
    Ok(())
}

fn tag(svn: &Svn, config: &Config, work: &Path) -> Result<(), String> {
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
    if get("scm")? != "svn" {
        return Err("delayed tag belongs to a different SCM type".to_owned());
    }
    let branch = get("branch")?;
    let version: Version = get("version")?.parse()?;
    let revision = get("revision")?;
    let current = svn.head_revision(&branch)?;
    if current != revision {
        return Err(format!(
            "{branch} advanced from delayed revision {revision} to {current}"
        ));
    }
    tag_and_bump(svn, config, &branch, &version, &revision)?;
    run_after_tag(config, work, &version)?;
    fs::remove_file(path).map_err(|e| format!("cannot remove delayed tag state: {e}"))?;
    println!("Applied delayed tag {version}");
    Ok(())
}

fn tag_and_bump(
    svn: &Svn,
    config: &Config,
    branch: &str,
    version: &Version,
    revision: &str,
) -> Result<(), String> {
    let tag = tag_path(config, version);
    crate::external::action(format_args!(
        "Creating SVN tag `{tag}` from `{branch}` at revision `{revision}`"
    ));
    let source = format!("SVN revision `r{revision}` of branch `{branch}`");
    svn.copy(branch, &tag, Some(revision), &format!("{version} release"))
        .map_err(|e| crate::tag_failure(&tag, &source, "creating the SVN tag", e))?;
    svn.checkout(branch, None, &svn.workspace).map_err(|e| {
        crate::tag_failure(
            &tag,
            &source,
            "checking out the release branch after creating the SVN tag",
            e,
        )
    })?;
    svn.write_and_commit_version(&svn.workspace, &config.version_file, &version.next_patch())
        .map_err(|e| {
            crate::tag_failure(
                &tag,
                &source,
                "updating the release version after creating the SVN tag",
                e,
            )
        })
}

fn run_after_tag(config: &Config, work: &Path, version: &Version) -> Result<(), String> {
    let Some(hook) = config.after_tag.as_deref() else {
        return Ok(());
    };
    let directory = work.join("builds").join(version.to_string());
    crate::external::action(format_args!(
        "Running afterTag for version `{version}` in {}",
        directory.display()
    ));
    let mut command = if cfg!(windows) {
        let mut command = Command::new("cmd");
        command.args(["/D", "/S", "/C", hook]);
        command
    } else {
        let mut command = Command::new("sh");
        command.args(["-c", hook]);
        command
    };
    let status = crate::external::status(
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

fn release_is_done(
    svn: &Svn,
    config: &Config,
    branch: &str,
    version: &Version,
) -> Result<bool, String> {
    let Some(previous) = version.previous_patch() else {
        return Ok(false);
    };
    let tag = tag_path(config, &previous);
    if !svn.last_message(branch)?.contains("#scm-ver") || !svn.exists(&tag) {
        return Ok(false);
    }
    let branch_history = svn.log_revisions(branch, 2)?;
    let tag_history = svn.log_revisions(&tag, 2)?;
    // A tag copy adds its own revision before the copied branch history.
    // Ignoring marked commits, the version bump must follow the tagged revision.
    Ok(match (branch_history.get(1), tag_history.get(1)) {
        (Some(parent), Some(tagged)) => parent == tagged,
        _ => false,
    })
}

fn latest_release(svn: &Svn, config: &Config) -> Result<(Version, String), String> {
    let release = match &config.release_line {
        Some(line) => format!("{line}.0").parse::<Version>()?,
        None => {
            let develop = config.develop_branch.as_deref().unwrap_or("trunk");
            let version = svn.read_version(develop, &config.version_file)?;
            validate_develop_release_line(svn, config, develop, &version)?;
            version
                .previous_minor_release()
                .ok_or_else(|| "no release branch; run `fork`".to_owned())?
        }
    };
    let branch = release_path(config, &release);
    if svn.exists(&branch) {
        Ok((release, branch))
    } else {
        Err("no release branch; run `fork`".to_owned())
    }
}

fn validate_develop_release_line(
    svn: &Svn,
    config: &Config,
    develop: &str,
    version: &Version,
) -> Result<(), String> {
    let branch = release_path(config, version);
    if svn.exists(&branch) {
        return Err(format!(
            "develop `{develop}` version `{version}` has not advanced past existing release branch `{branch}`; increase the develop minor version (normally to `{}`)",
            version.next_minor_snapshot()
        ));
    }
    Ok(())
}

fn develop_has_changes(svn: &Svn, config: &Config, develop: &str) -> Result<bool, String> {
    let Ok((_, branch)) = latest_release(svn, config) else {
        return Ok(true);
    };
    let revision = svn.branch_point(&branch, develop)?;
    // The copied revision itself already belongs to the release branch.
    let head = svn
        .head_revision(develop)?
        .parse::<u64>()
        .map_err(|e| format!("invalid SVN revision: {e}"))?;
    if head <= revision {
        return Ok(false);
    }
    let range = format!("HEAD:{}", revision + 1);
    Ok(!svn
        .meaningful_log_range(develop, 1, range, true)?
        .is_empty())
}

fn release_path(config: &Config, version: &Version) -> String {
    release_path_for_line(config, &version.release_line())
}

fn release_path_for_line(config: &Config, release_line: &str) -> String {
    format!(
        "branches/{}",
        prefixed_path(&config.release_branch_prefix, release_line)
    )
}

fn tag_path(config: &Config, version: &Version) -> String {
    prefixed_path(&config.tag_prefix, &version.to_string())
}

fn prefixed_path(prefix: &str, suffix: &str) -> String {
    format!("{}{suffix}", prefix.replace('\\', "/"))
}

fn display_component(config: &Config) -> String {
    match &config.release_line {
        Some(line) => format!("{}:{line}", config.component),
        None if config.component.is_empty() => "component".to_owned(),
        None => config.component.clone(),
    }
}

fn run_build(
    svn: &Svn,
    config: &Config,
    work: &Path,
    branch: &str,
    version: &Version,
    revision: &str,
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
    svn.checkout(branch, Some(revision), &directory)?;
    crate::external::action(format_args!(
        "Building version `{version}` from SVN path `{branch}` at revision `{revision}`"
    ));
    let mut command = crate::shell_command(&config.build_command);
    let status = crate::external::status(
        command
            .current_dir(&directory)
            .env("SVN_REVISION", revision)
            .env("SVN_BRANCH", branch)
            .env("SVN_URL", &config.repository),
    )
    .map_err(|e| format!("cannot start build command: {e}"))?;
    if !status.success() {
        return Err(format!("build command failed with {status}"));
    }
    Ok(())
}

struct Svn {
    repository: String,
    workspace: PathBuf,
    username: Option<String>,
    password: Option<String>,
}

impl Svn {
    fn new(
        repository: &str,
        workspace: PathBuf,
        username: Option<String>,
        password: Option<String>,
    ) -> Self {
        Self {
            repository: repository.trim_end_matches('/').to_owned(),
            workspace,
            username,
            password,
        }
    }

    fn url(&self, relative: &str) -> String {
        format!("{}/{}", self.repository, relative.trim_matches(['/', '\\']))
    }

    fn run<I, S>(&self, args: I) -> Result<String, String>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        self.run_at(self.workspace.parent().unwrap_or(Path::new(".")), args)
    }

    fn run_at<I, S>(&self, directory: &Path, args: I) -> Result<String, String>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        run_in(directory, "svn", self.arguments(args))
    }

    fn arguments<I, S>(&self, args: I) -> Vec<OsString>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        let mut args: Vec<OsString> = args
            .into_iter()
            .map(|arg| arg.as_ref().to_owned())
            .collect();
        args.push("--non-interactive".into());
        if let Some(username) = &self.username {
            args.extend(["--username".into(), username.into()]);
        }
        if let Some(password) = &self.password {
            args.extend([
                "--password".into(),
                password.into(),
                "--no-auth-cache".into(),
            ]);
        }
        args
    }

    fn exists(&self, relative: &str) -> bool {
        self.run(["info", &self.url(relative)]).is_ok()
    }

    fn read_version(&self, relative: &str, version_file: &str) -> Result<Version, String> {
        self.run(["cat", &format!("{}/{}", self.url(relative), version_file)])?
            .parse()
    }

    fn last_message(&self, relative: &str) -> Result<String, String> {
        Ok(self
            .meaningful_log(relative, 1)?
            .into_iter()
            .next()
            .map(|(_, message)| message)
            .unwrap_or_default())
    }

    fn head_revision(&self, relative: &str) -> Result<String, String> {
        let xml = self.run(["log", "--xml", "-l", "1", &self.url(relative)])?;
        // Subversion may put the revision attribute on the same line as
        // <logentry or on the following line, depending on its version.
        let marker = "revision=\"";
        let start = xml
            .find(marker)
            .ok_or_else(|| "SVN log contains no revision".to_owned())?
            + marker.len();
        let end = xml[start..]
            .find('"')
            .ok_or_else(|| "invalid SVN log XML".to_owned())?
            + start;
        Ok(xml[start..end].to_owned())
    }

    fn log_revisions(&self, relative: &str, count: usize) -> Result<Vec<String>, String> {
        Ok(self
            .meaningful_log(relative, count)?
            .into_iter()
            .map(|(revision, _)| revision)
            .collect())
    }

    fn meaningful_log(
        &self,
        relative: &str,
        count: usize,
    ) -> Result<Vec<(String, String)>, String> {
        self.meaningful_log_range(relative, count, "HEAD:0".to_owned(), false)
    }

    fn meaningful_log_range(
        &self,
        relative: &str,
        count: usize,
        mut range: String,
        skip_version: bool,
    ) -> Result<Vec<(String, String)>, String> {
        let entries =
            regex::Regex::new(r#"(?s)<logentry\s+revision="([0-9]+)"[^>]*>(.*?)</logentry>"#)
                .unwrap();
        let messages = regex::Regex::new(r"(?s)<msg>(.*?)</msg>").unwrap();
        let mut result = Vec::new();
        let lower = range
            .split_once(':')
            .unwrap()
            .1
            .parse::<u64>()
            .map_err(|e| format!("invalid SVN log range: {e}"))?;
        loop {
            let xml = self.run([
                "log",
                "--xml",
                "-l",
                "32",
                "-r",
                &range,
                &self.url(relative),
            ])?;
            let mut oldest = None;
            for entry in entries.captures_iter(&xml) {
                let revision = entry[1]
                    .parse::<u64>()
                    .map_err(|e| format!("invalid SVN revision: {e}"))?;
                oldest = Some(revision);
                let message = messages
                    .captures(&entry[2])
                    .map(|m| m[1].to_owned())
                    .unwrap_or_default();
                if !message.contains("#scm-ignore")
                    && !(skip_version && message.contains("#scm-ver"))
                {
                    result.push((entry[1].to_owned(), message));
                    if result.len() == count {
                        return Ok(result);
                    }
                }
            }
            match oldest {
                Some(revision) if revision > lower.max(1) => {
                    range = format!("{}:{lower}", revision - 1)
                }
                _ => return Ok(result),
            }
        }
    }

    fn repository_path(&self, relative: &str) -> Result<String, String> {
        let value = self.run(["info", "--show-item", "relative-url", &self.url(relative)])?;
        let value = value
            .trim()
            .strip_prefix('^')
            .ok_or_else(|| "invalid SVN repository-relative URL".to_owned())?;
        let mut decoded = Vec::new();
        let mut bytes = value.as_bytes().iter().copied();
        while let Some(byte) = bytes.next() {
            if byte == b'%' {
                let hex: String = bytes.by_ref().take(2).map(char::from).collect();
                decoded.push(u8::from_str_radix(&hex, 16).map_err(|e| e.to_string())?);
            } else {
                decoded.push(byte);
            }
        }
        String::from_utf8(decoded).map_err(|e| e.to_string())
    }

    fn branch_point(&self, branch: &str, develop: &str) -> Result<u64, String> {
        let xml = self.run([
            "log",
            "--xml",
            "--verbose",
            "--stop-on-copy",
            "-r",
            "0:HEAD",
            "-l",
            "1",
            &self.url(branch),
        ])?;
        let branch_path = self.repository_path(branch)?;
        let develop_path = self.repository_path(develop)?;
        let paths = regex::Regex::new(r"(?s)<path\s+([^>]*)>(.*?)</path>").unwrap();
        let source = regex::Regex::new(r#"copyfrom-path="([^"]*)""#).unwrap();
        let revision = regex::Regex::new(r#"copyfrom-rev="([0-9]+)""#).unwrap();
        for path in paths.captures_iter(&xml) {
            if xml_text(&path[2]) != branch_path {
                continue;
            }
            if let (Some(source), Some(revision)) =
                (source.captures(&path[1]), revision.captures(&path[1]))
            {
                if xml_text(&source[1]) == develop_path {
                    return revision[1].parse::<u64>().map_err(|e| e.to_string());
                }
            }
        }
        Err(format!("cannot determine SVN branch point: `{branch}` was not copied directly from `{develop}`"))
    }

    fn copy(
        &self,
        from: &str,
        to: &str,
        revision: Option<&str>,
        message: &str,
    ) -> Result<(), String> {
        let source = self.url(from);
        let target = self.url(to);
        let mut args = vec!["copy".to_owned(), source];
        if let Some(revision) = revision {
            args.extend(["-r".to_owned(), revision.to_owned()]);
        }
        args.extend([
            target,
            "--parents".to_owned(),
            "-m".to_owned(),
            message.to_owned(),
        ]);
        self.run(args)?;
        Ok(())
    }

    fn checkout(
        &self,
        relative: &str,
        revision: Option<&str>,
        directory: &Path,
    ) -> Result<(), String> {
        if directory.exists() {
            fs::remove_dir_all(directory)
                .map_err(|e| format!("cannot clear {}: {e}", directory.display()))?;
        }
        let mut args = vec!["checkout".to_owned(), self.url(relative)];
        if let Some(revision) = revision {
            args.extend(["-r".to_owned(), revision.to_owned()]);
        }
        args.push(directory.to_string_lossy().into_owned());
        self.run(args)?;
        Ok(())
    }

    fn write_and_commit_version(
        &self,
        workspace: &Path,
        version_file: &str,
        version: &Version,
    ) -> Result<(), String> {
        let path = workspace.join(version_file);
        crate::external::action(format_args!(
            "Updating version file `{}` to `{version}`",
            path.display()
        ));
        fs::write(&path, version.to_string())
            .map_err(|e| format!("cannot write {}: {e}", path.display()))?;
        self.run_at(
            workspace,
            ["commit", version_file, "-m", &format!("#scm-ver {version}")],
        )?;
        Ok(())
    }
}

fn xml_text(value: &str) -> String {
    value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn svn_commit_arguments_include_configured_authentication() {
        let svn = Svn::new(
            "https://example.test/repository",
            PathBuf::from("workspace"),
            Some("builder".to_owned()),
            Some("secret".to_owned()),
        );
        let args: Vec<_> = svn
            .arguments(["commit", "version", "-m", "#scm-ver 1.2.0"])
            .into_iter()
            .map(|value| value.to_string_lossy().into_owned())
            .collect();

        assert_eq!(
            args,
            [
                "commit",
                "version",
                "-m",
                "#scm-ver 1.2.0",
                "--non-interactive",
                "--username",
                "builder",
                "--password",
                "secret",
                "--no-auth-cache",
            ]
        );
    }

    #[test]
    fn svn_prefix_is_concatenated_literally() {
        assert_eq!(prefixed_path("B", "2"), "B2");
        assert_eq!(prefixed_path("release/", "2"), "release/2");
        assert_eq!(prefixed_path(r"release\", "2"), "release/2");
    }

    #[test]
    fn svn_release_paths_use_branches_directory() {
        assert_eq!(
            format!("branches/{}", prefixed_path("B", "2")),
            "branches/B2"
        );
        assert_eq!(
            format!("branches/{}", prefixed_path("release/", "2")),
            "branches/release/2"
        );
    }
}
