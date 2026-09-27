use std::ffi::OsStr;
use std::fmt::Arguments;
use std::io;
use std::process::{Command, ExitStatus, Output};
use std::sync::atomic::{AtomicBool, Ordering};

static TRACE: AtomicBool = AtomicBool::new(false);
static VERBOSE: AtomicBool = AtomicBool::new(false);

pub fn set_modes(verbose: bool, trace: bool) {
    VERBOSE.store(verbose, Ordering::Relaxed);
    TRACE.store(trace, Ordering::Relaxed);
}

pub fn action(message: Arguments<'_>) {
    if VERBOSE.load(Ordering::Relaxed) {
        eprintln!("* {message}");
    }
}

pub fn output(command: &mut Command) -> io::Result<Output> {
    trace(command);
    command.output()
}

pub fn status(command: &mut Command) -> io::Result<ExitStatus> {
    trace(command);
    command.status()
}

fn trace(command: &Command) {
    if TRACE.load(Ordering::Relaxed) {
        eprintln!("+ {}", display(command));
    }
}

fn display(command: &Command) -> String {
    let mut parts = vec![quote(command.get_program())];
    let mut redact_next = false;
    for argument in command.get_args() {
        if redact_next {
            parts.push("<redacted>".to_owned());
            redact_next = false;
        } else {
            parts.push(quote(argument));
            redact_next = argument == OsStr::new("--password");
        }
    }

    match command.get_current_dir() {
        Some(directory) => format!("[{}] {}", directory.display(), parts.join(" ")),
        None => parts.join(" "),
    }
}

fn quote(value: &OsStr) -> String {
    let value = value.to_string_lossy();
    if !value.is_empty()
        && value
            .chars()
            .all(|character| character.is_ascii_alphanumeric() || "-._/:\\".contains(character))
    {
        value.into_owned()
    } else {
        format!("{value:?}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn command_display_includes_directory_and_quotes_arguments() {
        let mut command = Command::new("git");
        command
            .args(["commit", "-m", "release branch created"])
            .current_dir("work tree");

        assert_eq!(
            display(&command),
            "[work tree] git commit -m \"release branch created\""
        );
    }

    #[test]
    fn command_display_redacts_svn_password() {
        let mut command = Command::new("svn");
        command.args([
            "info",
            "https://example.test/repository",
            "--password",
            "secret value",
            "--non-interactive",
        ]);

        let displayed = display(&command);
        assert!(displayed.contains("--password <redacted>"));
        assert!(!displayed.contains("secret value"));
    }
}
