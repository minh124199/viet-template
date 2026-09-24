package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRepositoryTest {

  @Test
  void inMemoryRepositoryCrud() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("hello.vm");

    assertThat(repo.find(id)).isEmpty();

    repo.put(id, "Hello $name!");
    assertThat(repo.size()).isEqualTo(1);

    Optional<TemplateSource> found = repo.find(id);
    assertThat(found).isPresent();
    assertThat(found.get().content()).isEqualTo("Hello $name!");
    assertThat(found.get().fingerprint()).isNotBlank();
    assertThat(found.get().origin().getScheme()).isEqualTo("memory");

    repo.remove(id);
    assertThat(repo.find(id)).isEmpty();
    assertThat(repo.size()).isEqualTo(0);
  }

  @Test
  void filesystemRepositoryLoadsFileAndEnforcesConfinement(@TempDir Path tempDir)
      throws IOException {
    Path templatesDir = tempDir.resolve("templates");
    Files.createDirectories(templatesDir);
    Path testFile = templatesDir.resolve("user.vm");
    Files.writeString(testFile, "User Profile: $user");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(templatesDir);
    TemplateId id = TemplateId.of("user.vm");

    Optional<TemplateSource> source = repo.find(id);
    assertThat(source).isPresent();
    assertThat(source.get().content()).isEqualTo("User Profile: $user");
    assertThat(source.get().origin().getScheme()).isEqualTo("file");
    assertThat(source.get().lastModifiedEpochMillis()).isGreaterThan(0L);

    // Missing file
    assertThat(repo.find(TemplateId.of("nonexistent.vm"))).isEmpty();
  }

  @Test
  void filesystemRepositoryRejectsPathTraversalOutsideRoot(@TempDir Path tempDir)
      throws IOException {
    Path rootDir = tempDir.resolve("root");
    Files.createDirectories(rootDir);

    // External file outside root
    Path secretFile = tempDir.resolve("secret.txt");
    Files.writeString(secretFile, "confidential");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(rootDir);

    // Direct traversal with normalize
    assertThatThrownBy(() -> repo.find(TemplateId.normalize("../secret.txt")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void filesystemRepositoryThreatModelCoverage(@TempDir Path tempDir) throws IOException {
    Path rootDir = tempDir.resolve("root");
    Path outsideDir = tempDir.resolve("outside");
    Files.createDirectories(rootDir);
    Files.createDirectories(outsideDir);

    FilesystemTemplateRepository repoDefault = FilesystemTemplateRepository.of(rootDir);
    FilesystemTemplateRepository repoFollowSymlinks =
        FilesystemTemplateRepository.of(rootDir, java.nio.charset.StandardCharsets.UTF_8, true);

    // 1. ../ traversal
    assertThatThrownBy(() -> TemplateId.of("../secret.txt"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.normalize("../secret.txt"))
        .isInstanceOf(IllegalArgumentException.class);

    // 2. Absolute paths
    assertThatThrownBy(() -> TemplateId.of("/etc/passwd"))
        .isInstanceOf(IllegalArgumentException.class);

    // 3. Nested traversal
    assertThatThrownBy(() -> TemplateId.of("nested/../../secret.txt"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.normalize("nested/../../secret.txt"))
        .isInstanceOf(IllegalArgumentException.class);

    // 4. Null-byte input
    assertThatThrownBy(() -> TemplateId.of("template.vm\0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.normalize("template.vm\0"))
        .isInstanceOf(IllegalArgumentException.class);

    // 5. Normal nested templates
    Path nestedFile = rootDir.resolve("views").resolve("sub").resolve("page.vm");
    Files.createDirectories(nestedFile.getParent());
    Files.writeString(nestedFile, "nested content");
    Optional<TemplateSource> nestedSource = repoDefault.find(TemplateId.of("views/sub/page.vm"));
    assertThat(nestedSource).isPresent();
    assertThat(nestedSource.get().content()).isEqualTo("nested content");

    // Symlink tests (only if OS supports creating symlinks in test environment)
    Path outsideSecret = outsideDir.resolve("outside_secret.txt");
    Files.writeString(outsideSecret, "confidential outside");

    Path symlinkFile = rootDir.resolve("outside_link.vm");
    boolean symlinksSupported = true;
    try {
      Files.createSymbolicLink(symlinkFile, outsideSecret);
    } catch (UnsupportedOperationException | IOException e) {
      symlinksSupported = false;
    }

    if (symlinksSupported) {
      // 6. Symlink to file outside root (rejected in both default and follow-symlinks mode)
      assertThatThrownBy(() -> repoDefault.find(TemplateId.of("outside_link.vm")))
          .isInstanceOf(TemplateSecurityException.class);
      assertThatThrownBy(() -> repoFollowSymlinks.find(TemplateId.of("outside_link.vm")))
          .isInstanceOf(TemplateSecurityException.class);

      // 7. Symlinked directory outside root
      Path outsideSubDir = outsideDir.resolve("templates_leak");
      Files.createDirectories(outsideSubDir);
      Files.writeString(outsideSubDir.resolve("leaked.vm"), "leaked data");

      Path symlinkDir = rootDir.resolve("sym_dir");
      Files.createSymbolicLink(symlinkDir, outsideSubDir);

      assertThatThrownBy(() -> repoDefault.find(TemplateId.of("sym_dir/leaked.vm")))
          .isInstanceOf(TemplateSecurityException.class);
      assertThatThrownBy(() -> repoFollowSymlinks.find(TemplateId.of("sym_dir/leaked.vm")))
          .isInstanceOf(TemplateSecurityException.class);

      // 8. Normal symlink within root
      Path localTarget = rootDir.resolve("target.vm");
      Files.writeString(localTarget, "local target content");
      Path localLink = rootDir.resolve("alias.vm");
      Files.createSymbolicLink(localLink, localTarget);

      // Default (followSymlinks = false) rejects internal symlinks
      assertThatThrownBy(() -> repoDefault.find(TemplateId.of("alias.vm")))
          .isInstanceOf(TemplateSecurityException.class);

      // When followSymlinks = true, internal symlinks within root are permitted
      Optional<TemplateSource> localLinkSource = repoFollowSymlinks.find(TemplateId.of("alias.vm"));
      assertThat(localLinkSource).isPresent();
      assertThat(localLinkSource.get().content()).isEqualTo("local target content");
    }
  }

  @Test
  void compositeRepositoryMaintainsDeterministicPrecedence() {
    InMemoryTemplateRepository repo1 = InMemoryTemplateRepository.create();
    InMemoryTemplateRepository repo2 = InMemoryTemplateRepository.create();

    TemplateId sharedId = TemplateId.of("shared.vm");
    TemplateId uniqueId2 = TemplateId.of("unique2.vm");

    repo1.put(sharedId, "From Repo 1");
    repo2.put(sharedId, "From Repo 2");
    repo2.put(uniqueId2, "Unique 2");

    CompositeTemplateRepository composite = CompositeTemplateRepository.of(repo1, repo2);

    // Repo 1 has precedence over Repo 2
    Optional<TemplateSource> sharedSource = composite.find(sharedId);
    assertThat(sharedSource).isPresent();
    assertThat(sharedSource.get().content()).isEqualTo("From Repo 1");

    // Fallback to Repo 2 when Repo 1 misses
    Optional<TemplateSource> uniqueSource = composite.find(uniqueId2);
    assertThat(uniqueSource).isPresent();
    assertThat(uniqueSource.get().content()).isEqualTo("Unique 2");

    // Missing from both
    assertThat(composite.find(TemplateId.of("missing.vm"))).isEmpty();
  }

  @Test
  void classpathRepositoryResolvesResources() {
    // We test with a known resource or prefix
    ClasspathTemplateRepository repo = ClasspathTemplateRepository.of("test-templates");
    assertThat(repo.prefix()).isEqualTo("test-templates");
    assertThat(repo.find(TemplateId.of("nonexistent.vm"))).isEmpty();
  }

  @Test
  void classpathRepositoryFallbackLookup() {
    ClassLoader cl =
        new ClassLoader(ClasspathTemplateRepository.class.getClassLoader()) {
          @Override
          public java.net.URL getResource(String name) {
            if ("templates/hello.vtl".equals(name)) {
              return super.getResource(
                  "io/github/minh124199/viettemplate/api/TemplateRepository.class");
            }
            return null;
          }
        };
    ClasspathTemplateRepository repo = new ClasspathTemplateRepository(cl, "templates");
    // When looking up "templates/hello.vtl", direct lookup attempts "templates/templates/hello.vtl"
    // (null),
    // and fallback queries "templates/hello.vtl", which succeeds!
    Optional<TemplateSource> source = repo.find(TemplateId.of("templates/hello.vtl"));
    assertThat(source).isPresent();
    assertThat(source.get().origin().toString()).isEqualTo("classpath:/templates/hello.vtl");
  }

  @Test
  void classpathRepositoryReleasesFileHandlesWithoutRetention(@TempDir Path tempDir)
      throws IOException {
    Path jarPath = tempDir.resolve("templates-repo.jar");
    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
      JarEntry entry = new JarEntry("templates/sample.vm");
      jos.putNextEntry(entry);
      jos.write("sample content from jar".getBytes(StandardCharsets.UTF_8));
      jos.closeEntry();
    }

    URLClassLoader cl =
        new URLClassLoader(
            new URL[] {jarPath.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    ClasspathTemplateRepository repo = ClasspathTemplateRepository.of(cl, "templates");

    // 1. Resource loaded normally via URLClassLoader
    TemplateId templateId = TemplateId.of("sample.vm");
    Optional<TemplateSource> source = repo.find(templateId);
    assertThat(source).isPresent();
    assertThat(source.get().content()).isEqualTo("sample content from jar");
    assertThat(source.get().origin().getScheme()).isEqualTo("classpath");

    // 4. Repeated lookups do not retain handles
    for (int i = 0; i < 5; i++) {
      Optional<TemplateSource> repeated = repo.find(templateId);
      assertThat(repeated).isPresent();
      assertThat(repeated.get().content()).isEqualTo("sample content from jar");
    }

    // 5. Freshness-token lookup does not retain handles
    Optional<FreshnessToken> token = repo.freshnessToken(templateId);
    assertThat(token).contains(FreshnessToken.immutable());

    // 6. Prefix behavior preserved
    assertThat(repo.prefix()).isEqualTo("templates");
    Optional<TemplateSource> viaPrefix = repo.find(TemplateId.of("templates/sample.vm"));
    assertThat(viaPrefix).isPresent();
    assertThat(viaPrefix.get().content()).isEqualTo("sample content from jar");

    // 2. Repository/classloader closed
    cl.close();

    // 3. Temporary classpath file can be deleted immediately without retention
    Files.delete(jarPath);
    assertThat(Files.exists(jarPath)).isFalse();

    // Also verify with direct directory classpath
    Path dirPath = tempDir.resolve("dir-classpath");
    Path subDir = dirPath.resolve("views");
    Files.createDirectories(subDir);
    Path filePath = subDir.resolve("page.vm");
    Files.writeString(filePath, "direct directory content");

    URLClassLoader dirCl =
        new URLClassLoader(
            new URL[] {dirPath.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    ClasspathTemplateRepository dirRepo = ClasspathTemplateRepository.of(dirCl, "views");

    Optional<TemplateSource> dirSource = dirRepo.find(TemplateId.of("page.vm"));
    assertThat(dirSource).isPresent();
    assertThat(dirSource.get().content()).isEqualTo("direct directory content");

    for (int i = 0; i < 3; i++) {
      assertThat(dirRepo.find(TemplateId.of("page.vm"))).isPresent();
    }

    assertThat(dirRepo.freshnessToken(TemplateId.of("page.vm"))).isPresent();
    assertThat(dirRepo.find(TemplateId.of("views/page.vm"))).isPresent();

    dirCl.close();
    Files.delete(filePath);
    assertThat(Files.exists(filePath)).isFalse();
  }
}
