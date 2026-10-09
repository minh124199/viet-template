# Viet Template + Svelte Islands Example

This example demonstrates the canonical **Frontend Asset Integration** architecture:
`Java -> Viet Template SSR -> Semantic HTML -> Svelte Island Mounting -> Java REST Endpoints`.

## Structure

- `src/pages/employees/`: Full employee directory island:
  - `index.ts`: TypeScript entry point mounting the island into `#employees-island`.
  - `Employees.svelte`: Reactive Svelte component filtering and formatting employee records.
  - `employees.css`: Island-specific stylesheet emitted by Vite into production CSS chunks.
  - `types.ts`: TypeScript interfaces for strongly-typed client data.
- `src/pages/counter/`: Reactive counter island (`index.ts`, `Counter.svelte`).
- `src/pages/payroll/Payroll.svelte`: Direct `.svelte` component entry point proving Vite framework neutrality without Java extension whitelists.
- `src/shared/format.ts`: Shared formatting module producing deduplicated shared chunks (`_index-*.js`, `_client-data-*.js`).
- `src/client-data.ts`: Type-safe client data reader extracting Unicode-safe JSON emitted by `$clientData.script()`.
- `templates/`: Server-side VTL templates (`employees.vtl`, `counter.vtl`) using `$assets.head()`, `$assets.body()`, and `$clientData.script()`.
- `vite.config.ts`: Vite build configuration generating production `.vite/manifest.json`.
- `tsconfig.json`: TypeScript configuration for bundler module resolution.

## Development (HMR)

1. Install dependencies:
   ```bash
   npm ci
   npm run dev
   ```
2. Configure the Java application:
   ```properties
   viet-template.assets.enabled=true
   viet-template.assets.mode=development
   viet-template.assets.dev-server=http://localhost:5173
   ```
3. Load the page in your browser. Changes update immediately via Hot Module Replacement.

## Production Build

1. Build frontend assets:
   ```bash
   npm run check
   npm run build
   ```
   This compiles TypeScript and Svelte components into hashed production assets and generates `.vite/manifest.json` under `dist/`.
2. In production mode, Viet Template reads the manifest once at startup and serves hashed, cached assets with preloaded CSS and static imports.

## Toolchain Pinning & Updates

Frontend dependencies (`vite`, `svelte`, `@sveltejs/vite-plugin-svelte`, `typescript`) are pinned with exact versions in `package.json` and locked in `package-lock.json`.

In accordance with repository supply-chain policy (which restricts Dependabot strictly to GitHub Actions), updates to this example are managed through deliberate review:
1. Audit packages using `npm outdated` and `npm audit`.
2. Update pinned versions and regenerate `package-lock.json`.
3. Qualify against the live compatibility suite:
   ```bash
   ./scripts/verify-frontend-vite-svelte.sh
   ```
