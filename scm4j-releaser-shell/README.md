# Overview

Shell runner for [scm4j-releaser](../scm4j-releaser).

# Usage

- Install JDK 8, Git, and a POSIX-compatible `sh` (Git for Windows supplies both commands)
- Clone the scm4j monorepo
- Run `releaser.cmd` or `releaser` to build and run scm4j-releaser
- Use Git in the monorepo checkout to select the branch, tag, or commit to run
- For more details, see [scm4j-releaser](../scm4j-releaser)

## Windows operator checklist

Run in PowerShell:

```powershell
java -version
git --version
Get-Command git
Get-Command sh
ssh -V
```

- Shell runner: Java 8 from a JDK and the intended `git.exe` and `sh.exe` are mandatory.
  `releaser.cmd` can find Git for Windows' bundled `sh.exe` even when `Get-Command sh` cannot.
- Native-driver tests: `git --version` must report 2.25+. The driver is not wired into the releaser.
- Native-driver SSH tests only: `ssh -V` must report OpenSSH; HTTPS tests do not require `ssh`.
- Builds use the checked-in `gradlew.bat` (`..\gradlew.bat` from this directory); system Gradle is
  not required.

# Under the Hood

- `releaser` resolves the monorepo root from its own location, regardless of the current working directory
- On every invocation, `releaser` uses the root Gradle wrapper to build the scm4j-releaser fat JAR from the current checkout; Gradle skips unchanged build work
- After a successful build, `releaser` runs the generated fat JAR and passes through the supplied CLI arguments
- `releaser.cmd` on Windows finds `git.exe` and `sh.exe`, then launches `sh releaser`
  - It searches `PATH` first for each executable
  - If Git is not on `PATH`, it checks the standard Git for Windows directory beneath the locations provided by the Windows Program Files environment variables
  - If `sh.exe` is not on `PATH`, it checks Git for Windows' standard `usr/bin` directory relative to the discovered `git.exe`
  - For custom installations, add the directories containing `git.exe` and `sh.exe` to `PATH`

The legacy `releaser pull` commands are no longer supported. Update or switch the monorepo checkout with Git instead.

# Historical version

[scm4j-releaser-shell standalone repository](https://github.com/scm4j/scm4j-releaser-shell)
