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

Escaping writes directly to output.

Avoid:

```java
String escaped = HtmlEscaper.escape(value);
out.write(escaped);
```

Prefer:

```java
HtmlEscaper.writeEscaped(value, out);
```

## 9. Safe content

Use explicit types:

```java
sealed interface SafeContent permits SafeHtml, SafeUrl {}
```

Ordinary `String` is not trusted markup.

## 10. Buffering

Core does not mandate a large buffer. Transport adapters choose buffering. Servlet containers often buffer already; avoid unmeasured double buffering.

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
