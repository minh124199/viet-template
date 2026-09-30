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
    } catch (IllegalArgumentException | IllegalStateException e) {
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
        if (setNode.value() instanceof VtlReferenceExpression refExpr) {
          resolveReferenceDefinition(
              refExpr.reference(), offset, doc, rootNodes, schemaResolver, out);
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(feNode.loopVariable().span())));
          return;
        }
        findDefinitions(feNode.body(), rootNodes, offset, doc, schemaResolver, out);
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          findDefinitions(branch.body(), rootNodes, offset, doc, schemaResolver, out);
        }
      }
    }
  }

  private static boolean findInTemplateDefinition(
      List<VtlNode> nodes, String rootName, TemplateDocument doc, List<LocationInfo> out) {
    if (nodes == null) return false;
    for (VtlNode n : nodes) {
      if (n instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().rootName().equals(rootName)) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(feNode.loopVariable().span())));
          return true;
        }
        if (findInTemplateDefinition(feNode.body(), rootName, doc, out)) {
          return true;
        }
      } else if (n instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget
            && refTarget.reference().rootName().equals(rootName)) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(setNode.target().span())));
          return true;
        }
      } else if (n instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          if (findInTemplateDefinition(branch.body(), rootName, doc, out)) {
            return true;
          }
        }
      }
    }
    return false;
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

    // Check in-template loop variable definitions
    if (findInTemplateDefinition(rootNodes, rootName, doc, out)) {
      return;
    }

    // Check schema parameter definition
    Optional<SchemaEnvelope> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent() && schema.get().parameters().containsKey(rootName)) {
      Optional<Path> schemaFile = schemaResolver.getSchemaFilePath(doc.uri());
      if (schemaFile.isPresent()) {
        int line =
            CanonicalSchemaResolver.findDefinitionLine(schema.get().rawJson(), "name", rootName);
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
            : (recType.get() instanceof NamedTypeRef ntr ? ntr.name() : null);

    Optional<Path> schemaFile = schemaResolver.getSchemaFilePath(doc.uri());
    if (schemaFile.isPresent()) {
      int line =
          CanonicalSchemaResolver.findDefinitionLine(schema.get().rawJson(), "name", propertyName);
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
