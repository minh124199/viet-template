# 08 — Runtime Rendering and Output Architecture

## 1. Goal

The runtime should be small, predictable and easy for HotSpot to optimize. A compiled template should conceptually reduce to:

```java
out.writeUtf8(C0);
out.writeEscaped(user.name(), HTML_TEXT);
out.writeUtf8(C1);
```

## 2. Public API

```java
public interface Template {
    TemplateDescriptor descriptor();
    void render(RenderContext context, TemplateOutput output);

    default String render(RenderContext context) {
        StringTemplateOutput out = new StringTemplateOutput();
        render(context, out);
        return out.toString();
    }
}
```

String rendering is convenience, not the reference performance path.

## 3. Typed API

```java
public interface UserCardTemplate {
    void render(User user, TemplateOutput output);
}
```

or generated invocation records. Benchmark API shapes and avoid reflection to call generated templates.

## 4. Generic context

```java
public interface RenderContext {
    Object get(String name);
    boolean contains(String name);
}
```

Implementations: immutable map context, schema/slot context, framework adapter. Execution locals remain internal.

## 5. Output abstraction

```java
public interface TemplateOutput {
    void write(CharSequence value);
    default void write(CharSequence value, int start, int end) throws IOException;
    void write(char value);
    void writeInt(int value);
    void writeLong(long value);
    void writeDouble(double value);
    void writeBoolean(boolean value);
    void writeUtf8(byte[] value);
    void writeEscaped(CharSequence value, EscapeMode mode);
}
```

### 5.1 Range-Write SPI Contract (`write(CharSequence, int, int)`)

The range-write method writes a subsequence of the specified `CharSequence` using half-open interval indexing `[start, end)`:
- **Interval**: `start` is inclusive, `end` is exclusive; written character count is `end - start`.
- **Null Safety**: Passing a `null` `CharSequence` is explicitly a safe no-op.
- **Empty Range**: If `start == end`, the method returns immediately without writing characters.
- **Bounds Validation**: Throws `IndexOutOfBoundsException` if `start < 0`, `end < start`, or `end > value.length()`, matching `Objects.checkFromToIndex(start, end, value.length())`.
- **Backward Compatibility**: Default implementation delegates to `write(value.charAt(i))` in a sequential loop, allowing external custom `TemplateOutput` implementations to function without immediate recompilation or code changes.
- **Implementation Characteristics**:
  - `StringTemplateOutput`: Zero allocation, delegates to `StringBuilder.append(CharSequence, int, int)`.
  - `Utf8OutputStreamTemplateOutput`: Zero allocation, direct bit-level UTF-8 encoding into stream buffer with surrogate pair validation.
  - `WriterTemplateOutput`: String writes forward directly to `Writer.write(String, int, int)` without allocation; non-String `CharSequence` writes use an instance-confined lazy scratch buffer (`char[1024]`), delivering zero per-call allocation ($0.000\text{ B/op}$) and avoiding per-character `Writer.write(int)` synchronization bottlenecks.
  - `CountingTemplateOutput`: Accurately debits output limit budget by `end - start` before delegating.

A backend may choose a Writer-oriented or UTF-8-oriented compiled artifact so capability checks are not repeated for every literal.

## 6. Output implementations

- `WriterTemplateOutput`;
- `Utf8OutputStreamTemplateOutput`;
- `StringTemplateOutput`;
- servlet adapter;
- future reactive adapter in separate module.

## 7. UTF-8 strategy

Static source is known at build time:

```text
literal -> UTF-8 bytes at compile time -> bulk output write at runtime
```

For dynamic content, escape while encoding where possible. Avoid per-value `String.getBytes()` allocations.

### 7.1 Bounded UTF-8 Stream-Buffer Reuse (`Utf8BufferPool`)

Streaming template renders via `Utf8OutputStreamTemplateOutput` standard constructor (`DEFAULT_BUFFER_SIZE = 8192`) lease their internal 8 KiB buffer from a package-private bounded pool (`Utf8BufferPool`).

- **Architecture**: Backed by a lock-free `AtomicReferenceArray<byte[]>` with a strictly bounded capacity of 16 slots ($128\text{ KiB}$ maximum retained payload).
- **Single-Owner Confinement**: The buffer is acquired upon `Utf8OutputStreamTemplateOutput` instantiation and released strictly upon `close()` inside a `try/finally` block. `flush()` preserves the buffer lease without premature release.
- **Post-Close Safety**: Upon `close()`, the internal buffer reference is immediately cleared to `null` and `closed` is set to `true`. Subsequent writes or flushes throw `IOException("Output is closed")`. Double `close()` is strictly idempotent.
- **Non-Blocking Resilience**: If the pool is temporarily exhausted under high concurrency, `tryAcquire()` returns `null`, and the constructor falls back to allocating an unpooled private `byte[8192]`. The render path never blocks, spins, or parks.
- **Custom Buffer Sizing**: Non-default buffer sizes (e.g. 64, 1024) allocate privately with `isPooled = false` and are never admitted to or released from the pool.
- **Virtual Thread Friendly**: Zero use of `ThreadLocal`; fully safe under massive virtual thread concurrency (JEP 444) without carrier-thread pinning or memory leakage.
- **Lifecycle & Stream Ownership Guidance**:
  - `close()` flushes buffered bytes, closes the wrapped underlying `OutputStream`, and returns the pooled buffer to `Utf8BufferPool`.
  - `flush()` flushes buffered bytes to the underlying stream but **does not release the buffer lease**, allowing continued writing.
  - **Best Practice**: Use `try-with-resources` whenever the caller owns the underlying stream. If the underlying stream must remain open (e.g. certain servlet container response streams), wrap the stream in a non-closing delegate (such as a `FilterOutputStream` that suppresses `close()`) before passing it to `Utf8OutputStreamTemplateOutput`.
  - **Unclosed Output Safety**: If an output instance is discarded without `close()`, its leased buffer is retained on that instance until reclaimed by the garbage collector. This does not cause an unbounded pool leak because the pool's retained capacity remains strictly bounded at 16 buffers.

## 8. Escaping

Escaping writes directly to output without intermediate `String` allocation. `HtmlTextEscaper` segment allocation is eliminated by streaming unescaped character slices directly to `TemplateOutput.write(CharSequence, int, int)`. `String` and `UTF-8` optimized range paths are allocation-free ($0.000\text{ B/op}$). `WriterTemplateOutput` uses direct string writes for `String` inputs and lazy instance-buffer batching for non-`String` inputs to eliminate per-range allocation without incurring per-character lock synchronization penalties.

Avoid:

```java
String escaped = HtmlEscaper.escape(value);
out.write(escaped);
```

Prefer:

```java
StandardEscapers.get(mode).escape(value, out);
```

### Contextual escaping decisions and boundaries

Generic HTML escaping is **not** sufficient across heterogeneous web contexts. Viet Template distinguishes context-specific escaping policies:

1. **HTML Body Text (`HTML_TEXT`)**:
   - Escapes `&`, `<`, `>`, `"`, and `'`.
   - Used for text nodes in HTML templates.
   - Fast-path verification streams unescaped content directly to `TemplateOutput` when no entity replacements are needed.
   - Bypassed when the input is an instance of `SafeHtml` (not bypassed by `SafeUrl`).

2. **HTML Quoted Attribute (`HTML_ATTRIBUTE_QUOTED`)**:
   - Escapes `&`, `<`, `>`, `"`, `'`, backtick (`` ` ``), and ASCII control characters (`0x00`-`0x1F` except `\t`, `\n`, `\r`) plus `0x7F`.
   - Prevents attribute boundary breakout in both double-quoted and single-quoted attributes, and neutralizes backtick attribute escapes in older browser DOM parsers.
   - Not bypassed by `SafeHtml` or `SafeUrl` (always escaped in attributes to prevent breakout).
   - **Boundary Decision (Attribute Escaping vs URL Scheme Validation)**:
     - Quoted attribute escaping protects syntactic delimiter boundaries (`"` or `'`), preventing an attacker from escaping the attribute value into the HTML tag context (e.g. `foo" onmouseover="...`).
     - Quoted attribute escaping **does not** validate URL schemes. For instance, `javascript:alert(1)` contains no characters requiring HTML entity replacement; escaping it in an `href` attribute produces `javascript:alert(1)`.
     - Consequently, emitting variables into `href` or `src` attributes requires URL scheme validation via `SafeUrl` or `SafeUrlValidator` in the application data layer.

3. **URI / URL Component (`URL_COMPONENT`)**:
   - Implements strict RFC 3986 percent-encoding for URI query parameters and path segments.
   - Preserves unreserved characters (`[a-zA-Z0-9_.~-]`) and percent-encodes all reserved delimiters (`?`, `&`, `=`, `#`, `/`, `:`, space `%20`) as uppercase UTF-8 hex `%XX`.
   - Bypassed when the input is an instance of `SafeUrl` (not bypassed by `SafeHtml`).
   - **Boundary Decision**: HTML escaping inside a URI query string is completely invalid; HTML entities such as `&amp;` break URI parameter parsing, while spaces must be `%20` or `+`. URL escaping must be applied before HTML escaping if emitting into an HTML `href` attribute.

4. **JavaScript Context (`JS_STRING`)**:
   - Escapes quotes (`'`, `"`), backslashes (`\`), control characters, line terminators (`\u2028`, `\u2029`), and HTML tag delimiters (`<`, `>`, `&` encoded as `\u003C`, `\u003E`, `\u0026`).
   - Prevents `</script>` tag breakouts and comment execution inside inline `<script>` tags.
   - **Boundary Decision**: Generic HTML escaping inside JavaScript code causes syntax errors (e.g. `&quot;`) and fails to prevent script breakout attacks. JavaScript string interpolation requires dedicated JS literal escaping.

5. **CSS Context (`CSS_STRING`)**:
   - Escapes quotes, backslashes, line breaks, and HTML tag delimiters inside CSS string literals.
   - **Boundary Decision**: Context-free escaping is insufficient for general CSS values. Unquoted property names, dimensions, or `url(...)` targets require structural CSS validation (e.g. preventing `javascript:` pseudo-protocols).

## 9. Safe content and URL Validation

Use explicit types:

```java
public sealed interface SafeContent permits SafeHtml, SafeUrl {
    CharSequence content();
}
```

- `SafeHtml`: Capability wrapper representing trusted HTML markup that only bypasses `HTML_TEXT` escaping (still escaped in `HTML_ATTRIBUTE_QUOTED`). It does not perform HTML sanitization.
  - `SafeHtml.of(html)`: Wraps trusted markup.
  - `SafeHtml.ofTrusted(html)`: Explicit capability factory documenting compile-time audited trusted markup. Passing unvalidated user input creates XSS vulnerabilities.
- `SafeUrl`: Capability wrapper representing a validated or trusted URL that only bypasses `URL_COMPONENT` escaping (still escaped in `HTML_TEXT` and `HTML_ATTRIBUTE_QUOTED`).
  - `SafeUrl.of(url)` / `SafeUrl.ofValidated(url)`: Validates the URL scheme against `SafeUrlValidator`; throws `IllegalArgumentException` on invalid scheme or obfuscation.
  - `SafeUrl.tryOf(url)`: Returns `Optional<SafeUrl>` (or `Optional.empty()` if invalid).
  - `SafeUrl.ofTrusted(url)`: Explicit escape hatch bypassing validation strictly for host-verified constants.
- Ordinary `String` is never treated as trusted markup or trusted URLs.

### Contextual Escaping Behavior Matrix

| Value Type | `HTML_TEXT` | `HTML_ATTRIBUTE_QUOTED` | `URL_COMPONENT` | `RAW` |
|---|---|---|---|---|
| **plain `String` / `CharSequence`** | Escaped (`HtmlTextEscaper`) | Escaped (`HtmlAttributeEscaper`) | Encoded (`UrlComponentEscaper`) | Raw* |
| **`SafeHtml`** | **Trusted (Bypasses escaping)** | Escaped (`HtmlAttributeEscaper`) | Encoded (`UrlComponentEscaper`) | Raw* |
| **`SafeUrl`** | Escaped (`HtmlTextEscaper`) | Escaped (`HtmlAttributeEscaper`) | **Trusted (Bypasses encoding)** | Raw* |

*\* In `VTL_SAFE` profile, `RAW` output still enforces class permission policy checks (`isClassPermitted`) before string coercion.*

- **Context-Specific Trust**: `SafeHtml` and `SafeUrl` bypass escaping *only* in the specific output contexts for which they grant trust (`HTML_TEXT` for `SafeHtml`, `URL_COMPONENT` for `SafeUrl`). Context-specific escaping rules continue to apply when those values are rendered into other output contexts.
- **Delimiter Breakout Protection**: When `SafeHtml` or `SafeUrl` is rendered inside an `HTML_ATTRIBUTE_QUOTED` context, it remains subject to attribute escaping to neutralize attribute delimiter breakouts (`"` or `'`) and backticks.
- **URL Validation Semantics**: Passing scheme validation via `SafeUrl.of(...)` confirms that the URL syntax and scheme conform to the configured scheme allowlist (`http`, `https`, `mailto`, `tel`, or safe relative paths); it does **not** imply that the remote web destination itself is trustworthy.

### SafeUrl Scheme Validation Rules (`SafeUrlValidator`)
- **Allowed Schemes**: `http`, `https`, `mailto`, `tel` (case-insensitive).
- **Allowed Relative Targets**: Relative path segments (`/`, `./`, `../`, `?`, `#`, `path/to/resource`).
- **Rejected Schemes**: `javascript`, `vbscript`, `data`, `file`, `blob`, and unknown/custom URI schemes.
- **Obfuscation Resistance**:
  - Strips leading/trailing ASCII whitespace and control characters before evaluation.
  - Rejects internal whitespace or control characters (`\t`, `\n`, `\r`, `\0`, `\u0001`-`\u001F`, `\u007F`) within the scheme token.
  - Rejects URL percent-encoded delimiters (e.g. `%3a`, `%3A`) anywhere in the scheme or path.
  - Rejects HTML entity references (`&#...` or `&colon;`) within the scheme token.

## 10. Buffering and back-pressure

### Synchronous servlet back-pressure
- For synchronous servlet rendering (`HttpServletResponse.getOutputStream()` or `getWriter()`), `Utf8OutputStreamTemplateOutput` and `WriterTemplateOutput` stream directly to the underlying transport stream without unbounded intermediate memory buffers.
- Back-pressure is provided naturally by the underlying operating system and TCP socket layer: when the servlet container's send buffer fills, write operations block the rendering thread via standard synchronous socket blocking (`SocketOutputStream.socketWrite`).
- This guarantees bounded JVM heap memory even under slow-client conditions.
- `TemplateOutput.flush()` propagates flushes to the underlying stream when explicit chunking or early-head flushes are intended, avoiding involuntary micro-flushes.

### Reactive back-pressure non-goal
- Asynchronous non-blocking back-pressure (e.g. Reactive Streams `Flow.Publisher`, RSocket, or Netty byte buffers) requires re-entrant render suspension and asynchronous continuation frames.
- Reactive output is an explicit **non-goal** for Milestone M8 and core runtime. It is reserved for a dedicated reactive adapter module in a future milestone.

## 11. Allocation policy

Typed hot path should allocate nothing solely for:

- property access;
- truthiness;
- unused loop metadata;
- primitive formatting when specialized (direct decimal encoding via NumberFormatting);
- static literals;
- direct template calls;
- HTML text escaping (direct range forwarding);
- streaming UTF-8 buffer setup (eliminated via 128 KiB bounded Utf8BufferPool).

Expected allocations may come from user code, requested String result, or underlying transport buffers (e.g. ByteArrayOutputStream resizing).

## 12. Render limits

```java
public record RenderLimits(
    long maxOutputBytes,
    long maxLoopIterations,
    int maxTemplateDepth,
    int maxMacroDepth,
    int maxEvaluateDepth
) {}
```

Optional time/cancellation checks should occur at coarse safe points, not every expression.

## 13. Error wrapping

If a getter throws, wrap with template id/span/operation/template stack while preserving cause for server diagnostics.

## 14. Thread safety & Concurrency

Templates, precompiled AOT executables, prepared IR templates, and call-site registries are immutable and thread-safe per engine generation. Rendering is strictly synchronous and caller-thread-bound. Context, output, scopes, budgets, and execution frames are per invocation, thread-confined, and need not be thread-safe. Virtual threads are a supported caller execution model and concurrency stress target, not an internal rendering tier; zero carrier pinning occurs.

## 15. Request-State Isolation & Variable Resolution Architecture

Following Milestones M19.3c.1 through M19.3c.5, the runtime maintains a clear separation between immutable generation state and render-local request state:

1. **Strict Request Isolation**:
   - Every render invocation receives its own fresh, thread-confined `ExecutionContext`, `ExecutionFrame`, and `RenderBudget`.
   - No mutable request state is ever stored on `Template`, `CompiledTemplate`, `VtlTemplateEngine`, or in static/global/ThreadLocal caches.
2. **Static Variable Slots as Primary Storage**:
   - Compiler-assigned integer slot IDs in `ExecutionFrame` (`EvaluationValue[] slots`) serve as the primary storage tier for compiler-known locals.
   - Variable reads and writes compile to direct array indexing (`ALOAD`/`ASTORE` or `slots[slotIndex]`), completely bypassing hash computations and map allocations.
3. **Dynamic `ExecutionContext` as Semantic Fallback & Boundary**:
   - Lexical `LocalScope` maps and render-local `templateVariables` serve as the semantic fallback for dynamic constructs: `#evaluate`, `#parse`, macro dynamic bindings, and unanalyzed variable lookups.
   - Single-probe lookup in `templateVariables` relies on the invariant that stored values are always non-null `EvaluationValue` instances.
4. **Three-State Evaluation Distinction**:
   - The runtime strictly preserves the distinction between `UNDEFINED` (reference not bound), `DEFINED_NULL` (explicitly assigned null), and `DEFINED_VALUE` (concrete value).
   - `EvaluationValue.undefined()` and `EvaluationValue.definedNull()` are immutable singletons; Java `null` is never exposed or conflated with `DEFINED_NULL`.
   - In strict mode, accessing an undefined variable throws `TemplateRenderException` with `InterpreterDiagnosticCodes.VARIABLE_UNDEFINED`.
