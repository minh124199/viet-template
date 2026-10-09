package io.github.minh124199.viettemplate.assets.vite;

import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.runtime.SafeUrlValidator;
import java.util.Locale;

/**
 * Validates and normalizes asset paths and URLs against directory traversal, hostile schemes, and
 * injection attacks.
 */
public final class AssetPathValidator {

  private AssetPathValidator() {}

  /**
   * Validates a configured public base or CDN prefix.
   *
   * @param publicBase the public base (e.g. {@code "/"}, {@code "/assets/"}, {@code
   *     "https://cdn.example.com/assets/"})
   * @return normalized public base with guaranteed trailing slash
   * @throws AssetException if public base is unsafe or malformed
   */
  public static String validateAndNormalizePublicBase(String publicBase) {
    if (publicBase == null || publicBase.isBlank()) {
      return "/";
    }

    String trimmed = publicBase.trim();
    checkForForbiddenCharacters(trimmed, AssetDiagnosticCode.VT_ASSET_008, "Public base URL");

    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.startsWith("//")
        || lower.startsWith("javascript:")
        || lower.startsWith("vbscript:")
        || lower.startsWith("data:")
        || lower.startsWith("file:")
        || lower.startsWith("blob:")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_008,
          trimmed,
          "Public base URL uses a forbidden or scheme-relative protocol: " + trimmed);
    }

    if (!trimmed.startsWith("/") && !lower.startsWith("http://") && !lower.startsWith("https://")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_008,
          trimmed,
          "Public base URL must be an absolute path starting with '/' or an http/https URL: "
              + trimmed);
    }

    if (lower.contains("..") || lower.contains("\\")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_008,
          trimmed,
          "Public base URL contains traversal characters ('..' or '\\'): " + trimmed);
    }

    if (!trimmed.endsWith("/")) {
      trimmed = trimmed + "/";
    }

    return trimmed;
  }

  /**
   * Validates a development server origin URL.
   *
   * @param origin the dev-server origin (e.g. {@code "http://localhost:5173"})
   * @return normalized origin without trailing slash
   * @throws AssetException if origin is unsafe or invalid
   */
  public static String validateDevServerOrigin(String origin) {
    if (origin == null || origin.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_007,
          origin,
          "Vite development server origin must not be null or blank.");
    }

    String trimmed = origin.trim();
    checkForForbiddenCharacters(trimmed, AssetDiagnosticCode.VT_ASSET_007, "Dev server origin");

    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_007,
          trimmed,
          "Vite development server origin must use 'http://' or 'https://': " + trimmed);
    }

    if (lower.contains("..") || lower.contains("\\")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_007,
          trimmed,
          "Vite development server origin contains traversal characters: " + trimmed);
    }

    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }

    return trimmed;
  }

  /**
   * Validates an asset path emitted by Vite and resolves it against the public base.
   *
   * @param publicBase the normalized public base
   * @param filePath the emitted relative file path (e.g. {@code "assets/main-C0de.js"})
   * @return combined safe URL string
   * @throws AssetException if the file path is unsafe
   */
  public static String resolveAssetUrl(String publicBase, String filePath) {
    if (filePath == null || filePath.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006, filePath, "Asset file path must not be null or blank.");
    }

    validateRelativePath(filePath);

    String normalizedPath = filePath.trim();
    if (normalizedPath.startsWith("/")) {
      normalizedPath = normalizedPath.substring(1);
    }

    String combined = publicBase + normalizedPath;
    if (!SafeUrlValidator.isValid(combined)) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006,
          combined,
          "Resolved asset URL failed safety validation: " + combined);
    }

    return combined;
  }

  /**
   * Validates that a relative asset path does not contain traversal, backslashes, or control
   * characters.
   *
   * @param path candidate relative path
   * @throws AssetException if the path is unsafe
   */
  public static void validateRelativePath(String path) {
    if (path == null || path.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006, path, "Relative asset path must not be null or blank.");
    }

    checkForForbiddenCharacters(path, AssetDiagnosticCode.VT_ASSET_006, "Asset path");

    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.contains("\\")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006,
          path,
          "Asset path contains forbidden backslash: " + path);
    }

    // Check for directory traversal sequences
    if (lower.contains("..") || lower.contains("%2e%2e") || lower.contains("%252e%252e")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006,
          path,
          "Asset path contains forbidden directory traversal ('..'): " + path);
    }

    // Check for dangerous schemes
    if (lower.startsWith("javascript:")
        || lower.startsWith("vbscript:")
        || lower.startsWith("data:")
        || lower.startsWith("file:")
        || lower.startsWith("blob:")
        || lower.startsWith("//")) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_006, path, "Asset path contains forbidden scheme: " + path);
    }
  }

  private static void checkForForbiddenCharacters(String s, String code, String context) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c <= 0x1F || c == 0x7F) {
        throw new AssetException(
            code,
            s,
            context + " contains forbidden ASCII control character (code point " + (int) c + ").");
      }
      if (c == '"' || c == '\'' || c == '<' || c == '>') {
        throw new AssetException(
            code, s, context + " contains forbidden quote or markup character ('" + c + "').");
      }
    }
  }
}
