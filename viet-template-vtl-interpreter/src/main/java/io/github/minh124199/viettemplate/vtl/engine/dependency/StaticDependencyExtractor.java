package io.github.minh124199.viettemplate.vtl.engine.dependency;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Traverses an intermediate representation (IR) template to extract static dependencies such as
 * `#parse("foo.vm")`, `#include("bar.html")`, global macro library dependencies, and layout
 * relationships.
 */
public final class StaticDependencyExtractor {

  private StaticDependencyExtractor() {}

  /**
   * Extracts all static dependencies from an IR template, combining IR call sites with configured
   * global macro libraries and layout references.
   */
  public static Set<TemplateDependency> extract(
      IrTemplate template, List<TemplateId> globalMacroLibraries, Optional<TemplateId> layoutId) {
    Objects.requireNonNull(template, "template must not be null");

    Set<TemplateDependency> dependencies = new LinkedHashSet<>();
    extractFromBlock(template.id(), template.root(), dependencies);

    for (IrFunction function : template.functions()) {
      extractFromBlock(template.id(), function.body(), dependencies);
    }

    if (globalMacroLibraries != null) {
      for (TemplateId libId : globalMacroLibraries) {
        if (!libId.equals(template.id())) {
          dependencies.add(
              TemplateDependency.of(
                  template.id(), libId, TemplateDependencyKind.GLOBAL_MACRO_LIBRARY));
        }
      }
    }

    if (layoutId != null && layoutId.isPresent()) {
      TemplateId layout = layoutId.get();
      if (!layout.equals(template.id())) {
        dependencies.add(
            TemplateDependency.of(template.id(), layout, TemplateDependencyKind.LAYOUT));
      }
    }

    return Collections.unmodifiableSet(dependencies);
  }

  private static void extractFromBlock(
      TemplateId sourceId, IrBlock block, Set<TemplateDependency> dependencies) {
    if (block == null) {
      return;
    }
    for (IrStatement stmt : block.statements()) {
      extractFromStatement(sourceId, stmt, dependencies);
    }
  }

  private static void extractFromStatement(
      TemplateId sourceId, IrStatement stmt, Set<TemplateDependency> dependencies) {
    if (stmt instanceof IrCallTemplate ct) {
      if (ct.staticTemplateName().isPresent()) {
        String name = ct.staticTemplateName().get();
        TemplateId targetId = TemplateId.normalize(name);
        TemplateDependencyKind kind =
            ct.isParse()
                ? TemplateDependencyKind.STATIC_PARSE
                : TemplateDependencyKind.STATIC_INCLUDE;
        dependencies.add(TemplateDependency.of(sourceId, targetId, kind));
      }
    } else if (stmt instanceof IrIf ifStmt) {
      extractFromBlock(sourceId, ifStmt.thenBlock(), dependencies);
      ifStmt.elseBlock().ifPresent(eb -> extractFromBlock(sourceId, eb, dependencies));
    } else if (stmt instanceof IrLoop loop) {
      extractFromBlock(sourceId, loop.body(), dependencies);
    } else if (stmt instanceof IrCallMacro cm) {
      cm.bodyContent().ifPresent(body -> extractFromBlock(sourceId, body, dependencies));
    }
  }
}
