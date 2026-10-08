# Viet Template Visual Studio Code Extension (M30)

## 1. Overview

Milestone M30 provides the repository-maintained Visual Studio Code client (`editors/vscode`) for Viet Template (`minh124199/viet-template`). The extension connects Visual Studio Code to the Viet Template Language Server (`VietTemplateLanguageServer`), providing editing support for templates across development workflows.

The extension is designed around these architectural principles:
- **Zero Overhead on Java Builds**: Kept in `editors/vscode`, leaving core Maven and Gradle builds completely independent of Node.js.
- **Self-Contained & Reproducible**: Can bundle `server/viet-template-lsp.jar` directly into the `.vsix` package, eliminating the need to compile the language server from source during editor installation.
- **Robust Java Discovery**: Discovers and validates a compatible Java 21+ runtime across configurable settings, `JAVA_HOME`, and `PATH`.
- **Pure LSP Client Architecture**: Built using the official `vscode-languageclient` package over standard I/O (stdio).
- **Graceful Lifecycle Management**: Provides automatic error recovery, diagnostic synchronization, and interactive server restart commands.

---

## 2. Supported File Types & Association

The extension registers the `viet-template` language identifier for the following file suffixes:
- `.vtl`: Canonical Velocity / Viet Template source files
- `.vm`: Legacy Velocity template and macro files
- `.vt`: Concise Viet Template source files

File patterns: `*.vtl`, `*.vm`, `*.vt`

---

## 3. Language Features

### 3.1 Syntax Highlighting
A comprehensive TextMate grammar (`syntaxes/viet-template.tmLanguage.json`) provides lexical highlighting for:
- Line comments (`## ...`) and block comments (`#* ... *#`)
- Core directives (`#if`, `#elseif`, `#else`, `#foreach`, `#macro`, `#define`, `#parse`, `#include`, `#evaluate`, `#stop`, `#break`, `#set`, `#end`)
- Custom directives (`#customDirective(...)`)
- Silent and standard references (`$user`, `$!user`, `$user.name`)
- Formal references (`${user}`, `${!user}`, `${user.name}`)
- String literals with escape sequence handling (double-quoted with interpolation, single-quoted literal)
- Numbers, operators (`&&`, `||`, `!`, `==`, `!=`, `<`, `<=`, `>`, `>=`), and punctuation

### 3.2 Language Configuration
The extension contributes a language configuration (`language-configuration.json`) covering:
- Comment toggling: line comments `##`, block comments `#* ... *#`
- Auto-closing pairs and surrounding pairs for quotes (`""`, `''`), brackets (`{}`, `[]`, `()`), and block comments (`#* ... *#`)
- Automatic indentation rules for nested directive blocks (`#if`, `#foreach`, `#macro`, `#define`, `#end`)

### 3.3 Semantic Diagnostics
Real-time diagnostics emitted by the language server surface:
- Syntax errors (unclosed directives, malformed tokens, unclosed comments)
- Unresolved root variables when a contract schema (`*.vt-schema.json`) is present (`VTLS:2101`)
- Missing properties and typo suggestions (`VTLS:2104`)
- Nullable dereference warnings (`VTLS:2107`)
- Security access violations (`VTLSEC:2401`)
- Diagnostic markers are automatically cleared (`diagnostics: []`) as soon as errors are resolved or files are closed.

### 3.4 Autocompletion
Context-aware code completion triggered on `.`, `$`, `{`, and `#`:
- Directive keyword completions (`#if`, `#foreach`, `#set`, etc.)
- In-template local variables (`#set` and `#foreach` loop variables)
- Contract schema parameters and JavaBean properties

### 3.5 Hover Documentation
Hovering over directives or variables presents formatted Markdown documentation, type signatures, and nullability information.

### 3.6 Go to Definition
Navigates directly to the declaration of local variables (`#set`, `#foreach`) within the template, jumps to property definitions in associated schema files (`*.vt-schema.json`, `*.schema.json`, `*.d.ts`), or jumps directly to matching workspace Java source declarations (`.java`) for JVM-backed models (`F12` / `Ctrl+Click`). Note that `.java` files remain natively owned and highlighted by VS Code's Java extension or standard editor.

### 3.7 Find All References
Finds all semantic usages of a symbol across workspace templates (`Shift+F12` / `Alt+Shift+F12` / context menu `Find All References`). Works for JavaBean properties, getters, boolean accessors, public fields, record components, companion schemas, root parameters, and template-local variables. Backed strictly by compiler/schema truth with zero false-positive collisions against unrelated symbols with identical property names.

### 3.8 Rename Symbol
Safely renames template-local variables (`#set`, `#foreach`) and schema-backed properties (`.d.ts`, `.contract`) across the workspace (`F2` / context menu `Rename Symbol`). Powered by `textDocument/prepareRename` and `textDocument/rename`:
- **Pre-flight Validation (`prepareRename`)**: Verifies the symbol under the cursor is eligible for renaming and provides the initial placeholder text.
- **Strict Conflict Detection**: Validates new identifiers against VTL syntax and keywords, and detects scope collisions or duplicate property names prior to emitting edits.
- **Atomic Workspace Edits**: Emits deterministic `WorkspaceEdit` changes without touching the filesystem directly.
- **Fail-Safe Java Model Policy**: Rename requests on JVM-backed model members are cleanly rejected (`Rename is not available for JVM-backed members...`), preventing dangerous out-of-sync refactorings across external Java source trees.

### 3.9 Go to Symbol in Workspace
Quickly find and navigate to symbols across the workspace (`Ctrl+T` / `Cmd+T` or Quick Open `#` prefix). Powered by `workspace/symbol`:
- **Semantic Symbol Coverage**: Searches canonical schema types, JavaBean and schema properties, JVM-backed model types/members with direct navigation to `.java` declarations, template macros (`#macro`), and schema root parameters.
- **Fast In-Memory Search**: Results are served instantly from the server's in-memory index without query-time filesystem traversal.
- **Deterministic Multi-Tier Ranking**: Sorts results deterministically based on exact, prefix, substring, and camelCase matching rules, capped at 500 entries to prevent editor sluggishness.
- **Noise Elimination**: Template-local variables (`#set`, `#foreach`) are suppressed to keep workspace symbol search focused on shared models and macros.

---

## 4. Java Runtime Discovery

The language server requires Java 21 or higher. The extension discovers the Java executable using the following deterministic priority order:
1. User setting: `vietTemplate.java.home` (in VS Code Settings)
2. Environment variable: `JAVA_HOME`
3. System `PATH` (`java` on Linux/macOS, `java.exe` on Windows)

If Java is missing or the detected version is older than Java 21, the extension displays an actionable error notification with guidance on configuring `vietTemplate.java.home`.

---

## 5. Server Launch Strategy

The extension supports three server resolution strategies:
1. **Bundled Server JAR (Default for VSIX)**:
   The extension bundles `server/viet-template-lsp.jar`. The client launches the server via:
   ```bash
   java [vmArgs] -jar <extensionPath>/server/viet-template-lsp.jar
   ```
2. **Explicit Custom JAR**:
   Configured via `vietTemplate.languageServer.jarPath`.
3. **Development Classpath Fallback**:
   When developing inside the `viet-template` repository, the extension automatically discovers compiled reactor classes or target JARs (`viet-template-vtl-interpreter`, `viet-template-language-vtl`, `viet-template-runtime`, `viet-template-api`) and launches the server via classpath:
   ```bash
   java [vmArgs] -cp <classpath> io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer
   ```

All communication between the VS Code extension and the Java server process takes place over standard input and output (`stdio`) using standard JSON-RPC 2.0 framing.

---

## 6. Extension Configuration

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `vietTemplate.java.home` | `string` | `""` | Path to the Java 21+ installation directory used to launch the language server. |
| `vietTemplate.languageServer.jarPath` | `string` | `""` | Path to a custom language server JAR file. |
| `vietTemplate.languageServer.trace` | `string` | `"off"` | Traces communication between VS Code and the language server (`off`, `messages`, `verbose`). |
| `vietTemplate.languageServer.vmArgs` | `array` | `[]` | Additional JVM arguments passed to the language server process (e.g. `["-Xmx256m"]`). |

---

## 7. Commands

- `Viet Template: Restart Language Server` (`vietTemplate.restartServer`): Gracefully shuts down the active server process and launches a fresh instance.

---

## 8. Development & Verification Workflow

From `editors/vscode`:

```bash
# Install dependencies
npm ci

# Compile TypeScript
npm run compile

# Bundle the language server JAR from compiled Java modules
npm run bundle:server

# Run unit and end-to-end LSP smoke tests
npm test

# Package the extension into a .vsix archive
npm run package
```

### 8.1 Packaging (.vsix)
Packaging is performed via `@vscode/vsce`:
```bash
npx @vscode/vsce package --no-git-tag-version --allow-missing-repository
```
The resulting `viet-template-<version>.vsix` can be installed in Visual Studio Code via:
```bash
code --install-extension viet-template-1.1.0.vsix
```

This workflow packages a local VSIX. The repository has no marketplace publishing configuration, so a VSIX build does not establish availability in the Visual Studio Marketplace.

---

## 9. Governance & Verification

The extension is governed by `scripts/verify-vscode-extension.py`:
- Validates extension manifest structure and contribution points
- Enforces presence of `package-lock.json`
- Verifies language configuration and TextMate grammar schema validity
- Guarantees zero hardcoded machine-specific or developer-specific file paths
- Exercised by unit tests in `scripts/tests/test_verify_vscode_extension.py`
