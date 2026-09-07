# 12a — Velocity Application Compatibility Architecture

## 1. Overview and Objective

Milestone M12.5 establishes the framework-neutral compatibility foundation required to support migration of real-world Apache Velocity applications—including legacy `VelocityViewServlet`, `VelocityLayoutServlet`, and Spring MVC Velocity applications—without redesigning the Viet Template core engine.

This architecture deliberately maintains **zero production dependencies** on external frameworks (Spring Framework, Velocity Tools, or Apache Velocity itself). Instead, it defines reusable abstractions in `viet-template-api` and production-grade implementations in `viet-template-vtl-interpreter` and `viet-template-runtime`.

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                             RenderRequest                                   │
│  (templateId, model, attributes, locale, metadata)                          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ContributingContextComposer                             │
│  ┌───────────────────────┐   ┌───────────────────────────────────────────┐  │
│  │ RenderContextContributor│...│ ContextCollisionPolicy                     │  │
│  │ (helpers, tools, req) │   │ (FAIL, MODEL_WINS, CONTRIBUTOR_WINS)      │  │
│  └───────────────────────┘   └───────────────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
       Standard Direct Render                    Two-Stage Layout Plan
   ┌─────────────────────────────┐           ┌─────────────────────────────┐
   │ Template.render(ctx, out)   │           │ 1. Render screen to buffer  │
   └─────────────────────────────┘           │ 2. Resolve layout template  │
                                             │ 3. Bind $screen_content     │
                                             │ 4. Render layout to output  │
                                             └─────────────────────────────┘
```

---

## 2. Template Dependency Graph and Invalidation

Real-world Velocity templates frequently compose via `#parse`, `#include`, global macro libraries, and layout wrappers. Modifying a shared dependency must immediately and atomically invalidate all dependent templates throughout the cache hierarchy.

### 2.1 Dependency Kinds (`TemplateDependencyKind`)

- `STATIC_PARSE`: Target template statically referenced by a `#parse("...")` directive.
- `STATIC_INCLUDE`: Target template statically referenced by a `#include("...")` directive.
- `GLOBAL_MACRO_LIBRARY`: Reusable Velocimacro library specified at engine configuration.
- `LAYOUT`: Outer layout template resolved for a screen template.

### 2.2 Directed Graph (`TemplateDependencyGraph`)

The `DefaultTemplateDependencyGraph` manages directed edges between caller (`source`) and dependency (`target`).

Key invariants:
- **Thread Safety**: Concurrent reads and atomic mutations via thread-safe internal structures.
- **Cycle Safety**: Cycle-safe breadth-first traversal in `transitiveDependentsOf(TemplateId id)`, preventing infinite loops in cyclic `#parse` scenarios.
- **Precise Eviction**: `engine.invalidateWithDependents(TemplateId id)` traverses reverse edges to evict the root and all transitive callers while leaving unrelated cached templates unaffected.

---

## 3. Context Composition and Collision Policies

Enterprise applications compose template contexts from multiple sources: controller model maps, request/session attributes, application helpers, and view tools.

### 3.1 Component Abstractions

- `RenderRequest`: Immutable invocation request carrying the target `TemplateId`, user model map, attributes map, `Locale`, and custom metadata.
- `ContributorContext`: Read-only snapshot of request metadata exposed to contributors.
- `RenderContextContributor`: Extension point called during context preparation to contribute helper objects, tool facades, or request-scoped attributes.
- `MutableRenderContext`: Thread-confined mutable context interface allowing in-template `#set` directives to write through variables during evaluation.

### 3.2 Collision Policies (`ContextCollisionPolicy`)

When the model and one or more contributors supply conflicting keys, the engine applies the configured `ContextCollisionPolicy`:

| Policy | Behavior |
| :--- | :--- |
| `FAIL` | Throws `ContextCollisionException` with diagnostic details and conflicting origins. |
| `MODEL_WINS` | The controller/user model takes precedence; conflicting contributor values are discarded. |
| `CONTRIBUTOR_WINS` | The later contributor takes precedence over the user model. |

### 3.3 Engine Reserved Variables

Engine-reserved variables (e.g., layout keys such as `$screen_content`) cannot be overwritten by contributors. Attempting to contribute a reserved key raises `ContextCollisionException`.

---

## 4. Global Macro Libraries (`velocimacro.library`)

Legacy Velocity applications heavily rely on global Velocimacro libraries (such as Spring macro libraries or shared UI macro sets) configured via `velocimacro.library`.

### 4.1 Fingerprint and Cache Integration

- Macro libraries are parsed and lowered into canonical Template IR once.
- Global macro library source hashes and configured precedence are cryptographically hashed into an engine-wide SHA-256 fingerprint.
- The `CompileCacheKey` incorporates this `globalMacrosFingerprint`, ensuring that modifying any macro library invalidates cached compiled templates.

### 4.2 Precedence and Shadowing Rules

- `GlobalMacroPrecedence.FIRST_WINS`: The first library declaring a macro wins across libraries.
- `GlobalMacroPrecedence.LAST_WINS`: Later libraries override earlier libraries (Velocity default).
- **Local Shadowing Invariant**: Local template macros (`#macro(...)` declared in the executing template) **unconditionally shadow** global macros of the same name, regardless of the configured global precedence.
- **Constant Pool Rebinding**: Macro function text constants are dynamically mapped into the target template's `IrConstantPool`, preserving memory deduplication and IR verification invariants.

---

## 5. Two-Stage Layout Rendering (`LayoutRenderPlan`)

To support `VelocityLayoutServlet` patterns without servlet container dependencies, Viet Template implements two-stage layout rendering.

### 5.1 Rendering Sequence

1. **Screen Capture**: The screen template is evaluated into an in-memory buffer (`StringTemplateOutput`).
2. **Budget Enforcement**: Screen capture strictly enforces the configured character budget (`maxOutputCharacters`), throwing `TemplateLimitException` on runaway output.
3. **Layout Resolution**: The layout template is resolved *after* screen rendering, allowing in-template directives (e.g., `#set($layout = 'custom.vm')` or `#set($layout = 'none')`) to override or bypass layout wrapping.
4. **Scope Propagation**: Depending on `LayoutContextScope`:
   - `SHARED_COMPATIBILITY_SCOPE`: Screen mutations (e.g., page titles, metadata variables) remain visible in the layout context.
   - `ISOLATED_SCREEN_SCOPE`: Layout context inherits only initial request variables, discarding screen mutations.
5. **Layout Evaluation**: Captured screen content is bound under `screenContentKey` (default `screen_content`), and the layout template renders directly to the caller's `TemplateOutput`.

### 5.2 Safety and Recursion Guards

- Self-wrapping cycles (`screenId == layoutId`) and cyclic layout chains are detected immediately, failing with `TemplateLayoutException` (`LAYOUT:CYCLE_DETECTED`).
- Recursion depth is capped by `maxLayoutDepth` (default 5), preventing runaway layout nesting (`LAYOUT:DEPTH_EXCEEDED`).

---

## 6. Verification and Architecture Boundaries

All M12.5 components are verified under strict ArchUnit architectural rules in `ArchitectureRulesTest`:
- `viet-template-api` has zero external dependencies.
- `viet-template-vtl-interpreter` depends only on `viet-template-api`, `viet-template-runtime`, and `viet-template-language-vtl`.
- Zero runtime dependencies on Spring Framework or Apache Velocity.
