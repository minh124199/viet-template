# 17 — Release Process & Maven Central Publishing

This is the canonical release and incident-recovery procedure for
`io.github.minh124199:viet-template-*`.

## 1. Immutable publication model

Apache Maven is the only authoritative remote publisher. Gradle publication exists for local
installation, metadata validation, and build parity; it never publishes releases remotely.

Once Maven Central accepts or publishes a version, that version must never be rebuilt and
republished with different bytes. Never delete, move, or force-push a release tag to correct a
published version. Use the next patch version for fixes.

Expected public coordinates in Maven Central:

> **Publication Scope & Branch State Distinction**:
> - **Historically Published Coordinates (`0.2.0`)**: Published 5 public coordinates (`viet-template-parent`, `viet-template-api`, `viet-template-runtime`, `viet-template-language-vtl`, `viet-template-vtl-interpreter`). In accordance with Maven Central immutability rules, published 0.2.0 artifacts remain immutable and unchanged.
> - **Remote `main`**: Authoritatively contains the merged reactor modules on GitHub. Newly developed modules (such as `viet-template-spring-security`) are developed separately on integration branches and are not part of remote `main` until formally merged.
> - **Working Tree / Integration State**: May include active unmerged modules (e.g. `viet-template-spring-security`). The release infrastructure dynamically derives the publication set directly from the active reactor `pom.xml` configuration rather than relying on brittle hardcoded counts.
> - **Planned Next Release (`0.2.1-SNAPSHOT` $\to$ `0.2.1`)**: Will publish all approved production modules in the merged reactor with verified and corrected POM/SCM metadata.

The following internal verification modules must remain strictly absent from Maven Central:

- `viet-template-tck` (Technology Compatibility Kit)
- `viet-template-benchmarks` (JMH benchmarks suite)

Their exclusion is enforced in each module POM, the Central publisher, Gradle publication
selection, workflow-contract checks, bundle validation, and the post-publication audit. Non-published
modules must never declare `<url>` and must set `<maven.deploy.skip>true</maven.deploy.skip>`,
`<skipPublishing>true</skipPublishing>`, and `<central.publishing.skip>true</central.publishing.skip>`.

## 1.1 Maven publication metadata inheritance and URL convention

In multi-module Apache Maven projects, child POMs inherit metadata from parent POMs according to
strict inheritance rules:
1. **SCM Appended Path Defect**: By default, Maven appends `/<artifactId>` to inherited `<connection>`,
   `<developerConnection>`, and `<url>` tags in `<scm>`, producing invalid Git URLs such as
   `scm:git:https://github.com/minh124199/viet-template.git/viet-template-api`.
2. **SCM Inheritance Controls**: The root `pom.xml` explicitly suppresses path appending using attributes:
   ```xml
   <scm child.scm.connection.inherit.append.path="false"
        child.scm.developerConnection.inherit.append.path="false"
        child.scm.url.inherit.append.path="false">
       <connection>scm:git:https://github.com/minh124199/viet-template.git</connection>
       <developerConnection>scm:git:ssh://git@github.com/minh124199/viet-template.git</developerConnection>
       <url>https://github.com/minh124199/viet-template</url>
   </scm>
   ```
   These inheritance-control attributes (`child.scm.*.inherit.append.path="false"`) are standard Maven model attributes supported since Maven 3.6.1.
   All effective POMs for parent and child modules resolve clean repository-level SCM coordinates.
3. **Project URL Inheritance Defense-in-Depth**: The root `<project>` element declares:
   ```xml
   child.project.url.inherit.append.path="false"
   ```
   (also supported since Maven 3.6.1). This prevents Maven from appending child paths if a POM omits `<url>`.
4. **Mandatory Module Tree URL Convention**: As defense-in-depth, every Central-published child module
   must explicitly declare:
   ```xml
   <url>${github.repository.url}/tree/main/<module></url>
   ```
   This resolves directly to the respective module's source tree on GitHub while the root POM points to
   `https://github.com/minh124199/viet-template`. Release verification tooling strictly fails if any published child
   module merely inherits the repository-root URL.
5. **Automated Verification**: Build parity (`scripts/verify-build-parity.py`), publication metadata
   verification (`scripts/verify-release-metadata.py --check-publication-metadata --check-effective-pom`),
   and release bundle validation (`scripts/validate-release-bundle.py`) guard these invariants in local builds and in CI workflows once merged.


## 1.2 Publication status taxonomy & manifest headers

The repository maintains an authoritative, machine-readable publication topology matrix in
`config/compatibility/publication-topology.json`. This taxonomy classifies all reactor modules,
reconciles historical publication events, defines the 1.0 General Availability (GA) publication
scope, and records the factual JAR manifest metadata across the project.

### Intended publication scope for 1.0 GA (12 Production Modules + Parent POM)

The intended 1.0 GA publication scope comprises **13 artifacts** (12 production modules + `viet-template-parent`):

| Module Artifact ID | Packaging | Configured for Publication | Historically Published Releases | Intended for 1.0 GA? |
|---|---|---|---|---|
| `viet-template-parent` | POM | Yes | `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-api` | JAR | Yes | `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-runtime` | JAR | Yes | `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-language-vtl` | JAR | Yes | `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-vtl-interpreter` | JAR | Yes | `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-spring` | JAR | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-spring-security` | JAR | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-spring-boot-autoconfigure` | JAR | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-spring-boot-starter` | JAR | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-maven-plugin` | Maven Plugin | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-gradle-plugin` | JAR (Plugin) | Yes | `0.2.1`, `0.2.2`, `1.0.0-RC1 (prerelease)` | **Yes** |
| `viet-template-quarkus` | JAR | Yes | `1.0.0-RC1 (prerelease; first publication)` | **Yes** |
| `viet-template-quarkus-deployment` | JAR | Yes | `1.0.0-RC1 (prerelease; first publication)` | **Yes** |

Two modules remain strictly internal and non-published:
- `viet-template-tck` (Technology Compatibility Kit test suite)
- `viet-template-benchmarks` (JMH performance benchmark suite)

Both non-published modules configure `<maven.deploy.skip>true</maven.deploy.skip>`, `<skipPublishing>true</skipPublishing>`, and `<central.publishing.skip>true</central.publishing.skip>` to ensure they are never uploaded or released remotely.

### Historical status reconciliation: 0.2.3 (`PREPARED_HELD`) and 0.3.0-SNAPSHOT

Historical commit logs and documentation contain references to version `0.2.3`. The authoritative publication history is reconciled as follows:
- **0.1.0 and 0.2.0**: Published 5 coordinates (`viet-template-parent`, `viet-template-api`, `viet-template-runtime`, `viet-template-language-vtl`, `viet-template-vtl-interpreter`).
- **0.2.1 and 0.2.2**: Published 11 coordinates (adding the 4 Spring modules and 2 build plugins).
- **0.2.3 Status (`PREPARED_HELD`)**: Commit `b8b3a8f` ("release: prepare 0.2.3") updated project descriptors to version `0.2.3` in preparation for a stabilization release. However, this release was **never tagged in Git** (`git tag v0.2.3` does not exist) and was **never uploaded or published to Sonatype / Maven Central**. Its historical status is authoritatively classified as `PREPARED_HELD`.
- **Quarkus Artifacts Publication History**: `viet-template-quarkus` and `viet-template-quarkus-deployment` were introduced during the 0.3.0 development cycle (commit `40974a7`). They were never tagged, published, or released under 0.2.x (`Never published. First intended release: 0.3.0+ / 1.0 candidate line`).
- **0.3.0-SNAPSHOT State**: Post-preparation, active development advanced to `0.3.0-SNAPSHOT`. This snapshot stream is strictly internal and **unpublished** (`currentSnapshotPublished: false`).

### Factual absence of `Automatic-Module-Name` manifest headers

Earlier architectural notes and historical roadmaps contemplated declaring Java Platform Module System (JPMS) headers in module manifests. A comprehensive audit of built JAR manifests establishes the factual reality:
- **Manifest Header Status**: `NO_AUTOMATIC_MODULE_NAME_HEADER` (`declared: false`).
- **Audit Findings**: JAR manifests generated by Maven (`maven-jar-plugin`) and Gradle (`jar` task) do not declare `Automatic-Module-Name`.
- **Architectural Rationale**: JPMS modularization is explicitly deferred to post-1.0 (targeted for milestone M22 module boundary review). Injecting premature automatic module names would commit the project to synthetic JPMS module names before physical module boundaries (such as between `language-vtl` and `vtl-interpreter`) are consolidated.
- **Runtime Model**: On Java 17 and later, all Viet Template JARs run cleanly on the standard classpath in the unnamed module.

### Gradle plugin distribution model

The Viet Template Gradle Plugin (`io.github.minh124199.viet-template`) is published exclusively to Maven Central:
- **Maven Coordinates**: `io.github.minh124199:viet-template-gradle-plugin`
- **Gradle Plugin Portal Status**: `gradlePluginPublishing: { "mavenCentral": true, "gradlePluginPortal": false }`
- **Consumer Resolution**: Consumers must declare `mavenCentral()` in their `pluginManagement.repositories` block (in `settings.gradle` or `settings.gradle.kts`) to resolve the plugin. The plugin is intentionally not published to `plugins.gradle.org`.


## 2. The 0.2.0 incident model

Deployment `7c8cd16e-b123-4422-81c9-b19a324570cb` followed this exact sequence:

```text
Maven reactor completed artifact assembly/signing
        ↓
Central bundle uploaded successfully
        ↓
Sonatype returned deployment ID
        ↓
autoPublish began server-side
        ↓
plugin waited for PUBLISHED
        ↓
30-minute client-side wait expired
        ↓
Maven returned non-zero
        ↓
GitHub publish job marked failed
        ↓
GitHub Release job skipped
        ↓
Sonatype continued asynchronously
        ↓
deployment eventually reached PUBLISHED
```

Central took about 68 minutes, outliving the plugin's 30-minute default client wait. The bundle
was accepted and validated; the local observer timed out while server-side publication continued.
Therefore:

```text
publication submission failure != publication observation timeout
```

## 3. Release state machine

The workflow models these successful states:

```text
PRECHECK
  → BUILD_VERIFIED
  → BUNDLE_VALIDATED
  → UPLOAD_ACCEPTED
  → CENTRAL_VALIDATING
  → CENTRAL_PUBLISHING
  → CENTRAL_PUBLISHED
  → PUBLIC_ARTIFACTS_VERIFIED
  → GITHUB_RELEASE_CREATED
```

Failures are classified as `METADATA_INVALID`, `BUILD_FAILED`, `BUNDLE_INVALID`,
`UPLOAD_REJECTED`, `CENTRAL_FAILED`, `PUBLICATION_TIMEOUT`, `PUBLIC_ARTIFACT_MISMATCH`, or
`GITHUB_RELEASE_FAILED`. `PENDING`, `VALIDATING`, `VALIDATED`, and `PUBLISHING` are non-terminal
Central states, never upload failures.

## 4. Workflow architecture

The previous chain was:

```text
metadata → builds/TCK/parity → bundle → Maven deploy waits for PUBLISHED → GitHub Release
```

The hardened chain is:

```text
metadata → builds/TCK/parity → bundle → immutable-version guard
  → sign + upload + validate + capture deployment ID
  → monitor Central to PUBLISHED (30-second polling, 120-minute timeout)
  → verify public artifacts (15-second retry, up to 10 minutes)
  → fresh Maven and Gradle Central-only consumer checks on Java 17
  → create or verify GitHub Release
```

The Sonatype plugin remains version `0.11.0` with `autoPublish=true`, but now uses
`waitUntil=validated`. Maven reports build, signing, upload, and validation failures without
waiting for long publication propagation. `scripts/check-central-deployment.py` owns publication
observation and returns `0=PUBLISHED`, `1=FAILED`, `2=non-terminal/timeout`, and
`3=API/auth/parse error`. It emits `state=...` and `deployment_id=...` without logging credentials.

The upload log is retained for 30 days. `scripts/extract-central-deployment-id.py` recognizes the
plugin's structured `deploymentId:` field and fails unless exactly one unique UUID is present.
That UUID is a job output consumed by the monitor.

Only the upload and authenticated monitor jobs use the protected `release` environment. Build,
bundle, public verification, and consumer jobs receive no publishing or signing secrets. GPG uses
the supported `MAVEN_GPG_PASSPHRASE` environment variable; the deprecated command-line
`gpg.passphrase` property is forbidden by contract validation.

## 5. Release qualification and dry run

Before tagging, keep all existing gates green:

```bash
./gradlew spotlessCheck --no-daemon
./mvnw spotless:check -B
./gradlew check --no-daemon -Dspotless.check.skip=true
./mvnw clean verify -B -Dspotless.check.skip=true
python3 scripts/verify-build-parity.py
python3 scripts/verify-release-metadata.py --require-release --require-match-tag \
  --tag vX.Y.Z --check-workflow-contract --check-publication-metadata --check-effective-pom
python3 -m unittest discover -s scripts/tests -v
```

Spotless runs on Java 21; release verification and TCK remain on Java 17. A manual Release workflow
dispatch defaults to `dry_run=true` and performs metadata, build, TCK, parity, unsigned release
assembly, publication-exclusion, and bundle-contract validation without Central credentials or an
upload. Never use `mvn deploy -P release` to test the workflow.

Normal live publication occurs only on the first run of a canonical `vX.Y.Z` tag push whose tag
matches the non-SNAPSHOT Maven and Gradle version. Release concurrency is global and never cancels
an in-flight run. A tag workflow rerun is prohibited from deploying because a deployment can be
accepted yet not visible on the public CDN.

## 6. Duplicate protection and recovery

Before upload, `scripts/verify-central-release.py --mode guard` checks every expected public file.
An entirely absent version may proceed on a first tag run. A fully published version skips upload
and proceeds through verification. Partial visibility is `PUBLIC_ARTIFACT_MISMATCH` and stops the
workflow. The tag-run-attempt guard covers the acceptance-to-CDN gap that public existence checks
cannot see.

If an upload succeeds but the runner dies or publication monitoring times out:

1. Do not redeploy immediately and do not rerun the failed tag workflow.
2. Capture the deployment ID from the `central-submission-log` artifact.
3. Use **Check Deployment Status** for one-shot or bounded polling.
4. If the state is `PENDING`, `VALIDATING`, `VALIDATED`, or `PUBLISHING`, continue polling.
5. If it is `FAILED`, inspect Central's evidence and use a new version for changed bytes.
6. If it is `PUBLISHED`, select the existing release tag in **Release**, set `dry_run=false`, and
   enter the existing UUID in `recovery_deployment_id`.
7. Recovery never runs Maven deploy. It monitors the given deployment if needed, verifies public
   artifacts and clean consumers, then creates or verifies the GitHub Release.

At the 120-minute observation limit the checker reports `PUBLICATION_TIMEOUT`, deployment ID, and
last observed state. Timeout is intentionally finite and does not imply that Central stopped.

GitHub Release finalization is idempotent: an existing non-draft release for the immutable tag is
verified and accepted; an absent release is created with generated notes. It never creates a
duplicate and only runs after public-coordinate and consumer verification.

---

## 7. Release Candidate (RC) Staging, One-SHA Provenance, and Manifest Invariants

Milestones M8 and M8.9 formalize the multi-stage candidate release qualification workflow:

```text
Contract Freeze (ADR-0020)
        ↓
RC Version Preparation (1.0.0-RC1 across build files)
        ↓
Full Clean Builds (Gradle check + Maven verify)
        ↓
Authoritative Commit Finalization (Commit SHA locked)
        ↓
Clean-Room Local Staging (build/rc-repository/)
        ↓
Artifact Manifest Generation (build/reports/rc-artifacts.json with SHA-256 and source_commit_sha)
        ↓
External Consumer Matrix Qualification (Plain Maven/Gradle, TCK, AOT parity, Spring, Quarkus)
        ↓
Native-Image Qualification (Spring Boot 3/4 Native on GraalVM 25, Quarkus Native on Mandrel 25)
        ↓
Explicit Remote Publication Authorization
        ↓
Automated Remote Release Workflow (.github/workflows/release.yml, prerelease=true)
        ↓
Central Post-Publication Smoke Verification
        ↓
Release Candidate Soak Period
        ↓
1.0.0 GA Promotion
```

### 7.1 The One-SHA Release Provenance Invariant

A remotely published Release Candidate must correspond **exactly** to the final locally qualified RC commit SHA and artifact checksum manifest. Staging artifacts before a commit, modifying code or tests, and then claiming qualification without rebuilding staged artifacts introduces a provenance gap. The repository strictly enforces:

$$\text{One SHA} \implies \text{One Artifact Set} \implies \text{One Checksum Manifest} \implies \text{One Qualification Pass} \implies \text{Publication Readiness}$$

Any tracked commit made after an artifact build invalidates release evidence and requires rebuilding the staged repository and rerunning all artifact-consuming qualification gates.

### 7.2 The Release Artifact Manifest Rule

Before remote publication, the release engineer must generate `build/reports/rc-artifacts.json` capturing:
- `version`: Canonical release candidate version (`1.0.0-RC1`).
- `source_commit_sha`: Exact git commit SHA from which artifacts were built.
- `publishedCoordinatesCount`: 13 primary published coordinates.
- `generatedPluginMarkerCount`: 1 generated Gradle plugin marker publication.
- `totalStagedCoordinatesCount`: 14 staged Maven coordinates.
- For each staged file: `groupId`, `artifactId`, `version`, `packaging`, `filename`, `sizeBytes`, `sha256`, and verification flags.

---

## 8. Incident Postmortem: INC-M11-01 (Gradle Plugin Publication Signing Lifecycle)

### 8.1 Incident Summary
During the authorized publication run for `1.0.0-RC2` (workflow run `36109461136` targeting tag `v1.0.0-RC2` at commit `8c0504f45926291964e5b45ff280b8f065cd8f04`), Job 4 (`package-and-validate-bundle`) failed during `./gradlew assemble generatePomFileForVietTemplatePluginMarkerMavenPublication` with:
```text
* What went wrong:
A problem occurred evaluating project ':viet-template-gradle-plugin'.
> Publication with name 'pluginMaven' not found.
```
Central deployment was successfully guarded and aborted before any deployment occurred; zero artifacts were uploaded to Maven Central or any remote repository.

### 8.2 Root Cause Analysis
Gradle's `java-gradle-plugin` registers the `pluginMaven` publication lazily inside an internal `afterEvaluate` lifecycle block. The project's `viet-template-gradle-plugin/build.gradle.kts` attempted an eager collection lookup:
```kotlin
if (hasSigningKey) {
    signing.sign(publishing.publications["pluginMaven"])
}
```
During developer builds and local qualifications where `SIGNING_KEY` was absent (`hasSigningKey == false`), this conditional block was bypassed. However, in the release workflow where `SIGNING_KEY` was provided as a CI secret, the eager collection access executed during Gradle configuration *before* `java-gradle-plugin` had registered `pluginMaven`, immediately throwing an `UnknownDomainObjectException`.

### 8.3 Architectural Remediation
The eager collection indexing was replaced with Gradle's live container collection API:
```kotlin
if (hasSigningKey) {
    publishing.publications
        .matching {
            it.name == "pluginMaven" || it.name == "vietTemplatePluginMarkerMaven"
        }
        .all {
            signing.sign(this)
        }
}
```
This leverages Gradle's live domain object collection semantics:
1. Registration order is irrelevant: the closure automatically executes for existing publications and any publications added in the future by plugins (such as `pluginMaven` from `java-gradle-plugin`).
2. Fragile `afterEvaluate` ordering games are avoided.
3. Both required publications (`pluginMaven` and `vietTemplatePluginMarkerMaven`) dynamically receive signing tasks (`signPluginMavenPublication` and `signVietTemplatePluginMarkerMavenPublication`).

### 8.4 Mandatory Pre-Tag Signing Qualification Invariant
To ensure that signing lifecycle failures cannot recur in future release candidates, clean-room release qualification enforces:
1. **Canonical Verification Tool (`scripts/verify-gradle-signing-lifecycle.py`)**:
   - Generates an in-memory ephemeral RSA 2048-bit OpenPGP key pair via `gpg` in a secure temporary directory (zero production secret exposure).
   - Validates that unauthenticated developer builds remain unaffected (zero signing tasks, configuration succeeds).
   - Validates that signing-enabled builds discover both publications and register both signing tasks.
   - Validates that staged signing generates valid `.asc` signature files.
2. **Gate 10 in M18 Qualification (`scripts/verify-m18-release-gates.sh`)**:
   - Unconditionally executes `scripts/verify-gradle-signing-lifecycle.py`.
3. **CI Release Workflow Enforced**:
   - Release workflow (`.github/workflows/release.yml`) executes `scripts/verify-gradle-signing-lifecycle.py` in `package-and-validate-bundle`.


## Post-GA 1.0.1 development baseline

`v1.0.0` (tag object `ac0dc481da40a718688ab800f1750d7498515c08`,
commit `b951021e9975b8e8103b2402dc244b32a96afaa8`) and its Maven Central
coordinates are immutable. Main now builds `1.0.1-SNAPSHOT`. PR #40 fixes the
atomic replacement/freshness race and belongs to the unreleased 1.0.1 patch.
Do not republish or move 1.0.0 to include this fix.

The release policy verifier selects state from candidate ancestry. Post-GA
compares to `v1.0.0`; product changes report `PATCH_RELEASE_REQUIRED` and never
retroactively request RC4. Exit zero means the development classification is
valid, not that release qualification or publication is approved. Unknown files,
changed GA provenance, and attempts to reuse 1.0.0 fail closed. API/ABI, security,
TCK and framework gates remain independently mandatory: a path classification
cannot establish that an arbitrary product change is patch-compatible.

```bash
python3 scripts/verify-ga-product-freeze.py --include-uncommitted
# Reproduce the historical pre-GA contract explicitly:
python3 scripts/verify-ga-product-freeze.py --release-state pre-ga \
  --baseline-tag v1.0.0-RC3 --candidate v1.0.0 --target-version 1.0.0
```

`verify-ga-readiness.py` remains a static aggregator; post-GA it reports
`PATCH_RELEASE_REQUIRED` or `NOT_READY_FOR_1_0_1`, never permission to tag 1.0.0.
Its framework-entrypoint checks do not execute native or public consumers.
Run the native and consumer suites separately and retain their logs. Central
verification and Central-only smoke tests continue to target **published 1.0.0**;
local/CI reactor and staged consumer tests target **1.0.1-SNAPSHOT**. Success for
published 1.0.0 does not qualify the unreleased patch artifact.
