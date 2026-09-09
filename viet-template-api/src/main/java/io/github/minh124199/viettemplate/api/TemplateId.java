package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Normalized immutable identifier for a template.
 *
 * <p>Template identifiers use forward slashes and strictly forbid absolute paths, path traversal
 * ('..'), backslashes, URI schemes, and device names.
 */
public record TemplateId(String value) implements Serializable, Comparable<TemplateId> {

  private static final Set<String> WINDOWS_RESERVED_NAMES =
      Set.of(
          "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
          "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

  public TemplateId {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Template id must not be blank");
    }
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Template id must not contain null bytes");
    }
    if (value.startsWith("/")) {
      throw new IllegalArgumentException("Template id must not start with '/': " + value);
    }
    if (value.contains("\\")) {
      throw new IllegalArgumentException("Template id must not contain backslashes: " + value);
    }
    if (value.contains("..")) {
      throw new IllegalArgumentException(
          "Template id must not contain path traversal ('..'): " + value);
    }
    if (value.contains(":")) {
      throw new IllegalArgumentException(
          "Template id must not contain URI scheme or drive letter (':'): " + value);
    }
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Template id must not contain null bytes: " + value);
    }
    String lower = value.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          "Template id contains encoded path separators or traversal sequences: " + value);
    }
  }

  public static TemplateId of(String value) {
    return new TemplateId(value);
  }

  /**
   * Normalizes a raw template path into a canonical, traversal-safe {@link TemplateId}.
   *
   * <p>Replaces backslashes with forward slashes, strips leading and duplicate slashes, resolves
   * relative segments ('.' and '..'), and strictly forbids escaping above the root directory, URI
   * schemes, encoded traversal, and reserved device names.
   *
   * @param rawPath the raw template path to normalize
   * @return a normalized, traversal-safe {@link TemplateId}
   * @throws IllegalArgumentException if the path is null, blank, contains null bytes, attempts
   *     traversal, or contains forbidden schemes
   */
  public static TemplateId normalize(String rawPath) {
    Objects.requireNonNull(rawPath, "rawPath must not be null");
    if (rawPath.isBlank()) {
      throw new IllegalArgumentException("Template path must not be blank");
    }
    if (rawPath.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Template path must not contain null bytes");
    }
    if (rawPath.contains(":")) {
      throw new IllegalArgumentException(
          "Template path must not contain URI scheme or drive letter (':'): " + rawPath);
    }

    String lower = rawPath.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          "Template path contains encoded path separators or traversal sequences: " + rawPath);
    }

    String unified = rawPath.replace('\\', '/');
    String[] segments = unified.split("/+");
    List<String> resolved = new ArrayList<>();

    for (String segment : segments) {
      if (segment.isEmpty() || segment.equals(".")) {
        continue;
      }
      if (segment.equals("..")) {
        if (resolved.isEmpty()) {
          throw new IllegalArgumentException(
              "Template path traversal above root is forbidden: " + rawPath);
        }
        resolved.remove(resolved.size() - 1);
        continue;
      }

      String baseName = segment;
      int dotIdx = segment.indexOf('.');
      if (dotIdx > 0) {
        baseName = segment.substring(0, dotIdx);
      }
      if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
        throw new IllegalArgumentException("Template path uses reserved device name: " + segment);
      }

      resolved.add(segment);
    }

    if (resolved.isEmpty()) {
      throw new IllegalArgumentException(
          "Normalized template path resulted in empty identifier: " + rawPath);
    }

    return new TemplateId(String.join("/", resolved));
  }

  /**
   * Checks whether the given raw path is traversal-safe and can be normalized without escaping the
   * root.
   */
  public static boolean isTraversalSafe(String rawPath) {
    if (rawPath == null || rawPath.isBlank() || rawPath.indexOf('\0') >= 0) {
      return false;
    }
    try {
      normalize(rawPath);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public int compareTo(TemplateId other) {
    Objects.requireNonNull(other, "other must not be null");
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
