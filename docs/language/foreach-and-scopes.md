# Viet Template Language (VTL) Foreach Loops and Scopes

> **Document Version:** 1.0.0  
> **Target Specification:** Apache Velocity 2.4.x Compatible  
> **Compiler & Runtime Baseline:** Java 21 (`--release 21`)  
> **Diagnostic Code:** `LIMIT:LIMIT_EXCEEDED`  
> **Companion Guides:**
> - [Syntax Reference](syntax-reference.md)
> - [Undefined and Null Semantics](undefined-null-semantics.md)
> - [Macros and Layouts](macros-and-layouts.md)
> - [TCK Compatibility Matrix](../migration/compatibility-matrix.md)

---

## 1. Overview: Lexical Scoping in VTL

Viet Template enforces a deterministic, stack-oriented lexical scoping model. Unlike traditional template engines that rely on a single global dictionary susceptible to accidental variable clobbering, Viet Template organizes variable storage into layered execution frames:

```text
┌─────────────────────────────────────────────────────────────┐
│                   Caller's RenderContext                    │
│                 (Immutable from templates)                  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   Root Template Execution Scope             │
│            (Variables created via template-level #set)      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   Macro Frame / Local Scope                 │
│         (Formal parameters, isolated local assignments)     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   Foreach Iteration Frame                   │
│        (Iteration variable $item and metadata $foreach)     │
└─────────────────────────────────────────────────────────────┘
```

This multi-tiered model ensures that:
- Call-site variables supplied by Spring controllers or services remain untouched.
- Inner loops cannot corrupt outer loop counters or variables.
- Macro execution does not inadvertently overwrite caller variables.

---

## 2. The `#foreach` Directive

The `#foreach` directive iterates sequentially over any supported Java iterable or collection type.

### 2.1 Supported Collection Types
Viet Template supports iteration over:
- **`java.util.Collection` / `java.lang.Iterable`**: Lists, sets, queues, and custom iterables.
- **Java Arrays**: Both object arrays (`String[]`, `Object[]`) and primitive arrays (`int[]`, `long[]`, etc.).
- **`java.util.Map`**: In accordance with Velocity 2.4 specification, iterating over a `Map` iterates over its values collection (`map.values()`).
- **`java.util.Iterator` / `java.util.Enumeration`**: Forward-only traversal.
- **Integer Range Literals**: `[1..5]`, `[10..1]`, or dynamic variable ranges `[$start..$end]`.

### 2.2 Basic Loop Syntax
```velocity
<ul>
#foreach($product in $category.products)
  <li>#$foreach.count: $product.name - $$product.price</li>
#end
</ul>
```

---

## 3. Loop Metadata (`$foreach`)

During each iteration, the engine automatically populates the reserved variable `$foreach` with an instance of `ForeachMetadata`. Both JavaBean property access and zero-argument method calls are supported:

| Property | Method Accessor | Return Type | Description |
| :--- | :--- | :--- | :--- |
| `$foreach.index` | `$foreach.getIndex()` | `int` | Zero-based index of the current item (`0, 1, 2, ...`). |
| `$foreach.count` | `$foreach.getCount()` | `int` | One-based iteration counter (`1, 2, 3, ...`). |
| `$foreach.first` | `$foreach.isFirst()` | `boolean` | `true` during the initial iteration; otherwise `false`. |
| `$foreach.last` | `$foreach.isLast()` | `boolean` | `true` during the final iteration; otherwise `false`. |
| `$foreach.hasNext` | `$foreach.getHasNext()` | `boolean` | `true` if additional elements remain; otherwise `false`. |
| `$foreach.parent` | `$foreach.getParent()` | `ForeachMetadata` | References the enclosing outer loop metadata, or `null` if top-level. |
| `$foreach.topmost` | `$foreach.getTopmost()` | `ForeachMetadata` | References the outermost enclosing loop metadata in deep nestings. |

### 3.1 Formatting Tables and Delimiters
Common UI formatting tasks are made trivial using `$foreach` metadata flags:

```velocity
## Comma-separated list with no trailing comma
#foreach($tag in $article.tags)
  $tag#if($foreach.hasNext), #end
#end

## Alternating table row styles
<table>
#foreach($user in $users)
  <tr class="#if($foreach.first)table-header#elseif($foreach.index % 2 == 0)even#else odd#end">
    <td>$foreach.count</td>
    <td>$user.username</td>
    <td>$user.email</td>
  </tr>
#end
</table>
```

### 3.2 Nested Loops and Parent Access
When nesting loops, each level maintains its own `$foreach` context. Outer loop metadata can be traversed via `$foreach.parent` or `$foreach.topmost`:

```velocity
#foreach($department in $company.departments)
  <h3>Department #$foreach.count: $department.name</h3>
  <ol>
  #foreach($employee in $department.employees)
    <li>
      Outer Dept Index: $foreach.parent.index |
      Employee #$foreach.count: $employee.fullName
    </li>
  #end
  </ol>
#end
```

In deeply nested loops (3+ levels), `$foreach.topmost` always yields the outermost enclosing loop's metadata without requiring repeated `.parent.parent` chains:
```velocity
Topmost Loop Index: $foreach.topmost.index
```

### 3.3 Loop Termination: `#break` vs. `$foreach.stop()`

Viet Template provides two mechanisms to terminate loop execution early:

1. **`#break` Directive**:
   Exits the immediately enclosing loop directive and transfers execution to the statement following `#end`:
   ```velocity
   #foreach($item in $search.results)
     #if($item.priority == "CRITICAL")
       <p>Critical item found: $item.title</p>
       #break
     #end
   #end
   ```

2. **Programmatic Termination (`$foreach.stop()`) (`EXT-001`)**:
   Enables programmatic loop termination via method invocation on the loop metadata object:
   ```velocity
   #foreach($row in $dataset)
     #if($row.hasError())
       $foreach.stop()
     #end
   #end
   ```
   This extension preserves backward compatibility with enterprise templates that invoke `$foreach.stop()` inside macro bodies or conditional expressions.

### 3.4 Immutable Snapshot Semantics
Each iteration step produces an immutable `ForeachMetadata` snapshot:
- Thread-safe: internal iteration state cannot be corrupted by external method invocations.
- Zero-leak: loop metadata is strictly scoped to the iteration frame and is reclaimed upon loop exit.

---

## 4. Scopes & Execution Frames

Viet Template implements strict lexical scoping rules across four distinct layers:

### 4.1 Root `RenderContext` Immutability
The root context supplied to `Template.render(RenderContext, TemplateOutput)` is strictly immutable from within the template:
- Any `#set($key = ...)` executed in a template writes only into the local execution frame.
- The underlying Java application context is never mutated, guaranteeing idempotence across repeated renders.

### 4.2 Local Execution Frames (`#set`)
Variables created or modified via `#set` are stored in the active template execution frame:
- Child `#parse` templates share read/write access to the caller's local execution frame.
- Variables survive for the remainder of that template's execution unless overwritten.

```velocity
#set($pageTitle = "Account Overview")
#parse("includes/header.vtl") ## header.vtl can read and modify $pageTitle
```

### 4.3 Loop Scopes & Variable Shadowing
The iteration variable (`$item`) and the metadata reference (`$foreach`) are confined to the body of the `#foreach` block:
- **Shadowing**: If an outer variable shares the same name as the loop variable, the outer variable is shadowed for the duration of the loop.
- **Restoration**: Once `#end` is reached, the shadowed outer variable is restored to its original value.
- **Containment**: If no outer variable existed with that name, the loop variable does not leak into the surrounding template scope after the loop finishes.

```velocity
#set($x = "outer")
#foreach($x in ["a", "b", "c"])
  Inside: $x
#end
After loop: $x  ## Renders "outer", outer variable is preserved
```

### 4.4 Macro Argument Scope Isolation
When invoking a `#macro`, Viet Template pushes an isolated lexical argument frame:
- **No Clobbering**: Macro formal parameters are bound in a new frame. They never overwrite or mutate caller variables of the same name.
- **Local Isolation**: Assignments made inside the macro body to formal parameters or local `#set` variables remain contained within the macro invocation.
- **Body Content Scoping**: For `#@` macro calls, the `$bodyContent` variable is bound specifically to that invocation frame.

```velocity
#set($counter = 100)

#macro(increment $counter)
  #set($counter = $counter + 1)
  Inside macro: $counter
#end

#increment(5)
After macro: $counter  ## Renders 100, caller variable was completely protected
```
