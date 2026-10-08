package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.io.Serializable;
import java.util.Objects;

/**
 * An exact resolved reference to a canonical semantic symbol located within a VTL template.
 *
 * <p>Carries the target {@link WorkspaceSymbolKey}, template document URI, exact identifier {@link
 * Range} (excluding leading navigation dots), {@link SourceSpan}, and the syntactic reference kind.
 */
record ResolvedTemplateReference(
    WorkspaceSymbolKey symbolKey, String templateUri, Range range, SourceSpan span, Kind kind)
    implements Comparable<ResolvedTemplateReference>, Serializable {

  enum Kind {
    PROPERTY_ACCESS,
    METHOD_CALL,
    ROOT_VARIABLE,
    LOCAL_VARIABLE,
    DIRECTIVE_TARGET
  }

  ResolvedTemplateReference {
    Objects.requireNonNull(symbolKey, "symbolKey must not be null");
    Objects.requireNonNull(templateUri, "templateUri must not be null");
    Objects.requireNonNull(range, "range must not be null");
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
  }

  public LocationInfo toLocationInfo() {
    return LocationInfo.of(templateUri, range);
  }

  @Override
  public int compareTo(ResolvedTemplateReference o) {
    Objects.requireNonNull(o, "o must not be null");
    int uriCmp = this.templateUri.compareTo(o.templateUri);
    if (uriCmp != 0) {
      return uriCmp;
    }
    int rangeCmp = this.range.compareTo(o.range);
    if (rangeCmp != 0) {
      return rangeCmp;
    }
    int kindCmp = this.kind.compareTo(o.kind);
    if (kindCmp != 0) {
      return kindCmp;
    }
    return this.symbolKey.toString().compareTo(o.symbolKey.toString());
  }
}
