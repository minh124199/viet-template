# Frontend Asset Integration Overview

Viet Template provides a first-class, opt-in **Frontend Asset Integration** capability designed for modern server-rendered web applications.

## Architecture

The canonical architecture supported by Viet Template is:

```text
Java Backend -> Viet Template SSR -> Semantic HTML -> Progressive Enhancement / Islands -> Java REST Endpoints
```

1. **Server-Side Rendering (SSR) is Primary**: The server renders complete, accessible, semantic HTML. Applications remain functional even if JavaScript is disabled, blocked, or fails to load.
2. **Progressive Enhancement**: Client-side JavaScript/TypeScript enhances the server-rendered DOM rather than replacing it with an empty Single Page Application (SPA) shell.
3. **Islands Architecture**: Complex interactive widgets (such as reactive filters, rich datepickers, or interactive dashboards) are mounted as isolated framework components (e.g. Svelte, Vue, or React) into specific DOM containers.
4. **Backend-Driven Data**: Initial page state is passed securely from the server via script-safe client data blocks (`$clientData.script()`), and runtime interactions communicate with Java REST endpoints.

## Non-Negotiable Boundaries

- **No Frontend Compilers in the JVM**: Viet Template does not parse TypeScript, compile Svelte/Vue/React, bundle assets, or run Node.js in the JVM hot path. Build-time frontend compilation is owned entirely by Vite (or other asset bundlers).
- **Zero Runtime Dependencies on Node/npm**: Production deployments require zero Node runtime or network access. The production asset resolver reads an immutable `manifest.json` emitted at frontend build time.
- **Pure Java Builds Remain Standalone**: Standard Maven (`./mvnw test`) and Gradle (`./gradlew test`) builds do not require Node.js or npm to build or run tests.
- **Dependency-Free Runtime Placement**: Vite asset resolution and the client-data bridge are integrated directly into `viet-template-runtime` without third-party dependencies or dedicated micro-artifacts, preserving the 12-module repository topology.
- **No New Template Grammar**: Asset integration requires no new VTL syntax (such as `#assetScript` directives). Instead, standard VTL context helpers (`$assets` and `$clientData`) are provided by framework integrations.

## Template Helpers

Two contextual helpers are exposed during rendering:

### `$assets`
Provides methods to emit HTML tags and URLs for frontend entries and static assets:
- `$assets.head("src/pages/dashboard/index.ts")`: Emits `<link rel="stylesheet">` tags and `<link rel="modulepreload">` tags for the entry and its transitive dependencies.
- `$assets.body("src/pages/dashboard/index.ts")`: Emits the entry `<script type="module" src="...">` tag.
- `$assets.url("src/images/logo.svg")`: Returns a safe URL string for an asset emitted in the Vite manifest.

### `$clientData`
Provides safe serialization of Java objects into `<script type="application/json">` elements:
- `$clientData.script("page-state", $pageData)`: Emits an HTML script block containing Unicode-safe JSON that cannot break out into script execution context.

## Next Steps

- [Vite Integration](vite.md): Details on development HMR and production manifest resolution.
- [Client Data](client-data.md): Script-safe serialization and TypeScript consumption.
- [Framework Islands](framework-islands.md): Mounting Svelte, Vue, and React components.
- [Spring Boot Integration](spring-boot.md): Setting up with Spring Boot.
- [Quarkus Integration](quarkus.md): Setting up with Quarkus and GraalVM native image.
- [Security Model](security.md): Path validation, JSON escaping, and CSP headers.
