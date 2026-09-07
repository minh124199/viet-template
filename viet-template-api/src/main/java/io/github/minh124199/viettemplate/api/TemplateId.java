package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Normalized immutable identifier for a template.
 *
 * <p>Template identifiers use forward slashes and do not permit absolute paths, path traversal
 * ('..'), or backslashes.
 */
public record TemplateId(String value) implements Serializable, Comparable<TemplateId> {

  public TemplateId {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Template id must not be blank");
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
  }

  public static TemplateId of(String value) {
    return new TemplateId(value);
  }

  /**
   * Normalizes a raw template path into a canonical, traversal-safe {@link TemplateId}.
   *
   * <p>Replaces backslashes with forward slashes, strips leading and duplicate slashes, resolves
   * relative segments ('.' and '..'), and strictly forbids escaping above the root directory.
   *
   * @param rawPath the raw template path to normalize
   * @return a normalized, traversal-safe {@link TemplateId}
   * @throws IllegalArgumentException if the path is null, blank, contains null bytes, or attempts
   *     to escape above the root
   */
  public static TemplateId normalize(String rawPath) {
    Objects.requireNonNull(rawPath, "rawPath must not be null");
    if (rawPath.isBlank()) {
      throw new IllegalArgumentException("Template path must not be blank");
    }
    if (rawPath.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Template path must not contain null bytes");
    }

    String unified = rawPath.replace('\\', '/');
    String[] segments = unified.split("/+");
    java.util.List<String> resolved = new java.util.ArrayList<>();

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
      } else {
        resolved.add(segment);
      }
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
