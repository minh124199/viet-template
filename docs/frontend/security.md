# Security Model and Best Practices

Frontend asset integration touches sensitive browser security boundaries. Viet Template implements layered defenses to prevent common web vulnerabilities.

## 1. Path Traversal Defenses

All entry and asset paths passed to `$assets.head()`, `$assets.body()`, and `$assets.url()` are validated by `AssetPathValidator`:

- **Traversal Sequences**: Any path containing `..` or `./` sequences is rejected immediately with diagnostic code `VT-ASSET-002`.
- **Absolute Paths**: Paths starting with `/` or Windows drive letters (`C:`) are prohibited.
- **Control Characters**: Null bytes (`\0`), backslashes (`\`), newlines, and unprintable characters trigger immediate validation errors.
- **Manifest Confinement**: In production mode, an asset or entry is only served if it was explicitly compiled and registered in the immutable `manifest.json`. Unmanifested paths throw `AssetException` (`VT-ASSET-001`).

## 2. Script Breakout & XSS Defenses

When embedding server-side data into HTML pages, raw string interpolation or standard JSON stringification poses high XSS risks if data contains user input.

- **`ScriptSafeAppendable`**: As documented in [Client Data](client-data.md), all JSON serialized via `$clientData.script()` encodes `<`, `>`, `&`, `U+2028`, and `U+2029` as Unicode escape sequences (`\u003c`, `\u003e`, etc.).
- **Non-Executable MIME Type**: `$clientData.script()` always emits `<script type="application/json">`. Browsers treat this as data rather than JavaScript, completely preventing script execution during HTML parsing.
- **Attribute Escaping**: The `data-vt-client-data="..."` attribute identifier is strictly HTML-attribute-escaped to prevent attribute injection attacks.

## 3. Development Server Origin Validation

In development mode:
- The configured dev server URL must be an absolute `http://` or `https://` origin.
- Trailing slashes, path components, or query strings in the origin property are normalized or rejected.
- Only the pre-configured dev server origin is permitted to be emitted into `<script src="...">` tags.

## 4. Content Security Policy (CSP) Compatibility

Viet Template's asset integration is designed for strict Content Security Policy (CSP) environments:

### Production Mode
- **No Inline JavaScript Required**: All executable logic resides in external hashed JavaScript files referenced via `<script type="module" src="...">`.
- **Data Blocks Require No `'unsafe-inline'`**: Browsers do not treat `<script type="application/json">` as script executions, so strict CSPs (`script-src 'self'`) work seamlessly without requiring `'unsafe-inline'` or per-request script nonces.

### Development Mode
During local development, your CSP policy must allow the local Vite dev server:
```http
Content-Security-Policy: default-src 'self'; script-src 'self' http://localhost:5173; connect-src 'self' ws://localhost:5173 http://localhost:5173;
```
