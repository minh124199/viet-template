package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Viet Template frontend asset integration.
 *
 * <p>Bound to prefix {@code viet-template.assets}.
 */
@ConfigurationProperties(prefix = "viet-template.assets")
public class VietTemplateAssetProperties {

  public static final String DEFAULT_PROVIDER = "vite";
  public static final String DEFAULT_MODE = "production";
  public static final String DEFAULT_PUBLIC_BASE = "/";
  public static final String DEFAULT_MANIFEST_LOCATION = "classpath:/static/.vite/manifest.json";
  public static final String DEFAULT_DEV_SERVER = "http://localhost:5173";

  /** Whether to enable frontend asset integration. Defaults to false (opt-in). */
  private boolean enabled = false;

  /** Frontend asset provider type. Defaults to "vite". */
  private String provider = DEFAULT_PROVIDER;

  /** Resolution mode: "production" or "development". Defaults to "production". */
  private String mode = DEFAULT_MODE;

  /** Public base URL or CDN path prefix for asset tags. Defaults to "/". */
  private String publicBase = DEFAULT_PUBLIC_BASE;

  /** Whether module preload tags are emitted in the HTML head. Defaults to true. */
  private boolean modulePreload = true;

  /**
   * Whether to fail startup if the production manifest is missing or malformed. Defaults to true.
   */
  private boolean failFast = true;

  /**
   * Resource location of the Vite production manifest file. Defaults to
   * "classpath:/static/.vite/manifest.json".
   */
  private String manifestLocation = DEFAULT_MANIFEST_LOCATION;

  /** Vite dev server URL when in development mode. Defaults to "http://localhost:5173". */
  private String devServer = DEFAULT_DEV_SERVER;

  /** Nested configuration specific to the Vite provider. */
  private final Vite vite = new Vite();

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getProvider() {
    return this.provider;
  }

  public void setProvider(String provider) {
    this.provider = provider != null ? provider : DEFAULT_PROVIDER;
  }

  public String getMode() {
    return this.mode;
  }

  public void setMode(String mode) {
    this.mode = mode != null ? mode : DEFAULT_MODE;
  }

  public String getPublicBase() {
    return this.publicBase;
  }

  public void setPublicBase(String publicBase) {
    this.publicBase = publicBase != null ? publicBase : DEFAULT_PUBLIC_BASE;
  }

  public boolean isModulePreload() {
    return this.modulePreload;
  }

  public void setModulePreload(boolean modulePreload) {
    this.modulePreload = modulePreload;
  }

  public boolean isFailFast() {
    return this.failFast;
  }

  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  public String getManifestLocation() {
    return this.manifestLocation;
  }

  public void setManifestLocation(String manifestLocation) {
    this.manifestLocation = manifestLocation != null ? manifestLocation : DEFAULT_MANIFEST_LOCATION;
  }

  public String getDevServer() {
    return this.devServer;
  }

  public void setDevServer(String devServer) {
    this.devServer = devServer != null ? devServer : DEFAULT_DEV_SERVER;
  }

  public Vite getVite() {
    return this.vite;
  }

  /**
   * Resolves the effective manifest location, preferring {@code vite.manifest} if explicitly
   * configured.
   *
   * @return effective manifest location string
   */
  public String getEffectiveManifestLocation() {
    if (this.vite.getManifest() != null && !this.vite.getManifest().isBlank()) {
      return this.vite.getManifest();
    }
    return this.manifestLocation;
  }

  /**
   * Resolves the effective dev server URL, preferring {@code vite.devServer} if explicitly
   * configured.
   *
   * @return effective dev server URL
   */
  public String getEffectiveDevServer() {
    if (this.vite.getDevServer() != null && !this.vite.getDevServer().isBlank()) {
      return this.vite.getDevServer();
    }
    return this.devServer;
  }

  /** Nested configuration for Vite. */
  public static class Vite {
    private String manifest;
    private String devServer;

    public String getManifest() {
      return this.manifest;
    }

    public void setManifest(String manifest) {
      this.manifest = manifest;
    }

    public String getDevServer() {
      return this.devServer;
    }

    public void setDevServer(String devServer) {
      this.devServer = devServer;
    }
  }
}
