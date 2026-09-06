package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/** Local variable allocated in a template, loop, or macro scope. */
public record IrLocal(String name, VType type, int slot, SourceSpan span) {

  public IrLocal {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
