package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateEngineProvider;

/** Reference implementation of {@link TemplateEngineProvider}. */
public final class VtlTemplateEngineProvider implements TemplateEngineProvider {

  @Override
  public TemplateEngine.Builder createBuilder() {
    return new VtlTemplateEngineBuilder();
  }
}
