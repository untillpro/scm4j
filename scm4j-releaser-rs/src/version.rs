use std::fmt;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct Version {
    pub major: u64,
    pub minor: u64,
    pub patch: u64,
    pub snapshot: bool,
}

impl Version {
    pub fn next_minor_snapshot(&self) -> Self {
        Self {
            major: self.major,
            minor: self.minor + 1,
            patch: 0,
            snapshot: true,
        }
    }

    pub fn release_zero(&self) -> Self {
        Self {
            major: self.major,
            minor: self.minor,
            patch: 0,
            snapshot: false,
        }
    }

    pub fn next_patch(&self) -> Self {
        Self {
            major: self.major,
            minor: self.minor,
            patch: self.patch + 1,
            snapshot: false,
        }
    }

    pub fn previous_patch(&self) -> Option<Self> {
        self.patch.checked_sub(1).map(|patch| Self {
            major: self.major,
            minor: self.minor,
            patch,
            snapshot: false,
        })
    }

    pub fn release_line(&self) -> String {
        format!("{}.{}", self.major, self.minor)
    }
}

impl FromStr for Version {
    type Err = String;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        let value = value.trim();
        let (plain, snapshot) = match value.strip_suffix("-SNAPSHOT") {
            Some(v) => (v, true),
            None => (value, false),
        };
        let fields: Vec<_> = plain.split('.').collect();
        if fields.len() != 3 {
            return Err(format!("version must have MAJOR.MINOR.PATCH form: {value}"));
        }
        let number = |index: usize| {
            fields[index]
                .parse::<u64>()
                .map_err(|_| format!("invalid numeric version: {value}"))
        };
        Ok(Self {
            major: number(0)?,
            minor: number(1)?,
            patch: number(2)?,
            snapshot,
        })
    }
}

impl fmt::Display for Version {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{}.{}.{}{}",
            self.major,
            self.minor,
            self.patch,
            if self.snapshot { "-SNAPSHOT" } else { "" }
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions_are_transformed() {
        let version: Version = "1.5.7-SNAPSHOT".parse().unwrap();
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
}
