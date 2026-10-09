package io.github.minh124199.viettemplate.assets;

import java.util.Objects;

/**
 * Immutable representation of a script element URL.
 *
 * @param url the public URL for the script
 * @param module true if the script is an ES module ({@code type="module"})
 */
public record AssetScript(String url, boolean module) {

  public AssetScript {
    Objects.requireNonNull(url, "url must not be null");
  }
}
