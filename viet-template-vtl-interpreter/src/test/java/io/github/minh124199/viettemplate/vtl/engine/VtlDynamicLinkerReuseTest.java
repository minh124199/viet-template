package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlDynamicLinkerReuseTest {

  public record User(String name, String email) {}

  public record Admin(String name, String role) {}

  @Test
  @DisplayName("CallSiteRegistry reuses dynamic call sites across renders without re-linking")
  void testDynamicLinkerReusedAcrossRenders() throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("user_profile.vtl");
    repo.put(templateId, "Hello, $user.name! Contact: $user.email");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();

    // 1. Initial 1st render to link call sites
    RenderContext warmupContext =
        RenderContext.builder().put("user", new User("Alice", "alice@example.com")).build();
    StringTemplateOutput warmupOut = new StringTemplateOutput();
    engine.render(RenderRequest.of(templateId, warmupContext), warmupOut);
    assertThat(warmupOut.toString()).isEqualTo("Hello, Alice! Contact: alice@example.com");

    long initialLinks = engine.callSiteRegistry().statistics().links();
    assertThat(initialLinks).as("Initial link count must be positive").isGreaterThan(0L);
    long initialPicHits = engine.callSiteRegistry().statistics().picHits();

    // 2. Verify link count remains identical after 2nd render
    RenderContext secondContext =
        RenderContext.builder().put("user", new User("Bob", "bob@example.com")).build();
    StringTemplateOutput secondOut = new StringTemplateOutput();
    engine.render(RenderRequest.of(templateId, secondContext), secondOut);
    assertThat(secondOut.toString()).isEqualTo("Hello, Bob! Contact: bob@example.com");
    assertThat(engine.callSiteRegistry().statistics().links())
        .as("Link count must remain identical after 2nd render")
        .isEqualTo(initialLinks);

    // 3. Verify link count remains identical after 50 repeated renders
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
        .as("Call site links count must not increase after 50 repeated renders")
        .isEqualTo(initialLinks);
    assertThat(engine.callSiteRegistry().statistics().picHits())
        .as("PIC hits must increase with each property evaluation")
        .isGreaterThan(initialPicHits);

    // 4. Concurrent repeat renders check (50 concurrent threads rendering the same receiver shape)
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Void>> tasks = new ArrayList<>(50);
      for (int i = 0; i < 50; i++) {
        final int id = i;
        tasks.add(
            () -> {
              RenderContext ctx =
                  RenderContext.builder()
                      .put("user", new User("Concurrent" + id, "c" + id + "@example.com"))
                      .build();
              StringTemplateOutput out = new StringTemplateOutput();
              engine.render(RenderRequest.of(templateId, ctx), out);
              assertThat(out.toString())
                  .isEqualTo("Hello, Concurrent" + id + "! Contact: c" + id + "@example.com");
              return null;
            });
      }
      for (Future<Void> f : executor.invokeAll(tasks)) {
        f.get();
      }
    }

    assertThat(engine.callSiteRegistry().statistics().links())
        .as(
            "Call site links count must not increase across 50 concurrent renders of identical"
                + " shape")
        .isEqualTo(initialLinks);

    // 5. Renders via engine.get(id).render() must also share the same interpreter and registry
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

    // 6. Introduce a new distinct receiver shape (Admin record with name and role)
    TemplateId adminTemplateId = TemplateId.of("admin_profile.vtl");
    repo.put(adminTemplateId, "Admin: $admin.name, role: $admin.role");
    long beforeAdminLinks = engine.callSiteRegistry().statistics().links();

    RenderContext adminCtx =
        RenderContext.builder().put("admin", new Admin("SuperUser", "ADMIN")).build();
    StringTemplateOutput adminOut = new StringTemplateOutput();
    engine.render(RenderRequest.of(adminTemplateId, adminCtx), adminOut);
    assertThat(adminOut.toString()).isEqualTo("Admin: SuperUser, role: ADMIN");

    long afterAdminLinks = engine.callSiteRegistry().statistics().links();
    assertThat(afterAdminLinks)
        .as("Admin shape must introduce exactly 2 additional links (name and role)")
        .isEqualTo(beforeAdminLinks + 2);

    // Subsequent repeated renders with Admin shape must not create duplicate links
    for (int i = 0; i < 20; i++) {
      RenderContext ctx =
          RenderContext.builder().put("admin", new Admin("Admin" + i, "ROLE_" + i)).build();
      StringTemplateOutput out = new StringTemplateOutput();
      engine.render(RenderRequest.of(adminTemplateId, ctx), out);
      assertThat(out.toString()).isEqualTo("Admin: Admin" + i + ", role: ROLE_" + i);
    }
    assertThat(engine.callSiteRegistry().statistics().links())
        .as("Links count must remain unchanged during repeated Admin renders")
        .isEqualTo(afterAdminLinks);

    // 7. Verify engine.callSiteRegistry().size() is bounded and clears to 0 on engine.close()
    assertThat(engine.callSiteRegistry().size())
        .as("Registry size must be bounded by maxCapacity")
        .isGreaterThan(0)
        .isLessThanOrEqualTo(engine.callSiteRegistry().maxCapacity());
    engine.close();
    assertThat(engine.callSiteRegistry().size()).isZero();
  }
}
