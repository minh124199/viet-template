package io.github.minh124199.viettemplate.assets;

import java.util.Objects;

/**
 * Immutable representation of a resolved static asset (image, font, media, etc.).
 *
 * @param logicalName the original logical asset name (e.g. {@code "src/images/logo.svg"})
 * @param url the public resolved URL (e.g. {@code "/assets/logo-D3f2.svg"})
 */
public record ResolvedAsset(String logicalName, String url) {

  public ResolvedAsset {
    Objects.requireNonNull(logicalName, "logicalName must not be null");
    Objects.requireNonNull(url, "url must not be null");
  }
}
