package io.github.minh124199.viettemplate.assets;

/** Stable diagnostic codes identifying frontend asset integration and client data bridge errors. */
public final class AssetDiagnosticCode {

  /** Manifest file could not be found at configured path or resource location. */
  public static final String VT_ASSET_001 = "VT-ASSET-001";

  /** Manifest JSON could not be parsed or contains unexpected syntax. */
  public static final String VT_ASSET_002 = "VT-ASSET-002";

  /** Requested logical frontend entry does not exist in the manifest. */
  public static final String VT_ASSET_003 = "VT-ASSET-003";

  /** Referenced static chunk or imported file is missing from manifest. */
  public static final String VT_ASSET_004 = "VT-ASSET-004";

  /** Invalid or cyclic dependency graph detected in manifest imports. */
  public static final String VT_ASSET_005 = "VT-ASSET-005";

  /** Asset path or resolved URL violates security rules (traversal, hostile scheme). */
  public static final String VT_ASSET_006 = "VT-ASSET-006";

  /** Development server origin configuration is invalid or missing. */
  public static final String VT_ASSET_007 = "VT-ASSET-007";

  /** Configured public base URL or CDN prefix is invalid or unsafe. */
  public static final String VT_ASSET_008 = "VT-ASSET-008";

  /** Provider invariant violated (e.g. invalid mode or configuration conflict). */
  public static final String VT_ASSET_009 = "VT-ASSET-009";

  /** No ClientDataSerializer is configured to serialize server data to JSON. */
  public static final String VT_CLIENT_001 = "VT-CLIENT-001";

  /** Client data JSON serialization failed. */
  public static final String VT_CLIENT_002 = "VT-CLIENT-002";

  /** Client data identifier token is empty, invalid, or unsafe. */
  public static final String VT_CLIENT_003 = "VT-CLIENT-003";

  /** Serialized payload contains unsafe sequences that failed safe encoding. */
  public static final String VT_CLIENT_004 = "VT-CLIENT-004";

  private AssetDiagnosticCode() {}
}
