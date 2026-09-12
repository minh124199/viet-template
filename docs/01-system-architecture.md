# 01 — System Architecture

## 1. End-to-end architecture

```text
                         BUILD TIME
┌────────────────────────────────────────────────────────────────────────────┐
│ Template Source                                                            │
│      │                                                                     │
│      ▼                                                                     │
│ Source Manager ──► Lexer ──► Parser ──► AST                               │
│                                      │                                     │
│                                      ▼                                     │
│                              Semantic Analyzer                             │
│                              /      |       \                              │
│                         symbols    types   capabilities                    │
│                              \      |       /                              │
│                                      ▼                                     │
│                                  Template IR                               │
│                                      │                                     │
│                             Optimization Pipeline                          │
│                              /         |          \                        │
│                     Interpreter   Dynamic Plan   AOT Bytecode              │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                                RUNTIME REGISTRY
                                      │
                     ┌────────────────┴─────────────────┐
                     ▼                                  ▼
              RenderContext                        TemplateOutput
                     │                                  │
                     └──────────► CompiledTemplate ◄────┘
                                      │
                                      ▼
                             Writer / UTF-8 stream
```

## 2. Logical modules

```text
viet-template-api
viet-template-source
viet-template-parser
viet-template-semantics
viet-template-ir
viet-template-runtime
viet-template-engine
viet-template-compiler-spi
viet-template-compiler-interpreter
viet-template-compiler-dynamic
viet-template-compiler-jdk25
viet-template-escape-html
viet-template-velocity-compat
viet-template-spring
viet-template-spring-boot-autoconfigure
viet-template-spring-boot-starter
viet-template-maven-plugin
viet-template-gradle-plugin
viet-template-testkit
viet-template-tck
viet-template-benchmarks
```

Start with fewer physical build modules if necessary, but preserve these dependency boundaries.

## 2.1 Naming and coordinates

All published modules use Maven group `io.github.minh124199` and artifact ids beginning with `viet-template-`. Java code lives under `io.github.minh124199.viettemplate`. Examples:

```text
io.github.minh124199:viet-template-api
io.github.minh124199:viet-template-runtime
io.github.minh124199:viet-template-spring-boot-starter

io.github.minh124199.viettemplate.api
io.github.minh124199.viettemplate.runtime
io.github.minh124199.viettemplate.compat.velocity
io.github.minh124199.viettemplate.spring
```

Do not use `velocity` in core package/class names except where a type specifically implements compatibility, migration, or differential testing behavior.

## 3. Responsibilities

### `viet-template-api`

Stable public abstractions only:

```java
public interface TemplateEngine { Template get(String name); }
public interface Template { void render(RenderContext context, TemplateOutput output); }
public interface RenderContext { Object get(String name); boolean contains(String name); }
```

### `viet-template-source`

Resource identity, canonical names, path normalization, confinement, source hashes, line maps, dependency graph.

### `viet-template-parser`

Lexer/parser and immutable syntax AST. No runtime reflection/Spring/servlet dependency.

### `viet-template-semantics`

Scopes, symbols, types, nullability, property/method binding, compatibility semantics, capability checks, static resource binding, diagnostics.

### `viet-template-ir`

Backend-neutral rendering operations. Example:

```text
TEXT_CONST 0
LOAD_MODEL user : User
GET_PROPERTY User.name : String [DIRECT]
WRITE_ESCAPED HTML_TEXT
```

### `viet-template-runtime`

Tiny dependency used by generated classes: output, escaping, exceptions, registry, dynamic linker helpers, context API.

### `viet-template-engine`

Facade composing repositories, parser, semantic analysis, caches, compiler selection and registry.

## 4. Dependency rules

```text
source <- parser <- semantics <- ir <- compiler-spi
api <- runtime
compiler-* -> compiler-spi + ir + runtime
engine -> api + source + parser + semantics + compiler-spi + runtime
integrations -> api + engine + framework APIs
```

Hard rules:

- runtime never depends on parser/compiler;
- API never depends on Spring;
- generated templates depend only on runtime + user model + JDK;
- Spring depends on engine, never reverse;
- Apache Velocity reference dependency is test/tool scope only.

## 5. Compile lifecycle

### Discovery

```java
record TemplateSource(
    TemplateId id,
    URI origin,
    Charset charset,
    String text,
    SourceMap lines,
    byte[] sha256
) {}
```

### Parse

```java
record ParseResult(AstTemplate template, List<Diagnostic> diagnostics) {}
```

Ordinary syntax errors should be diagnostics, not uncontrolled exceptions.

### Semantic analysis

Inputs: AST, compatibility profile, model schema, access/security policy, resource graph, extension registry.

Outputs: bound nodes, symbol table, types/nullability, access plan, capability decisions, diagnostics.

### Lowering

```velocity
Hello $user.name
```

becomes conceptually:

```text
WriteConst("Hello ")
WriteValue(GetProperty(LoadParameter("user", User), User::getName), HTML_TEXT)
```

### Optimization

See `06-optimization-pipeline.md`.

### Code generation

```java
record CompiledArtifact(
    TemplateId templateId,
    byte[] classBytes,
    String generatedClassName,
    TemplateFingerprint fingerprint,
    DependencyFingerprint dependencies,
    CompileReport report
) {}
```

## 6. Runtime lifecycle and execution frame

Registry lookup must be $O(1)$ by normalized id. Compiled templates are immutable and thread-safe. Per-render state is strictly local: parameters, locals, loop state, and recursion/output budgets.

### Execution Frame and Scoping Architecture

In 0.1.x, template variable evaluation relies on `ExecutionContext` managing an `ArrayDeque<LocalScope>` where each scope holds a `HashMap<String, EvaluationValue>`.

In 0.2.0, the runtime introduces a high-performance `ExecutionFrame` backed by stable compiler-assigned integer slot IDs:

```java
public final class ExecutionFrame {
    // Fast path: compiler-assigned variable slots (parameters, #set, loop vars, macro args)
    private final EvaluationValue[] slots;
    
    // Dynamic fallback: undeclared variables, #evaluate dynamic scopes, dynamic contributor variables
    private Map<String, EvaluationValue> dynamicVariables;
    
    // Read-through to outer immutable render context
    private final RenderContext renderContext;
    private final RenderBudget budget;
    
    public ExecutionFrame(int slotCount, RenderContext renderContext, RenderBudget budget) {
        this.slots = new EvaluationValue[slotCount];
        this.renderContext = renderContext;
        this.budget = budget;
    }
    
    public EvaluationValue getSlot(int slotIndex) {
        return slots[slotIndex];
    }
    
    public void setSlot(int slotIndex, EvaluationValue value) {
        slots[slotIndex] = value;
    }
}
```

- **Array Slot Performance**: Statically analyzed variables map to fixed integer indices (`int slotIndex`). Reading or writing a variable compiles to a direct array index load or store (`ALOAD`/`ASTORE`), completely bypassing string hashing, bucket resolution, and entry node traversal.
- **3-State Semantics**: Both slots and the dynamic map explicitly preserve the 3-state evaluation model (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`), guaranteeing full Velocity semantic compatibility.
- **Empty Slot Representation**: `ExecutionFrame` explicitly initializes each slot to `EvaluationValue.undefined()`. Java reference arrays themselves initialize to `null`, so the runtime never exposes `null` or conflates it with `DEFINED_NULL`.
- **Name-Based Fallback**: A name-based fallback is retained for variable accesses whose identity cannot safely be resolved to a static slot while preserving Velocity-compatible semantics.
- **Slot Reuse Deferred**: Current measured workloads do not demonstrate a need for slot packing in 0.2.0, so slot reuse remains deferred.
- **Stack Structures**: Lexical scopes, macro invocations, and layout pipelines utilize standard `ArrayDeque` for LIFO/FIFO operations, providing low constant factors and avoiding pointer overhead associated with linked lists.

### Direct Typed Facade

Typed mode offers a generated direct facade:

```java
public interface OrdersListTemplate {
    void render(User user, List<Order> orders, TemplateOutput out);
}
```

When invoked through this facade, no string-key context lookup occurs anywhere in the hot rendering path.

---

## 7. Cache and Resolution Architecture

```text
SourceCache
ParseCache
SemanticCache
CompileCache (with Secondary Invalidation Index)
TemplateRegistry
DynamicCallSiteCache (Polymorphic Inline Cache)
```

Every semantic and compile cache key must include all inputs affecting semantics, model schemas, and security policies.

### Secondary Invalidation Index (`TemplateId -> Set<CompileCacheKey>`)

In high-throughput environments with thousands of compiled templates, invalidating a template cannot perform an $O(N)$ full scan over cache keys (`entries.keySet().removeIf(...)`).

`TemplateCompileCache` maintains a concurrent secondary reverse index:
```text
ConcurrentMap<TemplateId, Set<CompileCacheKey>>
```
When a template is updated or invalidated:
1. The cache looks up the associated `Set<CompileCacheKey>` in average $O(1)$ time.
2. The $K$ associated entries are removed from the primary compilation cache in $O(K)$ operations.
3. Overall invalidation work is reduced to $O(K)$ rather than an $O(N)$ scan (never described as "instant $O(1)$ eviction", since removal of $K$ entries is proportional to $K$).
4. Memory and classloader references are immediately eligible for collection.

### Dynamic Call-Site PIC Architecture (`AccessLink[]` Array Scanning)

For dynamic property access on unknown receiver types, `DynamicCallSite` implements a Polymorphic Inline Cache (PIC) with a maximum depth of 4 shapes before falling back to a megamorphic cache:

```java
final class DynamicCallSite {
    private final int siteId;
    private final String property;
    private volatile AccessLink[] polymorphicLinks; // max length 4
    private final BoundedWeakClassCache megamorphicCache;
}
```

**Architectural Justification for Array Scanning over HashMap**:
- **Low Constant Factors and Memory Locality**: A small contiguous array of up to 4 elements provides sequential access without pointer chasing across non-contiguous heap nodes, yielding low constant factors and good memory locality.
- **Zero Hashing Overhead**: Scanned linearly with pointer equality checks (`receiver.getClass() == link.receiverClass`). It avoids `hashCode()` computation, modulo arithmetic, and entry node allocation.
- **Bounded Traversal & Simple Correctness**: For small bounded collections ($N \le 4$), linear traversal has a simple, provably correct concurrency model without bucket contention.
- **Megamorphic Guard**: If more than 4 distinct receiver types are encountered at a single site, the call site switches to `BoundedWeakClassCache` to prevent unbounded array scanning.

---

## 8. Hot Reload and Dependency Invalidation

```text
file change -> source hash -> invalidate dependency graph -> reanalyze/recompile
            -> atomic immutable registry swap
```

### Dependency Graph Architecture

Template relationships (`#parse`, `#include`, global macros, layouts) are maintained by `TemplateDependencyGraph`:
- **Reverse Dependency Index**: Stores mappings as `ConcurrentMap<TemplateId, Set<TemplateId>>` (depended-on template $\to$ dependents).
- **Breadth-First Search (BFS) Invalidation**: Transitive invalidation uses a standard `ArrayDeque<TemplateId>` queue and a visited `HashSet<TemplateId>`:
  1. Detects and handles circular references safely without infinite recursion.
  2. Traverses the reverse index in optimal $O(V + E)$ time.
  3. Triggers secondary index eviction across all transitively affected compilation units.
- **Atomic Generation Swap**: In-flight renders finish on the old generation; mixed-generation output is strictly prevented.

---

## 9. Error taxonomy

- `TemplateSyntaxException`
- `TemplateSemanticException`
- `TemplateSecurityException`
- `TemplateRenderException`
- `TemplateResourceException`
- `TemplateLimitException`
- `TemplateCompilationException`
- `TemplateLayoutException`
- `ContextCollisionException`

Each carries template id, source span, template stack, stable diagnostic code and optional cause. See [12a — Velocity Application Compatibility Architecture](12a-velocity-application-compatibility.md) for context composition, dependency graphs, global macros, and layout rendering.

---

## 10. Architectural invariants

1. **Immutability**: AST, IR, and runtime compiled template objects are strictly immutable and thread-safe.
2. **Canonical Paths**: Source paths are normalized before access control and policy decisions are evaluated.
3. **Security Invariant**: Dynamic invocation and cached inline links never bypass security policy allowlists or class restrictions.
4. **Pre-Encoded Literals**: Static output is pre-encoded to UTF-8 byte chunks when the output target supports byte streaming.
5. **Semantic Equivalence**: Optimization passes may fall back to safer paths but must never alter defined language semantics.
6. **Unified IR**: The reference interpreter and compiled backends consume the exact same verified IR.
7. **7-Part DSA Acceptance Rule**: No complex or custom data structure may be introduced without satisfying the 7-part DSA acceptance rule (see [15 — Benchmark and Performance Engineering Plan](15-benchmark-plan.md)).
8. **Java-First Design Policy**: Contiguous arrays, compact records, and standard JDK collections (`ArrayDeque`, `ArrayList`, `HashMap`, `ConcurrentHashMap`) are prioritized over external or complex pointer structures.
9. **Scalable Invalidation**: Invalidation of compilation and dependency caches must scale independently of total cache entry count via indexed secondary lookups.

