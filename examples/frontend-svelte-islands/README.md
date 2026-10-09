# Viet Template + Svelte Islands Example

This example demonstrates the canonical **Frontend Asset Integration** architecture:
`Java -> Viet Template SSR -> Semantic HTML -> Svelte 5 Island Mounting -> Java REST Endpoints`.

The canonical Svelte island example targets the current **Vite 8 / Svelte 5** toolchain (utilizing Rolldown bundler architecture).
CI also retains a **Vite 5 / Svelte 4** qualification lane (`integration-tests/frontend/vite5-svelte4`) to ensure the asset resolver remains compatible with the previously supported Rollup-era manifest generation.

## Structure

- `src/pages/employees/`: Full employee directory island:
  - `index.ts`: TypeScript entry point mounting the island into `#employees-island` via Svelte 5 `mount()`.
  - `Employees.svelte`: Reactive Svelte 5 component filtering and formatting employee records via runes (`$props`, `$state`, `$derived`).
  - `employees.css`: Island-specific stylesheet emitted by Vite into production CSS chunks.
  - `types.ts`: TypeScript interfaces for strongly-typed client data.
- `src/pages/counter/`: Reactive counter island (`index.ts`, `Counter.svelte`) using Svelte 5 `$props`, `$state`, and `onclick`.
- `src/pages/payroll/Payroll.svelte`: Direct `.svelte` component entry point proving Vite framework neutrality without Java extension whitelists.
- `src/shared/format.ts`: Shared formatting module producing deduplicated shared chunks.
- `src/client-data.ts`: Type-safe client data reader extracting Unicode-safe JSON emitted by `$clientData.script()`.
- `templates/`: Server-side VTL templates (`employees.vtl`, `counter.vtl`) using `$assets.head()`, `$assets.body()`, and `$clientData.script()`.
- `vite.config.ts`: Vite 8 build configuration generating production `.vite/manifest.json`.
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
   This compiles TypeScript and Svelte 5 components into hashed production assets and generates `.vite/manifest.json` under `dist/`.
2. In production mode, Viet Template reads the manifest once at startup and serves hashed, cached assets with preloaded CSS and static imports.

## Toolchain Pinning & Qualification Matrix

Frontend dependencies (`vite` 8.3.4, `svelte` 5.57.2, `@sveltejs/vite-plugin-svelte` 7.3.1, `typescript` 5.8.3) are pinned with exact versions in `package.json` and locked in `package-lock.json`.

Viet Template Frontend Asset Integration is continuously qualified against representative Vite 5 / Svelte 4 and Vite 8 / Svelte 5 stacks:
```bash
# Qualify current stack (Vite 8 + Svelte 5)
./scripts/verify-frontend-vite-svelte.sh --profile vite8-svelte5

# Qualify legacy stack (Vite 5 + Svelte 4)
./scripts/verify-frontend-vite-svelte.sh --profile vite5-svelte4

# Qualify both lanes
./scripts/verify-frontend-vite-svelte.sh all
```

In accordance with repository supply-chain policy (which restricts Dependabot strictly to GitHub Actions), updates to frontend examples are managed through deliberate, periodic review passes:
1. Audit packages using `npm outdated` or `./scripts/verify-frontend-vite-svelte.sh --check-freshness`.
2. Review compatibility and peer dependencies across the Vite and Svelte ecosystem.
3. Update pinned versions and regenerate `package-lock.json`.
4. Run full qualification across all profiles.
