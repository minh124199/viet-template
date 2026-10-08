package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

/**
 * Cross-language Find References provider resolving all template usages of a semantic symbol.
 *
 * <p>Identifies the target symbol under the cursor with strict semantic precision (zero
 * string-matching false positives) and queries the workspace reference graph. When {@code
 * includeDeclaration} is {@code true}, declarations discovered across Java, schemas, contracts, and
 * in-template definitions are prepended to the returned reference set.
 */
final class ReferenceProvider {

  private ReferenceProvider() {}

  public static List<LocationInfo> references(
      TemplateDocument doc,
      Position position,
      boolean includeDeclaration,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      WorkspaceReferenceIndex refIndex,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    CanonicalSchemaResolver resolver =
        schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    WorkspaceSchemaIndex sIndex =
        schemaIndex != null ? schemaIndex : new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex rIndex = refIndex != null ? refIndex : new WorkspaceReferenceIndex();
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    if (doc.uri().startsWith("file:/")) {
      try {
        sIndex.javaSourceLocator().probeSourceRootsFor(Path.of(URI.create(doc.uri())));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }

    Optional<WorkspaceSymbolResolver.ResolvedCursorSymbol> cursorOpt =
        WorkspaceSymbolResolver.resolve(doc, position, resolver, policy);
    if (cursorOpt.isEmpty() || cursorOpt.get().symbolKey() == null) {
      return List.of();
    }

    WorkspaceSymbolKey targetSymbolKey = cursorOpt.get().symbolKey();

    // Query index
    List<LocationInfo> locations =
        new ArrayList<>(rIndex.findReferences(targetSymbolKey, includeDeclaration));

    if (includeDeclaration) {
      List<LocationInfo> decls =
          DefinitionProvider.definition(doc, position, resolver, sIndex, policy);
      for (LocationInfo decl : decls) {
        if (!locations.contains(decl)) {
          locations.add(decl);
        }
      }
    }

    Collections.sort(locations);
    return List.copyOf(new LinkedHashSet<>(locations));
  }
}
