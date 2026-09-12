package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.Objects;

/**
 * Concrete implementation of the public {@link Template} contract wrapping a {@link
 * CompiledTemplateHandle}.
 */
public final class VtlTemplate implements Template {

  private final TemplateDescriptor descriptor;
  private final CompiledTemplateHandle handle;
  private final SourceText sourceText;
  private final VtlInterpreterOptions interpreterOptions;

  public VtlTemplate(
      TemplateDescriptor descriptor,
      CompiledTemplateHandle handle,
      SourceText sourceText,
      VtlInterpreterOptions interpreterOptions) {
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    this.handle = Objects.requireNonNull(handle, "handle must not be null");
    this.sourceText = Objects.requireNonNull(sourceText, "sourceText must not be null");
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");
  }

  @Override
  public TemplateDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public void render(RenderContext context, TemplateOutput output) throws IOException {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(output, "output must not be null");

    TemplateOutput wrappedOutput =
        (output instanceof CountingTemplateOutput cto)
            ? cto
            : new CountingTemplateOutput(
                output, interpreterOptions.limits().createRenderBudget(), descriptor.id());

    if (handle.compiledTemplate().isPresent()) {
      handle.compiledTemplate().get().render(context, wrappedOutput);
    } else if (interpreterOptions.executionTier() == ExecutionTier.AST) {
      VtlParseResult parseResult = VtlParser.parse(sourceText);
      new VtlInterpreter(interpreterOptions)
          .render(sourceText, parseResult.template(), context, wrappedOutput);
    } else if (handle.irTemplate().isPresent()) {
      new VtlInterpreter(interpreterOptions)
          .render(handle.irTemplate().get(), sourceText, context, wrappedOutput);
    } else {
      throw new IllegalStateException("Compiled template handle contains no executable target");
    }
  }

  public CompiledTemplateHandle handle() {
    return handle;
  }
}
