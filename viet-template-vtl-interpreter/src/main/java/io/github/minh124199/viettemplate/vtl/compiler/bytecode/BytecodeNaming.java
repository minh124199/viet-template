package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Deterministic naming utilities for generated template bytecode classes and methods. */
public final class BytecodeNaming {

  private BytecodeNaming() {}

  /**
   * Derives a deterministic, valid Java class name for a given template ID and fingerprint.
   *
   * <p>Format: {@code T_<sanitizedId>_<shortHash>}
   */
  public static String className(TemplateId templateId, String fingerprint) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    String raw = templateId.value();
    String sanitized = raw.replaceAll("[^a-zA-Z0-9_]", "_");
    if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
      sanitized = "T_" + sanitized;
    }

    String hash =
        (fingerprint != null && !fingerprint.isBlank())
            ? fingerprint
            : sha256Hex(templateId.value());
    String shortHash = hash.length() > 12 ? hash.substring(0, 12) : hash;

    return "T_" + sanitized + "_" + shortHash;
  }

  /** Derives a deterministic fully qualified class name under the specified package. */
  public static String fullyQualifiedClassName(
      String packagePrefix, TemplateId templateId, String fingerprint) {
    String pkg =
        (packagePrefix != null && !packagePrefix.isBlank())
            ? packagePrefix
            : "io.github.minh124199.viettemplate.generated";
    return pkg + "." + className(templateId, fingerprint);
  }

  /** Derives a deterministic helper method name for split template chunks. */
  public static String chunkMethodName(String chunkName) {
    Objects.requireNonNull(chunkName, "chunkName must not be null");
    return "renderChunk_" + chunkName.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  /** Computes a deterministic SHA-256 hex string for the given input. */
  public static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}
