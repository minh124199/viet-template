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
