# Contributing to Viet Template

Thank you for contributing to Viet Template! We welcome bug fixes, performance improvements, documentation updates, and new compatibility scenarios.

## Architecture and Design

Before starting work or proposing architectural changes, please review the relevant documentation:

- [Project Charter](docs/00-project-charter.md) — Scope, design goals, and non-goals
- [System Architecture](docs/01-system-architecture.md) — Module hierarchy and dependencies
- [Velocity Compatibility Specification](docs/06-velocity-2.4.1-compatibility.md) — Authoritative TCK reference and report
- [Repository and Engineering Design](docs/17-repository-engineering.md) — Conventions, baseline, and practices
- [Architecture Decision Records (ADRs)](docs/adr/) — Context for existing architectural choices
- [Implementation Checklist](docs/20-implementation-checklist.md) — Roadmap milestones and completed work

## Core Contribution Rules

1. **Clean-room Development Rule**:
   - Viet Template is an independent, clean-room implementation of a Velocity-compatible surface language.
   - **Never copy or decompile Apache Velocity source code** into Viet Template production modules.
   - Compatibility must be defined through public language specifications, observable behavior, and black-box differential tests.

2. **Language and Behavioral Changes**:
   - Any syntactic or semantic change requires an ADR, specification updates in `docs/`, and differential test coverage in `viet-template-tck`.
   - Never weaken, bypass, or delete differential test scenarios to inflate compatibility scores.

3. **Performance Changes**:
   - Optimization proposals for the runtime or compiler must include before-and-after benchmark evidence (JMH or allocations).

4. **Zero Framework Bloat in Core**:
   - Production modules (`api`, `runtime`, `language-vtl`, `vtl-interpreter`) must remain lightweight, fast, and free of external runtime frameworks (no Spring, Quarkus, Micronaut, ASM, or ByteBuddy dependencies in core).
   - Java baseline is strictly **Java 17** (`options.release.set(17)` / `<release>17</release>`).

## Development Workflow & Dual-Build Parity

Viet Template maintains **first-class dual-build parity** between Gradle 8.12 (Kotlin DSL) and Apache Maven 3.9.9. Every pull request must pass cleanly under both build systems.

### 1. Code Formatting

Both builds enforce Google Java Format (v1.24.0) via Spotless:

- **Gradle**:
  ```bash
  ./gradlew spotlessCheck
  ./gradlew spotlessApply
  ```
- **Maven**:
  ```bash
  ./mvnw spotless:check
  ./mvnw spotless:apply
  ```

### 2. Building and Testing

- **Gradle (Full Verification)**:
  ```bash
  ./gradlew clean build
  ```
- **Maven (Full Verification)**:
  ```bash
  ./mvnw clean verify
  ```

### 3. Running Single Tests

- **Gradle**:
  ```bash
  ./gradlew :viet-template-language-vtl:test --tests "VtlParserDirectiveTest"
  ./gradlew :viet-template-tck:test --tests "VelocityDifferentialTckTest"
  ```
- **Maven**:
  ```bash
  ./mvnw test -pl viet-template-language-vtl -am -Dtest=VtlParserDirectiveTest
  ./mvnw test -pl viet-template-tck -am -Dtest=VelocityDifferentialTckTest
  ```

### 4. Build Parity Validation

Before submitting changes, run the build parity verification script:

```bash
./scripts/verify-build-parity.sh
```

This verifies:
- Module consistency across `settings.gradle.kts` and `pom.xml`.
- Group, artifact, and version identity alignment.
- Java 17 release target and compiler flags (`-parameters`, `-Xlint:all`, `-Werror`).
- Dependency version parity between `libs.versions.toml` and `pom.xml`.
- Binary classfile and resource entry parity across generated JARs.

## Technology Compatibility Kit (TCK) Guidelines

The differential TCK in `viet-template-tck` runs side-by-side scenarios against official Apache Velocity 2.4.1.

### Execution Modes

- **Strict Mode (Default)**:
  ```bash
  ./gradlew :viet-template-tck:test
  ```
  Operates with `viet.tck.mode=STRICT`. Any unclassified difference or unexpected failure halts the build immediately.
- **Permissive Mode**:
  ```bash
  ./gradlew :viet-template-tck:test -Dviet.tck.mode=PERMISSIVE
  ```
  Generates reports without failing on unclassified bugs (useful during exploratory development).
- **Targeted Category**:
  ```bash
  ./gradlew :viet-template-tck:test -Dviet.tck.category=ESCAPING
  ```

### Adding New Compatibility Scenarios

1. Locate or create a corpus file under `viet-template-tck/src/test/java/.../corpus/`.
2. Add scenario using `CompatibilityScenario.builder()`.
3. If behavior matches Velocity 2.4.1, expect `EXACT_MATCH`.
4. If behavior intentionally diverges (e.g. security sandbox, syntax improvement):
   - Register the expectation in `StandardExpectations.java` with a reference to an accepted ADR.
   - Document the rationale in `docs/06-velocity-2.4.1-compatibility.md`.
5. Run tests and verify report generation:
   - `ReportGeneratorInvariantsTest` verifies that all 20 active categories are covered, scenario IDs are unique, and classifications sum correctly.

## Submitting a Pull Request

1. Create a descriptive feature branch (e.g., `git checkout -b fix/foreach-nested-break`).
2. Format code (`./gradlew spotlessApply`).
3. Verify tests and parity (`./gradlew clean build && ./mvnw clean verify && ./scripts/verify-build-parity.sh`).
4. Commit with conventional commit prefixes:
   - `feat:` New feature or engine capability
   - `fix:` Bug fix or semantic correction
   - `test:` New tests or TCK scenarios
   - `docs:` Documentation improvements
   - `refactor:` Code restructuring without behavioral changes
   - `chore:` Build scripts or repository maintenance
5. Push your branch and open a Pull Request using the provided PR template.
