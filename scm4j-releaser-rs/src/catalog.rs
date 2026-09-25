use crate::config::{Config, ScmType};
use crate::version::Version;
use regex::Regex;
use serde_yaml::{Mapping, Value};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};

pub const CC_TEMPLATE: &str = r#"# Coordinates are matched as regular expressions in declaration order.
# Capture groups may be used in url and subfolder values.
#
# mycompany:(.*):
#   url: https://github.com/mycompany/$1.git
#   subfolder: components/$1
#   type: git
#   releaseCommand: ./gradlew publish
#
# ~:
#   releaseBranchPrefix: release/
"#;

pub const CREDENTIALS_TEMPLATE: &str = r#"# Repository URLs are matched as regular expressions in declaration order.
# https?://github\.com/.*:
#   name: user
#   password: token
"#;

pub const CC_LIST_TEMPLATE: &str = r#"# Additional cc.yml sources, one local path or HTTP(S) URL per line.
"#;

#[derive(Debug)]
struct Rule {
    pattern: Option<String>,
    properties: Mapping,
}

pub struct Catalog {
    components: Vec<Rule>,
    credentials: Vec<Rule>,
}

impl Catalog {
    pub fn load(home: &Path) -> Result<Self, String> {
        let component_sources = component_sources(home)?;
        if component_sources.is_empty() {
            return Err(format!(
                "no component configuration found; create {} or set SCM4J_CC",
                home.join("cc.yml").display()
            ));
        }
        let credential_sources = credential_sources(home);
        Ok(Self {
            components: load_rules(home, &component_sources)?,
            credentials: load_rules(home, &credential_sources)?,
        })
    }

    pub fn resolve(&self, coordinates: &str) -> Result<Config, String> {
        let (component, release_line) = coordinate_parts(coordinates)?;
        let repository = self
            .placeholder(&self.components, &component, "url")?
            .filter(|value| !value.is_empty())
            .ok_or_else(|| format!("no url configured for component `{component}`"))?;
        let scm_type = match self
            .property(&self.components, &component, "type")?
            .unwrap_or_else(|| "git".to_owned())
            .to_ascii_lowercase()
            .as_str()
        {
            "git" => ScmType::Git,
            "svn" | "subversion" => ScmType::Svn,
            value => return Err(format!("unsupported SCM type `{value}` for `{component}`")),
        };
        let subfolder = self
            .placeholder(&self.components, &component, "subfolder")?
            .unwrap_or_default()
            .trim_matches(['/', '\\'])
            .replace('\\', "/");
        validate_relative("subfolder", &subfolder)?;
        let develop_branch = self.property(&self.components, &component, "developBranch")?;
        let release_branch_prefix = self
            .property(&self.components, &component, "releaseBranchPrefix")?
            .unwrap_or_else(|| "release/".to_owned());
        let reference_namespace = if scm_type == ScmType::Git && !subfolder.is_empty() {
            format!(
                "{}/",
                component.split(':').next_back().unwrap_or(&component)
            )
        } else {
            String::new()
        };
        let build_command = self
            .property(&self.components, &component, "releaseCommand")?
            .or(self.property(&self.components, &component, "builder")?)
            .unwrap_or_default();
        let after_tag = self.property(&self.components, &component, "afterTag")?;
        let username = self.property(&self.credentials, &repository, "name")?;
        let password = self.property(&self.credentials, &repository, "password")?;

        Ok(Config {
            component,
            release_line,
            scm_type,
            repository,
            subfolder,
            develop_branch,
            release_branch_prefix,
            reference_namespace,
            build_command,
            after_tag,
            version_file: "version".to_owned(),
            tag_prefix: "tags/".to_owned(),
            username,
            password,
        })
    }

    fn property(
        &self,
        rules: &[Rule],
        name: &str,
        property: &str,
    ) -> Result<Option<String>, String> {
        for rule in rules {
            if matches_rule(rule, name)? {
                if let Some(value) = rule.properties.get(Value::String(property.to_owned())) {
                    return yaml_string(value, property);
                }
            }
        }
        Ok(None)
    }

    fn placeholder(
        &self,
        rules: &[Rule],
        name: &str,
        property: &str,
    ) -> Result<Option<String>, String> {
        for rule in rules {
            if matches_rule(rule, name)? {
                if let Some(value) = rule.properties.get(Value::String(property.to_owned())) {
                    let Some(template) = yaml_string(value, property)? else {
                        return Ok(None);
                    };
                    return match &rule.pattern {
                        Some(pattern) => {
                            let regex = full_regex(pattern)?;
                            Ok(Some(regex.replace(name, template.as_str()).into_owned()))
                        }
                        None => Ok(Some(template)),
                    };
                }
            }
        }
        Ok(None)
    }
}

fn component_sources(home: &Path) -> Result<Vec<String>, String> {
    if let Some(value) = env::var_os("SCM4J_VCS_REPOS").or_else(|| env::var_os("SCM4J_CC")) {
        return Ok(split_sources(&value.to_string_lossy()));
    }
    let mut result = Vec::new();
    let priority = home.join("cc.yml");
    if priority.is_file() {
        result.push(priority.to_string_lossy().into_owned());
    }
    let list = home.join("cc");
    if list.is_file() {
        let text = fs::read_to_string(&list)
            .map_err(|e| format!("cannot read {}: {e}", list.display()))?;
        result.extend(text.lines().filter_map(valuable_line).map(str::to_owned));
    }
    Ok(result)
}

fn credential_sources(home: &Path) -> Vec<String> {
    if let Some(value) = env::var_os("SCM4J_CREDENTIALS") {
        return split_sources(&value.to_string_lossy());
    }
    let path = home.join("credentials.yml");
    if path.is_file() {
        vec![path.to_string_lossy().into_owned()]
    } else {
        Vec::new()
    }
}

fn split_sources(value: &str) -> Vec<String> {
    value
        .split(';')
        .filter(|item| !item.trim().is_empty())
        .map(|item| item.trim().to_owned())
        .collect()
}

fn valuable_line(line: &str) -> Option<&str> {
    let value = line.split('#').next().unwrap_or_default().trim();
    (!value.is_empty()).then_some(value)
}

fn load_rules(home: &Path, sources: &[String]) -> Result<Vec<Rule>, String> {
    let mut result = Vec::new();
    for source in sources {
        let content = load_source(home, source)?;
        if content.trim().is_empty() {
            continue;
        }
        let yaml: Value = serde_yaml::from_str(&content)
            .map_err(|e| format!("failed to parse YAML from `{source}`: {e}"))?;
        for (key, value) in ordered_entries(yaml, source)? {
            let pattern = match &key {
                Value::Null => None,
                Value::String(value) if value == "~" => None,
                Value::String(value) => Some(value.clone()),
                _ => return Err(format!("rule key in `{source}` must be a string or ~")),
            };
            let properties = value
                .as_mapping()
                .ok_or_else(|| format!("properties for rule in `{source}` must be a mapping"))?
                .clone();
            result.push(Rule {
                pattern,
                properties,
            });
        }
    }
    Ok(result)
}

fn ordered_entries(value: Value, source: &str) -> Result<Vec<(Value, Value)>, String> {
    match value {
        Value::Null => Ok(Vec::new()),
        Value::Mapping(mapping) => Ok(mapping.into_iter().collect()),
        Value::Tagged(tagged) => ordered_entries(tagged.value, source),
        Value::Sequence(items) => {
            let mut result = Vec::new();
            for item in items {
                let Value::Mapping(mapping) = item else {
                    return Err(format!("ordered map in `{source}` must contain mappings"));
                };
                if mapping.len() != 1 {
                    return Err(format!(
                        "ordered map entry in `{source}` must contain exactly one rule"
                    ));
                }
                result.extend(mapping);
            }
            Ok(result)
        }
        _ => Err(format!(
            "top level of `{source}` must be a mapping or !!omap"
        )),
    }
}

fn load_source(home: &Path, source: &str) -> Result<String, String> {
    if source.starts_with("http://") || source.starts_with("https://") {
        let mut response = ureq::get(source)
            .call()
            .map_err(|e| format!("failed to read config URL `{source}`: {e}"))?;
        return response
            .body_mut()
            .read_to_string()
            .map_err(|e| format!("failed to read config URL `{source}`: {e}"));
    }
    let path = if let Some(path) = source.strip_prefix("file:///") {
        #[cfg(windows)]
        {
            PathBuf::from(path)
        }
        #[cfg(not(windows))]
        {
            PathBuf::from(format!("/{path}"))
        }
    } else {
        let path = PathBuf::from(source);
        if path.is_absolute() {
            path
        } else {
            home.join(path)
        }
    };
    fs::read_to_string(&path).map_err(|e| format!("cannot read {}: {e}", path.display()))
}

fn matches_rule(rule: &Rule, name: &str) -> Result<bool, String> {
    match &rule.pattern {
        Some(pattern) => Ok(full_regex(pattern)?.is_match(name)),
        None => Ok(true),
    }
}

fn full_regex(pattern: &str) -> Result<Regex, String> {
    Regex::new(&format!("^(?:{pattern})$"))
        .map_err(|e| format!("invalid configuration regex `{pattern}`: {e}"))
}

fn yaml_string(value: &Value, property: &str) -> Result<Option<String>, String> {
    match value {
        Value::Null => Ok(None),
        Value::String(value) => Ok(Some(value.clone())),
        Value::Bool(value) => Ok(Some(value.to_string())),
        Value::Number(value) => Ok(Some(value.to_string())),
        _ => Err(format!("property `{property}` must be a scalar")),
    }
}

fn coordinate_parts(coordinates: &str) -> Result<(String, Option<String>), String> {
    let before_extension = coordinates
        .split_once('@')
        .map_or(coordinates, |(coordinates, _)| coordinates);
    let parts: Vec<_> = before_extension.split(':').collect();
    if !(2..=4).contains(&parts.len()) || parts[0].trim().is_empty() || parts[1].trim().is_empty() {
        return Err(format!(
            "invalid component coordinates `{coordinates}`; expected group:artifact[:version]"
        ));
    }
    let component = format!("{}:{}", parts[0].trim(), parts[1].trim());
    let release_line = parts
        .get(2)
        .map(|value| release_line(value.trim()))
        .transpose()?;
    Ok((component, release_line))
}

fn release_line(value: &str) -> Result<String, String> {
    if value.is_empty() || value.ends_with("-SNAPSHOT") {
        return Err(format!("invalid locked version `{value}`"));
    }
    if value.contains('.') {
        return value
            .parse::<Version>()
            .map(|version| version.release_line());
    }
    value
        .parse::<u64>()
        .map(|_| value.to_owned())
        .map_err(|_| format!("invalid locked version `{value}`"))
}

fn validate_relative(name: &str, value: &str) -> Result<(), String> {
    if Path::new(value).is_absolute() || value.split(['/', '\\']).any(|part| part == "..") {
        Err(format!("{name} must be relative to the repository root"))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn locked_coordinate_selects_release_line() {
        assert_eq!(
            coordinate_parts("eu.untill:Untill:152").unwrap(),
            ("eu.untill:Untill".to_owned(), Some("152".to_owned()))
        );
        assert_eq!(
            coordinate_parts("org.example:service:1.2.7").unwrap(),
            ("org.example:service".to_owned(), Some("1.2".to_owned()))
        );
        assert!(coordinate_parts("org.example:service:1.2-SNAPSHOT").is_err());
        assert_eq!(
            coordinate_parts("org.example:archive:2.3@zip").unwrap().1,
            Some("2".to_owned())
        );
        assert_eq!(
            coordinate_parts("org.example:archive:2.3:all@zip")
                .unwrap()
                .1,
            Some("2".to_owned())
        );
    }

    #[test]
    fn resolves_regex_rules_placeholders_defaults_and_credentials() {
        let suffix = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let home = std::env::temp_dir().join(format!("scm4j-catalog-{suffix}"));
        fs::create_dir_all(&home).unwrap();
        fs::write(
            home.join("cc.yml"),
            "'org.example:(.*)':\n  url: https://example.test/repos/all.git\n  subfolder: components/$1\n  releaseCommand: make $1\n'~':\n  releaseBranchPrefix: rel/\n",
        )
        .unwrap();
        fs::write(
            home.join("credentials.yml"),
            "'https://example\\.test/.*':\n  name: robot\n  password: secret\n",
        )
        .unwrap();
        let catalog = Catalog::load(&home).unwrap();
        let config = catalog.resolve("org.example:service").unwrap();
        let _ = fs::remove_dir_all(home);
        assert_eq!(config.subfolder, "components/service");
        assert_eq!(config.build_command, "make $1");
        assert_eq!(config.reference_namespace, "service/");
        assert_eq!(config.username.as_deref(), Some("robot"));
        assert_eq!(config.password.as_deref(), Some("secret"));
    }

    #[test]
    fn accepts_snake_yaml_ordered_map() {
        let yaml: Value = serde_yaml::from_str(
            "!!omap\n- 'org.example:(.*)':\n    url: https://example.test/$1.git\n- ~:\n    releaseCommand: make\n",
        )
        .unwrap();
        let entries = ordered_entries(yaml, "test.yml").unwrap();
        assert_eq!(entries.len(), 2);
        assert!(matches!(entries[0].0, Value::String(_)));
        assert!(matches!(entries[1].0, Value::Null));
    }
}
