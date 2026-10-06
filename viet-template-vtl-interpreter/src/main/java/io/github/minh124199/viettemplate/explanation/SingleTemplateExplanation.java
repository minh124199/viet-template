package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured compiler explanation for a single compiled template.
 *
 * @param templateId template identifier
 * @param relativePath relative file path within source directory
 * @param profile active VTL profile
 * @param typeCheckingMode type checking validation mode
 * @param strictReferences whether strict reference mode is enabled
 * @param nullRenderMode null rendering strategy name (e.g. LITERAL_EXPRESSION, THROW_ERROR)
 * @param escapeMode default output escaping mode name (e.g. RAW, HTML_TEXT)
 * @param typed whether this template has a bound typed contract or schema
 * @param contractClass contract class name if bound
 * @param dependencies static dependencies extracted from the template
 * @param aotEligible whether the template is eligible for static AOT compilation
 * @param compilationStatus compilation status (e.g. AOT_OK, AOT_OK_WITH_DYNAMIC_SITES)
 * @param aotRejectionReasons reasons if full static AOT was not achieved
 * @param expressions explanations for individual expressions and references in this template
 */
public record SingleTemplateExplanation(
    TemplateId templateId,
    String relativePath,
    VtlProfile profile,
    TypeCheckingMode typeCheckingMode,
    boolean strictReferences,
    String nullRenderMode,
    String escapeMode,
    boolean typed,
    Optional<String> contractClass,
    List<TemplateDependency> dependencies,
    boolean aotEligible,
    String compilationStatus,
    List<String> aotRejectionReasons,
    List<ExpressionExplanation> expressions)
    implements Serializable {

  public SingleTemplateExplanation {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(relativePath, "relativePath must not be null");
    Objects.requireNonNull(profile, "profile must not be null");
    Objects.requireNonNull(typeCheckingMode, "typeCheckingMode must not be null");
    Objects.requireNonNull(nullRenderMode, "nullRenderMode must not be null");
    Objects.requireNonNull(escapeMode, "escapeMode must not be null");
    contractClass = contractClass != null ? contractClass : Optional.empty();
    dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    Objects.requireNonNull(compilationStatus, "compilationStatus must not be null");
    aotRejectionReasons =
        aotRejectionReasons != null ? List.copyOf(aotRejectionReasons) : List.of();
    expressions = expressions != null ? List.copyOf(expressions) : List.of();
  }
}
