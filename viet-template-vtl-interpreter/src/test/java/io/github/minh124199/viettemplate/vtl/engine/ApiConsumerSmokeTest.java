package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.runtime.*;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Smoke test simulating an external application consuming Viet Template.
 *
 * <p>Strictly imports only {@code io.github.minh124199.viettemplate.api.*} and {@code
 * io.github.minh124199.viettemplate.runtime.*}. No internal VTL packages are referenced.
 */
class ApiConsumerSmokeTest {

  public static final class CustomerProfile {
    private final String id;
    private final String name;

    public CustomerProfile(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }
  }

  static class MemoryRepository implements TemplateRepository {
    private final ConcurrentHashMap<TemplateId, TemplateSource> sources = new ConcurrentHashMap<>();

    void put(String path, String content) {
      TemplateId id = TemplateId.normalize(path);
      sources.put(id, TemplateSource.fromString(id, content));
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) {
      return Optional.ofNullable(sources.get(id));
    }
  }

  @Test
  void externalConsumerEndToEndWorkflow() throws IOException {
    // 1. Consumer defines custom repository
    MemoryRepository repository = new MemoryRepository();
    repository.put(
        "views/profile.vm",
        "<h1>User Profile</h1>\n"
            + "<p>ID: $customer.id</p>\n"
            + "<p>Name: $customer.name</p>\n"
            + "#if($status)\n"
            + "<p>Status: $status</p>\n"
            + "#else\n"
            + "<p>Status: absent</p>\n"
            + "#end");

    // 2. Consumer defines custom member access policy
    MemberAccessPolicy policy =
        MemberAccessPolicy.builder()
            .allowClass(CustomerProfile.class)
            .allowProperty(CustomerProfile.class, "id")
            .allowProperty(CustomerProfile.class, "name")
            .build();

    // 3. Consumer configures and builds TemplateEngine using public SPI/API
    try (TemplateEngine engine =
        TemplateEngine.builder().repository(repository).memberAccessPolicy(policy).build()) {

      // 4. Consumer builds 3-state RenderContext with defined null
      RenderContext context =
          RenderContext.builder()
              .put("customer", new CustomerProfile("cust-123", "Alice Smith"))
              .put("status", null)
              .build();

      // 5. Render directly to String via public convenience
      String rendered = engine.render("views/profile.vm", context);
      assertThat(rendered).contains("ID: cust-123");
      assertThat(rendered).contains("Name: Alice Smith");
      assertThat(rendered).contains("Status: absent");

      // 6. Render to streaming StringTemplateOutput from runtime module
      StringTemplateOutput streamOutput = new StringTemplateOutput();
      Template template = engine.get("views/profile.vm");
      template.render(context, streamOutput);
      assertThat(streamOutput.toString()).isEqualTo(rendered);

      // 7. Verify custom Escaper SPI
      Escaper customEscaper =
          new Escaper() {
            @Override
            public EscapeMode mode() {
              return EscapeMode.HTML_TEXT;
            }

            @Override
            public void escape(CharSequence input, TemplateOutput output) throws IOException {
              if (input == null) {
                return;
              }
              output.write("[SAFE:" + input + "]");
            }
          };

      EscaperRegistry escaperRegistry = new EscaperRegistry();
      escaperRegistry.register(customEscaper);
      assertThat(escaperRegistry.get(EscapeMode.HTML_TEXT)).isSameAs(customEscaper);

      StringTemplateOutput escaperOutput = new StringTemplateOutput();
      customEscaper.escape("<script>alert(1)</script>", escaperOutput);
      assertThat(escaperOutput.toString()).isEqualTo("[SAFE:<script>alert(1)</script>]");

      // Null handling contract on Escaper
      customEscaper.escape(null, escaperOutput);
      assertThat(escaperOutput.toString()).isEqualTo("[SAFE:<script>alert(1)</script>]");
    }
  }
}
