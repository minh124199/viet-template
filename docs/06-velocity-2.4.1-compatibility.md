# 06 — Apache Velocity 2.4.1 Differential Compatibility TCK

> **Milestone:** M4 — Apache Velocity 2.4.1 Differential Compatibility TCK
> **Status:** Completed & Verified (2026-09-05)
> **Reference Baseline:** Apache Velocity Engine 2.4.1 (`org.apache.velocity:velocity-engine-core:2.4.1`)
> **Evaluated Engine:** Viet Template Reference Interpreter (`viet-template-vtl-interpreter`)
> **Authoritative Source of Truth:** `build/reports/velocity-compat/velocity-compatibility-report.json`

---

## 1. Executive Summary

Milestone M4 establishes an authoritative, repeatable, side-by-side differential test harness (TCK) comparing observable runtime behavior between Apache Velocity Engine 2.4.1 and Viet Template's reference interpreter.

The TCK executes **301 comprehensive test scenarios** across **20 functional categories** under both engines simultaneously, comparing:
1. Rendered character output;
2. Thrown exceptions and error classifications;
3. Context mutations (e.g. via `#set`);
4. Resource loading events and dynamic execution limits.

> **Summary Statement**: Apache Velocity 2.4.1 differential compatibility: **295/301 scenarios exact (98.01%)**, **5 documented intentional differences**, **1 Viet Template extension**, **0 unsupported scenarios**, and **0 unclassified regressions** (**100.00% accounted behavior coverage**).

### Compatibility Scorecard

| Metric | Count | Percentage | Description |
| :--- | :--- | :--- | :--- |
| **Total Scenarios** | **301** | **100.00%** | Total differential scenarios evaluated |
| **`EXACT_MATCH`** | **295** | **98.01%** | Byte-for-byte output and outcome match |
| **`EXPECTED_DIFFERENCE`** | **5** | **1.66%** | Documented intentional architectural differences |
| **`VIET_EXTENSION`** | **1** | **0.33%** | Intentional Viet Template extensions |
| **`UNSUPPORTED`** | **0** | **0.00%** | Unsupported Apache Velocity features |
| **`BUG`** | **0** | **0.00%** | Unclassified differences or defects |
| **Accounted Behavior Coverage** | **301** | **100.00%** | Scenarios conforming to specification |

In default test and CI runs, the TCK operates in **STRICT mode** (`viet.tck.mode=STRICT`). Any unclassified behavioral difference or unexpected failure causes immediate test failure, guaranteeing zero silent regressions.

---

## 2. Architecture & Design

```text
                           ┌───────────────────────────────┐
                           │   CompatibilityScenario       │
                           │   (template, context, config) │
                           └───────────────┬───────────────┘
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
     ┌─────────────────────────────┐               ┌─────────────────────────────┐
     │  Velocity241EngineAdapter   │               │ VietReferenceEngineAdapter  │
     │  (Apache Velocity 2.4.1)    │               │ (viet-template-interpreter) │
     └──────────────┬──────────────┘               └──────────────┬──────────────┘
                    ▼                                             ▼
     ┌─────────────────────────────┐               ┌─────────────────────────────┐
     │  EngineResult (Velocity)    │               │  EngineResult (Viet)        │
     │  - Output: String           │               │  - Output: String           │
     │  - Thrown: Exception        │               │  - Thrown: Exception        │
     │  - Context Mutations        │               │  - Context Mutations        │
     └──────────────┬──────────────┘               └──────────────┬──────────────┘
                    │                                             │
                    └──────────────────────┬──────────────────────┘
                                           ▼
                           ┌───────────────────────────────┐
                           │    DifferentialComparator     │
                           │    (compares results, consul- │
                           │     ts ExpectationRegistry)   │
                           └───────────────┬───────────────┘
                                           ▼
                           ┌───────────────────────────────┐
                           │      ScenarioResult           │
                           │  - Classification             │
                           │  - Divergence notes           │
                           └───────────────┬───────────────┘
                                           ▼
                           ┌───────────────────────────────┐
                           │      ReportGenerator          │
                           │  (enforces invariants, emits  │
                           │   Markdown & JSON reports)    │
                           └───────────────────────────────┘
```

### 2.1 Zero Production Dependency Guarantee

Apache Velocity is **strictly a test-scoped dependency** within `viet-template-tck`. Under no circumstances is Apache Velocity packaged, exposed, or linked in production modules (`viet-template-api`, `viet-template-runtime`, `viet-template-language-vtl`, `viet-template-vtl-interpreter`).

This architectural isolation is enforced at two levels:
1. **Build Descriptors**: `pom.xml` and `build.gradle.kts` declare `org.apache.velocity:velocity-engine-core:2.4.1` with `test` scope only in `viet-template-tck`.
2. **ArchUnit Verification**: Architecture tests (`ArchitectureRulesTest`) verify at compile and test phases that production packages contain zero references to `org.apache.velocity.*`.

### 2.2 Dual Engine Adapters

- **`Velocity241EngineAdapter`**: Boots an isolated, headless instance of Velocity 2.4.1 per test or execution batch. Translates TCK `CompatibilityConfiguration` settings into Velocity runtime properties:
  - `directive.if.empty_check`
  - `velocimacro.arguments.strict`
  - `runtime.strict_mode`
  - `parser.space_gobbling` (`lines`, `none`, `bc`)
  - `resource.loaders`
- **`VietReferenceEngineAdapter`**: Configures `VtlInterpreterOptions`, `VtlParserOptions`, and `ExecutionLimits` for the clean-room reference interpreter.

### 2.3 Strict vs. Permissive Execution Modes

- **`STRICT` (Default)**: Tests fail if any scenario classifies as `BUG` or if an unregistered behavioral divergence occurs. This mode is active during Gradle `./gradlew check` and Maven `./mvnw verify`.
- **`PERMISSIVE`**: Tests execute without failing on `BUG`, recording all divergences into reports for investigative auditing (`-Dviet.tck.mode=PERMISSIVE`).

### 2.4 Declarative Expectation Registry

Non-identical outcomes are governed through an explicit `ExpectationRegistry`. Every intentional difference or extension must declare:
1. Scenario ID;
2. Expected classification (`EXPECTED_DIFFERENCE` or `VIET_EXTENSION`);
3. Architectural rationale;
4. Reference to an Architecture Decision Record (ADR) or specification section.

Unregistered divergences are automatically flagged as `BUG`.

### 2.5 Programmatic Invariant Enforcement

To prevent reporting drift, `ReportGenerator.validateInvariants(List<ScenarioResult>)` programmatically verifies mathematical and structural consistency before generating any report:
- All scenario IDs are unique and non-blank.
- Unique scenario count equals total scenario result count.
- Global classification counts (`EXACT_MATCH`, `EXPECTED_DIFFERENCE`, `VIET_EXTENSION`, `UNSUPPORTED`, `BUG`) sum to total scenarios.
- Sum of category scenario counts equals global scenario total.
- Subtotals for each classification across all categories equal global totals.
- Every category represented in the executed scenarios is present in the scorecard.

A reporting discrepancy immediately triggers `IllegalStateException` and halts the build.

---

## 3. Canonical Scenario Inventory by Category

The canonical test inventory covers **301 distinct scenarios** across **20 active functional categories**:

| Category | Total | Exact | Expected Diff | Extension | Unsupported | Bug | Exact Parity | Accounted |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`ASSIGNMENT`** | 12 | 10 | 2 | 0 | 0 | 0 | 83.33% | 100.00% |
| **`BLOCK_MACRO`** | 2 | 2 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`CONDITIONAL`** | 7 | 7 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`DEFINE`** | 1 | 1 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`ERROR`** | 4 | 3 | 1 | 0 | 0 | 0 | 75.00% | 100.00% |
| **`ESCAPING`** | 56 | 56 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`EVALUATE`** | 2 | 2 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`EXPRESSION`** | 58 | 58 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`FOREACH`** | 12 | 11 | 0 | 1 | 0 | 0 | 91.67% | 100.00% |
| **`INTROSPECTION`** | 1 | 1 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`LEXICAL`** | 13 | 13 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`LITERAL`** | 9 | 9 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`MACRO`** | 6 | 6 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`METHOD_OVERLOAD`** | 4 | 4 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`REFERENCE`** | 30 | 30 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`RESOURCE`** | 3 | 3 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`SECURITY`** | 2 | 0 | 2 | 0 | 0 | 0 | 0.00% | 100.00% |
| **`STRICT_MODE`** | 8 | 8 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`TRUTHINESS`** | 66 | 66 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **`WHITESPACE`** | 5 | 5 | 0 | 0 | 0 | 0 | 100.00% | 100.00% |
| **Total** | **301** | **295** | **5** | **1** | **0** | **0** | **98.01%** | **100.00%** |

> **Metric Definitions**:
> - **Exact Parity**: `(Exact / Total) * 100%` (Byte-for-byte output and outcome match)
> - **Accounted**: `((Exact + Expected Diff + Extension) / Total) * 100%` (Behaviors classified and accounted for, with zero unexpected regressions)

---

## 4. Architectural Decisions & Expected Differences

Out of 301 scenarios, exactly 5 produce documented, deliberate architectural differences (`EXPECTED_DIFFERENCE`):

### 4.1 Division by Zero: Fail-Fast vs. Silent Null

- **Scenario ID**: `arithmetic.divide-by-zero`
- **Category**: `ERROR`
- **Velocity Behavior**: Logs a warning and returns `null`, causing `$x` to render literally as `[$x]`.
- **Viet Template Behavior**: Throws `TemplateRenderException` with diagnostic code `DIVISION_BY_ZERO` and exact source coordinates.
- **Rationale**: Silent arithmetic errors mask defects in financial, billing, and document-generation templates. Viet Template enforces fail-fast arithmetic safety.

### 4.2 Security Sandboxing: Class and Reflection Access

- **Scenario IDs**: `security.denial.class-property`, `security.denial.get-class-method`
- **Category**: `SECURITY`
- **Velocity Behavior**: Permitted by default in standard Velocity Engine unless custom `SecureUberspector` is configured.
- **Viet Template Behavior**: Denies access to `.class`, `getClass()`, `ClassLoader`, `Thread`, `Runtime`, and `ProcessBuilder` by default under the `VTL_SAFE` security profile.
- **Rationale**: Template injection vulnerabilities (RCE) in Velocity frequently pivot through `getClass()`. Viet Template is secure-by-default.

### 4.3 Legacy #set Null-RHS Preservation

- **Scenario IDs**: `set.null-rhs.legacy-preserved.undefined`, `set.null-rhs.legacy-preserved.method-null`
- **Category**: `ASSIGNMENT`
- **Velocity Behavior**: Velocity 2.x removed configuration option `directive.set.null.allowed` and unconditionally assigns `null` to the target.
- **Viet Template Behavior**: Provides explicit compatibility mode (`ignoreSetNullRhs = true`) preserving Velocity 1.x behavior where existing variable values are untouched if the RHS is null or undefined.
- **Rationale**: Many enterprise templates migrating from Velocity 1.7 rely on `#set` preservation semantics for default value cascading.

---

## 5. Viet Template Extensions

### 5.1 Programmatic Loop Termination via `$foreach.stop()`

- **Scenario ID**: `foreach.control.stop-method`
- **Category**: `FOREACH`
- **Velocity Behavior**: Velocity 2.4.1 deprecated and removed programmatic loop stoppage via method calls on loop metadata (`$foreach.stop()`), requiring the `#break` directive instead.
- **Viet Template Behavior**: Supports both `#break` and `$foreach.stop()` for backwards compatibility with legacy templates.

---

## 6. Understanding Test Counts: Differential Scenarios vs. Total TCK Tests

During build execution, test runners report different numbers for differential scenarios and total JUnit tests:
- **301 Differential Compatibility Scenarios**: Executed dynamically within `VelocityDifferentialTckTest` comparing Velocity 2.4.1 and Viet Template side-by-side.
- **37 Supporting & Unit Tests**: Specialized unit, baseline, and architecture tests in `viet-template-tck`:
  - `ArchitectureRulesTest`: 5 tests enforcing ArchUnit module and dependency boundaries.
  - `JavaBaselineTest`: 5 test invocations verifying runtime JVM level and Java 17 bytecode (classfile major 61) targets.
  - `FoundationIntegrationTest`: 1 smoke test.
  - `SemanticCompatibilityProbeTest`: 9 live Velocity 2.4.1 runtime probe tests.
  - `AdditionalSemanticProbeTest`: 4 strict-mode and alternate-value probe tests.
  - `SemanticCompatibilityDifferentialTest`: 9 focused differential test assertions.
  - `ReportGeneratorInvariantsTest`: 4 tests validating report model invariants, ID uniqueness, and scorecard mathematical properties.
- **Total TCK Tests**: **301 + 37 = 338 tests** (previously 334 prior to adding `ReportGeneratorInvariantsTest`).

---

## 7. How to Run the TCK

### 7.1 Gradle Execution

```bash
# Run all differential TCK tests in strict mode
./gradlew :viet-template-tck:test

# Run tests in permissive mode (generates reports without failing on unclassified bugs)
./gradlew :viet-template-tck:test -Dviet.tck.mode=PERMISSIVE

# Run tests for a specific category
./gradlew :viet-template-tck:test -Dviet.tck.category=ESCAPING
```

### 7.2 Maven Execution

```bash
# Run all differential TCK tests
./mvnw test -pl viet-template-tck -am

# Run with custom options
./mvnw test -pl viet-template-tck -am -Dviet.tck.mode=STRICT
```

### 7.3 Generated Reports

Each test execution automatically produces comprehensive reports in both Markdown and JSON:
- **Markdown**: `build/reports/velocity-compat/velocity-compatibility-report.md` (Gradle) or `target/reports/velocity-compat/velocity-compatibility-report.md` (Maven)
- **Structured JSON**: `build/reports/velocity-compat/velocity-compatibility-report.json` (Gradle) or `target/reports/velocity-compat/velocity-compatibility-report.json` (Maven)

---

## 8. Notice & Trademark Clarification

Apache Velocity and Apache are trademarks of the Apache Software Foundation. This project is an independent clean-room implementation and is not affiliated with, sponsored by, or endorsed by the Apache Software Foundation.
