package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable handle to a compiled template representation associated with an atomic generation
 * number.
 *
 * <p>Delegates execution to an encapsulated {@link ExecutionTarget}.
 */
public record CompiledTemplateHandle(
    TemplateId templateId,
    long generation,
    CompileCacheKey key,
    ExecutionTarget target,
    long compiledEpochMillis) {

  public CompiledTemplateHandle {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(target, "target must not be null");
  }

  public CompiledTemplateHandle(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      Optional<CompiledTemplate> compiledTemplate,
      Optional<IrTemplate> irTemplate,
      Optional<TemplateClassLoader> classLoader,
      long compiledEpochMillis) {
    this(
        templateId,
        generation,
        key,
        compiledTemplate
            .<ExecutionTarget>map(
                ct ->
                    classLoader.isPresent()
                        ? new AotExecutionTarget(ct, classLoader, irTemplate)
                        : new PreparedIrExecutionTarget(ct, irTemplate))
            .orElseGet(
                () ->
                    new PreparedIrExecutionTarget(
                        noOpCompiledTemplate(templateId), irTemplate)),
        compiledEpochMillis);
  }

  private static CompiledTemplate noOpCompiledTemplate(TemplateId templateId) {
    return new CompiledTemplate() {
      @Override
      public io.github.minh124199.viettemplate.api.TemplateDescriptor descriptor() {
        return io.github.minh124199.viettemplate.api.TemplateDescriptor.of(templateId, "NOOP");
      }

      @Override
      public void render(RenderContext context, TemplateOutput output) {
        // no-op
      }
    };
  }

  public void render(RenderContext context, TemplateOutput output) throws IOException {
    target.render(context, output);
  }

  public Optional<CompiledTemplate> compiledTemplate() {
    if (target instanceof AotExecutionTarget aot) {
      return Optional.of(aot.compiledTemplate());
    } else if (target instanceof PreparedIrExecutionTarget ir) {
      return Optional.of(ir.preparedIr());
    }
    return Optional.empty();
  }

  public Optional<IrTemplate> irTemplate() {
    if (target instanceof AotExecutionTarget aot) {
      return aot.irTemplate();
    } else if (target instanceof PreparedIrExecutionTarget ir) {
      return ir.irTemplate();
    }
    return Optional.empty();
  }

  public Optional<TemplateClassLoader> classLoader() {
    if (target instanceof AotExecutionTarget aot) {
      return aot.classLoader();
    }
    return Optional.empty();
  }

  public static CompiledTemplateHandle ofBytecode(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      CompiledTemplate compiledTemplate,
      IrTemplate irTemplate,
      TemplateClassLoader classLoader) {
    return new CompiledTemplateHandle(
        templateId,
        generation,
        key,
        new AotExecutionTarget(
            compiledTemplate,
            Optional.ofNullable(classLoader),
            Optional.ofNullable(irTemplate)),
        System.currentTimeMillis());
  }

  public static CompiledTemplateHandle ofPreparedIr(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      CompiledTemplate preparedTemplate,
      IrTemplate irTemplate) {
    return ofPreparedIr(
        templateId,
        generation,
        key,
        preparedTemplate,
        Optional.ofNullable(irTemplate));
  }

  public static CompiledTemplateHandle ofPreparedIr(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      CompiledTemplate preparedTemplate,
      Optional<IrTemplate> irTemplate) {
    return new CompiledTemplateHandle(
        templateId,
        generation,
        key,
        new PreparedIrExecutionTarget(preparedTemplate, irTemplate),
        System.currentTimeMillis());
  }

  public static CompiledTemplateHandle ofAst(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      VtlInterpreter interpreter,
      SourceText sourceText,
      VtlTemplate astNode) {
    return ofAst(
        templateId,
        generation,
        key,
        interpreter,
        sourceText,
        Optional.ofNullable(astNode));
  }

  public static CompiledTemplateHandle ofAst(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      VtlInterpreter interpreter,
      SourceText sourceText,
      Optional<VtlTemplate> astNode) {
    return new CompiledTemplateHandle(
        templateId,
        generation,
        key,
        new PreparedAstExecutionTarget(interpreter, sourceText, astNode),
        System.currentTimeMillis());
  }

  public static CompiledTemplateHandle ofAst(
      TemplateId templateId,
      long generation,
      CompileCacheKey key,
      VtlInterpreter interpreter,
      SourceText sourceText) {
    return ofAst(templateId, generation, key, interpreter, sourceText, Optional.empty());
  }

  public static CompiledTemplateHandle ofIr(
      TemplateId templateId, long generation, CompileCacheKey key, IrTemplate irTemplate) {
    return new CompiledTemplateHandle(
        templateId,
        generation,
        key,
        new PreparedIrExecutionTarget(
            noOpCompiledTemplate(templateId), Optional.ofNullable(irTemplate)),
        System.currentTimeMillis());
  }
}
