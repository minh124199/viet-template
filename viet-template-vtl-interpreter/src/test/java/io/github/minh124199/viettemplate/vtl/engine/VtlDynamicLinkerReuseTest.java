package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlDynamicLinkerReuseTest {

  public record User(String name, String email) {}

  @Test
  @DisplayName("CallSiteRegistry reuses dynamic call sites across renders without re-linking")
  void testDynamicLinkerReusedAcrossRenders() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("user_profile.vtl");
    repo.put(templateId, "Hello, $user.name! Contact: $user.email");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();

    // 1. Warmup render to link call sites
    RenderContext warmupContext =
        RenderContext.builder().put("user", new User("Alice", "alice@example.com")).build();
    StringTemplateOutput warmupOut = new StringTemplateOutput();
    engine.render(RenderRequest.of(templateId, warmupContext), warmupOut);
    assertThat(warmupOut.toString()).isEqualTo("Hello, Alice! Contact: alice@example.com");

    long initialLinks = engine.callSiteRegistry().statistics().links();
    assertThat(initialLinks).isGreaterThan(0L);
    long initialPicHits = engine.callSiteRegistry().statistics().picHits();

    // 2. Subsequent renders via engine.render() must hit PIC and not link new call sites
    for (int i = 0; i < 50; i++) {
      RenderContext ctx =
          RenderContext.builder()
              .put("user", new User("User" + i, "user" + i + "@example.com"))
              .build();
      StringTemplateOutput out = new StringTemplateOutput();
      engine.render(RenderRequest.of(templateId, ctx), out);
      assertThat(out.toString())
          .isEqualTo("Hello, User" + i + "! Contact: user" + i + "@example.com");
    }

    assertThat(engine.callSiteRegistry().statistics().links())
        .as("Call site links count must not increase for identical receiver shapes")
        .isEqualTo(initialLinks);
    assertThat(engine.callSiteRegistry().statistics().picHits())
        .as("PIC hits must increase with each property evaluation")
        .isGreaterThan(initialPicHits);

    // 3. Renders via engine.get(id).render() must also share the same interpreter and registry
    Template template = engine.get(templateId);
    for (int i = 50; i < 100; i++) {
      RenderContext ctx =
          RenderContext.builder()
              .put("user", new User("User" + i, "user" + i + "@example.com"))
              .build();
      StringTemplateOutput out = new StringTemplateOutput();
      template.render(ctx, out);
      assertThat(out.toString())
          .isEqualTo("Hello, User" + i + "! Contact: user" + i + "@example.com");
    }

    assertThat(engine.callSiteRegistry().statistics().links())
        .as("Template instance renders must share CallSiteRegistry without re-linking")
        .isEqualTo(initialLinks);

    // 4. Verify engine.close() clears the CallSiteRegistry
    assertThat(engine.callSiteRegistry().size()).isGreaterThan(0);
    engine.close();
    assertThat(engine.callSiteRegistry().size()).isZero();
  }
}
