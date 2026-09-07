package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

  @Test
  void extractsDirectDependenciesFromParseAndInclude() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("header.vm", "<header>Top</header>");
    repo.put("footer.html", "<footer>Bottom</footer>");
    repo.put("page.vm", "#parse('header.vm')\nContent\n#include('footer.html')");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build();
    engine.get("page.vm");

    TemplateDependencyGraph graph = engine.dependencyGraph();
    Set<TemplateDependency> deps = graph.dependenciesOf(TemplateId.of("page.vm"));

    assertThat(deps)
        .contains(
            TemplateDependency.of(
                TemplateId.of("page.vm"),
                TemplateId.of("header.vm"),
                TemplateDependencyKind.STATIC_PARSE),
            TemplateDependency.of(
                TemplateId.of("page.vm"),
                TemplateId.of("footer.html"),
                TemplateDependencyKind.STATIC_INCLUDE));

    engine.close();
  }

  @Test
  void queriesReverseDependenciesCorrectly() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("header.vm", "<header>Nav</header>");
    repo.put("page1.vm", "#parse('header.vm') Page 1");
    repo.put("page2.vm", "#parse('header.vm') Page 2");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build();
    engine.get("page1.vm");
    engine.get("page2.vm");

    TemplateDependencyGraph graph = engine.dependencyGraph();
    Set<TemplateId> dependents = graph.dependentsOf(TemplateId.of("header.vm"));

    assertThat(dependents)
        .containsExactlyInAnyOrder(TemplateId.of("page1.vm"), TemplateId.of("page2.vm"));

    engine.close();
  }

  @Test
  void computesTransitiveDependentsInLinearChain() {
    // Chain: A -> B -> C (A parses B, B parses C)
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("c.vm", "Leaf C");
    repo.put("b.vm", "#parse('c.vm') Middle B");
    repo.put("a.vm", "#parse('b.vm') Root A");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build();
    engine.get("c.vm");
    engine.get("b.vm");
    engine.get("a.vm");

    TemplateDependencyGraph graph = engine.dependencyGraph();

    // Querying dependents of C should transitively find B and A
    Set<TemplateId> transitive = graph.transitiveDependentsOf(TemplateId.of("c.vm"));
    assertThat(transitive).containsExactlyInAnyOrder(TemplateId.of("b.vm"), TemplateId.of("a.vm"));

    // Querying dependents of B should find only A
    assertThat(graph.transitiveDependentsOf(TemplateId.of("b.vm")))
        .containsExactly(TemplateId.of("a.vm"));

    // Leaf A has no dependents
    assertThat(graph.transitiveDependentsOf(TemplateId.of("a.vm"))).isEmpty();

    engine.close();
  }

  @Test
  void handlesDependencyCyclesWithoutInfiniteLoopOrStackOverflow() {
    DefaultTemplateDependencyGraph graph = new DefaultTemplateDependencyGraph();
    TemplateId a = TemplateId.of("a.vm");
    TemplateId b = TemplateId.of("b.vm");
    TemplateId c = TemplateId.of("c.vm");

    // A depends on B, B depends on C, C depends on A (circular)
    graph.replaceDependencies(
        a, Set.of(TemplateDependency.of(a, b, TemplateDependencyKind.STATIC_PARSE)));
    graph.replaceDependencies(
        b, Set.of(TemplateDependency.of(b, c, TemplateDependencyKind.STATIC_PARSE)));
    graph.replaceDependencies(
        c, Set.of(TemplateDependency.of(c, a, TemplateDependencyKind.STATIC_PARSE)));

    // Transitive dependents of C: B depends on C, A depends on B, C depends on A
    Set<TemplateId> transitive = graph.transitiveDependentsOf(c);
    assertThat(transitive).containsExactlyInAnyOrder(b, a);

    // Self-loop: A depends on A
    DefaultTemplateDependencyGraph selfLoop = new DefaultTemplateDependencyGraph();
    selfLoop.replaceDependencies(
        a, Set.of(TemplateDependency.of(a, a, TemplateDependencyKind.STATIC_PARSE)));
    assertThat(selfLoop.transitiveDependentsOf(a)).isEmpty();
  }

  @Test
  void removesAndClearsGraphProperly() {
    DefaultTemplateDependencyGraph graph = new DefaultTemplateDependencyGraph();
    TemplateId a = TemplateId.of("a.vm");
    TemplateId b = TemplateId.of("b.vm");

    graph.replaceDependencies(
        a, Set.of(TemplateDependency.of(a, b, TemplateDependencyKind.STATIC_PARSE)));
    assertThat(graph.size()).isEqualTo(2);
    assertThat(graph.dependentsOf(b)).containsExactly(a);

    graph.removeTemplate(a);
    assertThat(graph.dependentsOf(b)).isEmpty();
    assertThat(graph.dependenciesOf(a)).isEmpty();

    graph.replaceDependencies(
        a, Set.of(TemplateDependency.of(a, b, TemplateDependencyKind.STATIC_PARSE)));
    graph.clear();
    assertThat(graph.size()).isEqualTo(0);
  }
}
