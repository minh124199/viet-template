package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TestTemplateEngine implements TemplateEngine {

  private final TestTemplateEngineBuilder builder;
  private volatile boolean closed = false;
  private final Map<TemplateId, Template> templates = new ConcurrentHashMap<>();

  public TestTemplateEngine(TestTemplateEngineBuilder builder) {
    this.builder = builder;
  }

  public TestTemplateEngineBuilder getBuilder() {
    return this.builder;
  }

  public boolean isClosed() {
    return this.closed;
  }

  public void registerTemplate(TemplateId id, Template template) {
    this.templates.put(id, template);
  }

  @Override
  public void close() {
    this.closed = true;
  }

  @Override
  public Template get(TemplateId id) {
    return this.templates.get(id);
  }

  @Override
  public void render(RenderRequest request, TemplateOutput output) throws IOException {
    Template template = get(request.templateId());
    if (template != null) {
      template.render(request.userModel(), output);
    }
  }

  @Override
  public LayoutRenderPlan prepareLayoutPlan(TemplateId screenId, RenderContext context) {
    return null;
  }

  @Override
  public TemplateDependencyGraph dependencyGraph() {
    return null;
  }

  @Override
  public Set<TemplateId> invalidateWithDependents(TemplateId id) {
    return Set.of();
  }

  @Override
  public TemplateRepository repository() {
    return this.builder.getRepository();
  }

  @Override
  public boolean rejectRuntimeCompilation() {
    return this.builder.isRejectRuntimeCompilation();
  }
}
