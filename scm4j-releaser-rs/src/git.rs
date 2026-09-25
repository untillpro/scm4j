use std::ffi::OsStr;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

pub struct Git {
    directory: PathBuf,
    username: Option<String>,
    password: Option<String>,
}

impl Git {
    pub fn new(directory: PathBuf, username: Option<String>, password: Option<String>) -> Self {
        Self {
            directory,
            username,
            password,
        }
    }

    pub fn clone(
        repository: &str,
        directory: &Path,
        username: Option<String>,
        password: Option<String>,
    ) -> Result<Self, String> {
        let parent = directory
            .parent()
            .ok_or_else(|| "invalid repository directory".to_owned())?;
        let name = directory
            .file_name()
            .ok_or_else(|| "invalid repository directory".to_owned())?;
        run_git_in(
            parent,
            [
                OsStr::new("clone"),
                OsStr::new("--origin"),
                OsStr::new("origin"),
                OsStr::new(repository),
                name,
            ],
            username.as_deref(),
            password.as_deref(),
        )?;
        Ok(Self::new(directory.to_owned(), username, password))
    }

    pub fn run<I, S>(&self, args: I) -> Result<String, String>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        run_git_in(
            &self.directory,
            args,
            self.username.as_deref(),
            self.password.as_deref(),
        )
    }

    pub fn succeeds<I, S>(&self, args: I) -> bool
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        git_command(
            &self.directory,
            args,
            self.username.as_deref(),
            self.password.as_deref(),
        )
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
    }

    pub fn fetch(&self) -> Result<(), String> {
        self.run(["fetch", "origin", "--prune", "--tags"])?;
        Ok(())
    }

    pub fn show_file(&self, revision: &str, path: &str) -> Result<String, String> {
        self.run(["show", &format!("{revision}:{path}")])
    }

    pub fn checkout_remote(&self, branch: &str) -> Result<(), String> {
        self.run(["checkout", "-B", branch, &format!("origin/{branch}")])?;
        Ok(())
    }

    pub fn directory(&self) -> &Path {
        &self.directory
    }
}

fn run_git_in<I, S>(
    directory: &Path,
    args: I,
    username: Option<&str>,
    password: Option<&str>,
) -> Result<String, String>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let Output {
        status,
        stdout,
        stderr,
    } = git_command(directory, args, username, password)
        .output()
        .map_err(|e| format!("failed to start git: {e}"))?;
    if status.success() {
        Ok(String::from_utf8_lossy(&stdout).trim().to_owned())
    } else {
        let detail = String::from_utf8_lossy(&stderr).trim().to_owned();
        Err(if detail.is_empty() {
            format!("git failed with {status}")
        } else {
            detail
        })
    }
}

fn git_command<I, S>(
    directory: &Path,
    args: I,
    username: Option<&str>,
    password: Option<&str>,
) -> Command
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let mut command = Command::new("git");
    command.args(args).current_dir(directory);
    if username.is_some() || password.is_some() {
        if let Ok(executable) = std::env::current_exe() {
            command
                .env("GIT_ASKPASS", executable)
                .env("GIT_TERMINAL_PROMPT", "0")
                .env("SCM4J_ASKPASS", "1")
                .env("SCM4J_ASKPASS_USERNAME", username.unwrap_or_default())
                .env("SCM4J_ASKPASS_PASSWORD", password.unwrap_or_default());
        }
    }
    command
}

pub fn run_in<I, S>(directory: &Path, program: &str, args: I) -> Result<String, String>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let Output {
        status,
        stdout,
        stderr,
    } = Command::new(program)
        .args(args)
        .current_dir(directory)
        .output()
        .map_err(|e| format!("failed to start {program}: {e}"))?;
    if status.success() {
        Ok(String::from_utf8_lossy(&stdout).trim().to_owned())
    } else {
        let detail = String::from_utf8_lossy(&stderr).trim().to_owned();
        Err(if detail.is_empty() {
            format!("{program} failed with {status}")
        } else {
            detail
        })
    }
}
