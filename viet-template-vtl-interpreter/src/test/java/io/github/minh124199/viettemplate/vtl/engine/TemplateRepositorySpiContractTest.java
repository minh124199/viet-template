package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Contract test suite verifying that a third-party implementation of {@link TemplateRepository}
 * built strictly with the public API integrates cleanly into {@link TemplateEngine}.
 */
class TemplateRepositorySpiContractTest {

  /** Third-party custom repository implementation using only public API. */
  static class CustomThirdPartyRepository implements TemplateRepository {
    private final Map<TemplateId, String> storage = new ConcurrentHashMap<>();

    void register(String name, String content) {
      storage.put(TemplateId.normalize(name), content);
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) {
      String content = storage.get(id);
      if (content == null) {
        return Optional.empty();
      }
      return Optional.of(TemplateSource.fromString(id, content));
    }
  }

  @Test
  void engineLoadsAndRendersFromCustomRepository() throws IOException {
    CustomThirdPartyRepository repo = new CustomThirdPartyRepository();
    repo.register("custom/page.vm", "<h1>$title</h1><p>$message</p>");

    try (TemplateEngine engine = TemplateEngine.builder().repository(repo).build()) {
      RenderContext ctx =
          RenderContext.builder()
              .put("title", "Welcome")
              .put("message", "Custom Repository Success")
              .build();

      String result = engine.render("custom/page.vm", ctx);
      assertThat(result).isEqualTo("<h1>Welcome</h1><p>Custom Repository Success</p>");
    }
  }

  @Test
  void engineFailsGracefullyWithTemplateResourceExceptionOnMissingTemplate() {
    CustomThirdPartyRepository repo = new CustomThirdPartyRepository();

    try (TemplateEngine engine = TemplateEngine.builder().repository(repo).build()) {
      assertThatThrownBy(() -> engine.get("nonexistent.vm"))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository")
          .matches(
              e -> {
                TemplateResourceException tre = (TemplateResourceException) e;
                return tre.code().isPresent() && "NOT_FOUND".equals(tre.code().get().id());
              });
    }
  }
}
