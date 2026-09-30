package io.github.minh124199.viettemplate.lsp;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // Normalize Windows file URI drive letters: file:///c:/ -> file:///c:/
    // and file:///C:/ -> file:///c:/
    if (normalized.startsWith("file:///") && normalized.length() >= 10) {
      char drive = normalized.charAt(8);
      char colon = normalized.charAt(9);
      if (colon == ':' && Character.isLetter(drive)) {
        normalized = "file:///" + Character.toLowerCase(drive) + normalized.substring(9);
      }
    }
    return normalized;
  }

  public void put(TemplateDocument document) {
    Objects.requireNonNull(document, "document must not be null");
    documents.put(normalizeUri(document.uri()), document);
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
