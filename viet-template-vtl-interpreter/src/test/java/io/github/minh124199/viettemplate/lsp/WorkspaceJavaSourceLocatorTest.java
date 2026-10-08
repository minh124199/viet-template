package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceJavaSourceLocatorTest {

  @Test
  @DisplayName("Maven single-module source discovery and type location")
  void testMavenSingleModule(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMain);
    Path userJava = srcMain.resolve("User.java");
    Files.writeString(
        userJava,
        """
        package com.example;
        public record User(String username) {}
        """);

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    assertTrue(locator.sourceRoots().contains(tempDir.resolve("src/main/java")));

    Optional<LocationInfo> loc = locator.locate(JavaSourceSymbolKey.forType("com.example.User"));
    assertTrue(loc.isPresent());
    assertEquals(userJava.toUri().toString(), loc.get().uri());
    assertEquals(1, loc.get().range().start().line());
  }

  @Test
  @DisplayName("Maven multi-module source root discovery across submodules")
  void testMavenMultiModule(@TempDir Path tempDir) throws IOException {
    // Root pom
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

    // Module A
    Path modA = tempDir.resolve("module-a/src/main/java/com/example/a");
    Files.createDirectories(modA);
    Path classA = modA.resolve("ServiceA.java");
    Files.writeString(
        classA,
        """
        package com.example.a;
        public class ServiceA {
          public String getInfo() { return "A"; }
        }
        """);

    // Module B
    Path modB = tempDir.resolve("module-b/src/main/java/com/example/b");
    Files.createDirectories(modB);
    Path classB = modB.resolve("ServiceB.java");
    Files.writeString(
        classB,
        """
        package com.example.b;
        public class ServiceB {
          public boolean isReady() { return true; }
        }
        """);

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    assertEquals(2, locator.sourceRoots().size());

    // Locate ServiceA in Module A
    Optional<LocationInfo> locA =
        locator.locate(JavaSourceSymbolKey.forMethod("com.example.a.ServiceA", "getInfo", 0));
    assertTrue(locA.isPresent());
    assertEquals(classA.toUri().toString(), locA.get().uri());

    // Locate ServiceB in Module B
    Optional<LocationInfo> locB =
        locator.locate(JavaSourceSymbolKey.forMethod("com.example.b.ServiceB", "isReady", 0));
    assertTrue(locB.isPresent());
    assertEquals(classB.toUri().toString(), locB.get().uri());
  }

  @Test
  @DisplayName("Gradle multi-project source root discovery")
  void testGradleSubprojects(@TempDir Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("settings.gradle"), "include 'app', 'lib'");

    Path appJava = tempDir.resolve("app/src/main/java/com/example/app");
    Files.createDirectories(appJava);
    Path appClass = appJava.resolve("App.java");
    Files.writeString(appClass, "package com.example.app; public class App {}");

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    assertTrue(locator.sourceRoots().contains(tempDir.resolve("app/src/main/java")));
    Optional<LocationInfo> loc = locator.locate(JavaSourceSymbolKey.forType("com.example.app.App"));
    assertTrue(loc.isPresent());
    assertEquals(appClass.toUri().toString(), loc.get().uri());
  }

  @Test
  @DisplayName("Probe candidate source roots upward from document path")
  void testProbeSourceRootsFromDocumentPath(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMain);
    Path userJava = srcMain.resolve("Customer.java");
    Files.writeString(userJava, "package com.example; public record Customer(String name) {}");

    Path templateDir = tempDir.resolve("src/main/resources/templates");
    Files.createDirectories(templateDir);
    Path templateFile = templateDir.resolve("invoice.vt");
    Files.writeString(templateFile, "Hello $customer.name");

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    // Do NOT call setWorkspaceRoot - rely on probe
    locator.probeSourceRootsFor(templateFile);

    assertTrue(locator.sourceRoots().contains(tempDir.resolve("src/main/java")));
    Optional<LocationInfo> loc =
        locator.locate(JavaSourceSymbolKey.forType("com.example.Customer"));
    assertTrue(loc.isPresent());
    assertEquals(userJava.toUri().toString(), loc.get().uri());
  }

  @Test
  @DisplayName("Path traversal defense rejecting illegal and traversal sequences")
  void testPathTraversalSecurity(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMain);
    Files.writeString(
        srcMain.resolve("Secret.java"), "package com.example; public class Secret {}");

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    // Traversal attempts
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("../../etc/passwd")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("../Secret")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("com/example/Secret")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("com.example..Secret")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("com.example.Secret:evil")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("com.example.Secret\0")).isPresent());
    assertFalse(locator.locate(JavaSourceSymbolKey.forType("")).isPresent());
  }

  @Test
  @DisplayName("AST cache hit and cache invalidation on file modification")
  void testCacheHitAndInvalidationOnFileChanged(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMain);
    Path file = srcMain.resolve("Counter.java");
    Files.writeString(
        file,
        """
        package com.example;
        public class Counter {
          public int getCount() { return 1; }
        }
        """);

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    // 1. First lookup parses file
    Optional<LocationInfo> loc1 =
        locator.locate(JavaSourceSymbolKey.forMethod("com.example.Counter", "getCount", 0));
    assertTrue(loc1.isPresent());

    // 2. Lookup non-existent method -> empty
    assertFalse(
        locator
            .locate(JavaSourceSymbolKey.forMethod("com.example.Counter", "getReset", 0))
            .isPresent());

    // 3. Update file on disk
    Files.writeString(
        file,
        """
        package com.example;
        public class Counter {
          public int getCount() { return 1; }
          public int getReset() { return 0; }
        }
        """);
    locator.onFileChanged(file);

    // 4. Second lookup finds new method
    Optional<LocationInfo> loc2 =
        locator.locate(JavaSourceSymbolKey.forMethod("com.example.Counter", "getReset", 0));
    assertTrue(loc2.isPresent());
    assertEquals(file.toUri().toString(), loc2.get().uri());
  }

  @Test
  @DisplayName("Cache eviction on file deletion")
  void testOnFileDeleted(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMain);
    Path file = srcMain.resolve("Temp.java");
    Files.writeString(file, "package com.example; public class Temp {}");

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.setWorkspaceRoot(tempDir);

    assertTrue(locator.locate(JavaSourceSymbolKey.forType("com.example.Temp")).isPresent());

    Files.delete(file);
    locator.onFileDeleted(file);

    assertFalse(locator.locate(JavaSourceSymbolKey.forType("com.example.Temp")).isPresent());
  }

  @Test
  @DisplayName("Duplicate source root registration is idempotent")
  void testDuplicateSourceRoots(@TempDir Path tempDir) throws IOException {
    Path srcMain = tempDir.resolve("src/main/java");
    Files.createDirectories(srcMain);

    WorkspaceJavaSourceLocator locator = new WorkspaceJavaSourceLocator();
    locator.addSourceRoot(srcMain);
    locator.addSourceRoot(srcMain);
    locator.addSourceRoot(srcMain.resolve("../java"));

    assertEquals(1, locator.sourceRoots().size());
  }
}
