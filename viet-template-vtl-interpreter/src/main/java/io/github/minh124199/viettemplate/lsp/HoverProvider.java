package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hover engine providing markdown type signatures, documentation, and schema provenance metadata.
 */
final class HoverProvider {

  private HoverProvider() {}

  public static Optional<HoverInfo> hover(
      TemplateDocument doc, Position position, CanonicalSchemaResolver schemaResolver) {
    return hover(doc, position, schemaResolver, new WorkspaceSchemaIndex(schemaResolver));
  }

  public static Optional<HoverInfo> hover(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");
    WorkspaceSchemaIndex index =
        schemaIndex != null ? schemaIndex : new WorkspaceSchemaIndex(schemaResolver);

    int offset = doc.positionToOffset(position);
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      return Optional.empty();
    }

    List<VtlNode> rootNodes = parsed.template().children();
    return findHover(rootNodes, rootNodes, offset, doc, schemaResolver, index);
  }

  private static Optional<HoverInfo> findHover(
      List<VtlNode> currentNodes,
      List<VtlNode> rootNodes,
      int offset,
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex) {
    if (currentNodes == null) {
      return Optional.empty();
    }

    for (VtlNode node : currentNodes) {
      if (!node.span().isKnown()
          || offset < node.span().startOffset()
          || offset > node.span().endOffset()) {
        continue;
      }

      if (node instanceof VtlReferenceOutputNode refOut) {
        return hoverReference(
            refOut.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target().span().isKnown()
            && setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()
            && setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          Range range =
              Range.of(
                  doc.offsetToPosition(setNode.target().span().startOffset()),
                  doc.offsetToPosition(setNode.target().span().endOffset()));
          return Optional.of(
              HoverInfo.of(
                  "**$"
                      + refTarget.reference().rootName()
                      + "**: `Object`\n\n*(template local variable)*",
                  range));
        }
        Optional<HoverInfo> valHover =
            findExpressionHover(
                setNode.value(), offset, doc, rootNodes, schemaResolver, schemaIndex);
        if (valHover.isPresent()) return valHover;
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().isKnown()
            && feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          Range range =
              Range.of(
                  doc.offsetToPosition(feNode.loopVariable().span().startOffset()),
                  doc.offsetToPosition(feNode.loopVariable().span().endOffset()));
          String typeName = inferLoopVariableTypeName(feNode, doc, schemaResolver);
          return Optional.of(
              HoverInfo.of(
                  "**$"
                      + feNode.loopVariable().rootName()
                      + "**: `"
                      + typeName
                      + "`\n\n*(loop item variable)*",
                  range));
        }
        Optional<HoverInfo> iterHover =
            findExpressionHover(
                feNode.iterable(), offset, doc, rootNodes, schemaResolver, schemaIndex);
        if (iterHover.isPresent()) return iterHover;

        Optional<HoverInfo> bodyHover =
            findHover(feNode.body(), rootNodes, offset, doc, schemaResolver, schemaIndex);
        if (bodyHover.isPresent()) return bodyHover;

        if (feNode.elseBody().isPresent()) {
          Optional<HoverInfo> elseHover =
              findHover(
                  feNode.elseBody().get(), rootNodes, offset, doc, schemaResolver, schemaIndex);
          if (elseHover.isPresent()) return elseHover;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          Optional<HoverInfo> condHover =
              findExpressionHover(
                  branch.condition(), offset, doc, rootNodes, schemaResolver, schemaIndex);
          if (condHover.isPresent()) return condHover;

          Optional<HoverInfo> bHover =
              findHover(branch.body(), rootNodes, offset, doc, schemaResolver, schemaIndex);
          if (bHover.isPresent()) return bHover;
        }
        if (ifNode.elseBody().isPresent()) {
          Optional<HoverInfo> elseHover =
              findHover(
                  ifNode.elseBody().get(), rootNodes, offset, doc, schemaResolver, schemaIndex);
          if (elseHover.isPresent()) return elseHover;
        }
      }
    }

    // Direct string check for directives if on keyword
    return checkDirectiveHover(doc, offset);
  }

  private static Optional<HoverInfo> findExpressionHover(
      VtlExpression expr,
      int offset,
      TemplateDocument doc,
      List<VtlNode> rootNodes,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex) {
    if (expr == null) return Optional.empty();

    if (expr instanceof VtlReferenceExpression refExpr) {
      if (refExpr.reference().span().isKnown()
          && refExpr.reference().span().startOffset() <= offset
          && offset <= refExpr.reference().span().endOffset()) {
        return hoverReference(
            refExpr.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex);
      }
    } else if (expr instanceof VtlBinaryExpression bin) {
      Optional<HoverInfo> left =
          findExpressionHover(bin.left(), offset, doc, rootNodes, schemaResolver, schemaIndex);
      if (left.isPresent()) return left;
      return findExpressionHover(bin.right(), offset, doc, rootNodes, schemaResolver, schemaIndex);
    } else if (expr instanceof VtlUnaryExpression un) {
      return findExpressionHover(un.operand(), offset, doc, rootNodes, schemaResolver, schemaIndex);
    } else if (expr instanceof VtlGroupedExpression grp) {
      return findExpressionHover(
          grp.expression(), offset, doc, rootNodes, schemaResolver, schemaIndex);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression e : list.elements()) {
        Optional<HoverInfo> h =
            findExpressionHover(e, offset, doc, rootNodes, schemaResolver, schemaIndex);
        if (h.isPresent()) return h;
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        Optional<HoverInfo> kh =
            findExpressionHover(entry.key(), offset, doc, rootNodes, schemaResolver, schemaIndex);
        if (kh.isPresent()) return kh;
        Optional<HoverInfo> vh =
            findExpressionHover(entry.value(), offset, doc, rootNodes, schemaResolver, schemaIndex);
        if (vh.isPresent()) return vh;
      }
    } else if (expr instanceof VtlRangeExpression range) {
      Optional<HoverInfo> sh =
          findExpressionHover(range.start(), offset, doc, rootNodes, schemaResolver, schemaIndex);
      if (sh.isPresent()) return sh;
      return findExpressionHover(range.end(), offset, doc, rootNodes, schemaResolver, schemaIndex);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          if (rp.reference().span().isKnown()
              && rp.reference().span().startOffset() <= offset
              && offset <= rp.reference().span().endOffset()) {
            return hoverReference(
                rp.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex);
          }
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<HoverInfo> hoverReference(
      VtlReference ref,
      int offset,
      TemplateDocument doc,
      List<VtlNode> rootNodes,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex) {
    // 1. Check steps (property access)
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
              StringBuilder md = new StringBuilder();
              md.append(
                  String.format(
                      "**%s**: `%s`%s\n\n*Property of %s*",
                      pDef.name(),
                      pDef.type().displayName(),
                      nullStr,
                      recType.get().displayName()));
              if (!pDef.documentation().isBlank()) {
                md.append("\n\n").append(pDef.documentation());
              }

              md.append("\n\n---\n");
              String recTypeName = recType.get().displayName();
              Optional<SchemaProvenance> propProv =
                  schemaIndex.getPropertyProvenance(doc.uri(), recTypeName, pDef.name());
              Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
              SchemaFormat fmt =
                  schema
                      .map(CanonicalSchema::format)
                      .orElse(propProv.map(SchemaProvenance::format).orElse(SchemaFormat.CONTRACT));
              md.append("* **Source**: ").append(formatSource(fmt, recTypeName)).append("\n");
              md.append("* **Property of**: `").append(recTypeName).append("`\n");
              if (propProv.flatMap(SchemaProvenance::location).isPresent()) {
                md.append("* **Definition**: `")
                    .append(propProv.flatMap(SchemaProvenance::location).get().format())
                    .append("`\n");
              } else if (schemaIndex.getAssociatedSchemaPath(doc.uri()).isPresent()) {
                Path pPath = schemaIndex.getAssociatedSchemaPath(doc.uri()).get();
                String fn =
                    pPath.getFileName() != null ? pPath.getFileName().toString() : pPath.toString();
                md.append("* **Definition**: `").append(fn).append("`\n");
              }
              md.append("* **Optional**: ").append(pDef.optional() ? "yes" : "no").append("\n");
              md.append("* **Nullable**: ").append(pDef.nullable() ? "yes" : "no").append("\n");
              boolean jvmBound =
                  propProv.map(SchemaProvenance::jvmBound).orElse(fmt == SchemaFormat.JAVA);
              Optional<String> memberName = propProv.flatMap(SchemaProvenance::jvmMemberName);
              if (jvmBound) {
                md.append("* **JVM Binding**: proven (")
                    .append(memberName.orElse(pDef.name()))
                    .append(")");
              } else {
                md.append("* **JVM Binding**: shape-only");
              }

              return Optional.of(HoverInfo.of(md.toString(), doc.spanToRange(step.span())));
            }
          }
        }
      }
    }

    // 2. Check root variable
    int rootStart = 0;
    if (ref.span().isKnown()) {
      int idx = doc.content().indexOf(ref.rootName(), Math.max(0, ref.span().startOffset()));
      rootStart = idx >= 0 ? idx : Math.max(0, ref.span().startOffset());
    } else {
      rootStart = doc.content().indexOf(ref.rootName());
      if (rootStart < 0) {
        rootStart = 0;
      }
    }
    int rootEnd = Math.min(doc.content().length(), rootStart + ref.rootName().length());
    Position startPos = doc.offsetToPosition(rootStart);
    Position endPos = doc.offsetToPosition(rootEnd);
    if (startPos.compareTo(endPos) > 0) {
      endPos = startPos;
    }
    Range range = Range.of(startPos, endPos);

    // Check schema parameter
    Optional<ParameterDef> param = schemaResolver.getParameter(doc.uri(), ref.rootName());
    if (param.isPresent()) {
      ParameterDef p = param.get();
      String reqStr = p.required() ? " *(required)*" : (p.nullable() ? " *(nullable)*" : "");
      StringBuilder md = new StringBuilder();
      md.append(String.format("**$%s**: `%s`%s", p.name(), p.type().displayName(), reqStr));
      if (!p.documentation().isBlank()) {
        md.append("\n\n").append(p.documentation());
      } else {
        md.append("\n\n*(parameter)*");
      }

      md.append("\n\n---\n");
      Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
      Optional<SchemaProvenance> prov = schemaIndex.getParameterProvenance(doc.uri(), p.name());
      SchemaFormat fmt =
          schema
              .map(CanonicalSchema::format)
              .orElse(prov.map(SchemaProvenance::format).orElse(SchemaFormat.CONTRACT));
      md.append("* **Source**: ").append(formatSource(fmt, p.type().displayName())).append("\n");
      md.append("* **Type**: `").append(p.type().displayName()).append("`\n");
      if (prov.flatMap(SchemaProvenance::location).isPresent()) {
        md.append("* **Definition**: `")
            .append(prov.flatMap(SchemaProvenance::location).get().format())
            .append("`\n");
      } else if (schemaIndex.getAssociatedSchemaPath(doc.uri()).isPresent()) {
        Path pPath = schemaIndex.getAssociatedSchemaPath(doc.uri()).get();
        String fn = pPath.getFileName() != null ? pPath.getFileName().toString() : pPath.toString();
        md.append("* **Definition**: `").append(fn).append("`\n");
      }
      md.append("* **Optional**: ").append(p.optional() ? "yes" : "no").append("\n");
      md.append("* **Nullable**: ").append(p.nullable() ? "yes" : "no").append("\n");
      boolean jvmBound = prov.map(SchemaProvenance::jvmBound).orElse(fmt == SchemaFormat.JAVA);
      Optional<String> memberName = prov.flatMap(SchemaProvenance::jvmMemberName);
      if (jvmBound) {
        md.append("* **JVM Binding**: proven (").append(memberName.orElse(p.name())).append(")");
      } else {
        md.append("* **JVM Binding**: shape-only");
      }

      return Optional.of(HoverInfo.of(md.toString(), range));
    }

    // Check enclosing foreach loop variable
    VtlForeachDirectiveNode fe = findEnclosingForeach(rootNodes, offset, ref.rootName());
    if (fe != null) {
      String typeName = inferLoopVariableTypeName(fe, doc, schemaResolver);
      String md =
          String.format("**$%s**: `%s`\n\n*(loop item variable)*", ref.rootName(), typeName);
      return Optional.of(HoverInfo.of(md, range));
    }

    // Check foreach loop metadata
    if ("foreach".equals(ref.rootName()) && isInsideForeach(rootNodes, offset)) {
      String md =
          "**$foreach**: `ForeachMetadata`\n\n"
              + "*(loop metadata: index, count, first, last, hasNext)*";
      return Optional.of(HoverInfo.of(md, range));
    }

    // Check preceding #set variable
    if (hasPrecedingSet(rootNodes, offset, ref.rootName())) {
      String md = String.format("**$%s**: `Object`\n\n*(template local variable)*", ref.rootName());
      return Optional.of(HoverInfo.of(md, range));
    }

    return Optional.empty();
  }

  private static String formatSource(SchemaFormat format, String typeName) {
    if (format == null) return "Schema";
    return switch (format) {
      case TYPESCRIPT -> "TypeScript schema <" + typeName + ">";
      case JSON_SCHEMA -> "JSON Schema";
      case JAVA -> "Java model";
      case CONTRACT -> "Contract";
    };
  }

  private static VtlForeachDirectiveNode findEnclosingForeach(
      List<VtlNode> nodes, int offset, String varName) {
    if (nodes == null) return null;
    for (VtlNode node : nodes) {
      if (node instanceof VtlForeachDirectiveNode fe) {
        if (fe.span().startOffset() <= offset && offset <= fe.span().endOffset()) {
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

  private static boolean isInsideForeach(List<VtlNode> nodes, int offset) {
    if (nodes == null) return false;
    for (VtlNode node : nodes) {
      if (node instanceof VtlForeachDirectiveNode fe) {
        if (fe.span().startOffset() <= offset && offset <= fe.span().endOffset()) {
          return true;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          if (isInsideForeach(branch.body(), offset)) return true;
        }
        if (ifNode.elseBody().isPresent() && isInsideForeach(ifNode.elseBody().get(), offset)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasPrecedingSet(List<VtlNode> nodes, int offset, String varName) {
    if (nodes == null) return false;
    for (VtlNode node : nodes) {
      if (!node.span().isKnown()) {
        continue;
      }
      if (node.span().startOffset() > offset) {
        break;
      }
      if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.span().isKnown()
            && setNode.span().endOffset() <= offset
            && setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget
            && refTarget.reference().rootName().equals(varName)) {
          return true;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          if (hasPrecedingSet(branch.body(), offset, varName)) return true;
        }
        if (ifNode.elseBody().isPresent()
            && hasPrecedingSet(ifNode.elseBody().get(), offset, varName)) {
          return true;
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (hasPrecedingSet(feNode.body(), offset, varName)) return true;
      }
    }
    return false;
  }

  static String inferLoopVariableTypeName(
      VtlForeachDirectiveNode feNode,
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver) {
    if (feNode.iterable() instanceof VtlReferenceExpression refExpr) {
      VtlReference iterRef = refExpr.reference();
      List<String> steps = new ArrayList<>();
      for (VtlAccessStep step : iterRef.steps()) {
        if (step instanceof VtlAccessStep.PropertyAccess p) {
          steps.add(p.propertyName());
        }
      }
      Optional<TypeRef> iterType =
          schemaResolver.resolveReceiverType(doc.uri(), iterRef.rootName(), steps);
      if (iterType.isPresent()) {
        if (iterType.get() instanceof ParameterizedTypeRef ptr && !ptr.arguments().isEmpty()) {
          return ptr.arguments().get(0).displayName();
        }
        if (iterType.get() instanceof ArrayTypeRef atr) {
          return atr.componentType().displayName();
        }
      }
    }
    return "Object";
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
      int end = Math.max(offset, start + 1);
      while (end < text.length() && Character.isLetter(text.charAt(end))) {
        end++;
      }
      if (end <= start + 1) {
        return Optional.empty();
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
