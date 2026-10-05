package io.github.minh124199.viettemplate.validation;

/** Service interface for build-time template validation without template execution or rendering. */
public interface TemplateValidator {

  /**
   * Validates templates according to the provided request configuration.
   *
   * @param request the validation request configuration
   * @return the validation result containing diagnostics and dependency graph
   */
  TemplateValidationResult validate(TemplateValidationRequest request);

  /**
   * Creates a new instance of the default {@link TemplateValidator}.
   *
   * @return a default template validator instance
   */
  static TemplateValidator create() {
    return new DefaultTemplateValidator();
  }
}
