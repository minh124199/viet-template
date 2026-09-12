package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency stress test verifying thread-safe operations on {@link
 * DefaultTemplateDependencyGraph} and transitive invalidation during concurrent renders in a
 * realistic hierarchical layout: layout -> (header, page -> (macro-library, component), footer).
 */
class DependencyGraphConcurrencyStressTest {

  @Test
  @DisplayName("Stress test transitive dependency invalidation under high concurrent rendering")
  void testTransitiveDependencyInvalidationConcurrency() throws Exception {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    DefaultTemplateDependencyGraph graph = new DefaultTemplateDependencyGraph();

    TemplateId layoutId = TemplateId.of("layout.vm");
    TemplateId headerId = TemplateId.of("header.vm");
    TemplateId pageId = TemplateId.of("page.vm");
    TemplateId footerId = TemplateId.of("footer.vm");
    TemplateId macroId = TemplateId.of("macros.vm");
    TemplateId componentId = TemplateId.of("component.vm");

    // Populate templates
    repository.put(
        layoutId, "<html>#parse('header.vm')#parse('page.vm')#parse('footer.vm')</html>");
    repository.put(headerId, "<header>App Header v1</header>");
    repository.put(pageId, "<main>#parse('component.vm')</main>");
    repository.put(componentId, "<div class=\"comp\">Component v1</div>");
    repository.put(footerId, "<footer>Footer v1</footer>");
    repository.put(macroId, "#macro(testMacro $m)<span>$m</span>#end");

    // Build dependency relationships:
    // layout -> header, page, footer
    // page -> component, macros
    graph.replaceDependencies(
        layoutId,
        Set.of(
            TemplateDependency.of(layoutId, headerId, TemplateDependencyKind.STATIC_PARSE),
            TemplateDependency.of(layoutId, pageId, TemplateDependencyKind.STATIC_PARSE),
            TemplateDependency.of(layoutId, footerId, TemplateDependencyKind.STATIC_PARSE)));

    graph.replaceDependencies(
        pageId,
        Set.of(
            TemplateDependency.of(pageId, componentId, TemplateDependencyKind.STATIC_PARSE),
            TemplateDependency.of(pageId, macroId, TemplateDependencyKind.GLOBAL_MACRO_LIBRARY)));

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .dependencyGraph(graph)
            .executionTier(ExecutionTier.IR)
            .build()) {

      // Warm up layout
      StringTemplateOutput initialOut = new StringTemplateOutput();
      engine.get(layoutId).render(RenderContext.empty(), initialOut);
      assertThat(initialOut.toString()).contains("Component v1");

      int renderThreads = 16;
      ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
      Phaser phase = new Phaser(renderThreads + 1);

      try {
        List<Callable<Void>> renderTasks = new ArrayList<>(renderThreads);
        for (int i = 0; i < renderThreads; i++) {
          renderTasks.add(
              () -> {
                for (int version = 1; version <= 5; version++) {
                  phase.arriveAndAwaitAdvance();
                  StringTemplateOutput out = new StringTemplateOutput();
                  engine.get(layoutId).render(RenderContext.empty(), out);
                  String res = out.toString();
                  if (!res.contains("<header>") || !res.contains("<footer>")) {
                    throw new IllegalStateException("Corrupted output: " + res);
                  }
                  phase.arriveAndAwaitAdvance();
                }
                return null;
              });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : renderTasks) {
          futures.add(executor.submit(task));
        }

        phase.arriveAndAwaitAdvance();
        phase.arriveAndAwaitAdvance();
        for (int step = 2; step <= 5; step++) {
          repository.put(componentId, "<div class=\"comp\">Component v" + step + "</div>");
          Set<TemplateId> invalidated = engine.invalidateWithDependents(componentId);
          // Transitive dependents of component must include: component, page, layout
          assertThat(invalidated).contains(componentId, pageId, layoutId);
          phase.arriveAndAwaitAdvance();
          phase.arriveAndAwaitAdvance();
        }

        for (Future<Void> f : futures) {
          f.get();
        }

        // Final render must reflect the latest component version
        StringTemplateOutput finalOut = new StringTemplateOutput();
        engine.get(layoutId).render(RenderContext.empty(), finalOut);
        assertThat(finalOut.toString()).contains("Component v5");
      } finally {
        executor.shutdown();
      }
    }
  }
}
