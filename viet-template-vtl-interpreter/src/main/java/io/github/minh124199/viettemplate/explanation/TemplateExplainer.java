package io.github.minh124199.viettemplate.explanation;

/**
 * Service facade providing structured compiler explanation and optimization diagnostics for VTL
 * templates without template execution or runtime rendering.
 */
public interface TemplateExplainer {

  /**
   * Explains templates according to the provided request configuration.
   *
   * @param request the explanation request configuration
   * @return immutable structured explanation result
   */
  TemplateExplanation explain(TemplateExplainRequest request);

  /**
   * Creates a new instance of the default {@link TemplateExplainer}.
   *
   * @return default template explainer instance
   */
  static TemplateExplainer create() {
    return new DefaultTemplateExplainer();
  }
}
