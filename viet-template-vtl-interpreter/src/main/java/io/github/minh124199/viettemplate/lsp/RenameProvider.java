package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cross-language semantic rename and refactoring engine.
 *
 * <p>Enforces conservative JVM-backed rejection policies, strict identifier validation, AST scope
 * collision analysis, and atomic {@link WorkspaceEdit} construction.
 */
final class RenameProvider {

  private static final Set<String> VTL_RESERVED_KEYWORDS =
      Set.of(
          "if",
          "elseif",
          "else",
          "foreach",
          "end",
          "set",
          "macro",
          "define",
          "parse",
          "include",
          "evaluate",
          "stop",
          "break",
          "in",
          "true",
          "false",
          "null");

  private static final Pattern VTL_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

  private RenameProvider() {}

  public static Optional<PrepareRenameResult> prepareRename(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      WorkspaceReferenceIndex refIndex,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    CanonicalSchemaResolver resolver =
        schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      return Optional.empty();
    }
    if (parsed == null || parsed.hasErrors()) {
      return Optional.empty();
    }

    Optional<WorkspaceSymbolResolver.ResolvedCursorSymbol> cursorOpt =
        WorkspaceSymbolResolver.resolve(doc, position, resolver, policy);
    if (cursorOpt.isEmpty()) {
      return Optional.empty();
    }

    WorkspaceSymbolResolver.ResolvedCursorSymbol cursor = cursorOpt.get();

    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.JVM_MEMBER
        || cursor.symbolKey() instanceof JvmMemberSymbolKey) {
      return Optional.empty();
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.DYNAMIC) {
      return Optional.empty();
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.DENIED) {
      return Optional.empty();
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.METHOD) {
      return Optional.empty();
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.ROOT_PARAMETER
        || cursor.symbolKey() instanceof RootParameterSymbolKey) {
      return Optional.empty();
    }

    if (cursor.symbolKey() instanceof SchemaMemberSymbolKey schemaKey) {
      if (isUnsupportedSchemaFormat(schemaKey, resolver)) {
        return Optional.empty();
      }
    }

    return Optional.of(PrepareRenameResult.of(cursor.range(), cursor.placeholder()));
  }

  public static WorkspaceEdit rename(
      TemplateDocument doc,
      Position position,
      String newName,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      WorkspaceReferenceIndex refIndex,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(newName, "newName must not be null");
    CanonicalSchemaResolver resolver =
        schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    WorkspaceSchemaIndex sIndex =
        schemaIndex != null ? schemaIndex : new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex rIndex = refIndex != null ? refIndex : new WorkspaceReferenceIndex();
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    validateIdentifier(newName);

    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      throw new RenameConflictException(
          RenameConflictException.Reason.STALE_OR_MALFORMED_DOCUMENT,
          "Cannot rename in document with parse errors: " + doc.uri());
    }
    if (parsed == null || parsed.hasErrors()) {
      throw new RenameConflictException(
          RenameConflictException.Reason.STALE_OR_MALFORMED_DOCUMENT,
          "Cannot rename in document with syntax errors: " + doc.uri());
    }

    Optional<WorkspaceSymbolResolver.ResolvedCursorSymbol> cursorOpt =
        WorkspaceSymbolResolver.resolve(doc, position, resolver, policy);
    if (cursorOpt.isEmpty()) {
      throw new RenameConflictException(
          RenameConflictException.Reason.INVALID_IDENTIFIER,
          "No renameable symbol found at cursor position");
    }

    WorkspaceSymbolResolver.ResolvedCursorSymbol cursor = cursorOpt.get();

    // Eligibility checks
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.METHOD) {
      throw new RenameConflictException(
          RenameConflictException.Reason.UNSUPPORTED_METHOD,
          "Rename is not available for method invocations");
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.DENIED) {
      throw new RenameConflictException(
          RenameConflictException.Reason.UNSUPPORTED_DENIED_MEMBER,
          "Rename is not available for security-denied members");
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.DYNAMIC) {
      throw new RenameConflictException(
          RenameConflictException.Reason.UNSUPPORTED_DYNAMIC_SYMBOL,
          "Rename is not available for dynamic symbols");
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.ROOT_PARAMETER
        || cursor.symbolKey() instanceof RootParameterSymbolKey) {
      throw new RenameConflictException(
          RenameConflictException.Reason.UNSUPPORTED_ROOT_PARAMETER,
          "Rename is not available for root model parameters");
    }
    if (cursor.kind() == WorkspaceSymbolResolver.SymbolKind.JVM_MEMBER
        || cursor.symbolKey() instanceof JvmMemberSymbolKey) {
      throw new RenameConflictException(
          RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER,
          "Rename is not available for JVM-backed members because Java source refactoring is"
              + " outside Viet Template's ownership");
    }

    if (cursor.symbolKey() instanceof SchemaMemberSymbolKey schemaKey) {
      if (isJsonSchemaFormat(schemaKey, resolver)) {
        throw new RenameConflictException(
            RenameConflictException.Reason.UNSUPPORTED_SCHEMA_FORMAT,
            "Rename is not available for JSON Schema properties");
      }
      if (isUnsupportedSchemaFormat(schemaKey, resolver)) {
        throw new RenameConflictException(
            RenameConflictException.Reason.UNSUPPORTED_SCHEMA_FORMAT,
            "Rename is not supported for schema source: " + schemaKey.schemaSource());
      }
    }

    // Conflict analysis
    if (cursor.symbolKey() instanceof TemplateLocalSymbolKey localKey) {
      // 1. Root parameter shadowing
      Optional<CanonicalSchema> schemaOpt = resolver.resolveSchema(doc.uri());
      if (schemaOpt.isPresent() && schemaOpt.get().parameters().containsKey(newName)) {
        throw new RenameConflictException(
            RenameConflictException.Reason.NAME_COLLISION,
            "Variable '" + newName + "' would shadow root parameter '" + newName + "'");
      }

      // 2. Existing local declarations in document
      List<LocalDecl> allDecls = findAllLocalDeclarations(parsed.template().children());
      for (LocalDecl decl : allDecls) {
        if (decl.name().equals(newName)) {
          throw new RenameConflictException(
              RenameConflictException.Reason.NAME_COLLISION,
              "Variable '" + newName + "' is already defined in this scope");
        }
      }
    } else if (cursor.symbolKey() instanceof SchemaMemberSymbolKey schemaKey) {
      // Check if typeName already contains a property named newName
      boolean propertyExists = false;
      Optional<SchemaProvenance> prov =
          sIndex.getPropertyProvenance(schemaKey.schemaSource(), schemaKey.typeName(), newName);
      if (prov.isEmpty()) {
        prov = sIndex.getPropertyProvenance(doc.uri(), schemaKey.typeName(), newName);
      }
      if (prov.isPresent()) {
        propertyExists = true;
      } else {
        Optional<CanonicalSchema> schemaOpt = resolver.resolveSchema(schemaKey.schemaSource());
        if (schemaOpt.isEmpty()) {
          schemaOpt = resolver.resolveSchema(doc.uri());
        }
        if (schemaOpt.isPresent()) {
          TypeDef typeDef = schemaOpt.get().types().get(schemaKey.typeName());
          if (typeDef != null && typeDef.properties().containsKey(newName)) {
            propertyExists = true;
          }
        }
      }
      if (propertyExists) {
        throw new RenameConflictException(
            RenameConflictException.Reason.NAME_COLLISION,
            "Property '" + newName + "' already exists on type '" + schemaKey.typeName() + "'");
      }
    }

    Map<String, List<TextEdit>> changes = new HashMap<>();

    // Locate declaration edit
    if (cursor.symbolKey() instanceof TemplateLocalSymbolKey localKey) {
      int defStart = localKey.definitionStartOffset();
      int defEnd = localKey.definitionEndOffset();
      int nameStart = doc.content().indexOf(localKey.variableName(), defStart);
      if (nameStart < 0 || nameStart >= defEnd) {
        nameStart = defStart + 1;
      }
      int nameEnd = nameStart + localKey.variableName().length();
      Range declRange = Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
      changes
          .computeIfAbsent(doc.uri(), k -> new ArrayList<>())
          .add(TextEdit.of(declRange, newName));
    } else if (cursor.symbolKey() instanceof SchemaMemberSymbolKey schemaKey) {
      Optional<SchemaProvenance> provOpt =
          sIndex.getPropertyProvenance(doc.uri(), schemaKey.typeName(), schemaKey.propertyName());
      if (provOpt.isEmpty()) {
        provOpt =
            sIndex.getPropertyProvenance(
                schemaKey.schemaSource(), schemaKey.typeName(), schemaKey.propertyName());
      }
      if (provOpt.isEmpty() || provOpt.get().location().isEmpty()) {
        throw new RenameConflictException(
            RenameConflictException.Reason.DECLARATION_NOT_EDITABLE,
            "Declaration location not found for property: " + schemaKey.propertyName());
      }
      SchemaSourceLocation loc = provOpt.get().location().get();
      Range declRange =
          Range.of(
              loc.startLine() - 1, loc.startColumn() - 1, loc.endLine() - 1, loc.endColumn() - 1);

      if (loc.filePath() != null && Files.isRegularFile(loc.filePath())) {
        try {
          String text = Files.readString(loc.filePath(), StandardCharsets.UTF_8);
          TemplateDocument schemaDoc =
              new TemplateDocument(loc.filePath().toUri().toString(), 1, text);
          int sOffset = schemaDoc.positionToOffset(declRange.start());
          int eOffset = schemaDoc.positionToOffset(declRange.end());
          if (sOffset >= 0 && eOffset <= text.length() && eOffset > sOffset) {
            String textAtRange = text.substring(sOffset, eOffset);
            if (!textAtRange.equals(schemaKey.propertyName())) {
              throw new RenameConflictException(
                  RenameConflictException.Reason.DECLARATION_NOT_EDITABLE,
                  "Declaration text at "
                      + declRange
                      + " does not match '"
                      + schemaKey.propertyName()
                      + "'");
            }
          }
        } catch (IOException e) {
          throw new RenameConflictException(
              RenameConflictException.Reason.DECLARATION_NOT_EDITABLE,
              "Failed to read schema file: " + e.getMessage());
        }
        changes
            .computeIfAbsent(loc.filePath().toUri().toString(), k -> new ArrayList<>())
            .add(TextEdit.of(declRange, newName));
      } else {
        throw new RenameConflictException(
            RenameConflictException.Reason.DECLARATION_NOT_EDITABLE,
            "Declaration file is not accessible: " + loc.filePath());
      }
    }

    // Index template and query reference edits
    rIndex.indexTemplate(doc, resolver, policy);
    List<LocationInfo> refs = rIndex.findReferences(cursor.symbolKey(), false);
    for (LocationInfo refLoc : refs) {
      Range editRange = refLoc.range();
      if (cursor.symbolKey() instanceof TemplateLocalSymbolKey localKey) {
        if (refLoc.uri().equals(doc.uri())) {
          int start = doc.positionToOffset(editRange.start());
          int end = doc.positionToOffset(editRange.end());
          if (start >= 0 && end <= doc.content().length() && end > start) {
            String snippet = doc.content().substring(start, end);
            int idx = snippet.indexOf(localKey.variableName());
            if (idx >= 0) {
              int exactStart = start + idx;
              int exactEnd = exactStart + localKey.variableName().length();
              editRange =
                  Range.of(doc.offsetToPosition(exactStart), doc.offsetToPosition(exactEnd));
            }
          }
        }
      }
      changes
          .computeIfAbsent(refLoc.uri(), k -> new ArrayList<>())
          .add(TextEdit.of(editRange, newName));
    }

    return WorkspaceEdit.of(changes);
  }

  static void validateIdentifier(String name) {
    if (name == null || name.isBlank() || !VTL_IDENTIFIER_PATTERN.matcher(name).matches()) {
      throw new RenameConflictException(
          RenameConflictException.Reason.INVALID_IDENTIFIER,
          "Invalid identifier: '" + (name == null ? "" : name) + "'");
    }
    if (VTL_RESERVED_KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) {
      throw new RenameConflictException(
          RenameConflictException.Reason.INVALID_IDENTIFIER,
          "Identifier cannot be a reserved keyword: '" + name + "'");
    }
  }

  private static boolean isJsonSchemaFormat(
      SchemaMemberSymbolKey schemaKey, CanonicalSchemaResolver resolver) {
    String source = schemaKey.schemaSource();
    if (source.endsWith(".schema.json")) {
      return true;
    }
    Optional<CanonicalSchema> schema = resolver.resolveSchema(source);
    return schema.isPresent() && schema.get().format() == SchemaFormat.JSON_SCHEMA;
  }

  private static boolean isUnsupportedSchemaFormat(
      SchemaMemberSymbolKey schemaKey, CanonicalSchemaResolver resolver) {
    String source = schemaKey.schemaSource();
    if (source.endsWith(".d.ts") || source.endsWith(".contract")) {
      return false;
    }
    Optional<CanonicalSchema> schema = resolver.resolveSchema(source);
    if (schema.isPresent()) {
      SchemaFormat format = schema.get().format();
      return format != SchemaFormat.TYPESCRIPT && format != SchemaFormat.CONTRACT;
    }
    return true;
  }

  private record LocalDecl(String name, int startOffset, int endOffset) {}

  private static List<LocalDecl> findAllLocalDeclarations(List<VtlNode> nodes) {
    List<LocalDecl> list = new ArrayList<>();
    collectLocalDeclarations(nodes, list);
    return list;
  }

  private static void collectLocalDeclarations(List<VtlNode> nodes, List<LocalDecl> out) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          VtlReference ref = refTarget.reference();
          if (ref.steps().isEmpty()
              && !ref.rootName().isBlank()
              && setNode.target().span().isKnown()) {
            out.add(
                new LocalDecl(
                    ref.rootName(),
                    setNode.target().span().startOffset(),
                    setNode.target().span().endOffset()));
          }
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (!feNode.loopVariable().rootName().isBlank() && feNode.loopVariable().span().isKnown()) {
          out.add(
              new LocalDecl(
                  feNode.loopVariable().rootName(),
                  feNode.loopVariable().span().startOffset(),
                  feNode.loopVariable().span().endOffset()));
        }
        collectLocalDeclarations(feNode.body(), out);
        if (feNode.elseBody().isPresent()) {
          collectLocalDeclarations(feNode.elseBody().get(), out);
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          collectLocalDeclarations(branch.body(), out);
        }
        if (ifNode.elseBody().isPresent()) {
          collectLocalDeclarations(ifNode.elseBody().get(), out);
        }
      }
    }
  }
}
