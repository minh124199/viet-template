package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

record AotExecutionTarget(
    CompiledTemplate compiledTemplate,
    Optional<TemplateClassLoader> classLoader,
    Optional<IrTemplate> irTemplate)
    implements ExecutionTarget {

  AotExecutionTarget {
    Objects.requireNonNull(compiledTemplate, "compiledTemplate must not be null");
    Objects.requireNonNull(classLoader, "classLoader must not be null");
    Objects.requireNonNull(irTemplate, "irTemplate must not be null");
  }

  @Override
  public void render(RenderContext context, TemplateOutput output) throws IOException {
    compiledTemplate.render(context, output);
  }
}
