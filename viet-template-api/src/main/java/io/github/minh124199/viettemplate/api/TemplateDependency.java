package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Directed dependency relationship between a source template and a target template.
 *
 * @param source dependent template identifier
 * @param target dependency template identifier
 * @param kind dependency relationship kind
 */
public record TemplateDependency(TemplateId source, TemplateId target, TemplateDependencyKind kind)
    implements Serializable {

  public TemplateDependency {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
  }

  public static TemplateDependency of(
      TemplateId source, TemplateId target, TemplateDependencyKind kind) {
    return new TemplateDependency(source, target, kind);
  }
}
