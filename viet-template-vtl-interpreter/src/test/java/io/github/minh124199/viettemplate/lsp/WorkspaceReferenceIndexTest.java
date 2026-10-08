package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceReferenceIndexTest {

  @Test
  @DisplayName(
      "Indexes template, queries local variable references, and handles incremental replacement")
  void testIndexAndQueryLocalReferences() {
    WorkspaceReferenceIndex index = new WorkspaceReferenceIndex();
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    MemberAccessPolicy policy = MemberAccessPolicy.standard();

    String uri = "file:///test.vtl";
    String content = "#set($count = 10)\nCount is $count and next is $count.";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    index.indexTemplate(doc, resolver, policy);

    assertEquals(1, index.templateCount());
    Set<ResolvedTemplateReference> refs = index.getReferencesForTemplate(uri);
    // Should have 1 DIRECTIVE_TARGET and 2 LOCAL_VARIABLE usages
    assertEquals(3, refs.size());

    // Symbol query with includeDeclaration = true (returns all 3)
    WorkspaceSymbolKey key = refs.iterator().next().symbolKey();
    List<LocationInfo> allLocations = index.findReferences(key, true);
    assertEquals(3, allLocations.size());

    // Symbol query with includeDeclaration = false (returns only 2 usages)
    List<LocationInfo> usagesOnly = index.findReferences(key, false);
    assertEquals(2, usagesOnly.size());

    // Incremental update: new version with only 1 usage
    String newContent = "#set($count = 20)\nCount is $count.";
    TemplateDocument docV2 = new TemplateDocument(uri, 2, newContent);
    index.indexTemplate(docV2, resolver, policy);

    Set<ResolvedTemplateReference> refsV2 = index.getReferencesForTemplate(uri);
    assertEquals(2, refsV2.size()); // 1 target + 1 usage
    assertEquals(1, index.templateCount());

    // Removal
    index.removeTemplate(uri);
    assertEquals(0, index.templateCount());
    assertEquals(0, index.symbolCount());
    assertTrue(index.findReferences(key).isEmpty());
  }

  @Test
  @DisplayName("Parse error cleanly clears existing references for template")
  void testParseErrorCleansReferences() {
    WorkspaceReferenceIndex index = new WorkspaceReferenceIndex();
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    MemberAccessPolicy policy = MemberAccessPolicy.standard();

    String uri = "file:///test.vtl";
    String validContent = "#set($val = 1)\n$val";
    TemplateDocument docValid = new TemplateDocument(uri, 1, validContent);
    index.indexTemplate(docValid, resolver, policy);
    assertEquals(1, index.templateCount());

    // Update with malformed syntax (missing closing paren)
    String invalidContent = "#set($val = ";
    TemplateDocument docInvalid = new TemplateDocument(uri, 2, invalidContent);
    index.indexTemplate(docInvalid, resolver, policy);

    assertEquals(0, index.templateCount());
    assertEquals(0, index.symbolCount());
  }

  @Test
  @DisplayName(
      "scanWorkspaceTemplates indexes supported extensions and ignores hidden/build directories")
  void testScanWorkspaceTemplates(@TempDir Path workspaceDir) throws IOException {
    // 1. Supported files
    Files.writeString(workspaceDir.resolve("t1.vtl"), "#set($a = 1)\n$a");
    Files.writeString(workspaceDir.resolve("t2.vm"), "#set($b = 2)\n$b");
    Files.writeString(workspaceDir.resolve("t3.vt"), "#set($c = 3)\n$c");

    // 2. Unsupported extension
    Files.writeString(workspaceDir.resolve("ignore.txt"), "hello");

    // 3. Ignored directory
    Path targetDir = workspaceDir.resolve("target");
    Files.createDirectories(targetDir);
    Files.writeString(targetDir.resolve("in-target.vtl"), "#set($x = 1)\n$x");

    WorkspaceReferenceIndex index = new WorkspaceReferenceIndex();
    index.scanWorkspaceTemplates(
        workspaceDir, new CanonicalSchemaResolver(), MemberAccessPolicy.standard());

    assertEquals(3, index.templateCount());
    assertTrue(
        index.getReferencesForTemplate(workspaceDir.resolve("t1.vtl").toUri().toString()).size()
            > 0);
    assertTrue(
        index.getReferencesForTemplate(workspaceDir.resolve("t2.vm").toUri().toString()).size()
            > 0);
    assertTrue(
        index.getReferencesForTemplate(workspaceDir.resolve("t3.vt").toUri().toString()).size()
            > 0);
    assertTrue(
        index
            .getReferencesForTemplate(targetDir.resolve("in-target.vtl").toUri().toString())
            .isEmpty());
  }

  @Test
  @DisplayName("Concurrent multi-threaded indexing and reference querying remains thread-safe")
  void testConcurrentOperations() throws Exception {
    WorkspaceReferenceIndex index = new WorkspaceReferenceIndex();
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    MemberAccessPolicy policy = MemberAccessPolicy.standard();

    int threadCount = 8;
    int iterationsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  for (int i = 0; i < iterationsPerThread; i++) {
                    String uri = "file:///template_" + threadId + "_" + (i % 5) + ".vtl";
                    String text =
                        "#set($var"
                            + threadId
                            + " = "
                            + i
                            + ")\n$var"
                            + threadId
                            + "\n$var"
                            + threadId;
                    TemplateDocument doc = new TemplateDocument(uri, i, text);
                    index.indexTemplate(doc, resolver, policy);

                    WorkspaceSymbolKey key =
                        WorkspaceSymbolKey.templateLocal(
                            uri, "var" + threadId, 5, 5 + ("var" + threadId).length());
                    index.findReferences(key, true);
                    index.findReferences(key, false);

                    if (i % 10 == 0) {
                      index.removeTemplate(uri);
                    }
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    startLatch.countDown();
    for (Future<?> f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertTrue(index.templateCount() >= 0);
    index.clear();
    assertEquals(0, index.templateCount());
    assertEquals(0, index.symbolCount());
  }
}
