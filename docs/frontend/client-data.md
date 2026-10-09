# Client Data Bridge

Passing structured data from Java controllers to client-side JavaScript or TypeScript requires strict escaping defenses to prevent cross-site scripting (XSS) and script tag breakout vulnerabilities.

Viet Template provides `$clientData` to serialize server-side models into safe `<script type="application/json">` elements.

## Security Context

Embedding JSON directly into HTML script blocks poses significant security hazards:
- **`</script>` Breakout**: If a JSON payload contains `</script>`, an HTML parser terminates the script block early and interprets the remaining string as executable HTML/JavaScript.
- **HTML Comments & CDATA**: Sequences like `<!--` or `<![CDATA[` can alter browser HTML tokenization modes.
- **ECMAScript Line Terminators**: Line Separator (`U+2028`) and Paragraph Separator (`U+2029`) are valid JSON characters but cause syntax errors in raw JavaScript script blocks.

### Script-Safe Serialization

Viet Template wraps JSON serialization in `ScriptSafeAppendable`, which streams JSON characters and converts sensitive HTML/JavaScript boundary characters into Unicode escape sequences:
- `<` becomes `\u003c`
- `>` becomes `\u003e`
- `&` becomes `\u0026`
- `\u2028` becomes `\u2028`
- `\u2029` becomes `\u2029`

Because standard JSON decoders (like `JSON.parse()`) parse Unicode escape sequences transparently, the resulting data is identical in the client while remaining completely inert in HTML.

## Template Usage

In your VTL template:

```vtl
<div id="payroll-island"></div>

## Emits: <script type="application/json" data-vt-client-data="payroll-data">{"employees":[...]}</script>
$clientData.script("payroll-data", $payrollModel)

$assets.body("src/pages/payroll/index.ts")
```

Client data identifiers must conform to the regex `^[a-zA-Z0-9_.:-]+$` (alphanumeric characters, underscores, hyphens, dots, and colons). Empty or invalid identifiers trigger a `ClientDataSerializationException` with code `VT-CLIENT-003`.

## TypeScript Client Helper

In your TypeScript frontend codebase, extract client data with a type-safe helper:

```typescript
export function readClientData<T>(id: string): T {
  const element = document.querySelector<HTMLScriptElement>(
    `script[type="application/json"][data-vt-client-data="${id}"]`
  );

  if (!element) {
    throw new Error(`Client data element with ID '${id}' was not found in the DOM.`);
  }

  const content = element.textContent?.trim();
  if (!content) {
    throw new Error(`Client data element with ID '${id}' has empty content.`);
  }

  return JSON.parse(content) as T;
}
```

Usage in an island bootstrap:

```typescript
interface PayrollData {
  department: string;
  totalBudget: number;
}

const data = readClientData<PayrollData>('payroll-data');
console.log(`Loaded budget for ${data.department}: ${data.totalBudget}`);
```

## Serializer Providers

- **Spring Boot**: Automatically configures `JacksonClientDataSerializer` wrapping Spring's configured `ObjectMapper` (supporting both Jackson 2.x and Jackson 3.x).
- **Quarkus**: Automatically detects and configures Jackson's `ObjectMapper` from Quarkus's Arc container.
- **Standalone Java**: Uses `ClientData.defaultSerializer()` by default, or you can register a custom `ClientDataSerializer` via `FrontendAssetsRenderContextContributor`.

## Default Serializer Semantics & Native Image Reflection

The built-in serializer accessible via `ClientData.defaultSerializer()` is zero-dependency and provides:
- Standard primitives, strings, numbers, booleans, enums, characters, and UUIDs.
- `java.time.Instant`, `TemporalAccessor`, and `java.util.Date` formatted as ISO-8601 UTC strings.
- Collections, arrays, and Maps (keys serialized using `toString()`).
- `NaN` and `Infinity` numbers serialized as `null` per RFC 8259.
- Circular reference detection (throwing `ClientDataSerializationException` with code `VT-CLIENT-002`).
- Reflective record component and JavaBean getter extraction, sorted deterministically by property name.

### GraalVM Native Image Reflection Requirements
When running in GraalVM SubstrateVM (native image) without Jackson:
- The default serializer relies on reflection (`Class.getMethods()` and record component accessors) to discover properties on user domain classes and records.
- In SubstrateVM, user domain classes passed to `clientData.script()` must be registered for reflection (e.g. using `@RegisterForReflection` in Quarkus or `@RegisterReflectionForBinding` in Spring AOT).
- In applications using Quarkus Jackson or Spring Jackson, `JacksonClientDataSerializer` is used automatically, leveraging Jackson's existing native reflection registrations.
