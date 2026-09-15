package io.github.minh124199.viettemplate.spring.web.servlet;

import io.github.minh124199.viettemplate.api.TemplateEngine;

/** Callback interface for customizing a {@link TemplateEngine.Builder} prior to engine creation. */
@FunctionalInterface
public interface VietTemplateEngineCustomizer {

  /**
   * Customizes the given {@link TemplateEngine.Builder}.
   *
   * @param builder the engine builder to customize
   */
  void customize(TemplateEngine.Builder builder);
}
