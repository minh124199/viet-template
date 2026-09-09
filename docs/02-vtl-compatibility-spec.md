# 02 — VTL Compatibility Specification

## 1. Scope

Compatibility targets documented observable Apache Velocity 2.4.x behavior, verified independently. Internal implementation details are not a contract.

## 2. Profiles

### `VTL_CORE`

Plain text, comments, variables, formal/quiet references, property/index access, literals/operators, `#set`, `#if/#elseif/#else`, `#foreach`, `#break`, static `#include`, static `#parse`.

### `VTL_MIGRATION`

Adds legacy property lookup order, broader implicit conversions, macros, dynamic index/parse behavior where allowed, legacy undefined-reference behavior.

### `VTL_DYNAMIC`

Explicit opt-in for arbitrary approved public method invocation, `#evaluate`, runtime-computed parse targets, and other features that reduce AOT guarantees.

### `VTL_SAFE`

No arbitrary methods, no `#evaluate`, static resource targets, property allowlists, no reflection/classloading/process/filesystem/environment access, bounded loops/recursion/output, HTML auto-escape when using HTML profile.

## 3. Feature states

```text
SUPPORTED_EXACT
SUPPORTED_WITH_DECLARED_DIFFERENCE
SUPPORTED_ONLY_IN_DYNAMIC_MODE
SUPPORTED_ONLY_IN_INTERPRETER
PLANNED
INTENTIONALLY_UNSUPPORTED
```

Publish a machine-readable compatibility manifest per release.

## 4. References

Support:

```velocity
$user
$user.name
${user.name}
$!user
$!{user.name}
${name}Suffix
```

Undefined behavior is profile-defined. Suggested modern default is strict; migration profile may preserve Velocity-style unresolved rendering. Quiet references suppress unresolved/null output according to tested compatibility rules.

## 5. Property access

Velocity 2.4 documents lowercase `$customer.address` lookup candidates in this order:

1. `getaddress()`
2. `getAddress()`
3. `get("address")`
4. `isAddress()`
5. `address()`

Implement that ordering only in the compatibility binder. Modern safe binding should prefer a simpler explicit property model.

Resolved strategies:

```text
DIRECT_RECORD_COMPONENT
DIRECT_GETTER
DIRECT_BOOLEAN_GETTER
DIRECT_FIELD_ALLOWED
MAP_KEY
LIST_INDEX
ARRAY_INDEX
EXTENSION
DYNAMIC_CACHED
REFLECTION_COMPAT
DENIED
NOT_FOUND
```

## 6. Method calls

```velocity
$user.getName()
$service.lookup($id)
```

- safe: denied unless exported;
- typed: compile-time bind exported methods;
- migration/dynamic: compatibility resolution under policy;
- reflection pivots (`getClass`, classloader etc.) remain denied in safe defaults.

## 7. Indexing

```velocity
$items[0]
$map["key"]
$matrix[$row][$col]
```

Typed lowering: arrays -> JVM array access; list -> `get(int)`; map -> `get(key)`. Assignment into model/index is mutation and requires a separate capability.

## 8. Expressions and literals

Core operators:

```text
== != < <= > >=
&& || !
and or not
+ - * / %
```

Support string/boolean/number/list/range literals according to differential tests. Do not accidentally inherit Java coercion rules when Velocity behavior differs.

## 9. Truthiness

Velocity documentation treats false/null/empty strings or collections/numeric zero as false-like and performs additional object emptiness/boolean checks.

Separate policies:

```text
TRUTHINESS_VELOCITY
TRUTHINESS_STRICT
```

Suggested strict rule:

```text
null false
Boolean value
Number != 0
CharSequence !isEmpty
Collection/Map !isEmpty
array length != 0
other non-null object true
```

No arbitrary method calls merely to determine truthiness in safe mode.

## 10. `#set`

```velocity
#set($x = expression)
```

Modern mode uses template-local scope. Migration profile may emulate Velocity context mutation. Property/index assignment is an explicit mutation capability.

## 11. Conditionals

```velocity
#if($condition)
 A
#elseif($other)
 B
#else
 C
#end
```

Lower to structured branch IR and never evaluate unselected branches. Constant branches may be eliminated.

## 12. `#foreach`

Support `$foreach.index`, `count`, `first`, `last`, `hasNext`, parent/topmost where compatibility profile claims them, plus `#else` on empty iteration.

Specialize arrays/lists/iterables/iterators. Bound loop iteration using render policy.

## 13. `#include`

Raw resource insertion. Literal targets should be fingerprinted/embedded at build time where possible. Dynamic targets require runtime resource capability. Paths must remain inside allowed roots.

## 14. `#parse`

Static literal target should become dependency-graph edge and preferably direct compiled template call. Dynamic target requires policy/runtime lookup. Recursion is bounded.

## 15. `#break` / `#stop`

Represent as structured control flow, not generic user-visible exceptions.

## 16. `#evaluate`

Runtime-created VTL source is incompatible with strict AOT guarantees and increases injection/DoS risk. Disabled by default and in `VTL_SAFE`. When enabled, use independent source-size/depth/cache/budget controls and no capability escalation.

## 17. Macros

Static macro definitions become IR functions. Small macros may inline. Recursion is bounded. Argument evaluation semantics are decided by differential tests, not assumptions.

## 18. Escaping

Compatibility and security are separate. Recommended escape modes:

```text
NONE
HTML_TEXT
HTML_ATTRIBUTE
URL_COMPONENT
JS_STRING
CSS_STRING
CUSTOM
```

Generic `.vm` may default `NONE` for migration; Spring HTML view profile should prefer escaping with explicit safe/raw wrappers.

## 19. Compatibility test method

For each case:

1. run reference Velocity 2.4.x;
2. capture output/error category;
3. run Viet Template interpreter;
4. run Viet Template dynamic backend;
5. run typed backend when applicable;
6. compare output/side effects/error semantics;
7. classify exact or declared difference.

## 20. Application-Level Compatibility Architecture (Milestone M12.5)

For migration of real-world multi-template Velocity applications:
- **Dependency Invalidation**: `#parse` and `#include` form directed dependency edges in `TemplateDependencyGraph`. Modifying any dependency transitively invalidates all dependent callers.
- **Global Macro Libraries**: Configured via `velocimacro.library`, parsed once into IR functions, and cached with a SHA-256 fingerprint in compilation cache keys. Local template macros shadow global macros of the same name.
- **Context Composition**: Controller model maps, request/session attributes, and tools merge via `RenderContextContributor` according to `ContextCollisionPolicy` (`FAIL`, `MODEL_WINS`, `CONTRIBUTOR_WINS`).
- **Layout Rendering**: Emulates `VelocityLayoutServlet` via two-stage `LayoutRenderPlan` with screen capture and post-screen layout resolution, enforcing output character and recursion depth limits. See [12a — Velocity Application Compatibility Architecture](12a-velocity-application-compatibility.md).

## 21. Security Hardening and Execution Safety (Milestone M13)

For secure execution across untrusted, semi-trusted, and production multi-tenant environments:
- **Unified Render Budget**: A monotonic `RenderBudget` is enforced across all top-level renders, `#parse`, `#include`, `#evaluate`, macros, and screen/layout plans, bounding character output, loop iterations, and execution duration (`maxExecutionTimeMillis`).
- **Resource Confinement**: `TemplateId` rejects null bytes, URL-encoded traversal sequences (`%2e`, `%2f`, `%5c`, `%00`), URI schemes, and Windows drive roots. Filesystem repositories enforce real-path boundary confinement and reject symlinks escaping the repository root.
- **Member Access and Classifier**: Granular `MemberAccessPolicy` blocks dangerous runtime pivots (reflection, classloading, processes, threads, executors, system calls), with support for explicit safe-allowlist profiles. `SensitiveObjectClassifier` performs zero-dependency structural checks against framework and runtime internals.
- **Protected Variables**: Engine-reserved variables (such as `$screen_content`) are protected against in-template mutation (`#set`) and contributor overwrite.
- **Cache Isolation**: Compilation cache keys partition compiled artifacts by the SHA-256 fingerprint of the effective `MemberAccessPolicy`, preventing cache pollution or security privilege escalation.
- **Cross-Tier Invariant Parity**: Identical security denials, limits, and semantics are enforced across AST interpreter, IR interpreter, dynamic linker PIC, and AOT bytecode backends.

