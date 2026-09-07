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

## 6. Runtime lifecycle

Registry lookup must be O(1) by normalized id. Compiled templates are immutable/thread-safe. Per-render state is local: parameters, locals, loop state, recursion/output budgets.

Typed mode should offer a generated direct facade:

```java
public interface OrdersListTemplate {
    void render(User user, List<Order> orders, TemplateOutput out);
}
```

so no string-key context lookup occurs in the hot method.

## 7. Cache architecture

Separate caches:

```text
SourceCache
ParseCache
SemanticCache
CompileCache
TemplateRegistry
DynamicCallSiteCache
```

Every semantic/compile cache key must include all inputs affecting semantics/security.

## 8. Hot reload

```text
file change -> source hash -> invalidate dependency graph -> reanalyze/recompile
            -> atomic immutable registry swap
```

In-flight renders may finish on the old generation; mixed-generation output is not allowed.

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

## 10. Architectural invariants

1. AST/IR/runtime template objects are immutable.
2. Source paths are normalized before policy decisions.
3. Dynamic invocation never bypasses security policy.
4. Static output is pre-encoded when output mode supports it.
5. Optimization may fall back but never alter defined semantics.
6. Interpreter and compiled backends share analyzed semantics/IR.
