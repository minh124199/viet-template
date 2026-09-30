package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAssignmentTarget;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlForeachDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlSetDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.lsp.CanonicalSchemaModel.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic code completion engine providing variable, property, and directive completions.
 *
 * <p>Enforces prefix filtering, member security filtering, and deterministic sorting.
 */
final class CompletionProvider {

  private static final List<String> DIRECTIVES =
      List.of(
          "break",
          "define",
          "else",
          "elseif",
          "end",
          "evaluate",
          "foreach",
          "if",
          "include",
          "macro",
          "parse",
          "set",
          "stop");

  private CompletionProvider() {}

  public static CompletionList complete(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");

    int offset = doc.positionToOffset(position);
    String content = doc.content();
    if (offset < 0 || offset > content.length()) {
      return CompletionList.empty();
    }

    // 1. Check if member access (e.g. ${user. or $user.name. or $user.na)
    MemberCompletionContext memberCtx = findMemberContext(content, offset);
    if (memberCtx != null) {
      return completeMembers(doc.uri(), memberCtx, schemaResolver, policy);
    }

    // 2. Check if variable access (e.g. $ or ${ or $us or ${us)
    VariableCompletionContext varCtx = findVariableContext(content, offset);
    if (varCtx != null) {
      return completeVariables(doc, offset, varCtx.prefix(), schemaResolver);
    }

    // 3. Check if directive completion (e.g. # or #i)
    DirectiveCompletionContext dirCtx = findDirectiveContext(content, offset);
    if (dirCtx != null) {
      return completeDirectives(dirCtx.prefix());
    }

    return CompletionList.empty();
  }

  private static CompletionList completeMembers(
      String uri,
      MemberCompletionContext ctx,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    Optional<TypeRef> receiverType =
        schemaResolver.resolveReceiverType(uri, ctx.rootName(), ctx.steps());
    if (receiverType.isEmpty()) {
      return CompletionList.empty();
    }

    Map<String, PropertyDef> properties =
        schemaResolver.getAccessibleProperties(uri, receiverType.get(), policy);
    List<CompletionItem> items = new ArrayList<>();
    String prefix = ctx.memberPrefix();

    for (PropertyDef prop : properties.values()) {
      if (prefix.isEmpty() || prop.name().startsWith(prefix)) {
        String detail = prop.type().displayName() + (prop.nullable() ? " (nullable)" : "");
        String doc = "Property of " + receiverType.get().displayName();
        items.add(
            new CompletionItem(prop.name(), CompletionItemKind.PROPERTY, detail, doc, prop.name()));
      }
    }

    Collections.sort(items);
    return CompletionList.of(items);
  }

  private static CompletionList completeVariables(
      TemplateDocument doc, int offset, String prefix, CanonicalSchemaResolver schemaResolver) {
    Set<CompletionItem> items = new TreeSet<>();

    // 1. Schema parameters
    Optional<SchemaEnvelope> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent()) {
      for (ParameterDef param : schema.get().parameters().values()) {
        if (prefix.isEmpty() || param.name().startsWith(prefix)) {
          String detail = param.type().displayName() + (param.nullable() ? " (nullable)" : "");
          items.add(
              new CompletionItem(
                  param.name(),
                  CompletionItemKind.VARIABLE,
                  detail,
                  "Declared template parameter",
                  param.name()));
        }
      }
    }

    // 2. Local variables and loop variables from template AST
    try {
      VtlParseResult parsed = VtlParser.parse(doc.sourceText());
      collectLocalVariables(parsed.template().children(), offset, prefix, items);
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // Best-effort local variable discovery
    }

    return CompletionList.of(new ArrayList<>(items));
  }

  private static void collectLocalVariables(
      List<VtlNode> nodes, int offset, String prefix, Set<CompletionItem> items) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.span().startOffset() < offset
            && setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          String name = refTarget.reference().rootName();
          if (prefix.isEmpty() || name.startsWith(prefix)) {
            items.add(
                new CompletionItem(
                    name, CompletionItemKind.VARIABLE, "Object", "Template local variable", name));
          }
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.span().startOffset() <= offset && offset <= feNode.span().endOffset()) {
          String loopVar = feNode.loopVariable().rootName();
          if (prefix.isEmpty() || loopVar.startsWith(prefix)) {
            items.add(
                new CompletionItem(
                    loopVar, CompletionItemKind.VARIABLE, "Object", "Loop item variable", loopVar));
          }
          if (prefix.isEmpty() || "foreach".startsWith(prefix)) {
            items.add(
                new CompletionItem(
                    "foreach",
                    CompletionItemKind.VARIABLE,
                    "ForeachMetadata",
                    "Foreach loop metadata (index, count, first, last, hasNext)",
                    "foreach"));
          }
        }
        collectLocalVariables(feNode.body(), offset, prefix, items);
      }
    }
  }

  private static CompletionList completeDirectives(String prefix) {
    List<CompletionItem> items = new ArrayList<>();
    for (String dir : DIRECTIVES) {
      if (prefix.isEmpty() || dir.startsWith(prefix)) {
        items.add(
            new CompletionItem(
                dir,
                CompletionItemKind.KEYWORD,
                "directive",
                "Viet Template #" + dir + " directive",
                dir));
      }
    }
    Collections.sort(items);
    return CompletionList.of(items);
  }

  // --- Context Parsers ---

  private static boolean isIdentifierChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
  }

  private record MemberCompletionContext(
      String rootName, List<String> steps, String memberPrefix) {}

  private static MemberCompletionContext findMemberContext(String text, int offset) {
    int i = offset - 1;
    // Collect member prefix
    while (i >= 0 && isIdentifierChar(text.charAt(i))) {
      i--;
    }
    String memberPrefix = text.substring(i + 1, offset);

    // Expect '.'
    if (i < 0 || text.charAt(i) != '.') {
      return null;
    }
    i--; // skip '.'

    // Collect steps and rootName backwards
    List<String> chain = new ArrayList<>();
    while (i >= 0) {
      int segEnd = i + 1;
      while (i >= 0 && isIdentifierChar(text.charAt(i))) {
        i--;
      }
      int segStart = i + 1;
      if (segStart == segEnd) {
        break;
      }
      chain.add(0, text.substring(segStart, segEnd));
      if (i >= 0 && text.charAt(i) == '.') {
        i--; // continue preceding step
      } else {
        break;
      }
    }

    if (chain.isEmpty()) {
      return null;
    }

    // Must be preceded by $, $!, ${, or $!{
    if (i >= 0 && text.charAt(i) == '{') {
      i--;
    }
    if (i >= 0 && text.charAt(i) == '!') {
      i--;
    }
    if (i < 0 || text.charAt(i) != '$') {
      return null;
    }

    String rootName = chain.get(0);
    List<String> steps = chain.subList(1, chain.size());
    return new MemberCompletionContext(rootName, steps, memberPrefix);
  }

  private record VariableCompletionContext(String prefix) {}

  private static VariableCompletionContext findVariableContext(String text, int offset) {
    int i = offset - 1;
    while (i >= 0 && isIdentifierChar(text.charAt(i))) {
      i--;
    }
    String prefix = text.substring(i + 1, offset);
    if (i >= 0 && text.charAt(i) == '{') {
      i--;
    }
    if (i >= 0 && text.charAt(i) == '!') {
      i--;
    }
    if (i >= 0 && text.charAt(i) == '$') {
      return new VariableCompletionContext(prefix);
    }
    return null;
  }

  private record DirectiveCompletionContext(String prefix) {}

  private static DirectiveCompletionContext findDirectiveContext(String text, int offset) {
    int i = offset - 1;
    while (i >= 0 && isIdentifierChar(text.charAt(i))) {
      i--;
    }
    String prefix = text.substring(i + 1, offset);
    if (i >= 0 && text.charAt(i) == '#') {
      return new DirectiveCompletionContext(prefix);
    }
    return null;
  }
}
