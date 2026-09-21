# Viet Template Language (VTL) Macros and Layouts

> **Document Version:** 1.0.0  
> **Target Specification:** Apache Velocity 2.4.x Compatible  
> **Compiler & Runtime Baseline:** Java 21 (`--release 21`)  
> **Diagnostic Codes:** `LAYOUT:CYCLE_DETECTED`, `LAYOUT:DEPTH_EXCEEDED`, `LIMIT:LIMIT_EXCEEDED`, `SECURITY:VIOLATION`  
> **Companion Guides:**
> - [Syntax Reference](syntax-reference.md)
> - [Undefined and Null Semantics](undefined-null-semantics.md)
> - [Foreach Loops and Scopes](foreach-and-scopes.md)
> - [TCK Compatibility Matrix](../migration/compatibility-matrix.md)

---

## 1. Executive Summary

Viet Template separates view rendering into two complementary modular systems:
1. **Velocimacro System**: Encapsulates repeatable view snippets and UI widgets into parametrized, lexically scoped callable components.
2. **Layout Pipeline**: Implements a zero-overhead, two-stage layout rendering architecture that cleanly decouples page-level scaffolding (HTML shells, headers, footers) from view-specific screen contents.

Both systems are backed by strict recursion limits, memory and character budgets, and cycle-detection safeguards.

---

## 2. Velocimacro System

Velocimacros are reusable template functions declared with the `#macro` directive.

### 2.1 Macro Declaration and Invocation
Macros define formal parameter lists and an executable body. Macro calls can pass positional arguments:

```velocity
## Macro Declaration
#macro(pagination $page $totalPages $urlPrefix)
  <nav class="pagination">
    #if($page > 1)
      <a href="${urlPrefix}?page=${page - 1}">Previous</a>
    #end
    <span>Page $page of $totalPages</span>
    #if($page < $totalPages)
      <a href="${urlPrefix}?page=${page + 1}">Next</a>
    #end
  </nav>
#end

## Macro Invocation
#pagination($currentPage $totalPageCount "/products")
```

### 2.2 Default Arguments
VTL supports optional default parameter expressions evaluated lazily when the caller omits trailing arguments:

```velocity
#macro(button $label $variant = "primary" $size = "md")
  <button type="button" class="btn btn-$variant btn-$size">$label</button>
#end

#button("Submit")                      ## variant="primary", size="md"
#button("Delete" "danger")             ## variant="danger",  size="md"
#button("Confirm" "success" "lg")      ## variant="success", size="lg"
```

### 2.3 Body Content Macros (`#@`)
Macros can accept an inline template block passed via `#@` invocation. The enclosed block is executed and bound to the reserved reference `$bodyContent`:

```velocity
#macro(modalDialog $title $modalId)
  <div class="modal" id="$modalId" tabindex="-1">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">$title</h5>
        </div>
        <div class="modal-body">
          $bodyContent
        </div>
      </div>
    </div>
  </div>
#end

## Invocation passing an inline body block:
#@modalDialog("Terms of Service" "tosModal")
  <p>Please review and agree to our updated terms before proceeding.</p>
  <ul>
    <li>Privacy updates</li>
    <li>Service availability guarantees</li>
  </ul>
#end
```

### 2.4 Parameter Scoping & Execution Semantics
- **Call-by-Value Evaluation**: Macro arguments are evaluated at the invocation site in the caller's context before binding.
- **Isolated Lexical Frame**: Formal parameters are assigned inside a newly pushed execution scope (`pushOwnedScope`). They never overwrite or mutate caller variables sharing the same name.
- **`#break` Directive**: Calling `#break` within a macro body terminates the macro execution immediately, returning control to the caller without terminating enclosing loops.

### 2.5 Global Macro Libraries
Macros can be declared globally across an entire application without requiring explicit `#parse` in every template:
- Configured via `TemplateEngine.builder().globalMacroLibraries(...)` or engine property `velocimacro.library`.
- Discovered and parsed at engine startup by `GlobalMacroManager`.
- Governed by `GlobalMacroPrecedence`:
  - `LOCAL_FIRST`: Macros declared locally within a template override global library macros of the same name.
  - `GLOBAL_FIRST`: Global library macros take precedence over local definitions.

### 2.6 Recursion Guardrails
To prevent stack exhaustion from mutually recursive or unconstrained macros, the engine enforces `maxMacroDepth`:
- When macro call depth exceeds `maxMacroDepth`, a `TemplateLimitException` is thrown with diagnostic code `LIMIT:LIMIT_EXCEEDED`.

---

## 3. Layout System Architecture

Viet Template implements a two-stage layout rendering plan via the `LayoutRenderPlan` SPI and its canonical reference implementation `DefaultLayoutRenderPlan`.

```text
 Stage 1: Screen Rendering                     Stage 2: Layout Wrapping
 ┌───────────────────────────┐                 ┌───────────────────────────┐
 │   Screen Template         │                 │   Layout Template         │
 │   (e.g. products.vtl)     │                 │   (e.g. main-layout.vtl)  │
 └─────────────┬─────────────┘                 └─────────────┬─────────────┘
               │                                             │
               ▼                                             ▼
 ┌───────────────────────────┐                 ┌───────────────────────────┐
 │ Render to Bounded Buffer  │                 │ Binds $screen_content     │
 │ (enforces char budget)    │ ──────────────► │ Wraps screen into <html>  │
 └───────────────────────────┘                 └─────────────┬─────────────┘
                                                             │
                                                             ▼
                                               ┌───────────────────────────┐
                                               │ Final Streamed Output     │
                                               └───────────────────────────┘
```

### 3.1 Two-Stage Rendering Execution

1. **Stage 1 — Screen Evaluation**:
   - The screen template (e.g. `views/orders.vtl`) renders to an in-memory buffer.
   - Character budgets are enforced to prevent memory exhaustion attacks.
   - Any layout overrides configured by the screen template are captured.

2. **Stage 2 — Layout Wrapping**:
   - The resolved layout template (e.g. `layouts/default.vtl`) executes.
   - The captured screen output is bound to the protected reference `$screen_content` (configurable via `LayoutConfiguration.screenContentKey()`).
   - The layout template streams the completed HTML page directly to the destination `TemplateOutput`.

### 3.2 Layout Selection & Overrides

Layouts can be assigned globally, resolved dynamically, or bypassed entirely:

#### 1. Default Global Layout
Configured via `LayoutResolver`:
```velocity
## layouts/main.vtl
<!DOCTYPE html>
<html lang="en">
<head>
  <title>$!pageTitle | Enterprise App</title>
</head>
<body>
  #parse("fragments/navbar.vtl")
  <main class="container">
    $screen_content
  </main>
  #parse("fragments/footer.vtl")
</body>
</html>
```

#### 2. Dynamic Screen-Level Override
Screen templates can switch layouts dynamically using `#set`:
```velocity
## views/login.vtl
#set($layout = "layouts/blank.vtl")
<div class="login-box">
  <h2>Sign In</h2>
  <!-- Login Form -->
</div>
```

#### 3. Complete Layout Bypass
To render raw fragments or REST responses without any layout wrapping:
```velocity
## views/partial-table.vtl
#set($layout = false)  ## Or #set($layout = "")
<table>
  #foreach($item in $items)
    <tr><td>$item.name</td></tr>
  #end
</table>
```

### 3.3 Scope Sharing Policies (`LayoutContextScope`)

Viet Template supports two context propagation policies:
- **`SHARED_COMPATIBILITY_SCOPE`** (Default): Variables created or updated in the screen template (such as `#set($pageTitle = "Order Details")`) are visible in the layout template. This matches Apache Velocity layout tool semantics.
- **`ISOLATED`**: The layout template executes with a pristine snapshot of the initial `RenderContext` plus `$screen_content`, preventing screen templates from mutating layout state.

### 3.4 Protection & Safety Guardrails

- **Protected Key Enforcement**: The `$screen_content` variable is marked read-only within the execution frame; templates cannot overwrite the captured screen content.
- **Cycle Detection**: If a template declares itself as its own layout, or if an indirect recursive cycle is detected, rendering fails immediately with `TemplateLayoutException` and diagnostic code `LAYOUT:CYCLE_DETECTED`.
- **Depth Limits**: Nesting depth is checked against `maxLayoutDepth()`; violations throw `TemplateLayoutException` with diagnostic code `LAYOUT:DEPTH_EXCEEDED`.

---

## 4. Dynamic Evaluation (`#evaluate`)

The `#evaluate` directive parses and renders dynamic VTL code supplied as a string expression at runtime:

```velocity
#set($expr = "Order Total: $order.subtotal * (1 - $discount.rate)")
#evaluate($expr)
```

### 4.1 Evaluation Semantics
- Evaluated within the caller's active execution scope, allowing reading and writing of surrounding variables.
- Macros defined inside dynamically evaluated snippets are discovered and added to the local macro registry.
- Requires interpreter execution (`ExecutionTier.IR`); Ahead-of-Time (AOT) bytecode compilers safely delegate `#evaluate` calls to the reference interpreter.

### 4.2 Security & Resource Limits
- **Profile Restrictions**: `#evaluate` is disabled by default in the `VTL_SAFE` profile. Invoking `#evaluate` under a safe profile throws `TemplateSecurityException` with diagnostic code `SECURITY:VIOLATION`.
- **Recursion Guard**: Evaluated strings containing nested `#evaluate` directives are bounded by `maxEvaluateDepth()`. Violations throw `TemplateLimitException` (`LIMIT:LIMIT_EXCEEDED`).
- **Input Size Guard**: String expressions exceeding `maxDynamicSourceLength()` are rejected immediately.
- **Untrusted Input Caution**: Never pass unvalidated user input directly into `#evaluate`.
