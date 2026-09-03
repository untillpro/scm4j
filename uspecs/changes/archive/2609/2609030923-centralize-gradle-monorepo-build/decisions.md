# Clarification decisions

## 2026-09-02: Scope of the root build command

- Decision: The standard build command builds only `scm4j-releaser` and the project subfolders in its dependency closure.
- Rationale: This follows PRIME-95's explicit distinction between building `scm4j-releaser` and running all tests.
- Consequence: Unrelated deliverables are not built by the standard build command, while the standard test command still covers the entire monorepo.

## 2026-09-02: Extent of centralized dependency management

- Decision: The root build owns all dependencies between project subfolders and all third-party dependency declarations.
- Rationale: This gives the monorepo one dependency source of truth and makes the project subfolders source-only as requested by PRIME-95.
- Consequence: Project subfolders retain no independent build configuration.

## 2026-09-02: Resolution of dependencies between scm4j projects

- Decision: Every dependency between included scm4j projects resolves from the corresponding local source subfolder in the current checkout.
- Rationale: The root build must build and test the monorepo's current sources as one coherent unit instead of silently consuming older published scm4j artifacts.
- Consequence: Changes to one scm4j project are validated immediately against all local consumers and may reveal incompatibilities hidden by published-version dependencies.

## 2026-09-02: Projects covered by the root test command

- Decision: The root test command covers every migrated Gradle-based source project, including auxiliary projects such as `scm4j-template` and `scm4j-test-jitpack`.
- Rationale: This gives PRIME-95's requirement to run all tests its literal monorepo-wide meaning.
- Consequence: A test failure in any production, plugin, template, or validation project fails the root test command.

## 2026-09-02: Artifact contract for scm4j-releaser

- Decision: The centralized build preserves `scm4j-releaser`'s existing artifact formats and packaging behavior.
- Rationale: PRIME-95 centralizes build orchestration and dependency resolution but does not request a release-format change.
- Consequence: Producing a runnable fat artifact remains governed by the releaser's existing packaging behavior rather than becoming a new requirement of the standard root build.

## 2026-09-02: Content retained in source-only project subfolders

- Decision: Project subfolders retain source, resources, and relevant descriptive or legal documents, while standalone build, release, and CI configuration is removed or migrated to the repository root when still required.
- Rationale: This makes the root the sole configuration owner without discarding useful project context or legal notices.
- Consequence: Legacy module-level version, dependency, release, and CI configuration must not remain as competing or obsolete configuration.

## 2026-09-03: Visual Studio Code test workflow

- Decision: Opening the repository root in Visual Studio Code must import the complete root Gradle build and provide Run and Debug actions for test classes and methods in every project subfolder.
- Rationale: Contributors must be able to navigate to any project's tests and run them directly without opening that subfolder as a separate workspace.
- Consequence: The repository supplies root-level extension recommendations and shared Java/Gradle workspace settings; per-project VS Code metadata and custom test task or launch definitions are unnecessary.
