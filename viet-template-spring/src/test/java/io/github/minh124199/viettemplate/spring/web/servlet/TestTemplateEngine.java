package io.github.minh124199.viettemplate.spring.web.servlet;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TestTemplateEngine implements TemplateEngine {

  private final Map<TemplateId, Template> templates = new HashMap<>();
  private TemplateRepository repository;
  private RuntimeException renderException;
  private IOException renderIoException;
  private RenderContext lastRenderContext;
  private TemplateId lastScreenId;

  TestTemplateEngine() {
    this.repository =
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId id) {
            if (templates.containsKey(id)) {
              return Optional.of(TemplateSource.fromString(id, ""));
            }
            return Optional.empty();
          }
        };
  }

  void registerTemplate(TemplateId id, Template template) {
    this.templates.put(id, template);
  }

  void setRepository(TemplateRepository repository) {
    this.repository = repository;
  }

  void setRenderException(RuntimeException exception) {
    this.renderException = exception;
  }

  void setRenderIoException(IOException exception) {
    this.renderIoException = exception;
  }

  RenderContext getLastRenderContext() {
    return this.lastRenderContext;
  }

  TemplateId getLastScreenId() {
    return this.lastScreenId;
  }

  @Override
  public Template get(TemplateId id) {
    Template template = this.templates.get(id);
    if (template == null) {
      throw new TemplateResourceException(
          "Template not found: " + id.value(),
          id,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
    }
    return template;
  }

  @Override
  public void render(RenderRequest request, TemplateOutput output) throws IOException {
    if (this.renderIoException != null) {
      throw this.renderIoException;
    }
    if (this.renderException != null) {
      throw this.renderException;
    }
    this.lastScreenId = request.templateId();
    this.lastRenderContext = request.userModel();
    Template template = this.templates.get(request.templateId());
    if (template != null) {
      template.render(request.userModel(), output);
    } else {
      output.write("Default render: " + request.templateId().value());
    }
  }

  @Override
  public TemplateRepository repository() {
    return this.repository;
  }

  @Override
  public boolean rejectRuntimeCompilation() {
    return false;
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
}
