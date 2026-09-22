package io.github.minh124199.viettemplate.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VietTemplateRendererTest {

  private TemplateEngine engine;
  private VietTemplateConfig config;
  private VietTemplateRenderer renderer;

  @BeforeEach
  public void setUp() {
    InMemoryTemplateRepository repository =
        InMemoryTemplateRepository.create()
            .put("templates/hello.vtl", "Hello $name!")
            .put("welcome.vtl", "Welcome, $user!");

    engine = VtlTemplateEngine.builder().repository(repository).build();

    config =
        new VietTemplateConfig() {
          @Override
          public String path() {
            return "templates";
          }

          @Override
          public String suffix() {
            return ".vtl";
          }

          @Override
          public Optional<List<String>> additionalSuffixes() {
            return Optional.empty();
          }

          @Override
          public boolean runtimeCompilationEnabled() {
            return true;
          }

          @Override
          public int cacheMaxEntries() {
            return 100;
          }

          @Override
          public long negativeCacheTtlMillis() {
            return 1000L;
          }

          @Override
          public String undefinedReferencePolicy() {
            return "SILENT";
          }

          @Override
          public String encoding() {
            return "UTF-8";
          }
        };

    renderer = new VietTemplateRenderer(engine, config);
  }

  @AfterEach
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Test
  public void testRenderByTemplateNameWithSuffix() {
    String result = renderer.render("hello.vtl", Map.of("name", "Alice"));
    assertThat(result).isEqualTo("Hello Alice!");
  }

  @Test
  public void testRenderByTemplateNameWithoutSuffix() {
    String result = renderer.render("hello", Map.of("name", "Bob"));
    assertThat(result).isEqualTo("Hello Bob!");
  }

  @Test
  public void testRenderByTemplateId() {
    String result =
        renderer.render(TemplateId.of("templates/hello.vtl"), Map.of("name", "Charlie"));
    assertThat(result).isEqualTo("Hello Charlie!");
  }

  @Test
  public void testRenderDirectRootTemplate() {
    String result = renderer.render("welcome", Map.of("user", "Diana"));
    assertThat(result).isEqualTo("Welcome, Diana!");
  }

  @Test
  public void testRenderToOutputStream() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    renderer.render("hello", Map.of("name", "Eve"), baos);
    assertThat(baos.toString()).isEqualTo("Hello Eve!");
  }

  @Test
  public void testRenderToOutputStreamByTemplateId() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    renderer.render(TemplateId.of("templates/hello.vtl"), Map.of("name", "Frank"), baos);
    assertThat(baos.toString()).isEqualTo("Hello Frank!");
  }

  @Test
  public void testProducerLifecycle() {
    VietTemplateProducer producer = new VietTemplateProducer(config);
    TemplateEngine producedEngine = producer.produceTemplateEngine();
    assertThat(producedEngine).isNotNull();
    producer.close();
  }
}
