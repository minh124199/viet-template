package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.lsp.CanonicalSchemaModel.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition lookup engine navigating between template usages and schema/in-template declarations.
 *
 * <p>Never invents or fabricates non-existent paths; uses stable schema URIs or on-disk schema
 * files.
 */
final class DefinitionProvider {

  private DefinitionProvider() {}

  public static List<LocationInfo> definition(
      TemplateDocument doc, Position position, CanonicalSchemaResolver schemaResolver) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");

    int offset = doc.positionToOffset(position);
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      return List.of();
    }

    List<LocationInfo> locations = new ArrayList<>();
    List<VtlNode> rootNodes = parsed.template().children();
    findDefinitions(rootNodes, rootNodes, offset, doc, schemaResolver, locations);
    Collections.sort(locations);
    return List.copyOf(locations);
  }

  private static void findDefinitions(
      List<VtlNode> currentNodes,
      List<VtlNode> rootNodes,
      int offset,
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver,
      List<LocationInfo> out) {
    if (currentNodes == null) return;

    for (VtlNode node : currentNodes) {
      if (node instanceof VtlReferenceOutputNode refOut) {
        resolveReferenceDefinition(refOut.reference(), offset, doc, rootNodes, schemaResolver, out);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target().span().isKnown()
            && setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(setNode.target().span())));
          return;
        }
        resolveExpressionDefinition(setNode.value(), offset, doc, rootNodes, schemaResolver, out);
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().isKnown()
            && feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(feNode.loopVariable().span())));
          return;
        }
        resolveExpressionDefinition(feNode.iterable(), offset, doc, rootNodes, schemaResolver, out);
        findDefinitions(feNode.body(), rootNodes, offset, doc, schemaResolver, out);
        if (feNode.elseBody().isPresent()) {
          findDefinitions(feNode.elseBody().get(), rootNodes, offset, doc, schemaResolver, out);
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          resolveExpressionDefinition(
              branch.condition(), offset, doc, rootNodes, schemaResolver, out);
          findDefinitions(branch.body(), rootNodes, offset, doc, schemaResolver, out);
        }
        if (ifNode.elseBody().isPresent()) {
          findDefinitions(ifNode.elseBody().get(), rootNodes, offset, doc, schemaResolver, out);
        }
      }
    }
  }

  private static void resolveExpressionDefinition(
      VtlExpression expr,
      int offset,
      TemplateDocument doc,
      List<VtlNode> rootNodes,
      CanonicalSchemaResolver schemaResolver,
      List<LocationInfo> out) {
    if (expr == null || !out.isEmpty()) return;
    if (expr instanceof VtlReferenceExpression refExpr) {
      resolveReferenceDefinition(refExpr.reference(), offset, doc, rootNodes, schemaResolver, out);
    } else if (expr instanceof VtlBinaryExpression bin) {
      resolveExpressionDefinition(bin.left(), offset, doc, rootNodes, schemaResolver, out);
      resolveExpressionDefinition(bin.right(), offset, doc, rootNodes, schemaResolver, out);
    } else if (expr instanceof VtlUnaryExpression un) {
      resolveExpressionDefinition(un.operand(), offset, doc, rootNodes, schemaResolver, out);
    } else if (expr instanceof VtlGroupedExpression grp) {
      resolveExpressionDefinition(grp.expression(), offset, doc, rootNodes, schemaResolver, out);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression e : list.elements()) {
        resolveExpressionDefinition(e, offset, doc, rootNodes, schemaResolver, out);
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        resolveExpressionDefinition(entry.key(), offset, doc, rootNodes, schemaResolver, out);
        resolveExpressionDefinition(entry.value(), offset, doc, rootNodes, schemaResolver, out);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      resolveExpressionDefinition(range.start(), offset, doc, rootNodes, schemaResolver, out);
      resolveExpressionDefinition(range.end(), offset, doc, rootNodes, schemaResolver, out);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          resolveReferenceDefinition(rp.reference(), offset, doc, rootNodes, schemaResolver, out);
        }
      }
    }
  }

  private static boolean findInTemplateDefinition(
      List<VtlNode> rootNodes,
      int offset,
      String rootName,
      TemplateDocument doc,
      List<LocationInfo> out) {
    // 1. Check enclosing foreach loop variables
    VtlForeachDirectiveNode fe = findEnclosingForeach(rootNodes, offset, rootName);
    if (fe != null) {
      out.add(LocationInfo.of(doc.uri(), doc.spanToRange(fe.loopVariable().span())));
      return true;
    }

    // 2. Check preceding #set directives (nearest preceding assignment before offset)
    VtlSetDirectiveNode nearestSet = findPrecedingSet(rootNodes, offset, rootName);
    if (nearestSet != null) {
      out.add(LocationInfo.of(doc.uri(), doc.spanToRange(nearestSet.target().span())));
      return true;
    }

    return false;
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

  private static VtlSetDirectiveNode findPrecedingSet(
      List<VtlNode> nodes, int offset, String varName) {
    VtlSetDirectiveNode best = null;
    if (nodes == null) return null;
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
          best = setNode;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          VtlSetDirectiveNode inner = findPrecedingSet(branch.body(), offset, varName);
          if (inner != null
              && (best == null || inner.span().startOffset() > best.span().startOffset())) {
            best = inner;
          }
        }
        if (ifNode.elseBody().isPresent()) {
          VtlSetDirectiveNode inner = findPrecedingSet(ifNode.elseBody().get(), offset, varName);
          if (inner != null
              && (best == null || inner.span().startOffset() > best.span().startOffset())) {
            best = inner;
          }
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        VtlSetDirectiveNode inner = findPrecedingSet(feNode.body(), offset, varName);
        if (inner != null
            && (best == null || inner.span().startOffset() > best.span().startOffset())) {
          best = inner;
        }
      }
    }
    return best;
  }

  private static void resolveReferenceDefinition(
      VtlReference ref,
      int offset,
      TemplateDocument doc,
      List<VtlNode> rootNodes,
      CanonicalSchemaResolver schemaResolver,
      List<LocationInfo> out) {
    if (!ref.span().isKnown()
        || offset < ref.span().startOffset()
        || offset > ref.span().endOffset()) {
      return;
    }

    // 1. Check if on step (property access)
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
          resolvePropertyDefinition(
              doc, ref.rootName(), precedingSteps, prop.propertyName(), schemaResolver, out);
          return;
        }
      }
    }

    // 2. On root variable
    String rootName = ref.rootName();

    // Check in-template variable definitions respecting lexical scope
    if (findInTemplateDefinition(rootNodes, offset, rootName, doc, out)) {
      return;
    }

    // Check schema parameter definition
    Optional<SchemaEnvelope> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent() && schema.get().parameters().containsKey(rootName)) {
      Optional<Path> schemaFile = schemaResolver.getSchemaFilePath(doc.uri());
      if (schemaFile.isPresent()) {
        int line =
            CanonicalSchemaResolver.findParameterDefinitionLine(schema.get().rawJson(), rootName);
        out.add(LocationInfo.of(schemaFile.get().toUri().toString(), Range.of(line, 0, line, 0)));
      } else {
        String templateId =
            schema.get().templateId().isEmpty() ? doc.uri() : schema.get().templateId();
        out.add(
            LocationInfo.of(
                "schema://" + templateId + "#parameters/" + rootName, Range.of(0, 0, 0, 0)));
      }
    }
  }

  private static void resolvePropertyDefinition(
      TemplateDocument doc,
      String rootName,
      List<String> precedingSteps,
      String propertyName,
      CanonicalSchemaResolver schemaResolver,
      List<LocationInfo> out) {
    Optional<TypeRef> recType =
        schemaResolver.resolveReceiverType(doc.uri(), rootName, precedingSteps);
    if (recType.isEmpty()) {
      return;
    }
    Optional<SchemaEnvelope> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isEmpty()) {
      return;
    }

    String typeName =
        recType.get() instanceof ClassTypeRef ctr
            ? ctr.name()
            : (recType.get() instanceof NamedTypeRef ntr
                ? ntr.name()
                : (recType.get() instanceof ParameterizedTypeRef ptr ? ptr.rawType() : null));

    Optional<Path> schemaFile = schemaResolver.getSchemaFilePath(doc.uri());
    if (schemaFile.isPresent()) {
      int line =
          CanonicalSchemaResolver.findPropertyDefinitionLine(
              schema.get().rawJson(), typeName, propertyName);
      out.add(LocationInfo.of(schemaFile.get().toUri().toString(), Range.of(line, 0, line, 0)));
    } else {
      String templateId =
          schema.get().templateId().isEmpty() ? doc.uri() : schema.get().templateId();
      out.add(
          LocationInfo.of(
              "schema://" + templateId + "#types/" + typeName + "/properties/" + propertyName,
              Range.of(0, 0, 0, 0)));
    }
  }
}
