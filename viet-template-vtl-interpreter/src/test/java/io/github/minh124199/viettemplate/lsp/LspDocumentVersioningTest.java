package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspDocumentVersioningTest {

  @Test
  @DisplayName(
      "Full lifecycle: open v1 -> change v2 -> stale change v1 [rejected] -> change v3 -> close ->"
          + " reopen v1")
  void testFullVersioningLifecycle() {
    TemplateDocumentStore store = new TemplateDocumentStore();
    String uri = "file:///workspace/template.vt";

    // 1. open v1
    TemplateDocument docV1 = new TemplateDocument(uri, 1, "Hello v1");
    store.put(docV1);
    assertEquals(1, store.get(uri).orElseThrow().version());
    assertEquals("Hello v1", store.get(uri).orElseThrow().content());

    // 2. change v2 -> accepted
    TemplateDocument docV2 = new TemplateDocument(uri, 2, "Hello v2");
    boolean updatedV2 = store.updateIfNewer(docV2);
    assertTrue(updatedV2);
    assertEquals(2, store.get(uri).orElseThrow().version());
    assertEquals("Hello v2", store.get(uri).orElseThrow().content());

    // 3. stale change v1 -> rejected, existing kept
    TemplateDocument staleV1 = new TemplateDocument(uri, 1, "Stale v1 update");
    boolean updatedStale = store.updateIfNewer(staleV1);
    assertFalse(updatedStale);
    assertEquals(2, store.get(uri).orElseThrow().version());
    assertEquals("Hello v2", store.get(uri).orElseThrow().content());

    // 4. change v3 -> accepted
    TemplateDocument docV3 = new TemplateDocument(uri, 3, "Hello v3");
    boolean updatedV3 = store.updateIfNewer(docV3);
    assertTrue(updatedV3);
    assertEquals(3, store.get(uri).orElseThrow().version());
    assertEquals("Hello v3", store.get(uri).orElseThrow().content());

    // 5. close -> removed
    Optional<TemplateDocument> removed = store.remove(uri);
    assertTrue(removed.isPresent());
    assertEquals(3, removed.get().version());
    assertTrue(store.get(uri).isEmpty());

    // 6. reopen v1 -> accepted
    TemplateDocument reopenedV1 = new TemplateDocument(uri, 1, "Reopened v1");
    store.put(reopenedV1);
    assertEquals(1, store.get(uri).orElseThrow().version());
    assertEquals("Reopened v1", store.get(uri).orElseThrow().content());
  }

  @Test
  @DisplayName("Language service updateDocumentIfNewer delegates and enforces version monotonicity")
  void testLanguageServiceVersioning() {
    TemplateLanguageService service = TemplateLanguageService.create();
    String uri = "file:///workspace/service-test.vt";

    // Open v1
    service.openDocument(uri, 1, "initial text");
    assertEquals(1, service.getDocument(uri).orElseThrow().version());

    // Update v2
    boolean updated2 = service.updateDocumentIfNewer(uri, 2, "version 2 text");
    assertTrue(updated2);
    assertEquals(2, service.getDocument(uri).orElseThrow().version());
    assertEquals("version 2 text", service.getDocument(uri).orElseThrow().text());

    // Stale update v1 rejected
    boolean staleRejected = service.updateDocumentIfNewer(uri, 1, "stale text");
    assertFalse(staleRejected);
    assertEquals(2, service.getDocument(uri).orElseThrow().version());
    assertEquals("version 2 text", service.getDocument(uri).orElseThrow().text());

    // Close and reopen
    service.closeDocument(uri);
    assertTrue(service.getDocument(uri).isEmpty());

    service.openDocument(uri, 1, "reopened text");
    assertEquals(1, service.getDocument(uri).orElseThrow().version());
  }

  @Test
  @DisplayName("Atomic thread-safety under concurrent racing updates")
  void testConcurrentDocumentUpdates() throws InterruptedException {
    TemplateDocumentStore store = new TemplateDocumentStore();
    String uri = "file:///workspace/concurrent.vt";

    // Initial document at version 0
    store.put(new TemplateDocument(uri, 0, "version 0"));

    int threadCount = 20;
    int versionsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    List<Integer> versions = new ArrayList<>();
    for (int i = 1; i <= threadCount * versionsPerThread; i++) {
      versions.add(i);
    }
    // Shuffle versions so updates arrive out of order
    Collections.shuffle(versions);

    for (int t = 0; t < threadCount; t++) {
      final int threadIdx = t;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int v = 0; v < versionsPerThread; v++) {
                int ver = versions.get(threadIdx * versionsPerThread + v);
                store.updateIfNewer(new TemplateDocument(uri, ver, "content for version " + ver));
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    assertTrue(completed, "Concurrent update tasks timed out");

    // The winning version in the store MUST be the highest version in the list
    int maxVersion = threadCount * versionsPerThread;
    TemplateDocument finalDoc = store.get(uri).orElseThrow();
    assertEquals(maxVersion, finalDoc.version());
    assertEquals("content for version " + maxVersion, finalDoc.content());
  }
}
