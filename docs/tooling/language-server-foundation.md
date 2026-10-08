# Viet Template Language Server Foundation (M29)

## 1. Overview

Milestone M29 establishes the Language Server Protocol (LSP) and developer tooling foundation for Viet Template (`minh124199/viet-template`). The language server provides real-time semantic analysis, code completion, hover documentation, definition navigation, and diagnostics for Viet Template (`*.vt`, `*.vtl`, `*.vm`) documents. This repository includes client integrations for Visual Studio Code and IntelliJ IDEA; the LSP protocol can support other clients, but no Neovim or Eclipse extension is included here.

The LSP implementation is:
- **Pure Java 21 Standard Library**: Requires zero external JSON or LSP dependencies; implements standard JSON-RPC 2.0 framing and parsing with standard library primitives.
- **Contract & Schema-Backed**: Integrates directly with canonical contract schemas (`*.vt-schema.json`) to validate variable bindings, types, and nullability.
- **Fail-Closed Security**: Strictly enforces `MemberAccessPolicy` to deny reflective access (`getClass()`, `ClassLoader`, module introspection) in editor analysis.
- **Byte-for-Byte Deterministic**: Diagnostic reporting, completion items, and navigation locations are deterministically sorted.

---

## 2. Server Architecture & Lifecycle

### 2.1 Entrypoint & Decoupling

The primary entrypoint is `io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer`. It provides:
- Standard I/O execution: `VietTemplateLanguageServer.run(InputStream in, OutputStream out)`
- Headless / programmatic invocation: `VietTemplateLanguageServer.handleMessage(String jsonRpcMessage)`
- Direct service embedding: `VietTemplateLanguageServer.languageService()`

The core engine is encapsulated within `TemplateLanguageService`, separating LSP JSON-RPC protocol handling from language analysis so future plugin adapters (e.g. IntelliJ PSI or LSP4J bridges) can reuse the semantic engine without JSON overhead.

### 2.2 Lifecycle State Machine

The server implements the standard LSP lifecycle states:
1. **Uninitialized**: Only `initialize` and `exit` requests are accepted. All other requests return error `-32002` (`ServerNotInitialized`).
2. **Initialized**: Transitions upon receipt of `initialize` response followed by `initialized` notification. Ready for document synchronization and language feature requests.
3. **Shutting Down**: Transitions upon receipt of `shutdown`. Returns `result: null`. Subsequent requests return error `-32600` (`InvalidRequest`).
4. **Exited**: Terminated upon receipt of `exit`. Exits with process code `0` if shutdown was requested, or `1` if unexpected.

### 2.3 Cancellation & Notification Handling

The server gracefully absorbs standard LSP notifications:
- `$/cancelRequest`: Acknowledges cancellation requests for completed or in-flight operations without throwing error `-32601`.
- Unknown notifications: Safely ignored per LSP specification without sending error responses to the client.

---

## 3. Document Management & Coordinate Mapping

### 3.1 Document Synchronization

The server maintains in-memory documents via `TemplateDocumentStore` supporting:
- `textDocument/didOpen`: Loads document into memory and immediately triggers diagnostics publishing.
- `textDocument/didChange`: Updates document text and incrementing version; re-analyzes diagnostics.
- `textDocument/didClose`: Evicts document and publishes empty diagnostics array to clear editor markers.

### 3.2 UTF-16 Position & Range Mapping

LSP requires 0-indexed line and UTF-16 code unit character offsets. `TemplateDocument` provides bidirectional mapping between LSP `Position`/`Range` and AST `SourceSpan`:
- Handles `\n` (LF), `\r\n` (CRLF), and legacy `\r` (CR) line endings transparently.
- Correctly accounts for multi-byte Unicode (such as Vietnamese diacritics) and surrogate pairs (e.g., emojis requiring 2 UTF-16 code units).
- Safely clamps out-of-bounds line numbers and character offsets to valid boundaries.

### 3.3 Version Ordering & Stale Update Rejection

The language service guarantees monotonic document versioning:
- Atomic document storage via `TemplateDocumentStore.updateIfNewer()` rejects stale `textDocument/didChange` updates (`newVersion < currentVersion`).
- Rejected stale updates do not overwrite newer content and do not publish stale diagnostics.
- Supports full client lifecycle: `open v1 -> change v2 -> stale change v1 [rejected] -> change v3 -> close -> reopen v1`.

---

## 4. Language Features

### 4.1 Completion (`textDocument/completion`)

Provides context-sensitive completion items:
- **Directives**: Triggered at `#`, providing completions for `#if`, `#elseif`, `#else`, `#foreach`, `#set`, `#macro`, `#evaluate`, `#parse`, `#define`, `#stop`, and `#break`.
- **Root Variables**: Triggered at `$` or `$!`, providing declared schema parameters and in-scope local variables.
- **Member & Property Chaining**: Triggered at `.`, resolving receiver types via canonical contract schema and returning accessible properties with nullability detail.
- **Resilient Recovery**: Gracefully handles incomplete directives, unclosed expressions, and malformed syntax without unhandled exceptions.

### 4.2 Hover Information (`textDocument/hover`)

Returns Markdown-formatted documentation when hovering over:
- **Template Parameters**: Parameter name, type signature, and nullability status.
- **Properties**: Declared property type, nullability, and declaring model.
- **Directives**: Syntax description and documentation for VTL directives, including hovering directly on `#` or keyword tokens.

### 4.3 Definition Navigation (`textDocument/definition`)

Enables jump-to-definition:
- **Local Variables**: Navigates to the originating `#set` assignment or `#foreach` loop declaration within the template.
- **Schema Parameters & Properties**: Navigates to the corresponding line in the sibling or configured `*.vt-schema.json`, `.schema.json`, or `.d.ts` schema file.
- **Workspace Java Source Declarations**: When template references are backed by proven JVM model bindings, navigates directly to matching workspace Java source declarations (`.java`), including record components in headers, explicit record accessors, JavaBean getters (`getXxx()`), boolean getters (`isXxx()`), public fields, and inherited members on declaring superclasses. Gracefully returns empty definitions when source is unavailable (external dependencies or JDK classes) without synthetic locations.

### 4.4 Find References (`textDocument/references`)

Enables workspace-wide "Find References" across all template documents (`*.vtl`, `*.vm`, `*.vt`) based strictly on compiler-proven semantic identities (`WorkspaceSymbolKey`):
- **Semantic Precision**: Resolves references after AST parsing and semantic binding. Never performs superficial textual matching; two models exposing the same property name (e.g. `Customer#getName()` vs `Product#getName()`) remain 100% isolated.
- **Inherited Members**: Inherited members index under their declaring superclass (e.g. `BaseUser#getName()`), converging references across subclasses while isolating overridden members.
- **Exact Identifier Ranges**: Emits exact coordinates for referenced identifiers (e.g. `name` in `$customer.name`, omitting the leading `.`).
- **Declaration Inclusion**: Respects LSP `ReferenceContext.includeDeclaration`, querying `DefinitionProvider` to dynamically prepend declarations when requested.
- **Incremental Indexing**: Uses a thread-safe inverted index (`WorkspaceReferenceIndex`) with atomic template replacement on file open, change, and close, as well as automatic re-indexing when companion schemas change.

### 4.5 Safe Rename Refactoring (`textDocument/prepareRename` & `textDocument/rename`)

Enables safe, previewable, and atomic symbol renaming across template files (`*.vtl`, `*.vm`, `*.vt`) and companion schema declarations (`.d.ts`, `.contract`):
- **Pre-Flight Validation (`prepareRename`)**: Verifies whether the symbol under cursor is eligible for renaming, extracting the exact token range and placeholder string. Returns `null` for unnameable symbols (JVM-backed members, methods, dynamic symbols, denied members).
- **Semantic Conflict Detection**: Validates target identifiers against VTL syntax rules and reserved keywords (`if`, `foreach`, `null`, `true`, `false`, etc.), and detects local variable scope collisions, loop variable shadowing, and schema property name collisions prior to generating edits.
- **Conservative Java Model Boundary**: Rename requests targeting JVM-backed model members (getters, boolean getters, fields, record components, methods) are cleanly rejected with informative user guidance (`Rename is not available for JVM-backed members because Java source refactoring is outside Viet Template's ownership`), avoiding out-of-sync edits across external Java projects.
- **Atomic `WorkspaceEdit` Generation**: Collects exact declaration and reference coordinate ranges from `WorkspaceReferenceIndex`, checks for and rejects overlapping edits, and generates deterministically sorted `WorkspaceEdit` changes without direct disk mutation.

### 4.6 Workspace Symbol Search (`workspace/symbol`)

Enables workspace-wide semantic symbol search across Viet Template files, schema declarations, and Java models:
- **Canonical Projections Only**: Projects symbols solely from the compiler-proven semantic binding and schema graph (`WorkspaceSymbolIndex`). Never scans identifiers textually, grep-searches filenames, or traverses directories at query time.
- **Indexed Symbol Categories**:
  - **Schema Types**: `Class`, `Interface`, `Enum`, `Struct` declared in Java companion models, TypeScript `.d.ts`, JSON Schema, and `.contract` files.
  - **Properties & Members**: `Property`, `Field`, `Method` exposed on declared models and schemas, strictly filtering out `MemberAccessPolicy`-denied and dynamic members.
  - **JVM-Backed Models**: Fully qualified and simple type names and member symbols pointing directly to exact Java declaration coordinates via `WorkspaceJavaSourceLocator`.
  - **Template Macros**: `#macro(name ...)` definitions extracted from parsed template ASTs, classified as `Function`.
  - **Root Parameters**: Explicit root variables declared in companion schema contracts.
  - **Noise Suppression**: Template-local variables (`#set`, `#foreach`) are omitted from workspace symbol search by default to maintain high signal-to-noise ratio.
- **Deterministic Multi-Tier Ranking**: Matches queries across 9 deterministic tiers (exact case-sensitive, exact case-insensitive, qualified exact, simple prefix case-sensitive, simple prefix case-insensitive, qualified prefix, simple substring, qualified substring, camelCase) with stable tie-breaking and a 500-result cap.
- **In-Memory Thread-Safe Index**: Thread-safe caching with `ReentrantReadWriteLock`, atomic incremental eviction/replacement during document open/change/close and schema watch events, and zero application `Class<?>` retention.

### 4.7 Diagnostics (`textDocument/publishDiagnostics`)

Emits deterministic diagnostics with stable diagnostic codes:
- `SYNTAX:PARSE_ERROR`: VTL parser syntax errors and unclosed directives.
- `VTLS:2101`: Unresolved root variable referenced under an authoritative contract schema.
- `VTLS:2104`: Property not found on declared model type.
- `VTLS:2107`: Nullable receiver dereferenced without quiet reference notation (`$!`) outside null guards.
- `VTLSEC:2401`: Property or member access denied by `MemberAccessPolicy`.

### 4.8 Cleared Diagnostics on Repair and Close

- When syntax errors or schema violations are resolved by subsequent edits (`didChange`), the server immediately publishes `diagnostics: []` to clear error markers in the client editor.
- When a document is closed (`didClose`), the server publishes an empty diagnostics array `[]` to remove any lingering problems.

---

## 5. Editor Clients

The repository includes these packaged editor clients:
- **[Visual Studio Code Extension Documentation](vscode-extension.md)**: Full guide to installation, configuration, features, and debugging in `editors/vscode`.
- **[IntelliJ IDEA Plugin Documentation](intellij-plugin.md)**: Full guide to installation, configuration, features, and debugging in `editors/intellij`.

The repository documents local VSIX/ZIP packaging and install-from-disk workflows. It does not establish publication to either editor marketplace.
