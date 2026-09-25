# Viet Template Language (VTL) Syntax Reference

> **Document Version:** 1.0.0  
> **Target Specification:** Apache Velocity 2.4.x Compatible  
> **Compiler & Runtime Baseline:** Java 21 (`--release 21`)  
> **Companion Guides:**
> - [Undefined and Null Semantics](undefined-null-semantics.md)
> - [Foreach Loops and Scopes](foreach-and-scopes.md)
> - [Macros and Layouts](macros-and-layouts.md)
> - [TCK Compatibility Matrix](../migration/compatibility-matrix.md)

---

## 1. Overview & Architecture

The Viet Template Language (VTL) is an enterprise-grade, high-throughput templating language designed for deterministic rendering on modern Java platforms (Java 21+). Viet Template supports all 80 tracked VTL grammar features. In the current 301-scenario differential suite against Apache Velocity 2.4.1, 295 scenarios match exactly (98.01%), with 5 documented expected differences and 1 Viet Template extension (100% accounted behavior coverage), while providing:
- Zero-allocation streaming output pipelines.
- Modern type-safe execution profiles (`VTL_CORE`, `VTL_MIGRATION`, `VTL_DYNAMIC`, `VTL_SAFE`).
- High-performance ahead-of-time (AOT) bytecode compilation alongside a clean-room AST/IR reference interpreter.
- Confinement security preventing reflection attacks, remote code execution (RCE), and classloader escaping.

---

## 2. Comments & Lexical Conventions

### 2.1 Single-Line Comments
Single-line comments begin with `##` and extend to the end of the line. All text following `##` on that line is discarded during tokenization and produces no rendered output.

```velocity
## This is a single-line comment
Hello $user.name! ## Greet the authenticated user
```

### 2.2 Multi-Line Block Comments
Multi-line block comments begin with `#*` and end with `*#`. Any text, directives, or reference sigils contained within the comment delimiters are ignored.

```velocity
#*
  This is a multi-line block comment.
  Directives like #set($x = 10) inside comments are ignored.
*#
```

### 2.3 Documentation Comments
Documentation comments begin with `#**` and end with `*#`. These comments can be utilized by tools, IDE indexers, and schema documentation generators.

```velocity
#**
  @param $customer Customer record containing account details.
  @param $orders   Collection of active order items.
*#
```

### 2.4 Sigil and Directive Escaping
Literal dollar signs (`$`) and hash marks (`#`) that precede valid identifiers or directives can be rendered literally using backslash (`\`) escape sequences:

| Input Text | Rendered Output | Rule Description |
| :--- | :--- | :--- |
| `\$user` | `$user` | Single backslash escapes shorthand reference |
| `\\$user` | `\Alice` (if `$user` is `"Alice"`) | Escaped backslash followed by resolved reference |
| `\\\$user` | `\$user` | Escaped backslash followed by escaped reference |
| `\${user}` | `${user}` | Single backslash escapes formal reference |
| `\#set($x = 1)` | `#set($x = 1)` | Single backslash escapes directive parsing |

---

## 3. References & Navigation

References in VTL begin with the `$` sigil and refer to variables or expressions supplied via `RenderContext` or defined within the template using `#set`.

### 3.1 Reference Notations

```velocity
$user              ## Shorthand reference
${user}            ## Formal reference (delimited by braces)
$!user             ## Quiet shorthand reference (suppresses output if null/undefined)
$!{user}           ## Quiet formal reference
$user|'Guest'      ## Fallback shorthand (evaluates to 'Guest' if $user is null/undefined)
${user|'Guest'}    ## Fallback formal reference
```

#### Formal Delimiters for Suffix Concatenation
When a reference is directly followed by literal alphanumeric text without whitespace, formal notation `${...}` prevents ambiguous identifier parsing:

```velocity
${environment}Service    ## If $environment="prod", renders "prodService"
```

#### Quiet Notation (`$!`)
In standard non-strict rendering:
- `$missing` renders literally as the string `"$missing"`.
- `$!missing` renders as an empty string `""`.
- `$!{user.address.zipCode}` renders as an empty string if any element in the navigation path is null or undefined.

#### Fallback / Alternate Value (`|`)
VTL provides first-class fallback expressions using the pipe operator (`|`):
```velocity
Welcome, $user.name|'Valued Customer'!
Order count: ${stats.totalOrders|0}
```
If the expression preceding `|` evaluates to `UNDEFINED` or `DEFINED_NULL`, the right-hand operand is evaluated and rendered.

### 3.2 Property Navigation

Properties are accessed via the dot (`.`) operator: `$object.property`. VTL resolves properties using the following deterministic candidate order:

1. **Java Record Components**: `$record.component` resolves directly to `record.component()`.
2. **Standard JavaBean Getters**: `$bean.property` resolves to `bean.getProperty()`.
3. **Capitalized Getters**: `$bean.property` resolves to `bean.GetProperty()`.
4. **Boolean Accessors**: `$bean.active` resolves to `bean.isActive()`.
5. **Direct Map Lookup**: If the receiver implements `java.util.Map`, `$map.key` resolves to `map.get("key")`.
6. **Zero-Argument Methods**: `$object.property` resolves to `object.property()`.

```velocity
$account.id            ## account.id() (record) or account.getId() (bean)
$account.active        ## account.isActive()
$headers.authorization ## headers.get("authorization") (Map)
```

### 3.3 Method Invocations

VTL supports invoking public methods on context objects with zero or more arguments:

```velocity
$string.toUpperCase()
$list.contains("admin")
$calculator.add(10, 20)
$dateFormatter.format($now, "yyyy-MM-dd")
```

#### Security Confinement
Under standard and safe security policies:
- Methods such as `getClass()`, `ClassLoader` accessors, and Java reflection APIs (`Class.forName`, `Method.invoke`) are strictly denied at both runtime and AOT compilation.
- Invocations on unpermitted methods throw `TemplateSecurityException` with diagnostic code `SECURITY:VIOLATION`.

### 3.4 Safe Index Access

Bracket indexing (`[...]`) provides uniform index and key access across arrays, lists, and maps:

```velocity
$items[0]              ## Array or java.util.List element at index 0
$matrix[1][2]          ## Multi-dimensional array or nested list access
$lookup["account-id"]  ## java.util.Map key access using string literal
$lookup[$dynamicKey]   ## java.util.Map key access using dynamic reference
```

If an index is out of bounds or a map key does not exist, the expression evaluates safely to `UNDEFINED` without throwing an unhandled runtime exception.

---

## 4. Literals & Data Structures

VTL supports a rich set of literal values:

### 4.1 String Literals
- **Single-Quoted Strings (`'...'`)**: Uninterpreted verbatim text. Reference sigils and directive keywords are not evaluated.
  ```velocity
  #set($raw = 'Hello $name!')  ## $raw contains the literal string "Hello $name!"
  ```
- **Double-Quoted Strings (`"..."`)**: Interpolated text. Embedded references are evaluated when the literal is formed.
  ```velocity
  #set($greeting = "Hello $name!")  ## If $name="World", $greeting contains "Hello World!"
  ```

### 4.2 Numeric Literals
- **Integers**: Decimal integers (`0`, `42`, `-17`).
- **Longs**: Suffix `L` or `l` (`9223372036854775807L`).
- **Floating-Point**: Decimal points (`3.14159`, `-0.5`).

### 4.3 Boolean Literals
- Case-sensitive keywords: `true` and `false`.

### 4.4 Null Literal
- Keyword `null` representing a defined null value (`DEFINED_NULL`).

### 4.5 List Literals
Lists are declared using square brackets with comma-separated expressions:
```velocity
#set($colors = ["red", "green", "blue"])
#set($mixed = [1, "two", true, $user.name])
```

### 4.6 Map Literals
Maps are declared using curly braces with key-value pairs separated by colons:
```velocity
#set($config = {
  "timeout": 5000,
  "retries": 3,
  "enabled": true
})
```

### 4.7 Integer Range Literals
Integer ranges construct lightweight iterable sequences:
```velocity
#set($ascending = [1..5])       ## Produces [1, 2, 3, 4, 5]
#set($descending = [5..1])      ## Produces [5, 4, 3, 2, 1]
#set($dynamic = [$start..$end]) ## Range bounded by variable values
```

---

## 5. Operators & Expressions

### 5.1 Arithmetic Operators

| Operator | Name | Example | Behavior |
| :--- | :--- | :--- | :--- |
| `+` | Addition / Concat | `$a + $b` | Adds numbers, or concatenates if either operand is `String` |
| `-` | Subtraction | `$a - $b` | Subtracts numeric operands |
| `*` | Multiplication | `$a * $b` | Multiplies numeric operands |
| `/` | Division | `$a / $b` | Divides numeric operands; fails fast on division by zero |
| `%` | Modulo | `$a % $b` | Remainder of numeric division |

> [!IMPORTANT]
> **Division by Zero (`DIFF-001`)**: Unlike legacy Velocity which silently returns `null` or raw text, Viet Template immediately fails fast with `TemplateRenderException` upon division by zero, preventing undetected corruption in critical financial or billing outputs.

### 5.2 Comparison Operators

| Operator | Description | Supported Types |
| :--- | :--- | :--- |
| `==` | Value equality | Numbers, Strings, Booleans, Objects (`equals`) |
| `!=` | Value inequality | Numbers, Strings, Booleans, Objects |
| `<` | Less than | Numbers, `Comparable` |
| `<=` | Less than or equal | Numbers, `Comparable` |
| `>` | Greater than | Numbers, `Comparable` |
| `>=` | Greater than or equal | Numbers, `Comparable` |

### 5.3 Logical Operators

VTL supports both symbolic and word-based logical operators with short-circuit evaluation:

| Symbolic | Keyword | Description |
| :--- | :--- | :--- |
| `&&` | `and` | Logical conjunction (short-circuit) |
| `\|\|` | `or` | Logical disjunction (short-circuit) |
| `!` | `not` | Logical unary negation |

```velocity
#if($user.authenticated && ($user.role == "ADMIN" || $user.isSuperuser()))
  Access granted.
#end
```

### 5.4 Operator Precedence

Listed from highest precedence to lowest precedence:

1. Parenthesized expressions `(...)`, array/map indexing `[...]`, property access `.`
2. Unary operators: `!`, `not`, unary `-`
3. Multiplicative: `*`, `/`, `%`
4. Additive: `+`, `-`
5. Relational: `<`, `<=`, `>`, `>=`
6. Equality: `==`, `!=`
7. Logical AND: `&&`, `and`
8. Logical OR: `||`, `or`
9. Fallback: `|`

---

## 6. Directives

Directives begin with `#` and control template logic, flow, definitions, and inclusion.

### 6.1 `#set` — Variable Assignment
Assigns the result of an expression to a context reference within the current execution scope:

```velocity
#set($title = "Customer Portal")
#set($discount = $cart.subtotal * 0.15)
#set($items[0] = "Updated Item")
```

#### Null Assignment Semantics
- In standard VTL: `#set($var = null)` assigns `DEFINED_NULL` to `$var`.
- Under legacy null RHS preservation (`DIFF-003`): if the RHS evaluates to `null` or `UNDEFINED`, the target reference retains its existing value.

### 6.2 `#if`, `#elseif`, `#else` — Conditionals
Conditionally renders template blocks based on truthiness:

```velocity
#if($cart.isEmpty())
  Your cart is empty.
#elseif($cart.itemCount == 1)
  You have 1 item in your cart.
#else
  You have $cart.itemCount items in your cart.
#end
```

### 6.3 `#foreach` — Collection Iteration
Iterates over arrays, collections, iterables, and maps:

```velocity
#foreach($item in $catalog.products)
  <div class="product #if($foreach.first)product-hero#end">
    <h3>#$foreach.count: $item.name</h3>
    <p>Index: $foreach.index</p>
  </div>
#end
```

#### Loop Termination
- `#break`: Exits the current loop immediately.
- `$foreach.stop()`: Programmatic termination method (`EXT-001`) on loop metadata.

For in-depth loop mechanics, nested loop traversal, and variable scoping, see [Foreach Loops and Scopes](foreach-and-scopes.md).

### 6.4 `#macro` — Velocimacro Definitions
Defines reusable inline presentation macros:

```velocity
#macro(badge $label $variant)
  <span class="badge badge-$variant">$label</span>
#end

## Invocation:
#badge("Active" "success")
```

#### Default Parameters and Macro Bodies
```velocity
#macro(alert $message $level = "info")
  <div class="alert alert-$level">$message</div>
#end

#macro(card $title)
  <div class="card">
    <div class="card-header">$title</div>
    <div class="card-body">$bodyContent</div>
  </div>
#end

## Body invocation using #@
#@card("Account Status")
  <p>Your subscription renews next month.</p>
#end
```

For macro precedence, recursion guardrails, and global libraries, see [Macros and Layouts](macros-and-layouts.md).

### 6.5 `#define` — Deferred Block Rendering
Captures a template fragment as a deferred renderable block:

```velocity
#define($headerBlock)
  <header>
    <h1>Welcome, $user.name</h1>
  </header>
#end

## Rendered whenever $headerBlock is referenced:
$headerBlock
```

Unlike `#set` with a string literal, `#define` preserves lazy evaluation: references inside the block evaluate at the moment `$headerBlock` is printed.

### 6.6 `#parse` — Dynamic Template Parsing
Parses and executes a sub-template within the current execution scope:

```velocity
#parse("fragments/navigation.vtl")
#parse($dynamicPath)
```

Sub-templates share access to the caller's execution scope.

### 6.7 `#include` — Static File Inclusion
Includes the raw, unparsed content of one or more files directly into the output stream:

```velocity
#include("static/legal-disclaimer.html")
```

No VTL directives or references inside the included file are evaluated.

### 6.8 `#evaluate` — Dynamic String Evaluation
Parses and executes a dynamically constructed string template in the current scope:

```velocity
#set($snippet = "Hello $user.name! Total: $cart.total")
#evaluate($snippet)
```

> [!WARNING]
> `#evaluate` requires interpreter execution and is disabled by default in `VTL_CORE`, `VTL_MIGRATION`, and `VTL_SAFE`, and only permitted when explicitly configured under `VTL_DYNAMIC`. It is subject to strict depth limits (`maxEvaluateDepth`) and source length limits (`maxDynamicSourceLength`) to prevent denial-of-service vulnerabilities.

### 6.9 `#stop` — Execution Termination
Stops template rendering immediately:

```velocity
#if($unauthorized)
  #stop
#end
```

---

## 7. Output Escaping & Streaming Mechanics

Viet Template features an optimized output pipeline designed for high concurrency:

1. **Contextual Auto-Escaping**: When configured with the HTML profile, standard reference expressions are automatically HTML-escaped to neutralize XSS attacks.
2. **Raw Output Bypass**: References wrapped in raw output markers or templates rendering via dedicated raw streams bypass contextual escaping without allocation.
3. **Zero-Allocation Formatting**: Integers, longs, floats, and decimals are formatted directly into target output buffers (`TemplateOutput`) without creating intermediate `java.lang.String` instances.
4. **Buffer Pool Recycling**: High-throughput rendering utilizes bounded thread-safe buffer reuse pools, preventing garbage collection pressure in high-concurrency environments.
