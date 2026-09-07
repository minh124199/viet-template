package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VtlTemplateEngineTest {

  @Test
  void buildsViaPublicApiBuilderAndServiceLoader() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("hello.vm", "Hello $name from ServiceLoader!");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
    assertThat(engine).isInstanceOf(VtlTemplateEngine.class);

    Template template = engine.get("hello.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(RenderContext.builder().put("name", "World").build(), out);

    assertThat(out.toString()).isEqualTo("Hello World from ServiceLoader!");
  }

  @Test
  void rendersViaAotBytecodeTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "users.vm",
        "#foreach($user in $users)" + "[$foreach.count] $user#if($foreach.hasNext), #end" + "#end");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    Template template = engine.get("users.vm");
    assertThat(template.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

    StringTemplateOutput out = new StringTemplateOutput();
    RenderContext ctx =
        RenderContext.builder().put("users", List.of("Alice", "Bob", "Charlie")).build();
    template.render(ctx, out);

    assertThat(out.toString()).isEqualTo("[1] Alice, [2] Bob, [3] Charlie");
    engine.close();
  }

  @Test
  void rendersViaIrTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("greeting.vm", "#if($lang == 'vi')Xin chao $name#else Hello $name#end");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();

    Template template = engine.get("greeting.vm");
    assertThat(template.descriptor().executionTier()).isEqualTo("IR");

    StringTemplateOutput out = new StringTemplateOutput();
    template.render(RenderContext.builder().put("lang", "vi").put("name", "Minh").build(), out);

    assertThat(out.toString()).isEqualTo("Xin chao Minh");
    engine.close();
  }

  @Test
  void handlesMissingTemplateAndEnforcesNegativeCaching() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).negativeCacheTtlMillis(1000L).build();

    TemplateId missing = TemplateId.of("missing.vm");

    // First lookup: misses in repo and caches negative entry
    assertThatThrownBy(() -> engine.get(missing))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("Template not found in repository");

    assertThat(engine.cache().isNegativelyCached(missing)).isTrue();

    // Second lookup: served from negative cache
    assertThatThrownBy(() -> engine.get(missing))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("Template not found (cached negative lookup)");

    engine.close();
  }

  @Test
  void hotReloadsWhenFileChangesWithFilesystemRepository(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    Path tplDir = tempDir.resolve("templates");
    Files.createDirectories(tplDir);
    Path tplFile = tplDir.resolve("page.vm");
    Files.writeString(tplFile, "Initial Content: $val");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tplDir);
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .hotReload(true)
            .watchDebounceMillis(50L)
            .build();

    TemplateId id = TemplateId.of("page.vm");
    Template t1 = engine.get(id);
    StringTemplateOutput out1 = new StringTemplateOutput();
    t1.render(RenderContext.builder().put("val", "A").build(), out1);
    assertThat(out1.toString()).isEqualTo("Initial Content: A");

    // Modify file
    Files.writeString(tplFile, "Updated Content: $val");

    // Wait for file watcher debounce
    Thread.sleep(180L);

    // Next get must fetch reloaded version
    Template t2 = engine.get(id);
    StringTemplateOutput out2 = new StringTemplateOutput();
    t2.render(RenderContext.builder().put("val", "B").build(), out2);
    assertThat(out2.toString()).isEqualTo("Updated Content: B");

    engine.close();
  }
}
