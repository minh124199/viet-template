# Spring Boot Integration

Viet Template includes automated Spring Boot support for frontend asset integration in `viet-template-spring-boot-autoconfigure`.

## Configuration Properties

To enable frontend asset integration, configure the `viet-template.assets.*` properties in your `application.properties` or `application.yml`:

```properties
# Enable asset integration (default: false)
viet-template.assets.enabled=true

# Asset provider (default: vite)
viet-template.assets.provider=vite

# Resolution mode: "development" or "production"
# Defaults to "development" if active profile is "dev", otherwise "production"
viet-template.assets.mode=production

# Public base URL or CDN path for assets (default: /)
viet-template.assets.public-base=/

# Emit <link rel="modulepreload"> tags for transitive dependencies (default: true)
viet-template.assets.module-preload=true

# Fail fast at startup if manifest cannot be loaded in production mode (default: false)
viet-template.assets.fail-fast=true

# Location of Vite manifest in classpath or filesystem
# Default: classpath:/static/dist/.vite/manifest.json
viet-template.assets.manifest-location=classpath:/static/dist/.vite/manifest.json

# Development server origin for HMR in development mode
# Default: http://localhost:5173
viet-template.assets.dev-server=http://localhost:5173
```

## Profile-Based Setup

A typical setup uses Spring profiles to switch between development HMR and production manifests:

### Development (`application-dev.properties`)

```properties
viet-template.assets.enabled=true
viet-template.assets.mode=development
viet-template.assets.dev-server=http://localhost:5173
```

### Production (`application-prod.properties`)

```properties
viet-template.assets.enabled=true
viet-template.assets.mode=production
viet-template.assets.fail-fast=true
viet-template.assets.manifest-location=classpath:/static/dist/.vite/manifest.json
```

## Auto-Configured Beans

When `viet-template.assets.enabled=true` is set, `VietTemplateAssetAutoConfiguration` automatically registers:

1. **`AssetResolver`**: A `ViteAssetResolver` configured for development or production based on properties.
2. **`FrontendAssets`**: The facade providing `$assets.head()`, `$assets.body()`, and `$assets.url()`.
3. **`ClientDataSerializer`**: A `JacksonClientDataSerializer` wrapping Spring's primary `ObjectMapper` (compatible with both Jackson 2.x and Jackson 3.x).
4. **`ClientData`**: The facade providing `$clientData.script()`.
5. **`VietTemplateEngineCustomizer`**: Automatically registers `FrontendAssetsRenderContextContributor` on the template engine so `$assets` and `$clientData` are available in all templates.

## Local Development with DevTools & Vite HMR

When developing locally with `spring-boot-devtools` and Vite:
- Set `viet-template.assets.mode=development` and point `viet-template.assets.dev-server` to your Vite dev server (e.g. `http://localhost:5173`).
- For dynamic template updates without application restart, configure `FilesystemTemplateRepository` with `hotReload(true)` and a small `watchDebounceMillis`.
- When Java source files or controller classes are edited, DevTools reloads the application via `RestartClassLoader`.
- The Vite dev server runs as an independent OS process, preserving active WebSocket connections and Svelte component state across JVM restarts.

### CLI Qualification Command

```bash
# Execute dev mode & HMR qualification for Spring Boot DevTools
./scripts/verify-frontend-dev-mode-e2e.sh spring
```
