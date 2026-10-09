package io.github.minh124199.viettemplate.assets;

import java.util.Objects;

/**
 * Immutable representation of an external stylesheet link.
 *
 * @param url the public URL for the stylesheet
 */
public record AssetStylesheet(String url) {

  public AssetStylesheet {
    Objects.requireNonNull(url, "url must not be null");
  }
}
