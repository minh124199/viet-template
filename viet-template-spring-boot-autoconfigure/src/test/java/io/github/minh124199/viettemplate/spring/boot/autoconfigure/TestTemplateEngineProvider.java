package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateEngineProvider;

public final class TestTemplateEngineProvider implements TemplateEngineProvider {

  @Override
  public TemplateEngine.Builder createBuilder() {
    return new TestTemplateEngineBuilder();
  }
}
