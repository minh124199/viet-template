package io.github.minh124199.viettemplate.lsp;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe in-memory store for active template documents managed by the language server.
 *
 * <p>Normalizes document URIs across Windows drive letters and Unix file paths to guarantee
 * deterministic document identity.
 */
final class TemplateDocumentStore {

  private final Map<String, TemplateDocument> documents = new ConcurrentHashMap<>();

  public static String normalizeUri(String uri) {
    if (uri == null || uri.isBlank()) {
      return "";
    }
    String normalized = uri.trim();
    // Normalize Windows file URI drive letters: file:///c:/ or file:/c:/
    if (normalized.startsWith("file:/")) {
      int driveIndex = -1;
      if (normalized.startsWith("file:///") && normalized.length() >= 10) {
        driveIndex = 8;
      } else if (!normalized.startsWith("file://") && normalized.length() >= 8) {
        driveIndex = 6;
      }
      if (driveIndex >= 0 && driveIndex + 1 < normalized.length()) {
        char drive = normalized.charAt(driveIndex);
        char colon = normalized.charAt(driveIndex + 1);
        if (colon == ':' && Character.isLetter(drive) && Character.isUpperCase(drive)) {
          normalized =
              normalized.substring(0, driveIndex)
                  + Character.toLowerCase(drive)
                  + normalized.substring(driveIndex + 1);
        }
      }
    }
    return normalized;
  }

  public void put(TemplateDocument document) {
    Objects.requireNonNull(document, "document must not be null");
    documents.put(normalizeUri(document.uri()), document);
  }

  public boolean updateIfNewer(TemplateDocument document) {
    Objects.requireNonNull(document, "document must not be null");
    String key = normalizeUri(document.uri());
    AtomicBoolean updated = new AtomicBoolean(false);
    documents.compute(
        key,
        (k, existing) -> {
          if (existing != null && existing.version() > document.version()) {
            return existing;
          }
          updated.set(true);
          return document;
        });
    return updated.get();
  }

  public Optional<TemplateDocument> get(String uri) {
    if (uri == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(documents.get(normalizeUri(uri)));
  }

  public Optional<TemplateDocument> remove(String uri) {
    if (uri == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(documents.remove(normalizeUri(uri)));
  }

  public Set<String> allUris() {
    return Set.copyOf(documents.keySet());
  }

  public int count() {
    return documents.size();
  }

  public void clear() {
    documents.clear();
  }
}
