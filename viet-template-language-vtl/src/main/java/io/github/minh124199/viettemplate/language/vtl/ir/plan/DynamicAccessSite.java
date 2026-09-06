package io.github.minh124199.viettemplate.language.vtl.ir.plan;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/**
 * Metadata identifying a dynamic dispatch site in the template.
 *
 * <p>Used by dynamic linkers and polymorphic inline caches (PIC) to bind call sites at runtime.
 */
public record DynamicAccessSite(int id, DynamicKind kind, String targetName, SourceSpan span) {

  public DynamicAccessSite {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(targetName, "targetName must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
