use std::cmp::Ordering;
use std::fmt;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Version {
    major: String,
    minor: u64,
    patch: u64,
    pub snapshot: bool,
}

impl Version {
    pub fn next_minor_snapshot(&self) -> Self {
        Self {
            major: self.major.clone(),
            minor: self.minor + 1,
            patch: 0,
            snapshot: true,
        }
    }

    pub fn release_zero(&self) -> Self {
        Self {
            major: self.major.clone(),
            minor: self.minor,
            patch: 0,
            snapshot: false,
        }
    }

    pub fn next_patch(&self) -> Self {
        Self {
            major: self.major.clone(),
            minor: self.minor,
            patch: self.patch + 1,
            snapshot: false,
        }
    }

    pub fn previous_patch(&self) -> Option<Self> {
        self.patch.checked_sub(1).map(|patch| Self {
            major: self.major.clone(),
            minor: self.minor,
            patch,
            snapshot: false,
        })
    }

    pub fn release_line(&self) -> String {
        format!("{}{}", self.major, self.minor)
    }

    pub fn previous_minor_release(&self) -> Option<Self> {
        self.minor.checked_sub(1).map(|minor| Self {
            major: self.major.clone(),
            minor,
            patch: 0,
            snapshot: false,
        })
    }

    pub fn has_zero_patch(&self) -> bool {
        self.patch == 0
    }

    fn numeric_segments(&self) -> Option<Vec<u64>> {
        let mut segments = self
            .release_line()
            .split('.')
            .map(str::parse::<u64>)
            .collect::<Result<Vec<_>, _>>()
            .ok()?;
        segments.push(self.patch);
        Some(segments)
    }
}

impl FromStr for Version {
    type Err = String;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        let value = value.trim();
        if value.is_empty() {
            return Err("version cannot be empty".to_owned());
        }
        let (plain, snapshot) = match value.strip_suffix("-SNAPSHOT") {
            Some(value) => (value, true),
            None => (value, false),
        };
        if plain.is_empty() {
            return Err(format!("invalid version: {value}"));
        }

        let (without_patch, patch) = plain
            .rsplit_once('.')
            .ok_or_else(|| format!("version must have [major-prefix]minor.patch form: {value}"))?;
        if without_patch.is_empty() {
            return Err(format!("invalid version: {value}"));
        }
        let patch = parse_number(patch, value)?;
        let (major, minor) = match without_patch.rsplit_once('.') {
            Some((major, minor)) => (format!("{major}."), parse_number(minor, value)?),
            None => (String::new(), parse_number(without_patch, value)?),
        };

        Ok(Self {
            major,
            minor,
            patch,
            snapshot,
        })
    }
}

fn parse_number(value: &str, complete: &str) -> Result<u64, String> {
    value
        .parse()
        .map_err(|_| format!("invalid numeric version: {complete}"))
}

impl fmt::Display for Version {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}{}", self.major, self.minor)?;
        write!(f, ".{}", self.patch)?;
        if self.snapshot {
            write!(f, "-SNAPSHOT")?;
        }
        Ok(())
    }
}

impl Ord for Version {
    fn cmp(&self, other: &Self) -> Ordering {
        let ordering = match (self.numeric_segments(), other.numeric_segments()) {
            (Some(left), Some(right)) => left.cmp(&right),
            _ => self.to_string().cmp(&other.to_string()),
        };
        ordering.then_with(|| self.to_string().cmp(&other.to_string()))
    }
}

impl PartialOrd for Version {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn three_part_versions_are_transformed_like_scm4j() {
        let version: Version = "1.5.7-SNAPSHOT".parse().unwrap();
        assert_eq!(version.release_line(), "1.5");
        assert_eq!(version.release_zero().to_string(), "1.5.0");
        assert_eq!(version.next_minor_snapshot().to_string(), "1.6.0-SNAPSHOT");
        assert_eq!(version.release_zero().next_patch().to_string(), "1.5.1");
        assert_eq!(
            version
                .release_zero()
                .next_patch()
                .previous_patch()
                .unwrap()
                .to_string(),
            "1.5.0"
        );
        assert!(version.release_zero().previous_patch().is_none());
    }

    #[test]
    fn two_part_versions_use_first_part_as_release_line() {
        let version: Version = "3.0-SNAPSHOT".parse().unwrap();
        assert_eq!(version.release_line(), "3");
        assert_eq!(version.release_zero().to_string(), "3.0");
        assert_eq!(version.next_minor_snapshot().to_string(), "4.0-SNAPSHOT");
        assert_eq!(version.release_zero().next_patch().to_string(), "3.1");
    }

    #[test]
    fn versions_without_patch_are_rejected() {
        assert!("3-SNAPSHOT".parse::<Version>().is_err());
    }

    #[test]
    fn numeric_ordering_does_not_sort_ten_before_two() {
        let two: Version = "2.0".parse().unwrap();
        let ten: Version = "10.0".parse().unwrap();
        assert!(ten > two);
    }

    #[test]
    fn major_prefix_is_preserved_but_not_modified() {
        let version: Version = "1.2.3.0-SNAPSHOT".parse().unwrap();
        assert_eq!(version.release_line(), "1.2.3");
        assert_eq!(
            version.next_minor_snapshot().to_string(),
            "1.2.4.0-SNAPSHOT"
        );
        assert_eq!(
            version.previous_minor_release().unwrap().to_string(),
            "1.2.2.0"
        );
    }
}
