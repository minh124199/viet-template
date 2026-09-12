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
    void write(char value);
    void writeInt(int value);
    void writeLong(long value);
    void writeDouble(double value);
    void writeBoolean(boolean value);
    void writeUtf8(byte[] value);
    void writeEscaped(CharSequence value, EscapeMode mode);
}
```

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

## 8. Escaping

Escaping writes directly to output without intermediate `String` allocation.

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
- primitive formatting when specialized;
- static literals;
- direct template calls.

Expected allocations may come from user code, requested String result, or transport buffers.

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

## 14. Thread safety

Templates and registry are immutable/thread-safe. Context/output/render frame are per invocation and need not be thread-safe.
