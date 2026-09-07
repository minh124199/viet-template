package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateClassLoader;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable handle to a compiled template representation associated with an atomic generation
 * number.
 *
 * <p>Retains the {@link TemplateClassLoader} of the compilation generation so that evicting this
 * handle releases the classloader and permits garbage collection of generated classes.
 */
public record CompiledTemplateHandle(
    TemplateId templateId,
    long generation,
    CompileCacheKey key,
    Optional<CompiledTemplate> compiledTemplate,
    Optional<IrTemplate> irTemplate,
    Optional<TemplateClassLoader> classLoader,
    long compiledEpochMillis) {

  public CompiledTemplateHandle {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(compiledTemplate, "compiledTemplate must not be null");
    Objects.requireNonNull(irTemplate, "irTemplate must not be null");
    Objects.requireNonNull(classLoader, "classLoader must not be null");
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
        Optional.of(compiledTemplate),
        Optional.ofNullable(irTemplate),
        Optional.ofNullable(classLoader),
        System.currentTimeMillis());
  }

  public static CompiledTemplateHandle ofIr(
      TemplateId templateId, long generation, CompileCacheKey key, IrTemplate irTemplate) {
    return new CompiledTemplateHandle(
        templateId,
        generation,
        key,
        Optional.empty(),
        Optional.ofNullable(irTemplate),
        Optional.empty(),
        System.currentTimeMillis());
  }
}
