package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HotReloadDependencyTest {

  @Test
  void modifyingSubtemplateTriggersHotReloadOfCallerTemplate(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    Path tplDir = tempDir.resolve("templates");
    Files.createDirectories(tplDir);

    Path subFile = tplDir.resolve("sub.vm");
    Files.writeString(subFile, "Sub-v1");

    Path mainFile = tplDir.resolve("main.vm");
    Files.writeString(mainFile, "Main [#parse('sub.vm')]");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tplDir);
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .hotReload(true)
            .watchDebounceMillis(40L)
            .build();

    // 1. Initial render of main template
    Template t1 = engine.get("main.vm");
    StringTemplateOutput out1 = new StringTemplateOutput();
    t1.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("Main [Sub-v1]");

    // 2. Modify only the sub-template on the filesystem
    Files.writeString(subFile, "Sub-v2-reloaded");

    // Wait for file watcher debounce and invalidation
    Thread.sleep(180L);

    // 3. Next get on main template must fetch reloaded composite
    Template t2 = engine.get("main.vm");
    StringTemplateOutput out2 = new StringTemplateOutput();
    t2.render(RenderContext.empty(), out2);
    assertThat(out2.toString()).isEqualTo("Main [Sub-v2-reloaded]");

    engine.close();
  }

  @Test
  void hotReloadPropagatesThroughTransitiveChain(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    Path tplDir = tempDir.resolve("templates");
    Files.createDirectories(tplDir);

    Path badgeFile = tplDir.resolve("badge.vm");
    Files.writeString(badgeFile, "v1");

    Path widgetFile = tplDir.resolve("widget.vm");
    Files.writeString(widgetFile, "widget(#parse('badge.vm'))");

    Path pageFile = tplDir.resolve("page.vm");
    Files.writeString(pageFile, "page(#parse('widget.vm'))");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tplDir);
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .hotReload(true)
            .watchDebounceMillis(40L)
            .build();

    Template page = engine.get("page.vm");
    StringTemplateOutput out1 = new StringTemplateOutput();
    page.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("page(widget(v1))");

    // Modify deep leaf
    Files.writeString(badgeFile, "v2-hot");
    Thread.sleep(180L);

    Template reloadedPage = engine.get("page.vm");
    StringTemplateOutput out2 = new StringTemplateOutput();
    reloadedPage.render(RenderContext.empty(), out2);
    assertThat(out2.toString()).isEqualTo("page(widget(v2-hot))");

    engine.close();
  }
}
