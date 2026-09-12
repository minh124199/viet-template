# Robustness, Property-Based Testing, and Differential Fuzzing

## 1. Overview and Testing Strategy

VietTemplate employs a comprehensive multi-layered robustness testing and differential fuzzing framework designed to discover:
- Parser crashes, panics, and lexer edge cases (unclosed constructs, deep nesting, null bytes, unicode surrogates).
- Cross-tier execution divergence across the three execution tiers:
  $$\text{AST} \equiv \text{IR} \equiv \text{AOT\_BYTECODE}$$
- Semantic compatibility divergence against reference Apache Velocity 2.4.1.
- Context-aware escaping and URL validation trust boundary bypasses.
- Deterministic budget and limit enforcement without thread leakage.
- Integer, floating-point, and boundary-value arithmetic issues (overflow, division by zero, non-finite IEEE 754 representations).
- Concurrent execution race conditions and state leakage.

All fuzzing and property tests are **100% deterministic, seeded, self-contained, and reproducible** without relying on un-cached external networks or external runtime dependencies.

---

## 2. Execution Modes: Normal vs Deep

The testing suite supports two operating modes configured via system property `-DvietTemplate.fuzz.mode` or environment variable `VIET_FUZZ_MODE`:

| Mode | Configuration | Iterations / Samples | Target Runtime | Primary Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Normal Mode** (Default) | Default, or `-DvietTemplate.fuzz.mode=normal` | ~100–150 iterations per test | $< 10$ seconds per suite | Every local Gradle/Maven build, pre-commit checks, CI pull requests |
| **Deep Mode** | `-DvietTemplate.fuzz.mode=deep` | ~500–1,000 iterations per test | ~30–60 seconds per suite | Nightly CI cron, dedicated GitHub Actions fuzz workflow, pre-release audits |

### Running in Deep Mode
Via Gradle:
```bash
./gradlew check -DvietTemplate.fuzz.mode=deep --rerun-tasks
```

Via Maven:
```bash
./mvnw verify -DvietTemplate.fuzz.mode=deep
```

---

## 3. Differential Harness Architecture

The differential testing harness resides in `viet-template-tck` under package `io.github.minh124199.viettemplate.tck.differential`:

```
viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/differential/
├── TierDifferentialHarness.java        # Central multi-tier differential execution engine
├── BoundedVtlGenerator.java            # Grammar-guided AST & template generator
├── TemplateMinimizer.java              # Delta-debugging template reducer
├── AstIrAotDifferentialFuzzTest.java   # AST == IR == AOT parity validation
├── VelocityDifferentialFuzzTest.java   # Apache Velocity 2.4.1 cross-engine fuzzing
├── NumericArithmeticEdgeTest.java      # Boundary & non-finite arithmetic verification
├── NestedBudgetFuzzTest.java           # Nested control-flow & budget boundary testing
├── SecurityPolicyDifferentialTest.java # Cross-tier security isolation testing
└── ConcurrencyRobustnessTest.java      # Multi-threaded concurrent execution stress testing
```

### `TierDifferentialHarness`
Executes template sources identically across all three execution tiers:
1. `ExecutionTier.AST` (recursive AST tree-walk interpreter)
2. `ExecutionTier.IR` (linear register-based IR interpreter)
3. `ExecutionTier.AOT_BYTECODE` (compiled JVM bytecode backend)

`TierDifferentialHarness.assertTierParity(result)` verifies:
- If AST succeeds, IR and AOT must also succeed and emit bit-for-bit identical output and mutated context state.
- If AST fails, IR and AOT must fail within the same semantic exception hierarchy (`TemplateSyntaxException`, `TemplateLimitException`, `TemplateSecurityException`, or `TemplateRenderException`).
- Bytecode verifier errors (`VerifyError`, `ClassFormatError`, `BootstrapMethodError`) are flagged as critical P11 failures.

---

## 4. Reproducing Failures via Seed

Every generator and property test consumes a fixed 64-bit seed (e.g. `0xD1FF3871EL`, `0xCAFEBABE1234L`).

When a differential fuzz test fails, it prints diagnostic details including:
- Test iteration number
- PRNG Seed (e.g., `Seed: 0xD1FF3871E`)
- Original input template and variable context
- Minimized reproducer template
- Expected vs actual outputs or failure stack trace

To reproduce a specific failure deterministically:
1. Instantiate `new SplittableRandom(FAILING_SEED)`.
2. Fast-forward the PRNG to the reported iteration index, or directly run the minimized template reported in the failure log.
3. Every test fixture is hermetic and does not depend on wall-clock time or OS-specific thread scheduling.

---

## 5. Automated Test Minimization

The `TemplateMinimizer` provides automated delta-debugging for template sources:

```java
TemplateMinimizer.MinimizedCase minimized =
    TemplateMinimizer.minimize(
        originalSource,
        context,
        src -> doesReproduceBug(src, context));
```

### Minimization Strategies
1. **Line Deletion**: Sequentially attempts removing lines or blocks while checking if the reproducer condition still holds.
2. **Binary Halving**: Divides large template blocks in halves to isolate the minimal offending directive block in $O(\log N)$ steps.
3. **Identifier / String Simplification**: Reduces complex variable expressions down to minimal primitives.

Minimized reproducers are saved permanently to `src/test/resources/regressions/` or as unit tests in the appropriate module.

---

## 6. Continuous Integration Workflow

Nightly and on-demand deep differential fuzzing is managed via `.github/workflows/fuzz.yml`:
- Triggered on schedule (`0 2 * * *` UTC) and manually via `workflow_dispatch`.
- Strict security permissions: `contents: read`.
- All actions pinned to full commit SHAs.
- Runs both Gradle and Maven builds with `-DvietTemplate.fuzz.mode=deep`.
