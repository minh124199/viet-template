package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.List;
import java.util.Objects;

/** Root node representing an entire parsed VTL template. */
public record VtlTemplate(
    TemplateId templateId,
    SourceSpan span,
    List<VtlParamDeclarationNode> parameters,
    List<VtlNode> children)
    implements VtlNode {

  public VtlTemplate {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(span, "span must not be null");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
  }

  public VtlTemplate(TemplateId templateId, SourceSpan span, List<VtlNode> children) {
    this(templateId, span, List.of(), children);
  }

  public static VtlTemplate of(
      TemplateId templateId,
      SourceSpan span,
      List<VtlParamDeclarationNode> parameters,
      List<VtlNode> children) {
    return new VtlTemplate(templateId, span, parameters, children);
  }

  public static VtlTemplate of(TemplateId templateId, SourceSpan span, List<VtlNode> children) {
    return new VtlTemplate(templateId, span, List.of(), children);
  }
}
