# Diagnostics & Error Code Catalog

## 1. Overview & Diagnostic Architecture

Viet Template provides a structured, compiler-grade diagnostic model centered on the `Diagnostic` record and `DiagnosticCode` identifier in `viet-template-api`:

```java
public record DiagnosticCode(String category, String id) implements Serializable {
    public static DiagnosticCode of(String category, String id) { ... }
}
```

Every compilation, semantic, security, resource, and runtime exception in the engine inherits from `TemplateException` and associates a structured `DiagnosticCode`, source template identifier (`TemplateId`), and source position span (`SourceSpan`).

This catalog provides an authoritative reference for all stable diagnostic codes emitted by Viet Template.

---

## 2. Diagnostics Catalog

### 2.1 SYNTAX Diagnostics (Compile-Time / Parse-Time)

Emitted during lexing and AST parsing when template source violates VTL grammar.

#### `SYNTAX:PARSE_ERROR`
- **Category**: `SYNTAX`
- **Phase**: Compile-Time / Parse-Time
- **Exception**: `TemplateSyntaxException`
- **When Emitted**: The parser encounters a statement, token sequence, or directive structure that violates VTL grammar rules.
- **Common Variations**:
  1. **Structural Mismatch**: Missing `#end` block terminator, unclosed parenthetical expression, or illegal directive nesting.
     ```vtl
     #if($condition)
         <p>Missing closing tag</p>
     ## Missing #end
     ```
     *Fix*: Verify all `#if`, `#foreach`, `#macro`, and `#define` blocks have corresponding `#end` tags.
  2. **Unexpected Token**: Illegal operators or misplaced punctuation within directives.
     ```vtl
     #set($x == 5)  ## Illegal comparison operator in #set assignment
     ```
     *Fix*: Use single `=` for variable assignment in `#set`.
  3. **Unterminated String**: A single-quoted or double-quoted string literal opened but never closed before end-of-line or end-of-file.
     ```vtl
     #set($title = "Unclosed string)
     ```
     *Fix*: Close the string literal with matching quotation marks.

---

### 2.2 VTLS Semantic Diagnostics (Compile-Time)

Emitted during static semantic analysis when typed model declarations (`#* @vtlvariable ... *#`) are enabled.

#### `VTLS:2101` (`UNRESOLVED_ROOT`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: A root variable is referenced in strict model mode without a corresponding model binding.
- **Example**:
  ```vtl
  <p>$unknownVariable</p>
  ```
- **Likely Cause**: Typo in variable name or missing `@vtlvariable` declaration.
- **Recommended Fix**: Add `#* @vtlvariable name="unknownVariable" type="..." *#` or bind the variable in `RenderContext`.

#### `VTLS:2102` (`INVALID_ASSIGNMENT`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: Assigning a value whose type is incompatible with the declared variable type.
- **Example**:
  ```vtl
  #* @vtlvariable name="count" type="java.lang.Integer" *#
  #set($count = "text")
  ```
- **Likely Cause**: Type mismatch in `#set` expression.
- **Recommended Fix**: Assign a value compatible with the declared type.

#### `VTLS:2103` (`TYPE_MISMATCH`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: Incompatible argument type supplied to a Java method call on a typed receiver.
- **Example**:
  ```vtl
  #* @vtlvariable name="service" type="com.example.OrderService" *#
  $service.findById("not-a-long")  ## Expected Long id
  ```
- **Likely Cause**: Passing wrong parameter type or mismatched method overload.
- **Recommended Fix**: Match the method's expected parameter types.

#### `VTLS:2104` (`PROPERTY_NOT_FOUND`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: Property access on a typed receiver class has no matching getter, record component, or public field.
- **Example**:
  ```vtl
  #* @vtlvariable name="user" type="com.example.UserDto" *#
  <p>$user.nonExistentProperty</p>
  ```
- **Likely Cause**: Typo in property name or missing getter method on Java class.
- **Recommended Fix**: Check getter naming conventions (`getFoo()`, `isFoo()`, or record component `foo()`).

#### `VTLS:2105` (`METHOD_NOT_FOUND`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: Method invocation on a typed receiver has no matching name or argument count.
- **Example**:
  ```vtl
  #* @vtlvariable name="user" type="com.example.UserDto" *#
  <p>$user.computeDiscount(1, 2, 3)</p>
  ```
- **Likely Cause**: Incorrect method name or wrong parameter count.
- **Recommended Fix**: Verify method signature on the receiver class.

#### `VTLS:2106` (`INVALID_ITERABLE`)
- **Category**: `VTLS`
- **Phase**: Compile-Time
- **Exception**: `TemplateSemanticException`
- **When Emitted**: Target of a `#foreach` loop is statically known to not implement `Iterable`, `Collection`, `Map`, or array.
- **Example**:
  ```vtl
  #* @vtlvariable name="user" type="com.example.UserDto" *#
  #foreach($item in $user.age) ... #end  ## age is int
  ```
- **Likely Cause**: Attempting to loop over a scalar or non-iterable object.
- **Recommended Fix**: Loop over a `List`, `Set`, `Map`, or array property.

---

### 2.3 SECURITY Diagnostics (Compile-Time & Runtime)

Emitted when template operations violate sandboxing, access policies, or protected variable boundaries.

#### `SECURITY:ACCESS_DENIED`
- **Category**: `SECURITY`
- **Phase**: Runtime
- **Exception**: `TemplateSecurityException`
- **When Emitted**: A template attempts to access a class, method, or property blocked by `MemberAccessPolicy`.
- **Example**:
  ```vtl
  $user.getClass().getName()
  ```
- **Likely Cause**: Calling reflective methods (`getClass()`, `getClassLoader()`) or accessing types outside the policy allowlist.
- **Recommended Fix**: Remove reflective calls from templates; pass required metadata explicitly in the model.

#### `SECURITY:COMPILATION_REJECTED`
- **Category**: `SECURITY`
- **Phase**: Runtime
- **Exception**: `TemplateSecurityException`
- **When Emitted**: Dynamic runtime compilation is attempted when `rejectRuntimeCompilation(true)` is active.
- **Example**: Loading an unindexed template in production with `viet-template.runtime-compilation-enabled=false`.
- **Likely Cause**: Template was not precompiled during the Maven/Gradle build.
- **Recommended Fix**: Run the Maven/Gradle AOT plugin to compile templates into bytecode and update `META-INF/viet-template/templates.idx`.

#### `SECURITY:PROTECTED_VARIABLE`
- **Category**: `SECURITY`
- **Phase**: Runtime
- **Exception**: `TemplateSecurityException`
- **When Emitted**: A template attempts to overwrite a reserved context variable via `#set` or contributor.
- **Example**:
  ```vtl
  #set($foreach = "corrupted")
  #set($security = "hacked")
  ```
- **Likely Cause**: Attempting to reassign engine-managed variables (`$foreach`, `$security`, `$csrf`).
- **Recommended Fix**: Use a different variable name for template logic.

#### `SECURITY:VIOLATION`
- **Category**: `SECURITY`
- **Phase**: Runtime
- **Exception**: `TemplateSecurityException`
- **When Emitted**: Dynamic linker (`DynamicCallSite`) detects access to an inaccessible member or classloader violation.
- **Likely Cause**: Non-public class or method invoked from template without `@TemplateData`.
- **Recommended Fix**: Ensure receiver class and methods are `public` and annotate with `@TemplateData` if using safe mode.

---

### 2.4 LIMIT Diagnostics (Execution Budget & Resource Confinement)

Emitted when template execution violates `RenderBudget` constraints.

#### `LIMIT:TIME_LIMIT_EXCEEDED`
- **Category**: `LIMIT`
- **Phase**: Runtime
- **Exception**: `TemplateLimitException`
- **When Emitted**: Template execution duration exceeds `RenderBudget.maxExecutionTimeMillis`.
- **Likely Cause**: Infinite loop, slow recursive macro, or blocking external call.
- **Recommended Fix**: Audit `#foreach` loops and macros; ensure loop termination conditions are met.

#### `LIMIT:LIMIT_EXCEEDED`
- **Category**: `LIMIT`
- **Phase**: Runtime
- **Exception**: `TemplateLimitException`
- **When Emitted**: Total rendered character output or total loop iterations exceed configured caps.
- **Likely Cause**: Malicious or accidental exponential text generation in nested loops.
- **Recommended Fix**: Increase `RenderBudget` thresholds if legitimate, or paginate output data.

#### `LIMIT:AST_NODE_LIMIT` & `LIMIT:SOURCE_TOO_LARGE`
- **Category**: `LIMIT`
- **Phase**: Compile-Time
- **Exception**: `TemplateLimitException`
- **When Emitted**: Raw template string exceeds maximum character or AST node thresholds.
- **Likely Cause**: Uploading excessively large files as templates.
- **Recommended Fix**: Decompose large monolithic templates into smaller `#parse` fragments.

---

### 2.5 RESOURCE Diagnostics (Repository & Loading)

#### `RESOURCE:NOT_FOUND`
- **Category**: `RESOURCE`
- **Phase**: Runtime / Load-Time
- **Exception**: `TemplateResourceException`
- **When Emitted**: The requested template path cannot be resolved by any registered `TemplateRepository`, or path traversal (`..`) was detected.
- **Example**:
  ```java
  engine.get("non-existent.vm");
  engine.get("../../etc/passwd");
  ```
- **Likely Cause**: Typo in template path, file missing from classpath, or path traversal attempt.
- **Recommended Fix**: Check template file location in `src/main/resources/templates` and verify prefix/suffix configuration.

---

### 2.6 LAYOUT Diagnostics (Two-Stage Rendering)

#### `LAYOUT:CYCLE_DETECTED`
- **Category**: `LAYOUT`
- **Phase**: Runtime
- **Exception**: `TemplateLayoutException`
- **When Emitted**: Layout resolution encounters a circular dependency.
- **Example**: Layout A specifies Layout B, which specifies Layout A.
- **Likely Cause**: Misconfigured layout override hierarchy.
- **Recommended Fix**: Break circular references by terminating layouts or setting `#set($layout = false)` on inner templates.

#### `LAYOUT:DEPTH_EXCEEDED`
- **Category**: `LAYOUT`
- **Phase**: Runtime
- **Exception**: `TemplateLayoutException`
- **When Emitted**: Nested layout hierarchy exceeds maximum configured layout depth.
- **Likely Cause**: Accidental recursive layout nesting.
- **Recommended Fix**: Restructure layout nesting hierarchy.

---

### 2.7 INTERPRETER Diagnostics (Dynamic Runtime)

#### `INTERPRETER:VARIABLE_UNDEFINED`
- **Category**: `INTERPRETER`
- **Phase**: Runtime
- **Exception**: `TemplateRenderException`
- **When Emitted**: A reference is accessed that does not exist in the context while `strictReferences(true)` is enabled.
- **Example**:
  ```vtl
  Hello, $user!  ## with strictReferences(true) and user not bound
  ```
- **Likely Cause**: Unbound variable in strict mode.
- **Recommended Fix**: Provide variable in `RenderContext`, use quiet reference `$!user`, or provide fallback `${user|'Guest'}`.

---

### 2.8 CONTEXT Diagnostics (Context Composition)

#### `CONTEXT:COLLISION`
- **Category**: `CONTEXT`
- **Phase**: Runtime
- **Exception**: `ContextCollisionException`
- **When Emitted**: Two `RenderContextContributor` instances register duplicate keys under `ContextCollisionPolicy.FAIL`.
- **Likely Cause**: Conflicting attribute names contributed by independent extensions.
- **Recommended Fix**: Rename colliding context keys or configure `ContextCollisionPolicy.MODEL_WINS`.

---

### 2.9 RENDER Diagnostics (Execution Failures)

#### `RENDER:FAILURE`
- **Category**: `RENDER`
- **Phase**: Runtime
- **Exception**: `TemplateRenderException`
- **When Emitted**: An unrecoverable runtime evaluation failure occurs, such as integer division by zero (`DIFF-001`).
- **Example**:
  ```vtl
  #set($val = 100 / 0)
  ```
- **Likely Cause**: Arithmetic division or modulo by zero.
- **Recommended Fix**: Guard division with `#if($divisor != 0)`.

---

## 3. Diagnostic Handling Best Practices

In application exception handlers:

```java
try {
    engine.render(templateName, context);
} catch (TemplateSecurityException e) {
    logger.warn("Security policy violation in template [{}]: {}", e.templateId(), e.getMessage());
    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
} catch (TemplateLimitException e) {
    logger.error("Execution limit exceeded [{}]: {}", e.code(), e.getMessage());
    response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT, "Template execution timed out");
} catch (TemplateResourceException e) {
    logger.info("Template not found: {}", e.templateId());
    response.sendError(HttpServletResponse.SC_NOT_FOUND, "View not found");
} catch (TemplateRenderException e) {
    logger.error("Rendering error in [{}]: {}", e.templateId(), e.getMessage());
    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Rendering error");
}
```
