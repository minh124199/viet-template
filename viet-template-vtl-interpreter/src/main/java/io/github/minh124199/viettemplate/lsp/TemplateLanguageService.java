package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Reusable, protocol-independent Viet Template language service foundation.
 *
 * <p>Serves as the core engine powering completion, hover, definition, diagnostics, and document
 * state tracking. Usable standalone or adapted to LSP, CLI, or IDE environments.
 */
class TemplateLanguageService {

  private final TemplateDocumentStore documentStore;
  private final CanonicalSchemaResolver schemaResolver;
  private final WorkspaceSchemaIndex schemaIndex;
  private final WorkspaceReferenceIndex referenceIndex;
  private volatile MemberAccessPolicy memberAccessPolicy;
  private final List<Consumer<String>> diagnosticListeners = new CopyOnWriteArrayList<>();

  TemplateLanguageService(
      CanonicalSchemaResolver schemaResolver, MemberAccessPolicy memberAccessPolicy) {
    this(new TemplateDocumentStore(), schemaResolver, memberAccessPolicy);
  }

  TemplateLanguageService(
      TemplateDocumentStore documentStore,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy memberAccessPolicy) {
    this.documentStore = Objects.requireNonNull(documentStore, "documentStore must not be null");
    this.schemaResolver = Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");
    this.schemaIndex = new WorkspaceSchemaIndex(this.schemaResolver);
    this.referenceIndex = new WorkspaceReferenceIndex();
    this.memberAccessPolicy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();
  }

  public static TemplateLanguageService create() {
    return new TemplateLanguageService(
        new TemplateDocumentStore(), new CanonicalSchemaResolver(), MemberAccessPolicy.standard());
  }

  public static TemplateLanguageService create(MemberAccessPolicy accessPolicy) {
    return new TemplateLanguageService(
        new TemplateDocumentStore(), new CanonicalSchemaResolver(), accessPolicy);
  }

  public void addDiagnosticListener(Consumer<String> listener) {
    if (listener != null) {
      diagnosticListeners.add(listener);
    }
  }

  public void setDiagnosticListener(Consumer<String> listener) {
    diagnosticListeners.clear();
    if (listener != null) {
      diagnosticListeners.add(listener);
    }
  }

  public void notifyDiagnosticListeners(String uri) {
    for (Consumer<String> listener : diagnosticListeners) {
      listener.accept(uri);
    }
  }

  // --- Document Lifecycle ---

  public TemplateDocument openDocument(String uri, int version, String text) {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(text, "text must not be null");
    TemplateDocument doc = new TemplateDocument(uri, version, text);
    documentStore.put(doc);
    referenceIndex.indexTemplate(doc, schemaResolver, memberAccessPolicy);
    return doc;
  }

  public boolean updateDocumentIfNewer(String uri, int version, String text) {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(text, "text must not be null");
    TemplateDocument doc = new TemplateDocument(uri, version, text);
    boolean updated = documentStore.updateIfNewer(doc);
    if (updated) {
      referenceIndex.indexTemplate(doc, schemaResolver, memberAccessPolicy);
    }
    return updated;
  }

  public TemplateDocument updateDocument(String uri, int version, String text) {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(text, "text must not be null");
    TemplateDocument doc = new TemplateDocument(uri, version, text);
    documentStore.put(doc);
    referenceIndex.indexTemplate(doc, schemaResolver, memberAccessPolicy);
    return doc;
  }

  public void closeDocument(String uri) {
    if (uri != null) {
      documentStore.remove(uri);
      try {
        if (uri.startsWith("file:/")) {
          Path p = Path.of(URI.create(uri));
          if (Files.isRegularFile(p)) {
            String content = Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
            referenceIndex.indexTemplate(
                new TemplateDocument(uri, 1, content), schemaResolver, memberAccessPolicy);
          } else {
            referenceIndex.removeTemplate(uri);
          }
        } else {
          referenceIndex.removeTemplate(uri);
        }
      } catch (java.io.IOException
          | SecurityException
          | IllegalArgumentException
          | java.nio.file.FileSystemNotFoundException ignored) {
        referenceIndex.removeTemplate(uri);
      }
    }
  }

  public Optional<TemplateDocument> getDocument(String uri) {
    return documentStore.get(uri);
  }

  // --- File Watching & Incremental Invalidation ---

  public void onWatchedFileChanged(String uriOrPath, int changeType) {
    if (uriOrPath == null || uriOrPath.isBlank()) {
      return;
    }
    Path path;
    try {
      if (uriOrPath.startsWith("file:/")) {
        path = Path.of(URI.create(uriOrPath));
      } else {
        path = Path.of(uriOrPath);
      }
    } catch (IllegalArgumentException | SecurityException e) {
      return;
    }

    if (path.toString().endsWith(".java")) {
      if (changeType == 3) {
        schemaIndex.onJavaFileDeleted(path);
      } else {
        schemaIndex.onJavaFileChanged(path);
      }
      for (String openUri : documentStore.allUris()) {
        notifyDiagnosticListeners(openUri);
      }
      return;
    }

    String pathStr = path.toString().toLowerCase(Locale.ROOT);
    if (pathStr.endsWith(".vtl") || pathStr.endsWith(".vm") || pathStr.endsWith(".vt")) {
      String fileUri = path.toUri().toString();
      if (changeType == 3) {
        referenceIndex.removeTemplate(fileUri);
      } else {
        Optional<TemplateDocument> openDoc = documentStore.get(fileUri);
        if (openDoc.isPresent()) {
          referenceIndex.indexTemplate(openDoc.get(), schemaResolver, memberAccessPolicy);
        } else {
          try {
            if (Files.isRegularFile(path)) {
              String content = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
              referenceIndex.indexTemplate(
                  new TemplateDocument(fileUri, 1, content), schemaResolver, memberAccessPolicy);
            }
          } catch (java.io.IOException | SecurityException | IllegalArgumentException ignored) {
          }
        }
      }
    }

    List<String> affected;
    if (changeType == 3) {
      // 3 = Deleted
      affected = schemaIndex.onSchemaDeleted(path);
    } else {
      // 1 = Created, 2 = Changed
      affected = schemaIndex.onSchemaChanged(path);
    }

    for (String affectedUri : affected) {
      Optional<TemplateDocument> openDoc = documentStore.get(affectedUri);
      if (openDoc.isPresent()) {
        referenceIndex.indexTemplate(openDoc.get(), schemaResolver, memberAccessPolicy);
        notifyDiagnosticListeners(affectedUri);
      } else {
        try {
          if (affectedUri.startsWith("file:/")) {
            Path p = Path.of(URI.create(affectedUri));
            if (Files.isRegularFile(p)) {
              String content = Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
              referenceIndex.indexTemplate(
                  new TemplateDocument(affectedUri, 1, content),
                  schemaResolver,
                  memberAccessPolicy);
            }
          }
        } catch (java.io.IOException
            | SecurityException
            | IllegalArgumentException
            | java.nio.file.FileSystemNotFoundException ignored) {
        }
      }
    }
  }

  // --- Language Features ---

  public List<Diagnostic> diagnostics(String uri) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return List.of();
    }
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc.get(), schemaResolver, memberAccessPolicy);
    schemaResolver.getSchemaFilePath(uri).ifPresent(p -> schemaIndex.recordDependency(uri, p));
    return diags;
  }

  public CompletionList complete(String uri, Position position) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return CompletionList.empty();
    }
    return CompletionProvider.complete(doc.get(), position, schemaResolver, memberAccessPolicy);
  }

  public CompletionList completion(String uri, Position position) {
    return complete(uri, position);
  }

  public Optional<HoverInfo> hover(String uri, Position position) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return Optional.empty();
    }
    return HoverProvider.hover(doc.get(), position, schemaResolver, schemaIndex);
  }

  public List<LocationInfo> definition(String uri, Position position) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return List.of();
    }
    return DefinitionProvider.definition(
        doc.get(), position, schemaResolver, schemaIndex, memberAccessPolicy);
  }

  public List<LocationInfo> references(String uri, Position position, boolean includeDeclaration) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return List.of();
    }
    return ReferenceProvider.references(
        doc.get(),
        position,
        includeDeclaration,
        schemaResolver,
        schemaIndex,
        referenceIndex,
        memberAccessPolicy);
  }

  public Optional<PrepareRenameResult> prepareRename(String uri, Position position) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      return Optional.empty();
    }
    if (uri.startsWith("file:/")) {
      try {
        schemaIndex.javaSourceLocator().probeSourceRootsFor(Path.of(URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }
    return RenameProvider.prepareRename(
        doc.get(), position, schemaResolver, schemaIndex, referenceIndex, memberAccessPolicy);
  }

  public WorkspaceEdit rename(String uri, Position position, String newName) {
    Optional<TemplateDocument> doc = getDocument(uri);
    if (doc.isEmpty()) {
      throw new RenameConflictException(
          RenameConflictException.Reason.STALE_OR_MALFORMED_DOCUMENT, "Document not found: " + uri);
    }
    if (uri.startsWith("file:/")) {
      try {
        schemaIndex.javaSourceLocator().probeSourceRootsFor(Path.of(URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }
    return RenameProvider.rename(
        doc.get(),
        position,
        newName,
        schemaResolver,
        schemaIndex,
        referenceIndex,
        memberAccessPolicy);
  }

  public WorkspaceReferenceIndex referenceIndex() {
    return referenceIndex;
  }

  // --- Configuration ---

  public void registerSchema(String templateIdOrUri, String schemaJson) {
    schemaResolver.registerSchema(templateIdOrUri, schemaJson);
    schemaIndex.getOrComputeProvenance(templateIdOrUri);
  }

  public void registerSchemaFile(Path schemaPath) throws IOException {
    schemaResolver.registerSchemaFile(schemaPath);
    if (schemaPath != null) {
      schemaIndex.onSchemaChanged(schemaPath);
    }
  }

  public void setSchemaDirectory(Path directory) {
    schemaResolver.setSchemaDirectory(directory);
  }

  public void setMemberAccessPolicy(MemberAccessPolicy policy) {
    this.memberAccessPolicy = policy != null ? policy : MemberAccessPolicy.standard();
  }

  public MemberAccessPolicy memberAccessPolicy() {
    return memberAccessPolicy;
  }

  public CanonicalSchemaResolver schemaResolver() {
    return schemaResolver;
  }

  public WorkspaceSchemaIndex schemaIndex() {
    return schemaIndex;
  }

  public void setWorkspaceRoot(Path workspaceRoot) {
    if (workspaceRoot != null) {
      schemaIndex.javaSourceLocator().setWorkspaceRoot(workspaceRoot);
      referenceIndex.scanWorkspaceTemplates(workspaceRoot, schemaResolver, memberAccessPolicy);
    }
  }

  public void addSourceRoot(Path sourceRoot) {
    if (sourceRoot != null) {
      schemaIndex.javaSourceLocator().addSourceRoot(sourceRoot);
    }
  }

  public void probeSourceRootsFor(Path documentPath) {
    if (documentPath != null) {
      schemaIndex.javaSourceLocator().probeSourceRootsFor(documentPath);
    }
  }

  public WorkspaceJavaSourceLocator javaSourceLocator() {
    return schemaIndex.javaSourceLocator();
  }

  public TemplateDocumentStore documentStore() {
    return documentStore;
  }
}
