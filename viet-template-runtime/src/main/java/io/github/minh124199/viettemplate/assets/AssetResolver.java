package io.github.minh124199.viettemplate.assets;

/**
 * Generic abstraction for resolving frontend build assets and entrypoints.
 *
 * <p>Implementations must be immutable and thread-safe once initialized. Implementations must
 * perform no network I/O, Node invocations, or filesystem parsing on the template rendering hot
 * path.
 */
public interface AssetResolver {

  /**
   * Resolves a logical frontend entrypoint (e.g. {@code "src/pages/employees/index.ts"}) to its
   * resolved scripts, stylesheets, and module preloads.
   *
   * @param logicalEntry the logical entry path or identifier
   * @return the resolved entry representation
   * @throws AssetException if the entry cannot be found or resolved
   */
  ResolvedFrontendEntry resolveEntry(String logicalEntry);

  /**
   * Resolves a logical static asset (e.g. {@code "src/images/logo.svg"}) to its public URL.
   *
   * @param logicalAsset the logical asset path
   * @return the resolved asset metadata
   * @throws AssetException if the asset cannot be found or resolved
   */
  ResolvedAsset resolveAsset(String logicalAsset);
}
