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
