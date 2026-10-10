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
- **Pure Java Builds Remain Standalone**: Standard Maven (`./mvnw test`, `./mvnw verify`) and Gradle (`./gradlew test`, `./gradlew check`) builds do not require Node.js or npm to build or run tests.
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

## Testing Strategy & CI Qualification

Viet Template employs a multi-tiered testing strategy to guarantee stability, speed, and real-world compatibility:

1. **Layer 1: Fast Java Unit Tests (No Node Required)**:
   - Tests in `viet-template-runtime` evaluate `ViteAssetResolver` against checked-in, deterministic JSON manifest fixtures.
   - These tests execute offline during normal development and CI without requiring Node.js or npm.
2. **Layer 2: Real Vite & Svelte Compatibility Qualification Matrix**:
   - Pinned CI workflow (`.github/workflows/frontend-compatibility.yml`) verifies end-to-end integration against a dual-generation matrix:
     - **Current Canonical Stack**: `vite` 8.3.4 (Rolldown bundler architecture), `svelte` 5.57.2, `@sveltejs/vite-plugin-svelte` 7.3.1, `typescript` 5.8.3 running on Node 24 LTS.
     - **Legacy Qualified Stack**: `vite` 5.4.2 (Rollup bundler architecture), `svelte` 4.2.19, `@sveltejs/vite-plugin-svelte` 3.1.2, `typescript` 5.5.4 running on Node 22 LTS (`integration-tests/frontend/vite5-svelte4`).
   - Runs `npm ci`, compiles TypeScript and Svelte components into production bundles, and validates that `ViteAssetResolver` correctly resolves real generated hashes, extracted CSS, shared chunks, and direct `.svelte` entries across both bundler generations.
   - Artifacts generated during this process (`node_modules/`, `dist/`) are strictly build-time artifacts and ignored by version control.
3. **Layer 3: Framework & Native Image Verification**:
   - Spring Boot auto-configuration, Quarkus CDI extension, and GraalVM/Mandrel native image execution tests ensure seamless enterprise deployment.
4. **Layer 4: Deterministic Chromium Browser E2E Qualification**:
   - Dedicated CI workflow (`.github/workflows/frontend-browser-e2e.yml`) boots a live Spring Boot application serving production Vite 8 + Svelte 5 assets and executes headless Chromium browser tests via Playwright.
   - Proves full end-to-end integration: Viet Template SSR -> semantic HTML -> `$assets.head/body(...)` -> `$clientData.script(...)` -> Vite 8 production build -> Chromium -> Svelte 5 island mount -> user DOM click -> Java REST API (`POST /api/employees/42/follow`) -> reactive DOM update.
   - **SSR-First Architecture & Progressive Enhancement**: Validates that primary semantic content (heading, employee name, department, fallback cards) is rendered server-side prior to client-side JavaScript execution.
   - **No-JavaScript Behavior**: When client-side JavaScript is disabled (`javaScriptEnabled: false`), the canonical example preserves full document structure, employee directory details, and a functional HTML `<form method="post">` fallback button without relying on Svelte hydration.
   - **Script-Breakout Protection**: Asserts that hostile script closing tags (`</script><script>...`) in client data remain strictly inert in the browser DOM while deserializing safely into typed frontend models.

### Local Reproduction & Prerequisites

Standard Java builds (`./mvnw test`, `./gradlew test`) remain 100% independent of Node and browser tooling. Running the browser E2E qualification requires:
- **Java 21+**
- **Node.js 24 LTS** & **npm**
- **Playwright Chromium & OS dependencies** (`npx playwright install --with-deps chromium` within `integration-tests/frontend/browser`)

To run the qualification locally:

```bash
# Execute deterministic Chromium browser E2E qualification
./scripts/verify-frontend-browser-e2e.sh

# Qualify current canonical stack (Vite 8 + Svelte 5)
./scripts/verify-frontend-vite-svelte.sh --profile vite8-svelte5

# Qualify legacy stack (Vite 5 + Svelte 4)
./scripts/verify-frontend-vite-svelte.sh --profile vite5-svelte4

# Qualify both lanes sequentially
./scripts/verify-frontend-vite-svelte.sh all
```

## Support Statement & Dependency Pinning

Viet Template Frontend Asset Integration is continuously qualified against representative Vite 5 / Svelte 4 and Vite 8 / Svelte 5 stacks. The canonical Svelte island example targets the current Vite 8 / Svelte 5 toolchain, while CI retains the Vite 5 / Svelte 4 qualification lane to ensure the asset resolver remains compatible with Rollup-era manifest generations.

- **Exact Version Pinning**: All frontend dependencies are pinned to exact versions with committed, immutable `package-lock.json` files for each lane.
- **Dependabot Policy**: Under repository engineering policy (enforced by `scripts/tests/test_dependabot_config.py`), Dependabot is strictly limited to GitHub Actions to prevent automated supply-chain drift and unvetted dependency updates.
- **Alternative Maintenance Strategy**: Frontend dependencies are updated through intentional, manual review passes:
  1. Inspect available updates via `npm outdated` or `./scripts/verify-frontend-vite-svelte.sh --check-freshness`.
  2. Bump exact versions and regenerate `package-lock.json` via `npm install`.
  3. Validate full toolchain compatibility and resolver semantics across all profiles using `./scripts/verify-frontend-vite-svelte.sh all`.

## Next Steps

- [Vite Integration](vite.md): Details on development HMR and production manifest resolution.
- [Client Data](client-data.md): Script-safe serialization and TypeScript consumption.
- [Framework Islands](framework-islands.md): Mounting Svelte, Vue, and React components.
- [Spring Boot Integration](spring-boot.md): Setting up with Spring Boot.
- [Quarkus Integration](quarkus.md): Setting up with Quarkus and GraalVM native image.
- [Security Model](security.md): Path validation, JSON escaping, and CSP headers.
