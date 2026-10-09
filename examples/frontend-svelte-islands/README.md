# Viet Template + Svelte Islands Example

This example demonstrates the canonical **Frontend Asset Integration** architecture:
`Java -> Viet Template SSR -> Semantic HTML -> Svelte Island Mounting -> Java REST Endpoints`.

## Structure

- `src/islands/Counter.svelte`: Reusable Svelte reactive component.
- `src/pages/counter/index.ts`: Lightweight TypeScript bootstrap module mounting the island into `#counter-island`.
- `src/client-data.ts`: Type-safe client data reader extracting Unicode-safe JSON emitted by `$clientData.script()`.
- `templates/counter.vtl`: Server-side VTL template using `$assets.head()`, `$assets.body()`, and `$clientData.script()`.
- `vite.config.ts`: Vite build configuration generating production `manifest.json`.

## Development (HMR)

1. Run the Vite development server:
   ```bash
   npm install
   npm run dev
   ```
2. Configure the Java application:
   ```properties
   viet-template.assets.enabled=true
   viet-template.assets.mode=development
   viet-template.assets.dev-server=http://localhost:5173
   ```
3. Load the page in your browser. Changes to `Counter.svelte` update immediately via Hot Module Replacement.

## Production

1. Compile frontend assets:
   ```bash
   npm run build
   ```
   This generates `manifest.json` in the target static resources directory.
2. In production mode, Viet Template reads the manifest once at startup and serves hashed, cached assets with preloaded CSS and dependencies.
