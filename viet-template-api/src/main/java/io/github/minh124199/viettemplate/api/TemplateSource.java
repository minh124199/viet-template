package io.github.minh124199.viettemplate.api;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable canonical representation of template source text loaded from a repository.
 *
 * @param id normalized template identifier
 * @param origin URI representing source origin (e.g. classpath:/..., file:///...)
 * @param charset charset used to decode the source
 * @param content decoded template source text
 * @param fingerprint SHA-256 hex digest of the template source content
 * @param lastModifiedEpochMillis epoch milliseconds when source was last modified, or 0 if unknown
 */
public record TemplateSource(
    TemplateId id,
    URI origin,
    Charset charset,
    String content,
    String fingerprint,
    long lastModifiedEpochMillis) {

  public TemplateSource {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(charset, "charset must not be null");
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
  }

  public static TemplateSource of(
      TemplateId id, URI origin, Charset charset, String content, long lastModifiedEpochMillis) {
    return new TemplateSource(
        id, origin, charset, content, computeSha256(content), lastModifiedEpochMillis);
  }

  public static TemplateSource of(TemplateId id, URI origin, String content) {
    return of(id, origin, StandardCharsets.UTF_8, content, 0L);
  }

  public static TemplateSource fromString(TemplateId id, String content) {
    return of(
        id,
        URI.create("memory:" + id.value()),
        StandardCharsets.UTF_8,
        content,
        System.currentTimeMillis());
  }

  public static String computeSha256(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(text.hashCode());
    }
  }
}
