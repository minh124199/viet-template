package io.github.minh124199.viettemplate.tooling.maven;

import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.util.Locale;

/**
 * Internal configuration parser converting Maven string configuration into {@link
 * TypeCheckingMode}.
 */
final class TypeCheckingConfigParser {

  private TypeCheckingConfigParser() {}

  static TypeCheckingMode parse(String value) {
    if (value == null || value.isBlank()) {
      return TypeCheckingMode.OFF;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "OFF" -> TypeCheckingMode.OFF;
      case "WARN", "WARNING" -> TypeCheckingMode.WARN;
      case "ERROR" -> TypeCheckingMode.ERROR;
      default ->
          throw new IllegalArgumentException(
              "Invalid typeChecking configuration: '"
                  + value
                  + "'. Supported values are: OFF, WARN (or WARNING), ERROR.");
    };
  }
}
