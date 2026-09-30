package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.lsp.CanonicalSchemaModel.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Hover engine providing markdown type signatures, documentation, and source ranges. */
final class HoverProvider {

  private HoverProvider() {}

  public static Optional<HoverInfo> hover(
      TemplateDocument doc, Position position, CanonicalSchemaResolver schemaResolver) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");

    int offset = doc.positionToOffset(position);
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Optional.empty();
    }

    return findHover(parsed.template().children(), offset, doc, schemaResolver);
  }

  private static Optional<HoverInfo> findHover(
      List<VtlNode> nodes,
      int offset,
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver) {
    if (nodes == null) {
      return Optional.empty();
    }

    for (VtlNode node : nodes) {
      if (!node.span().isKnown()
          || offset < node.span().startOffset()
          || offset > node.span().endOffset()) {
        continue;
      }

      if (node instanceof VtlReferenceOutputNode refOut) {
        return hoverReference(refOut.reference(), offset, doc, schemaResolver);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.value() instanceof VtlReferenceExpression refExpr) {
          Optional<HoverInfo> h = hoverReference(refExpr.reference(), offset, doc, schemaResolver);
          if (h.isPresent()) return h;
        }
        if (setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()
            && setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          Range range = doc.spanToRange(setNode.target().span());
          return Optional.of(
              HoverInfo.of(
                  "**$"
                      + refTarget.reference().rootName()
                      + "**: `Object`\n\n*(template local variable)*",
                  range));
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          Range range = doc.spanToRange(feNode.loopVariable().span());
          return Optional.of(
              HoverInfo.of(
                  "**$"
                      + feNode.loopVariable().rootName()
                      + "**: `Object`\n\n*(loop item variable)*",
                  range));
        }
        Optional<HoverInfo> bodyHover = findHover(feNode.body(), offset, doc, schemaResolver);
        if (bodyHover.isPresent()) return bodyHover;
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          Optional<HoverInfo> bHover = findHover(branch.body(), offset, doc, schemaResolver);
          if (bHover.isPresent()) return bHover;
        }
      }
    }

    // Direct string check for directives if on keyword
    return checkDirectiveHover(doc, offset);
  }

  private static Optional<HoverInfo> hoverReference(
      VtlReference ref, int offset, TemplateDocument doc, CanonicalSchemaResolver schemaResolver) {
    // 1. Check steps
    List<VtlAccessStep> steps = ref.steps();
    for (int i = 0; i < steps.size(); i++) {
      VtlAccessStep step = steps.get(i);
      if (step.span().startOffset() <= offset && offset <= step.span().endOffset()) {
        if (step instanceof VtlAccessStep.PropertyAccess prop) {
          List<String> precedingSteps = new ArrayList<>();
          for (int j = 0; j < i; j++) {
            if (steps.get(j) instanceof VtlAccessStep.PropertyAccess p) {
              precedingSteps.add(p.propertyName());
            }
          }
          Optional<TypeRef> recType =
              schemaResolver.resolveReceiverType(doc.uri(), ref.rootName(), precedingSteps);
          if (recType.isPresent()) {
            Map<String, PropertyDef> props =
                schemaResolver.getAccessibleProperties(doc.uri(), recType.get(), null);
            PropertyDef pDef = props.get(prop.propertyName());
            if (pDef != null) {
              String nullStr = pDef.nullable() ? " *(nullable)*" : "";
              String md =
                  String.format(
                      "**%s**: `%s`%s\n\n*Property of %s*",
                      pDef.name(), pDef.type().displayName(), nullStr, recType.get().displayName());
              return Optional.of(HoverInfo.of(md, doc.spanToRange(step.span())));
            }
          }
        }
      }
    }

    // 2. Check root
    Optional<ParameterDef> param = schemaResolver.getParameter(doc.uri(), ref.rootName());
    if (param.isPresent()) {
      ParameterDef p = param.get();
      String reqStr = p.required() ? " *(required)*" : (p.nullable() ? " *(nullable)*" : "");
      String docStr =
          !p.documentation().isBlank() ? "\n\n" + p.documentation() : "\n\n*(parameter)*";
      String md =
          String.format("**$%s**: `%s`%s%s", p.name(), p.type().displayName(), reqStr, docStr);
      // Span of root name
      int rootStart = doc.content().indexOf(ref.rootName(), ref.span().startOffset());
      if (rootStart < 0) {
        rootStart = ref.span().startOffset();
      }
      int rootEnd = rootStart + ref.rootName().length();
      Range range = doc.spanToRange(SourceSpan.of(rootStart, rootEnd, 1, 1, 1, 1));
      return Optional.of(HoverInfo.of(md, range));
    }

    return Optional.empty();
  }

  private static Optional<HoverInfo> checkDirectiveHover(TemplateDocument doc, int offset) {
    String text = doc.content();
    if (offset < 0 || offset >= text.length()) {
      return Optional.empty();
    }
    int start = offset;
    while (start >= 0 && Character.isLetter(text.charAt(start))) {
      start--;
    }
    if (start >= 0 && text.charAt(start) == '#') {
      int end = offset;
      while (end < text.length() && Character.isLetter(text.charAt(end))) {
        end++;
      }
      String dirName = text.substring(start + 1, end);
      String docMsg = getDirectiveDocumentation(dirName);
      if (docMsg != null) {
        Range range = Range.of(doc.offsetToPosition(start), doc.offsetToPosition(end));
        return Optional.of(HoverInfo.of(docMsg, range));
      }
    }
    return Optional.empty();
  }

  private static String getDirectiveDocumentation(String dir) {
    return switch (dir) {
      case "if" -> "**`#if`**: Conditional branching directive.";
      case "else" -> "**`#else`**: Fallback branch for an `#if` directive.";
      case "elseif" -> "**`#elseif`**: Alternative conditional branch for an `#if` directive.";
      case "end" ->
          "**`#end`**: Closes a block directive (`#if`, `#foreach`, `#macro`, `#define`).";
      case "foreach" -> "**`#foreach`**: Loop over a collection or iterable (loop).";
      case "set" -> "**`#set`**: Assigns a value to a template-scoped variable.";
      case "macro" -> "**`#macro`**: Defines a reusable template macro.";
      case "parse" -> "**`#parse`**: Evaluates and renders an external sub-template.";
      case "include" -> "**`#include`**: Inserts raw text of an external file without evaluation.";
      case "stop" -> "**`#stop`**: Halts template execution immediately.";
      case "break" -> "**`#break`**: Breaks out of the current `#foreach` loop.";
      case "define" -> "**`#define`**: Assigns an unrendered block of VTL code to a variable.";
      case "evaluate" -> "**`#evaluate`**: Dynamically evaluates a string expression as VTL.";
      default -> null;
    };
  }
}
