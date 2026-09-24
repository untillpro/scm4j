use std::fs;
use std::path::Path;

#[derive(Debug)]
pub struct Config {
    pub repository: String,
    pub develop_branch: Option<String>,
    pub release_branch_prefix: String,
    pub build_command: String,
    pub version_file: String,
    pub push: bool,
}

impl Config {
    pub fn load(path: &Path) -> Result<Self, String> {
        let text = fs::read_to_string(path).map_err(|e| {
            format!(
                "cannot read {}: {e}; run `scm4j-releaser init` first",
                path.display()
            )
        })?;
        let mut repository = None;
        let mut develop_branch = None;
        let mut release_branch_prefix = "release/".to_owned();
        let mut build_command = None;
        let mut version_file = "version".to_owned();
        let mut push = true;

        for (index, source_line) in text.lines().enumerate() {
            let line = source_line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let (key, value) = line
                .split_once('=')
                .ok_or_else(|| format!("{}:{}: expected key=value", path.display(), index + 1))?;
            let key = key.trim();
            let value = value.trim();
            match key {
                "repository" => repository = Some(value.to_owned()),
                "develop_branch" if !value.is_empty() => develop_branch = Some(value.to_owned()),
                "develop_branch" => develop_branch = None,
                "release_branch_prefix" => release_branch_prefix = value.to_owned(),
                "build_command" => build_command = Some(value.to_owned()),
                "version_file" => version_file = value.to_owned(),
                "push" => {
                    push = value.parse::<bool>().map_err(|_| {
                        format!(
                            "{}:{}: push must be true or false",
                            path.display(),
                            index + 1
                        )
                    })?
                }
                _ => {
                    return Err(format!(
                        "{}:{}: unknown setting `{key}`",
                        path.display(),
                        index + 1
                    ))
                }
            }
        }

        let repository = repository
            .filter(|v| !v.is_empty())
            .ok_or_else(|| "repository is required in scm4j-releaser.conf".to_owned())?;
        let build_command = build_command
            .filter(|v| !v.is_empty())
            .ok_or_else(|| "build_command is required in scm4j-releaser.conf".to_owned())?;
        if release_branch_prefix.is_empty() || version_file.is_empty() {
            return Err("release_branch_prefix and version_file cannot be empty".to_owned());
        }
        if Path::new(&version_file).is_absolute()
            || version_file.split(['/', '\\']).any(|p| p == "..")
        {
            return Err("version_file must be a relative path inside the repository".to_owned());
        }

        Ok(Self {
            repository,
            develop_branch,
            release_branch_prefix,
            build_command,
            version_file,
            push,
        })
    }
}

pub const TEMPLATE: &str = r#"# scm4j-releaser: one Git repository, one component
repository=https://example.org/company/project.git
# Empty means the branch referenced by origin/HEAD.
develop_branch=
release_branch_prefix=release/
version_file=version
# Executed in an isolated checkout. On Windows cmd /C is used, elsewhere sh -c.
build_command=./gradlew clean build publish
# Set to false for a local dry run (commits and tags stay in the managed clone).
push=true
"#;

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn parses_minimal_config() {
        let suffix = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let path = std::env::temp_dir().join(format!("scm4j-releaser-config-{suffix}"));
        fs::write(
            &path,
            "repository=repo.git\nbuild_command=make release\npush=false\n",
        )
        .unwrap();
        let config = Config::load(&path).unwrap();
        let _ = fs::remove_file(path);
        assert_eq!(config.repository, "repo.git");
        assert_eq!(config.release_branch_prefix, "release/");
        assert_eq!(config.build_command, "make release");
        assert!(!config.push);
    }
}
