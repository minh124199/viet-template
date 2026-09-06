# 14 — Testing, Differential Compatibility and TCK

## 1. Test strategy

```text
framework E2E
compatibility/differential
backend equivalence
semantic/optimizer property tests
parser/runtime unit tests
fuzz/adversarial
```

## 2. Parser golden tests

Fixtures contain source, expected AST snapshot and diagnostics. Snapshots omit unstable implementation ids.

## 3. Semantic tests

For template + model schema, assert root binding, property resolution, type/nullability, access plan, capability decision and diagnostics.

## 4. Interpreter/compiler equivalence

For every supported semantic case:

```text
IR interpreter output == compiled backend output
```

Failure cases compare stable error category and source span rather than brittle full exception strings.

## 5. Velocity differential harness

Apache Velocity Engine 2.4.1 (`org.apache.velocity:velocity-engine-core:2.4.1`) is strictly a test-scoped dependency within `viet-template-tck`.

```java
EngineResult velocity = velocityEngine.execute(scenario, velocityContext, config, resources);
EngineResult viet = vietEngine.execute(scenario, vietContext, config, resources);
ScenarioResult result = comparator.compare(scenario, velocity, viet);
```

Case classifications:

```text
EXACT_MATCH
EXPECTED_DIFFERENCE
VIET_EXTENSION
UNSUPPORTED
BUG
```

See [06-velocity-2.4.1-compatibility.md](06-velocity-2.4.1-compatibility.md) for the authoritative Milestone M4 specification and results (301 scenarios across 20 active categories: 295 exact parity [98.01%], 5 documented expected differences [1.66%], 1 Viet Template extension [0.33%], 0 unsupported, and 0 bugs). Canonical reports are generated at `build/reports/velocity-compat/velocity-compatibility-report.md` and `.json`.

## 6. Compatibility corpus

```text
references/
properties/
methods/
index/
set/
if/
foreach/
include/
parse/
macros/
evaluate/
escaping/
comments/
strings/
numbers/
errors/
```

Every case has metadata identifying profile and expected state.

## 7. Java model corpus

Records, JavaBeans, booleans, getter case variations, maps, arrays, ArrayList/LinkedList, inheritance, interfaces/default methods, overloads, varargs, null getters, throwing getters, non-public members and proxies.

## 8. Property-based tests

Examples:

- text merge preserves output;
- constant folding matches unoptimized interpreter;
- random boolean expression has same optimized result;
- path normalization never escapes configured root;
- escaper obeys encoded invariants.

## 9. Fuzzing

Inputs: random UTF-8, nested `$`/`#`/braces/parentheses, huge identifiers, malformed comments/strings, extreme nesting, Unicode edge cases.

Properties: terminate under limits, bounded memory relative to configured max, no stack overflow, valid diagnostic spans, no JVM crash.

## 10. Security tests

Run complete attack corpus against `VTL_SAFE`. Add regression fixture for every security issue.

## 11. Escaping tests

Test known HTML/XSS payload families per supported context. Where meaningful, parse rendered HTML with an HTML parser and verify structure/attribute values rather than relying only on string snapshots.

## 12. Concurrency and reload

Render same immutable template concurrently with independent contexts and assert no data bleed.

Hot-reload stress: many renderer threads while template generations swap. Complete old or new output is valid; mixed output is not.

## 13. Classloader leak test

Repeated runtime compile/reload generations must become collectible. Use weak references and metaspace monitoring in a long-running test.

## 14. Public TCK

Suites:

```text
LanguageCoreTck
DynamicCompatibilityTck
SecuritySafeProfileTck
BackendEquivalenceTck
RepositoryTck
OutputTck
```

## 15. Release gates

Block release on TCK failure, interpreter/compiler mismatch, security regression, unresolved fuzz reproducer, compatibility manifest mismatch, or unexplained major performance regression.
