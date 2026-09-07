package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyInvalidationTest {

  @Test
  void invalidatingDependencyInvalidatesAllTransitiveDependents() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("c.vm", "v1-c");
    repo.put("b.vm", "[b:#parse('c.vm')]");
    repo.put("a.vm", "[a:#parse('b.vm')]");
    repo.put("unrelated.vm", "isolated");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build();

    // 1. Initial compilation and execution
    Template ta = engine.get("a.vm");
    engine.get("unrelated.vm");

    StringTemplateOutput out1 = new StringTemplateOutput();
    ta.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("[a:[b:v1-c]]");

    // All templates are cached
    assertThat(engine.cache().getActive(TemplateId.of("a.vm"))).isPresent();
    assertThat(engine.cache().getActive(TemplateId.of("b.vm"))).isPresent();
    assertThat(engine.cache().getActive(TemplateId.of("c.vm"))).isPresent();
    assertThat(engine.cache().getActive(TemplateId.of("unrelated.vm"))).isPresent();

    // 2. Modify c.vm and invalidate with dependents
    repo.put("c.vm", "v2-c-updated");
    Set<TemplateId> invalidated = engine.invalidateWithDependents(TemplateId.of("c.vm"));

    // Verify returned set of invalidated templates
    assertThat(invalidated)
        .containsExactlyInAnyOrder(
            TemplateId.of("c.vm"), TemplateId.of("b.vm"), TemplateId.of("a.vm"));

    // Cache entries for a, b, c are evicted, but unrelated remains cached
    assertThat(engine.cache().getActive(TemplateId.of("a.vm"))).isEmpty();
    assertThat(engine.cache().getActive(TemplateId.of("b.vm"))).isEmpty();
    assertThat(engine.cache().getActive(TemplateId.of("c.vm"))).isEmpty();
    assertThat(engine.cache().getActive(TemplateId.of("unrelated.vm"))).isPresent();

    // 3. Re-render a.vm reflects updated dependency
    Template ta2 = engine.get("a.vm");
    StringTemplateOutput out2 = new StringTemplateOutput();
    ta2.render(RenderContext.empty(), out2);
    assertThat(out2.toString()).isEqualTo("[a:[b:v2-c-updated]]");

    engine.close();
  }

  @Test
  void invalidatingRootDoesNotInvalidateDependencies() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("child.vm", "child-content");
    repo.put("parent.vm", "parent:#parse('child.vm')");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build();

    // Compile both
    Template parent = engine.get("parent.vm");
    StringTemplateOutput out1 = new StringTemplateOutput();
    parent.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("parent:child-content");

    // Invalidate parent only (leaf dependent)
    Set<TemplateId> invalidated = engine.invalidateWithDependents(TemplateId.of("parent.vm"));
    assertThat(invalidated).containsExactly(TemplateId.of("parent.vm"));

    // Parent is invalidated, child is still active in cache
    assertThat(engine.cache().getActive(TemplateId.of("parent.vm"))).isEmpty();
    assertThat(engine.cache().getActive(TemplateId.of("child.vm"))).isPresent();

    engine.close();
  }
}
