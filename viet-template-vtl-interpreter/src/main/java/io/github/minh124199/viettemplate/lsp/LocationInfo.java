package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * Definition location result pointing to a document URI and source range.
 *
 * <p>Supports deterministic natural ordering.
 */
record LocationInfo(String uri, Range range) implements Comparable<LocationInfo>, Serializable {

  public LocationInfo {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(range, "range must not be null");
  }

  public static LocationInfo of(String uri, Range range) {
    return new LocationInfo(uri, range);
  }

  @Override
  public int compareTo(LocationInfo o) {
    Objects.requireNonNull(o, "o must not be null");
    int uriCmp = this.uri.compareTo(o.uri);
    if (uriCmp != 0) {
      return uriCmp;
    }
    return this.range.compareTo(o.range);
  }
}
