# Quarkus Integration

Viet Template provides native Quarkus extension support for frontend asset integration in `viet-template-quarkus` and `viet-template-quarkus-deployment`.

## Configuration Properties

Configure the `quarkus.viet-template.assets.*` properties in `application.properties`:

```properties
# Enable frontend asset integration (default: false)
quarkus.viet-template.assets-enabled=true

# Asset provider (default: vite)
quarkus.viet-template.assets-provider=vite

# Resolution mode: "development" or "production" (default: production)
quarkus.viet-template.assets-mode=production

# Public base URL or CDN path for assets (default: /)
quarkus.viet-template.assets-public-base=/

# Emit <link rel="modulepreload"> tags for transitive dependencies (default: true)
quarkus.viet-template.assets-module-preload=true

# Fail fast at startup if manifest cannot be loaded in production mode (default: false)
quarkus.viet-template.assets-fail-fast=true

# Classpath location of Vite manifest (default: static/dist/.vite/manifest.json)
quarkus.viet-template.assets-manifest-location=static/dist/.vite/manifest.json

# Development server origin for HMR in development mode
# Default: http://localhost:5173
quarkus.viet-template.assets-dev-server=http://localhost:5173
```

## Quarkus Profiles

Use Quarkus profile prefixes (`%dev`, `%prod`) to configure environments:

```properties
# Development profile: Point to Vite dev server
%dev.quarkus.viet-template.assets-enabled=true
%dev.quarkus.viet-template.assets-mode=development
%dev.quarkus.viet-template.assets-dev-server=http://localhost:5173

# Production profile: Read bundled manifest
%prod.quarkus.viet-template.assets-enabled=true
%prod.quarkus.viet-template.assets-mode=production
%prod.quarkus.viet-template.assets-fail-fast=true
%prod.quarkus.viet-template.assets-manifest-location=static/dist/.vite/manifest.json
```

## GraalVM Native Image Support

When building a native executable (`./mvnw package -Dnative` or `./gradlew build -Dquarkus.package.type=native`), the deployment processor (`VietTemplateProcessor`) registers a `NativeImageResourceBuildItem` for the configured `manifest-location`.

This guarantees:
- The `manifest.json` file is baked directly into the native image binary as a classpath resource.
- At runtime, file system access is never needed to load the Vite manifest.
- Asset URLs and CSS preloads resolve instantly from memory inside the native binary.

### Reflection Considerations for Client Data
When serializing domain objects with `clientData.script()` in Native Image:
- If Quarkus Jackson is used, Quarkus automatically registers Jackson reflection metadata for serialized types.
- If the default fallback serializer (`ClientData.defaultSerializer()`) is used, domain records and POJOs must be registered for reflection using Quarkus's `@RegisterForReflection` annotation so their getters and record components remain accessible at runtime.

## Arc Container Integration

The Quarkus producer (`VietTemplateProducer`) automatically inspects Quarkus's Arc CDI container:
- If a custom `ClientDataSerializer` bean is available, it is injected.
- If Quarkus Jackson is installed, the container's `ObjectMapper` is automatically adapted via `JacksonClientDataSerializer`.
- If no Jackson bean is available, it cleanly falls back to `ClientData.defaultSerializer()`.
