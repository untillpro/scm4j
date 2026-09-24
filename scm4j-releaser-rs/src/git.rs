use std::ffi::OsStr;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

pub struct Git {
    directory: PathBuf,
}

impl Git {
    pub fn new(directory: PathBuf) -> Self {
        Self { directory }
    }

    pub fn clone(repository: &str, directory: &Path) -> Result<Self, String> {
        let parent = directory
            .parent()
            .ok_or_else(|| "invalid repository directory".to_owned())?;
        let name = directory
            .file_name()
            .ok_or_else(|| "invalid repository directory".to_owned())?;
        run_in(
            parent,
            "git",
            [
                OsStr::new("clone"),
                OsStr::new("--origin"),
                OsStr::new("origin"),
                OsStr::new(repository),
                name,
            ],
        )?;
        Ok(Self::new(directory.to_owned()))
    }

    pub fn run<I, S>(&self, args: I) -> Result<String, String>
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        run_in(&self.directory, "git", args)
    }

    pub fn succeeds<I, S>(&self, args: I) -> bool
    where
        I: IntoIterator<Item = S>,
        S: AsRef<OsStr>,
    {
        Command::new("git")
            .args(args)
            .current_dir(&self.directory)
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
