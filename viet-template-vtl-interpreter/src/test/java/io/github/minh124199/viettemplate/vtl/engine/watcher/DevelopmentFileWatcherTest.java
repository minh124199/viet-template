package io.github.minh124199.viettemplate.vtl.engine.watcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevelopmentFileWatcherTest {

  @Test
  void debouncesMultipleRapidWrites(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    Path templatesDir = tempDir.resolve("templates");
    Files.createDirectories(templatesDir);
    Path testFile = templatesDir.resolve("index.vm");
    Files.writeString(testFile, "initial content");

    List<Path> notifiedPaths = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    try (DevelopmentFileWatcher watcher =
        new DevelopmentFileWatcher(
            templatesDir,
            100L, // 100ms debounce
            path -> {
              notifiedPaths.add(path);
              latch.countDown();
            })) {

      assertThat(watcher.isRunning()).isTrue();

      // Rapidly write to the file 5 times within 40ms
      for (int i = 0; i < 5; i++) {
        Files.writeString(testFile, "update content " + i);
        Thread.sleep(8L);
      }

      // Wait for debounce window to fire
      boolean fired = latch.await(2, TimeUnit.SECONDS);
      assertThat(fired).isTrue();

      // Small extra pause to verify no trailing duplicate triggers
      Thread.sleep(150L);

      // Coalesced into 1 (or at most 2 depending on OS filesystem poll cadence)
      assertThat(notifiedPaths).isNotEmpty();
      assertThat(notifiedPaths.size()).isLessThanOrEqualTo(2);
      assertThat(notifiedPaths.get(0).getFileName().toString()).isEqualTo("index.vm");
    }
  }

  @Test
  void cleanlyClosesAndStopsThreads(@TempDir Path tempDir) throws IOException {
    DevelopmentFileWatcher watcher = new DevelopmentFileWatcher(tempDir, 50L, path -> {});
    assertThat(watcher.isRunning()).isTrue();

    watcher.close();
    assertThat(watcher.isRunning()).isFalse();
  }
}
