package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
}
