# 17 — Release Process & Maven Central Publishing

This document describes the canonical release procedure for Viet Template (`io.github.minh124199:viet-template-*`).

---

## 1. Architecture & Publishing Principles

1. **Single Authoritative Publisher**:
   - **Apache Maven** (`mvnw`) is the sole authoritative release publisher deploying production artifacts to the **Sonatype Central Publisher Portal** (`central.sonatype.com`).
   - Gradle publication (`maven-publish`) is maintained strictly for local cache installation (`publishToMavenLocal`), metadata validation, and dual-build parity verification. The release workflow never publishes remotely via Gradle.
2. **Modern Central Portal Infrastructure**:
   - Deployment utilizes `org.sonatype.central:central-publishing-maven-plugin:0.11.0` in the `release` profile, replacing legacy OSSRH (`s01.oss.sonatype.org`) and `nexus-staging-maven-plugin`.
   - The plugin is configured with `<publishingServerId>central</publishingServerId>`, `<autoPublish>true</autoPublish>`, and `<waitUntil>published</waitUntil>`.
   - Normal builds (`./mvnw clean verify`, `./mvnw test`) never attempt to sign or publish artifacts.
3. **Publication Terminal State Contract**:
   - Live publication (`./mvnw clean deploy -P release`) blocks until Sonatype Central Publisher Portal successfully validates and transitions artifacts to the `PUBLISHED` state.
   - GitHub Release creation is strictly gated on successful Central publication; failures or timeouts halt the pipeline before any GitHub Release is created.
4. **Defense-in-Depth TCK Exclusion**:
   - The test kit module (`viet-template-tck`) contains test fixtures, differential adapters, and Apache Velocity dependencies. It is strictly internal and **never deployed**.
   - Exclusion is enforced via:
     - `viet-template-tck/pom.xml`: `<maven.deploy.skip>true</maven.deploy.skip>`, `<skipPublishing>true</skipPublishing>`, and plugin-level `<configuration><skipPublishing>true</skipPublishing></configuration>`.
     - `build.gradle.kts`: zero publication declarations for `:viet-template-tck`.
     - Automated bundle validation script (`scripts/validate-release-bundle.py`).
5. **Clean-Room & Leakage Safeguards**:
   - Production JARs (`api`, `runtime`, `language-vtl`, `vtl-interpreter`) must contain zero test classes, zero TCK classes, and zero Apache Velocity implementation classes.

---

## 2. Release Preconditions

Before initiating a release:

1. **Clean Git State**: Working directory must be clean (`git status` shows no uncommitted changes).
2. **Test Suite Green**:
   - Gradle: `./gradlew clean build` passes 100%.
   - Maven: `./mvnw clean verify` passes 100%.
3. **Dual-Build Parity Green**:
   - `./scripts/verify-build-parity.sh` reports 100% parity across all 4 production JARs.
4. **Version Alignment & Non-SNAPSHOT**:
   - Both `pom.xml` and `build.gradle.kts` must specify the exact release version (e.g. `0.1.0`, without `-SNAPSHOT`).
   - Run `./scripts/verify-release-metadata.py --require-non-snapshot --check-workflow-contract` to verify.
5. **Changelog Finalized**:
   - Finalize the release entry in `CHANGELOG.md` (e.g. `## [0.1.0] - 2026-09-06`) while preserving the existing `## [Unreleased]` section at the top.
6. **External Credentials Configured**:
   - The GitHub repository must have the following secrets configured in repository settings or in the `release` environment:
     - `MAVEN_CENTRAL_USERNAME`: Sonatype Central Portal User Token.
     - `MAVEN_CENTRAL_PASSWORD`: Sonatype Central Portal Token Password.
     - `SIGNING_KEY`: ASCII-armored GPG private key.
     - `SIGNING_PASSWORD`: Passphrase for the GPG private key.

---

## 3. Release Simulation & Dry-Run

The release pipeline can be validated locally or in CI without credentials:

### 3.1 Simulated v0.1.0 Release Validation

Execute the automated release simulation script:

```bash
./scripts/simulate-release.sh
```

This creates an isolated temporary workspace, substitutes `0.1.0`, validates metadata against tag `v0.1.0`, assembles release packages in Maven and Gradle, validates bundle contents and POMs, confirms zero leaks, and leaves the working tree untouched at `0.1.0-SNAPSHOT`.

### 3.2 Local Bundle Validation

```bash
# 1. Assemble artifacts under both build systems
./mvnw clean package -P release -Dgpg.skip=true -DskipTests -B
./gradlew assemble generatePomFileForMavenJavaPublication --no-daemon

# 2. Validate production bundle integrity
./scripts/validate-release-bundle.sh --build-tool both
```

This verifies:
- All 4 production modules produce binary JAR, sources JAR, Javadoc JAR, and valid POM.
- No test classes, TCK classes, or Velocity classes leaked into production JARs.
- `viet-template-tck` is absent from the publication set.
- All POMs specify required Maven Central metadata (groupId, license, developers, scm).

### 3.3 CI Dry-Run via GitHub Actions

Trigger the **Release** workflow via `workflow_dispatch`:
- Input `dry_run`: `true` (default).
- Input `recovery_mode`: `false` (default).

The workflow executes metadata validation, full multi-JDK verification, artifact assembly, and bundle inspection, without uploading any artifacts.

### 3.4 Emergency Recovery Publication (Exception Path Only)

Manual dispatch with live publication is strictly restricted as an emergency recovery path (e.g. if the tag-triggered workflow failed at Sonatype Central during an external portal outage after the tag was pushed). To trigger recovery publication:
- Select the release tag in GitHub Actions.
- Set `dry_run`: `false`.
- Set `recovery_mode`: `true`.
- Enter confirmation string: `I CONFIRM RECOVERY PUBLISH`.

> [!WARNING]
> Do NOT trigger manual recovery publication after a tag-triggered release has already run. Pushing the release tag is the only normal live release trigger.

---

## 4. Live Release Procedure

When preconditions are satisfied and dry-run succeeds:

### Step 1: Commit and Tag the Release

Follow semantic versioning tag format `vX.Y.Z`:

```bash
git checkout main
git pull origin main

# Tag the commit matching the release version (e.g. 0.1.0)
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin main
git push origin v0.1.0
```

### Step 2: Automated Publication via GitHub Actions

> [!IMPORTANT]
> **Do NOT manually trigger another release workflow after pushing the tag.**
> Pushing tag `v0.1.0` automatically triggers the canonical `.github/workflows/release.yml` pipeline. Simply monitor the automatically triggered workflow in GitHub Actions.

The workflow executes:

1. **Job 1 (`validate-metadata`)**:
   - Asserts tag `v0.1.0` matches repository version `0.1.0`.
   - Asserts version is non-SNAPSHOT.
   - Verifies workflow contract and publishing configuration.
   - Checks that a GitHub Release does not already exist for `v0.1.0`.
   - Sets `is_live_publish = true`.
2. **Job 2 (`verify-builds`)**:
   - Executes `./gradlew check`, `./mvnw clean verify`, and `./scripts/verify-build-parity.sh`.
3. **Job 3 (`package-and-validate-bundle`)**:
   - Assembles release bundles and runs `./scripts/validate-release-bundle.py --build-tool both`.
4. **Job 4 (`publish-to-central`)**:
   - Executes under protected GitHub environment `release`.
   - Imports GPG signing key and configures credentials in temporary Maven settings.
   - Executes `./mvnw clean deploy -P release -DskipTests -Dgpg.passphrase="..." -B`.
   - Blocks until Central transitions deployment to `published`.
   - Cleans up temporary credentials and GPG keys in a post-execution step.
5. **Job 5 (`create-github-release`)**:
   - Runs **only after** `publish-to-central` reports successful publication.
   - Creates the official GitHub Release with generated release notes and tag metadata via `gh release create`.
   - *(Note: Production JARs, sources, and Javadoc bundles are published directly to Maven Central; GitHub Releases contain tag metadata and generated release notes.)*

---

## 5. Operational Notes & Post-Release Lifecycle

1. **Sonatype Central Operational Limits**:
   - Sonatype Central Portal typically processes and validates uploads within 5–15 minutes.
   - Once validated, deployments with `autoPublish=true` transition automatically to `PUBLISHED` and sync to canonical Maven Central mirrors (`repo1.maven.org`) within 15–30 minutes.
2. **Verify Central Publication**:
   - Monitor [Sonatype Central Portal](https://central.sonatype.com/) until the deployment transitions to `PUBLISHED`.
   - Check sync to [Maven Central](https://repo1.maven.org/maven2/io/github/minh124199/).
3. **Bump to Next Development Version**:
   - Update `version` in `pom.xml` and `build.gradle.kts` to next SNAPSHOT (e.g., `0.1.1-SNAPSHOT`).
   - Keep the existing `## [Unreleased]` section in `CHANGELOG.md` and begin recording post-0.1.0 changes there (do not create duplicate `[Unreleased]` sections).
   - Verify build parity: `./scripts/verify-build-parity.sh`.
   - Commit and push to `main`: `chore: prepare next development iteration [skip ci]`.
