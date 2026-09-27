#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ScmType {
    Git,
    Svn,
}

#[derive(Clone, Debug)]
pub struct Config {
    pub component: String,
    pub release_line: Option<String>,
    pub scm_type: ScmType,
    pub repository: String,
    pub subfolder: String,
    pub develop_branch: Option<String>,
    pub release_branch_prefix: String,
    pub reference_namespace: String,
    pub build_command: String,
    pub after_tag: Option<String>,
    pub version_file: String,
    pub tag_prefix: String,
    pub username: Option<String>,
    pub password: Option<String>,
}
