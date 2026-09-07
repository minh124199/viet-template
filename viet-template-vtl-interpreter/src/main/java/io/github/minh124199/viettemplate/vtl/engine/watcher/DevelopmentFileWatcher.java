package io.github.minh124199.viettemplate.vtl.engine.watcher;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Development filesystem watcher using {@link WatchService} with event debouncing to coalesce rapid
 * write bursts before triggering cache invalidation.
 *
 * <p>Safely handles thread lifecycle and guarantees clean, leak-free termination upon {@link
 * #close()}.
 */
public final class DevelopmentFileWatcher implements AutoCloseable {

  private final Path rootDir;
  private final long debounceMillis;
  private final Consumer<Path> changeListener;

  private final WatchService watchService;
  private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<Path, Long> pendingEvents = new ConcurrentHashMap<>();

  private final Thread watchThread;
  private final ScheduledExecutorService debounceExecutor;
  private volatile boolean running = true;

  public DevelopmentFileWatcher(Path rootDir, long debounceMillis, Consumer<Path> changeListener)
      throws IOException {
    Objects.requireNonNull(rootDir, "rootDir must not be null");
    Objects.requireNonNull(changeListener, "changeListener must not be null");
    this.rootDir = rootDir.toAbsolutePath().normalize();
    this.debounceMillis = Math.max(10, debounceMillis);
    this.changeListener = changeListener;

    this.watchService = rootDir.getFileSystem().newWatchService();
    registerRecursive(this.rootDir);

    this.debounceExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "viet-template-watcher-debounce");
              t.setDaemon(true);
              return t;
            });

    this.watchThread = new Thread(this::pollEvents, "viet-template-file-watcher");
    this.watchThread.setDaemon(true);
    this.watchThread.start();

    // Schedule debounce drain task
    this.debounceExecutor.scheduleWithFixedDelay(
        this::drainDebouncedEvents,
        this.debounceMillis,
        this.debounceMillis / 2,
        TimeUnit.MILLISECONDS);
  }

  private void registerRecursive(Path start) throws IOException {
    if (!Files.exists(start)) {
      return;
    }
    Files.walkFileTree(
        start,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchKeys.put(key, dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private void pollEvents() {
    while (running) {
      WatchKey key;
      try {
        key = watchService.poll(500, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        break;
      } catch (ClosedWatchServiceException e) {
        break;
      }

      if (key == null) {
        continue;
      }

      Path dir = watchKeys.get(key);
      if (dir == null) {
        key.reset();
        continue;
      }

      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
          continue;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();
        Path fullPath = dir.resolve(filename).normalize();

        // If a new directory is created, register it
        if (kind == ENTRY_CREATE && Files.isDirectory(fullPath)) {
          try {
            registerRecursive(fullPath);
          } catch (IOException ignored) {
          }
        }

        pendingEvents.put(fullPath, System.currentTimeMillis());
      }

      boolean valid = key.reset();
      if (!valid) {
        watchKeys.remove(key);
      }
    }
  }

  private void drainDebouncedEvents() {
    long now = System.currentTimeMillis();
    List<Path> ready = new ArrayList<>();

    for (Map.Entry<Path, Long> entry : pendingEvents.entrySet()) {
      if (now - entry.getValue() >= debounceMillis) {
        ready.add(entry.getKey());
      }
    }

    for (Path path : ready) {
      pendingEvents.remove(path);
      try {
        changeListener.accept(path);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void close() {
    running = false;
    try {
      watchService.close();
    } catch (IOException ignored) {
    }

    watchThread.interrupt();
    debounceExecutor.shutdownNow();

    try {
      watchThread.join(2000);
      debounceExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
    watchKeys.clear();
    pendingEvents.clear();
  }

  public Path rootDirectory() {
    return rootDir;
  }

  public boolean isRunning() {
    return running;
  }
}
