package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Filesystem-backed {@link TemplateRepository} that loads template sources from an explicitly
 * configured directory root.
 *
 * <p>Enforces canonical path verification and root containment to harden against path traversal
 * ('..') and symlink directory escapes.
 *
 * <h2>Threat Model &amp; Concurrency Assumptions</h2>
 *
 * <p>This repository provides canonical-path confinement and symlink escape resistance by
 * normalizing candidate paths, verifying canonical paths ({@link Path#toRealPath}), and reading
 * directly from the verified canonical path.
 *
 * <p><b>Host Environment Assumption:</b> The configured template repository directory itself is
 * expected not to be writable by an untrusted user who is simultaneously racing template
 * resolution. While canonical-path confinement and check/read path consistency provide strong
 * symlink escape resistance, true race-free filesystem operations against hostile local users
 * require that the repository directory is not writable by untrusted concurrent actors.
 */
public final class FilesystemTemplateRepository implements TemplateRepository {

  private final Path rootDir;
  private final Charset charset;
  private final boolean followSymlinks;

  public FilesystemTemplateRepository(Path rootDir, Charset charset, boolean followSymlinks) {
    Objects.requireNonNull(rootDir, "rootDir must not be null");
    this.rootDir = rootDir.toAbsolutePath().normalize();
    this.charset = Objects.requireNonNull(charset, "charset must not be null");
    this.followSymlinks = followSymlinks;
  }

  public FilesystemTemplateRepository(Path rootDir) {
    this(rootDir, StandardCharsets.UTF_8, false);
  }

  public static FilesystemTemplateRepository of(Path rootDir) {
    return new FilesystemTemplateRepository(rootDir);
  }

  public static FilesystemTemplateRepository of(Path rootDir, Charset charset) {
    return new FilesystemTemplateRepository(rootDir, charset, false);
  }

  public static FilesystemTemplateRepository of(
      Path rootDir, Charset charset, boolean followSymlinks) {
    return new FilesystemTemplateRepository(rootDir, charset, followSymlinks);
  }

  @Override
  public Optional<TemplateSource> find(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");

    TemplateId normalized = TemplateId.normalize(id.value());
    Path candidate = rootDir.resolve(normalized.value()).normalize();

    // Confinement check 1: Candidate path must start with root directory
    if (!candidate.startsWith(rootDir)) {
      throw new TemplateSecurityException(
          "Path traversal outside root directory is forbidden: " + id.value(),
          id,
          SourceSpan.UNKNOWN);
    }

    if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
      return Optional.empty();
    }

    // Confinement check 2: Symlink policy enforcement
    if (!followSymlinks) {
      if (Files.isSymbolicLink(candidate)) {
        throw new TemplateSecurityException(
            "Symlinks are forbidden by repository configuration: " + id.value(),
            id,
            SourceSpan.UNKNOWN);
      }
      Path rel = rootDir.relativize(candidate);
      Path cur = rootDir;
      for (Path seg : rel) {
        cur = cur.resolve(seg);
        if (Files.isSymbolicLink(cur)) {
          throw new TemplateSecurityException(
              "Symlinks are forbidden by repository configuration: " + id.value(),
              id,
              SourceSpan.UNKNOWN);
        }
      }
    }

    // Confinement check 3: Canonical real-path verification against out-of-root escapes
    Path realCandidate;
    try {
      realCandidate = candidate.toRealPath();
      Path realRoot = rootDir.toRealPath();
      if (!realCandidate.startsWith(realRoot)) {
        throw new TemplateSecurityException(
            "Symlink directory escape outside root directory is forbidden: " + id.value(),
            id,
            SourceSpan.UNKNOWN);
      }
    } catch (IOException e) {
      return Optional.empty();
    }

    try {
      String content = Files.readString(realCandidate, charset);
      long lastModified = Files.getLastModifiedTime(realCandidate).toMillis();
      return Optional.of(TemplateSource.of(id, candidate.toUri(), charset, content, lastModified));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public Path rootDirectory() {
    return rootDir;
  }

  public Charset charset() {
    return charset;
  }

  public boolean followSymlinks() {
    return followSymlinks;
  }
}
