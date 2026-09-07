package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.Objects;

/**
 * Record representing a negatively cached lookup (e.g. missing template or fatal syntax error) to
 * protect against repeated disk/classpath misses.
 */
public record NegativeCacheEntry(TemplateId templateId, String reason, long expirationEpochMillis) {

  public NegativeCacheEntry {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
  }

  public boolean isExpired(long now) {
    return now >= expirationEpochMillis;
  }
}
