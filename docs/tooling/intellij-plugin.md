# Viet Template IntelliJ IDEA Plugin (M31)

## 1. Overview

Milestone M31 provides the repository-maintained IntelliJ IDEA client (`editors/intellij`) for Viet Template (`minh124199/viet-template`). The plugin connects IntelliJ IDEA to the Java 21 Viet Template Language Server (`VietTemplateLanguageServer`), providing editing support for templates across JetBrains IDE workflows.

The plugin is designed around these architectural principles:
- **Zero Overhead on Core Java Builds**: Maintained in `editors/intellij`, leaving core Maven and Gradle reactor builds completely independent of the IntelliJ Platform SDK.
- **Single Source of Language Truth**: Reuses the authoritative Viet Template Language Server (`io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer`), ensuring semantic consistency with Visual Studio Code and command-line diagnostics through the shared language server engine.
- **Self-Contained & Reproducible Packaging**: Bundles `server/viet-template-lsp.jar` directly inside the plugin distribution ZIP, requiring no local language server build steps during IDE installation.
- **Robust Java 21+ Discovery**: Discovers and validates a compatible Java 21+ runtime across configurable settings, `JAVA_HOME`, and system `PATH`.
- **Clean Project Lifecycle Management**: Project-level service manages language server processes with strict cleanup on disposal (graceful shutdown with timeout and forced kill fallback), preventing orphaned background processes.

---

## 2. Supported File Types & Association

The plugin registers the `Viet Template` file type for the following file extensions:
- `.vtl`: Canonical Velocity / Viet Template source files
- `.vm`: Legacy Velocity template and macro files
- `.vt`: Concise Viet Template source files

File patterns: `*.vtl`, `*.vm`, `*.vt`

---

## 3. Language Features

### 3.1 Lexical Syntax Highlighting & Tokenizing
A native IntelliJ lexer (`VietTemplateLexer`) and syntax highlighter (`VietTemplateSyntaxHighlighter`) provide syntax coloring for:
- Line comments (`## ...`) and block comments (`#* ... *#`)
- Directives (`#if`, `#elseif`, `#else`, `#foreach`, `#macro`, `#define`, `#parse`, `#evaluate`, `#stop`, `#break`, `#set`, `#end`)
- Silent and standard references (`$user`, `$!user`, `$user.name`)
- Formal references (`${user}`, `${!user}`, `${user.name}`)
- String literals with quote handling (double-quoted with escape sequences, single-quoted literal)
- Numbers, operators (`&&`, `||`, `!`, `==`, `!=`, `<`, `<=`, `>`, `>=`), and punctuation

### 3.2 Commenter Integration
The plugin contributes a native commenter (`VietTemplateCommenter`) supporting:
- Line comment prefix: `##`
- Block comment prefix / suffix: `#*` and `*#`
- Compatible with standard IntelliJ shortcuts (`Comment with Line Comment` and `Comment with Block Comment`)

### 3.3 Semantic Diagnostics
Real-time diagnostics emitted by the language server surface directly in the editor via an `ExternalAnnotator` (`VietTemplateLspAnnotator`):
- Syntax errors (unclosed directives, malformed tokens, unclosed comments)
- Unresolved root variables when a contract schema (`*.vt-schema.json`) is present (`VTLS:2101`)
- Missing properties and typo suggestions (`VTLS:2104`)
- Nullable dereference warnings (`VTLS:2107`)
- Security access violations (`VTLSEC:2401`)
- Diagnostic markers are cleared automatically as soon as errors are resolved or files are closed.

### 3.4 Autocompletion
Context-aware code completion via `VietTemplateCompletionContributor` triggered on `.`, `$`, `{`, and `#`:
- Directive keyword completions (`#if`, `#foreach`, `#set`, etc.)
- In-template local variables (`#set` and `#foreach` loop variables)
- Contract schema parameters and JavaBean properties

### 3.5 Hover Documentation
Hovering over directives or template variables presents formatted documentation and type signatures via `VietTemplateDocumentationProvider`.

### 3.6 Go to Definition / Declaration
Navigates directly to the declaration of local variables (`#set`, `#foreach`), schema properties, or matching workspace Java source declarations (`.java`) for JVM-backed models via `VietTemplateGotoDeclarationHandler`. `.java` files remain natively opened by IntelliJ IDEA's native Java language support.

### 3.7 Find Usages / References
Finds all semantic usages of a symbol across workspace templates via standard IDE Find Usages (`Alt+F7`). Locates references to JavaBean properties, record components, getters, fields, and template-local declarations across templates with complete semantic isolation from coincidental textual name collisions.

### 3.8 Rename Symbol / Refactoring
Safely renames template-local variables (`#set`, `#foreach`) and schema-backed properties (`.d.ts`, `.contract`) across the workspace (`Shift+F6` / `Rename...`). Powered by `textDocument/prepareRename` and `textDocument/rename`:
- **Pre-flight Validation (`prepareRename`)**: Confirms renameability and supplies exact identifier range and placeholder before opening refactoring dialog.
- **Conflict Prevention**: Detects VTL syntax violations, keyword collisions, scope shadowing, and schema property collisions.
- **Deterministic Changes**: Computes exact `WorkspaceEdit` changes across affected template and schema documents.
- **Safe JVM Boundary**: Safely rejects requests targeting Java-backed members with explanatory diagnostic messaging, delegating Java source refactorings to IntelliJ IDEA's native Java refactoring tools.

### 3.9 Actions & Menus
- **Restart Viet Template Language Server** (`VietTemplate.RestartLspServer`): Available under the IDE `Tools` menu to recycle the language server process and re-synchronize open buffers.

---

## 4. Java Runtime Discovery

The language server requires Java 21 or higher. The plugin discovers the Java executable via `JavaRuntimeResolver` using the following deterministic priority order:
1. User setting: `Java Home Path` (in IDE Settings > Tools > Viet Template)
2. Environment variable: `JAVA_HOME`
3. System `PATH` (`java` on Linux/macOS, `java.exe` on Windows)

If Java is missing or the detected runtime version is older than Java 21, the plugin logs an actionable warning and falls back cleanly without crashing the IDE.

---

## 5. Server Launch Strategy

The plugin supports three server resolution strategies via `LspServerLauncher`:
1. **Bundled Server JAR (Default for Plugin ZIP)**:
   The plugin distribution contains `server/viet-template-lsp.jar`. The launcher loads the JAR and executes:
   ```bash
   java -Dfile.encoding=UTF-8 [vmArgs] -jar <extractedPath>/viet-template-lsp.jar
   ```
2. **Explicit Custom JAR**:
   Configured via `Server JAR Path` in the Viet Template settings panel.
3. **Development Classpath Fallback**:
   When developing within the `viet-template` repository, the launcher automatically discovers compiled module outputs or workspace JARs.

All communication between IntelliJ IDEA and the Java language server process takes place over standard I/O (`stdio`) using standard JSON-RPC 2.0 framing. Stderr is consumed asynchronously by background threads to prevent pipe buffer stalls.

---

## 6. Plugin Configuration

Configurable via **Settings / Preferences > Tools > Viet Template**:

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `Java Home Path` | `String` | `""` | Path to the Java 21+ installation directory used to launch the language server. |
| `Server JAR Path` | `String` | `""` | Optional path to an external language server JAR file. |
| `Trace Level` | `String` | `"off"` | LSP message tracing level (`off`, `messages`, `verbose`). |
| `JVM Arguments` | `List<String>` | `[]` | Additional JVM arguments passed to the language server process (e.g. `-Xmx256m`). |

---

## 7. Development & Verification Workflow

From `editors/intellij`:

```bash
# Run unit, platform, and real language server integration tests
./gradlew -p editors/intellij test

# Package the plugin into an installable ZIP distribution
./gradlew -p editors/intellij buildPlugin

# Verify plugin structure and compatibility against recommended IDE releases (2024.2 - 2025.1.x)
./gradlew -p editors/intellij verifyPluginStructure verifyPluginProjectConfiguration verifyPlugin
```

### 7.1 Packaging & Installation
Building the plugin produces:
```
editors/intellij/build/distributions/viet-template-intellij-1.1.0.zip
```
The plugin can be installed in IntelliJ IDEA (2024.2 - 2025.1.x, build range 242 to 251.*) via:
- **Settings / Preferences > Plugins > ⚙ (Gear icon) > Install Plugin from Disk...**
- Select `viet-template-intellij-1.1.0.zip`.

This is a local plugin ZIP workflow. The repository configures packaging and compatibility verification; a ZIP build does not establish publication to JetBrains Marketplace.

---

## 8. Governance & Verification

The plugin is verified by `scripts/verify-intellij-plugin.py`:
- Validates plugin descriptor metadata (`plugin.xml`) and extension points
- Validates Gradle build configuration (`build.gradle.kts` and `settings.gradle.kts`)
- Enforces strict path safety (zero hardcoded machine or developer paths)
- Validates distribution ZIP structure and bundled language server JAR integrity
- Exercised by unit tests in `scripts/tests/test_verify_intellij_plugin.py`
