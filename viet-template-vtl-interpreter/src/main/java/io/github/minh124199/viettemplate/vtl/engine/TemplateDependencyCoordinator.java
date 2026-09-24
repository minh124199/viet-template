package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.internal.engine.dependency.StaticDependencyExtractor;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Encapsulates dependency extraction, loop-protected dependency pre-compilation, dependency graph
 * queries, and cascade invalidation traversal.
 */
final class TemplateDependencyCoordinator {

  private final TemplateDependencyGraph dependencyGraph;
  private final ThreadLocal<Set<TemplateId>> compilingTemplates = new ThreadLocal<>();

  TemplateDependencyCoordinator(TemplateDependencyGraph dependencyGraph) {
    this.dependencyGraph =
        dependencyGraph != null ? dependencyGraph : new DefaultTemplateDependencyGraph();
  }

  TemplateDependencyGraph graph() {
    return dependencyGraph;
  }

  ThreadLocal<Set<TemplateId>> compilingTemplates() {
    return compilingTemplates;
  }

  void recordAndPrecompileDependencies(
      TemplateId id,
      IrTemplate ir,
      Set<TemplateId> libraryIds,
      Optional<TemplateId> layoutId,
      Consumer<TemplateId> precompileAction) {
    Objects.requireNonNull(libraryIds, "libraryIds must not be null");
    recordAndPrecompileDependencies(id, ir, List.copyOf(libraryIds), layoutId, precompileAction);
  }

  void recordAndPrecompileDependencies(
      TemplateId id,
      IrTemplate ir,
      List<TemplateId> libraryIds,
      Optional<TemplateId> layoutId,
      Consumer<TemplateId> precompileAction) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(ir, "ir must not be null");
    Objects.requireNonNull(libraryIds, "libraryIds must not be null");
    Objects.requireNonNull(layoutId, "layoutId must not be null");
    Objects.requireNonNull(precompileAction, "precompileAction must not be null");

    Set<TemplateDependency> deps = StaticDependencyExtractor.extract(ir, libraryIds, layoutId);
    dependencyGraph.replaceDependencies(id, deps);

    Set<TemplateId> compiling = compilingTemplates.get();
    if (compiling == null) {
      compiling = new HashSet<>();
      compilingTemplates.set(compiling);
    }
    if (compiling.add(id)) {
      try {
        for (TemplateDependency dep : deps) {
          if ((dep.kind() == TemplateDependencyKind.STATIC_PARSE
                  || dep.kind() == TemplateDependencyKind.STATIC_INCLUDE)
              && !compiling.contains(dep.target())) {
            try {
              precompileAction.accept(dep.target());
            } catch (TemplateResourceException ignored) {
              // Expected missing resources are tolerated during static pre-compilation;
              // internal engine, parser, and compiler exceptions must propagate.
            }
          }
        }
      } finally {
        compiling.remove(id);
        if (compiling.isEmpty()) {
          compilingTemplates.remove();
        }
      }
    }
  }

  Set<TemplateId> invalidateWithDependents(TemplateId id, TemplateCompileCache cache) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(cache, "cache must not be null");
    return cache.invalidateWithDependents(id, dependencyGraph);
  }
}
