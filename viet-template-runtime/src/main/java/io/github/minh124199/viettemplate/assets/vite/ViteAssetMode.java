package io.github.minh124199.viettemplate.assets.vite;

/** Execution mode for Vite frontend asset resolution. */
public enum ViteAssetMode {

  /**
   * Production mode: resolves pre-built, hashed asset chunks and stylesheets from a Vite-generated
   * {@code manifest.json} file.
   */
  PRODUCTION,

  /**
   * Development mode: routes asset references and HMR client scripts to an active Vite development
   * server (e.g. {@code http://localhost:5173}).
   */
  DEVELOPMENT
}
