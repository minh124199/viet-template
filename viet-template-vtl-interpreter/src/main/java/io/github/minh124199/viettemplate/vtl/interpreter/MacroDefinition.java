package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroParameter;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Objects;

/** Registered macro definition in the render-local macro registry. */
public record MacroDefinition(
    String name,
    List<VtlMacroParameter> parameters,
    List<VtlNode> body,
    TemplateId sourceTemplateId,
    SourceText sourceText) {

  public MacroDefinition {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");
    Objects.requireNonNull(body, "body must not be null");
    Objects.requireNonNull(sourceTemplateId, "sourceTemplateId must not be null");
    parameters = List.copyOf(parameters);
    body = List.copyOf(body);
  }

  public MacroDefinition(
      String name,
      List<VtlMacroParameter> parameters,
      List<VtlNode> body,
      TemplateId sourceTemplateId) {
    this(name, parameters, body, sourceTemplateId, null);
  }
}
