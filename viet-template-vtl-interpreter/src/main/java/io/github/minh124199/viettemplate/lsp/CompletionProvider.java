package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
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
 * <p>Enforces exact prefix prioritization, member security filtering, and deterministic sorting.
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
      return completeMembers(doc, offset, memberCtx, schemaResolver, policy);
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
      TemplateDocument doc,
      int offset,
      MemberCompletionContext ctx,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    Optional<TypeRef> receiverType =
        schemaResolver.resolveReceiverType(doc.uri(), ctx.rootName(), ctx.steps());
    if (receiverType.isEmpty()) {
      receiverType = resolveLoopVariableReceiverType(doc, offset, ctx, schemaResolver);
    }
    // Dynamic fallback: if receiver is unknown or dynamic, return empty (no invented properties)
    if (receiverType.isEmpty() || receiverType.get() instanceof DynamicTypeRef) {
      return CompletionList.empty();
    }

    Map<String, PropertyDef> properties =
        schemaResolver.getAccessibleProperties(doc.uri(), receiverType.get(), policy);
    List<CompletionItem> items = new ArrayList<>();
    String prefix = ctx.memberPrefix();

    for (PropertyDef prop : properties.values()) {
      if (prefix.isEmpty() || prop.name().startsWith(prefix)) {
        String detail =
            prop.type().displayName()
                + (prop.optional() ? "?" : "")
                + (prop.nullable() ? " | null" : "");
        String docMsg = "Property of " + receiverType.get().displayName();
        String sortText =
            prefix.isEmpty()
                ? prop.name()
                : (prop.name().startsWith(prefix) ? ("0_" + prop.name()) : ("1_" + prop.name()));
        items.add(
            new CompletionItem(prop.name(), CompletionItemKind.PROPERTY, detail, docMsg, sortText));
      }
    }

    Collections.sort(items);
    return CompletionList.of(items);
  }

  private static Optional<TypeRef> resolveLoopVariableReceiverType(
      TemplateDocument doc,
      int offset,
      MemberCompletionContext ctx,
      CanonicalSchemaResolver schemaResolver) {
    try {
      VtlParseResult parsed = VtlParser.parse(doc.sourceText());
      VtlForeachDirectiveNode feNode =
          findEnclosingForeach(parsed.template().children(), offset, ctx.rootName());
      if (feNode != null && feNode.iterable() instanceof VtlReferenceExpression refExpr) {
        VtlReference iterRef = refExpr.reference();
        List<String> iterSteps = new ArrayList<>();
        for (VtlAccessStep s : iterRef.steps()) {
          if (s instanceof VtlAccessStep.PropertyAccess p) {
            iterSteps.add(p.propertyName());
          }
        }
        Optional<TypeRef> iterType =
            schemaResolver.resolveReceiverType(doc.uri(), iterRef.rootName(), iterSteps);
        if (iterType.isPresent()) {
          TypeRef elemType = null;
          if (iterType.get() instanceof ParameterizedTypeRef ptr && !ptr.arguments().isEmpty()) {
            elemType = ptr.arguments().get(0);
          } else if (iterType.get() instanceof ArrayTypeRef atr) {
            elemType = atr.componentType();
          }
          if (elemType != null) {
            return schemaResolver.resolveChainedType(doc.uri(), elemType, ctx.steps());
          }
        }
      }
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ignored) {
    }
    return Optional.empty();
  }

  private static VtlForeachDirectiveNode findEnclosingForeach(
      List<VtlNode> nodes, int offset, String varName) {
    if (nodes == null) return null;
    for (VtlNode node : nodes) {
      if (node instanceof VtlForeachDirectiveNode fe) {
        if (fe.span().isKnown()
            && fe.span().startOffset() <= offset
            && offset <= fe.span().endOffset()) {
          VtlForeachDirectiveNode inner = findEnclosingForeach(fe.body(), offset, varName);
          if (inner != null) return inner;
          if (fe.loopVariable().rootName().equals(varName)) {
            return fe;
          }
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          VtlForeachDirectiveNode inner = findEnclosingForeach(branch.body(), offset, varName);
          if (inner != null) return inner;
        }
        if (ifNode.elseBody().isPresent()) {
          VtlForeachDirectiveNode inner =
              findEnclosingForeach(ifNode.elseBody().get(), offset, varName);
          if (inner != null) return inner;
        }
      }
    }
    return null;
  }

  private static CompletionList completeVariables(
      TemplateDocument doc, int offset, String prefix, CanonicalSchemaResolver schemaResolver) {
    Set<CompletionItem> items = new TreeSet<>();

    // 1. Schema parameters
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent()) {
      for (ParameterDef param : schema.get().parameters().values()) {
        if (prefix.isEmpty() || param.name().startsWith(prefix)) {
          String detail =
              param.type().displayName()
                  + (param.optional() ? "?" : "")
                  + (param.nullable() ? " | null" : "");
          String sortText =
              prefix.isEmpty()
                  ? param.name()
                  : (param.name().startsWith(prefix)
                      ? ("0_" + param.name())
                      : ("1_" + param.name()));
          items.add(
              new CompletionItem(
                  param.name(),
                  CompletionItemKind.VARIABLE,
                  detail,
                  "Declared template parameter",
                  sortText));
        }
      }
    }

    // 2. Local variables and loop variables from template AST
    try {
      VtlParseResult parsed = VtlParser.parse(doc.sourceText());
      collectLocalVariables(
          parsed.template().children(), doc, offset, prefix, schemaResolver, items);
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ignored) {
      // Best-effort local variable discovery
    }

    return CompletionList.of(new ArrayList<>(items));
  }

  private static void collectLocalVariables(
      List<VtlNode> nodes,
      TemplateDocument doc,
      int offset,
      String prefix,
      CanonicalSchemaResolver schemaResolver,
      Set<CompletionItem> items) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.span().isKnown()
            && setNode.span().startOffset() < offset
            && setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          String name = refTarget.reference().rootName();
          if (prefix.isEmpty() || name.startsWith(prefix)) {
            String sortText =
                prefix.isEmpty() ? name : (name.startsWith(prefix) ? ("0_" + name) : ("1_" + name));
            items.add(
                new CompletionItem(
                    name,
                    CompletionItemKind.VARIABLE,
                    "Object",
                    "Template local variable",
                    sortText));
          }
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.span().isKnown()
            && feNode.span().startOffset() <= offset
            && offset <= feNode.span().endOffset()) {
          String loopVar = feNode.loopVariable().rootName();
          if (prefix.isEmpty() || loopVar.startsWith(prefix)) {
            String typeName = HoverProvider.inferLoopVariableTypeName(feNode, doc, schemaResolver);
            String sortText =
                prefix.isEmpty()
                    ? loopVar
                    : (loopVar.startsWith(prefix) ? ("0_" + loopVar) : ("1_" + loopVar));
            items.add(
                new CompletionItem(
                    loopVar,
                    CompletionItemKind.VARIABLE,
                    typeName,
                    "Loop item variable",
                    sortText));
          }
          if (prefix.isEmpty() || "foreach".startsWith(prefix)) {
            String sortText =
                prefix.isEmpty()
                    ? "foreach"
                    : ("foreach".startsWith(prefix) ? "0_foreach" : "1_foreach");
            items.add(
                new CompletionItem(
                    "foreach",
                    CompletionItemKind.VARIABLE,
                    "ForeachMetadata",
                    "Foreach loop metadata (index, count, first, last, hasNext)",
                    sortText));
          }
        }
        collectLocalVariables(feNode.body(), doc, offset, prefix, schemaResolver, items);
      }
    }
  }

  private static CompletionList completeDirectives(String prefix) {
    List<CompletionItem> items = new ArrayList<>();
    for (String dir : DIRECTIVES) {
      if (prefix.isEmpty() || dir.startsWith(prefix)) {
        String sortText =
            prefix.isEmpty() ? dir : (dir.startsWith(prefix) ? ("0_" + dir) : ("1_" + dir));
        items.add(
            new CompletionItem(
                dir,
                CompletionItemKind.KEYWORD,
                "directive",
                "Viet Template #" + dir + " directive",
                sortText));
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
