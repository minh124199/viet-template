package io.github.minh124199.viettemplate.assets;

import java.util.Objects;

/**
 * Immutable representation of a module preload link.
 *
 * @param url the public URL for the preloaded module chunk
 */
public record AssetModulePreload(String url) {

  public AssetModulePreload {
    Objects.requireNonNull(url, "url must not be null");
  }
}
