package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.internal.CanonicalModelSchemaConverter;
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

    int offset = doc.positionToOffset(position);
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      return List.of();
    }

    if (parsed == null || parsed.hasErrors()) {
      return List.of();
    }

    SemanticAnalysisResult analysis = null;
    try {
      Optional<CanonicalSchema> schemaOpt = resolver.resolveSchema(doc.uri());
      ModelSchema modelSchema = ModelSchema.empty();
      if (schemaOpt.isPresent()) {
        ClassLoader cl =
            resolver.classLoader().orElse(Thread.currentThread().getContextClassLoader());
        modelSchema = CanonicalModelSchemaConverter.toModelSchema(schemaOpt.get(), cl);
      }
      VtlSemanticOptions options =
          VtlSemanticOptions.builder()
              .profile(VtlProfile.VTL_CORE)
              .modelSchema(modelSchema)
              .memberAccessPolicy(policy)
              .typeCheckingMode(TypeCheckingMode.WARN)
              .allowArbitraryMethods(true)
              .build();
      analysis = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    } catch (IllegalArgumentException
        | IllegalStateException
        | UnsupportedOperationException ignored) {
    }

    List<VtlNode> rootNodes = parsed.template().children();
    WorkspaceSymbolKey targetSymbolKey =
        findSymbolAtOffset(rootNodes, rootNodes, offset, doc, analysis, resolver, policy);

    if (targetSymbolKey == null) {
      return List.of();
    }

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

  private static WorkspaceSymbolKey findSymbolAtOffset(
      List<VtlNode> currentNodes,
      List<VtlNode> rootNodes,
      int offset,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    if (currentNodes == null) return null;

    for (VtlNode node : currentNodes) {
      if (!node.span().isKnown()) continue;

      if (node instanceof VtlReferenceOutputNode refOut) {
        if (refOut.reference().span().isKnown()
            && refOut.reference().span().startOffset() <= offset
            && offset <= refOut.reference().span().endOffset()) {
          return resolveReferenceSymbolKey(
              refOut.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        }
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target().span().isKnown()
            && setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()) {
          if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
            VtlReference ref = refTarget.reference();
            if (ref.steps().isEmpty()) {
              return WorkspaceSymbolKey.templateLocal(
                  doc.uri(),
                  ref.rootName(),
                  setNode.target().span().startOffset(),
                  setNode.target().span().endOffset());
            } else {
              return resolveReferenceSymbolKey(
                  ref, offset, rootNodes, doc, analysis, schemaResolver, policy);
            }
          }
        }
        WorkspaceSymbolKey valKey =
            findSymbolInExpression(
                setNode.value(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (valKey != null) return valKey;
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().isKnown()
            && feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          return WorkspaceSymbolKey.templateLocal(
              doc.uri(),
              feNode.loopVariable().rootName(),
              feNode.loopVariable().span().startOffset(),
              feNode.loopVariable().span().endOffset());
        }
        WorkspaceSymbolKey iterKey =
            findSymbolInExpression(
                feNode.iterable(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (iterKey != null) return iterKey;

        WorkspaceSymbolKey bodyKey =
            findSymbolAtOffset(
                feNode.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
        if (bodyKey != null) return bodyKey;

        if (feNode.elseBody().isPresent()) {
          WorkspaceSymbolKey elseKey =
              findSymbolAtOffset(
                  feNode.elseBody().get(),
                  rootNodes,
                  offset,
                  doc,
                  analysis,
                  schemaResolver,
                  policy);
          if (elseKey != null) return elseKey;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          WorkspaceSymbolKey condKey =
              findSymbolInExpression(
                  branch.condition(), offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (condKey != null) return condKey;

          WorkspaceSymbolKey bodyKey =
              findSymbolAtOffset(
                  branch.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
          if (bodyKey != null) return bodyKey;
        }
        if (ifNode.elseBody().isPresent()) {
          WorkspaceSymbolKey elseKey =
              findSymbolAtOffset(
                  ifNode.elseBody().get(),
                  rootNodes,
                  offset,
                  doc,
                  analysis,
                  schemaResolver,
                  policy);
          if (elseKey != null) return elseKey;
        }
      } else if (node instanceof VtlDirectiveCallNode dirCall) {
        for (VtlExpression arg : dirCall.arguments()) {
          WorkspaceSymbolKey argKey =
              findSymbolInExpression(arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (argKey != null) return argKey;
        }
      } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
        for (VtlExpression arg : blockCall.arguments()) {
          WorkspaceSymbolKey argKey =
              findSymbolInExpression(arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (argKey != null) return argKey;
        }
        WorkspaceSymbolKey bodyKey =
            findSymbolAtOffset(
                blockCall.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
        if (bodyKey != null) return bodyKey;
      }
    }
    return null;
  }

  private static WorkspaceSymbolKey findSymbolInExpression(
      VtlExpression expr,
      int offset,
      List<VtlNode> rootNodes,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    if (expr == null) return null;

    if (expr instanceof VtlReferenceExpression refExpr) {
      if (refExpr.reference().span().isKnown()
          && refExpr.reference().span().startOffset() <= offset
          && offset <= refExpr.reference().span().endOffset()) {
        return resolveReferenceSymbolKey(
            refExpr.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
      }
    } else if (expr instanceof VtlBinaryExpression bin) {
      WorkspaceSymbolKey l =
          findSymbolInExpression(
              bin.left(), offset, rootNodes, doc, analysis, schemaResolver, policy);
      if (l != null) return l;
      return findSymbolInExpression(
          bin.right(), offset, rootNodes, doc, analysis, schemaResolver, policy);
    } else if (expr instanceof VtlUnaryExpression un) {
      return findSymbolInExpression(
          un.operand(), offset, rootNodes, doc, analysis, schemaResolver, policy);
    } else if (expr instanceof VtlGroupedExpression grp) {
      return findSymbolInExpression(
          grp.expression(), offset, rootNodes, doc, analysis, schemaResolver, policy);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression e : list.elements()) {
        WorkspaceSymbolKey k =
            findSymbolInExpression(e, offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (k != null) return k;
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        WorkspaceSymbolKey k =
            findSymbolInExpression(
                entry.key(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (k != null) return k;
        WorkspaceSymbolKey v =
            findSymbolInExpression(
                entry.value(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (v != null) return v;
      }
    } else if (expr instanceof VtlRangeExpression range) {
      WorkspaceSymbolKey s =
          findSymbolInExpression(
              range.start(), offset, rootNodes, doc, analysis, schemaResolver, policy);
      if (s != null) return s;
      return findSymbolInExpression(
          range.end(), offset, rootNodes, doc, analysis, schemaResolver, policy);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          if (rp.reference().span().isKnown()
              && rp.reference().span().startOffset() <= offset
              && offset <= rp.reference().span().endOffset()) {
            return resolveReferenceSymbolKey(
                rp.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
          }
        }
      }
    }
    return null;
  }

  private static WorkspaceSymbolKey resolveReferenceSymbolKey(
      VtlReference ref,
      int offset,
      List<VtlNode> rootNodes,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {

    // 1. Check if cursor is on any step
    List<VtlAccessStep> steps = ref.steps();
    for (int i = 0; i < steps.size(); i++) {
      VtlAccessStep step = steps.get(i);
      if (step.span().isKnown()
          && step.span().startOffset() <= offset
          && offset <= step.span().endOffset()) {
        if (step instanceof VtlAccessStep.PropertyAccess prop) {
          return WorkspaceReferenceIndex.resolvePropertySymbolKey(
              doc, ref, prop, i, analysis, schemaResolver, policy);
        } else if (step instanceof VtlAccessStep.MethodCall call) {
          // Check if cursor is on method name vs argument expression
          int dotOffset = call.span().startOffset();
          int nameStart = doc.content().indexOf(call.methodName(), dotOffset);
          if (nameStart < 0 || nameStart >= call.span().endOffset()) {
            nameStart = dotOffset + 1;
          }
          int nameEnd = nameStart + call.methodName().length();
          if (offset <= nameEnd) {
            return WorkspaceReferenceIndex.resolveMethodSymbolKey(call, analysis);
          }
          // Inside arguments
          for (VtlExpression arg : call.arguments()) {
            WorkspaceSymbolKey argKey =
                findSymbolInExpression(
                    arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
            if (argKey != null) return argKey;
          }
          return WorkspaceReferenceIndex.resolveMethodSymbolKey(call, analysis);
        } else if (step instanceof VtlAccessStep.IndexAccess idx) {
          return findSymbolInExpression(
              idx.indexExpression(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        }
      }
    }

    if (ref.alternateValue().isPresent()) {
      VtlExpression alt = ref.alternateValue().get();
      if (alt.span().isKnown()
          && alt.span().startOffset() <= offset
          && offset <= alt.span().endOffset()) {
        return findSymbolInExpression(
            alt, offset, rootNodes, doc, analysis, schemaResolver, policy);
      }
    }

    // 2. Cursor is on root variable
    String rootName = ref.rootName();
    if (rootName.isBlank()) {
      return null;
    }

    int refOffset = ref.span().startOffset();

    // Check lexical scope for local variable
    VtlForeachDirectiveNode fe = findEnclosingForeach(rootNodes, refOffset, rootName);
    if (fe != null && fe.loopVariable().span().isKnown()) {
      return WorkspaceSymbolKey.templateLocal(
          doc.uri(),
          rootName,
          fe.loopVariable().span().startOffset(),
          fe.loopVariable().span().endOffset());
    }

    VtlSetDirectiveNode set = findPrecedingSet(rootNodes, refOffset, rootName);
    if (set != null && set.target().span().isKnown()) {
      return WorkspaceSymbolKey.templateLocal(
          doc.uri(), rootName, set.target().span().startOffset(), set.target().span().endOffset());
    }

    // Root model parameter
    Optional<CanonicalSchema> schemaOpt = schemaResolver.resolveSchema(doc.uri());
    String schemaSource;
    if (schemaOpt.isPresent() && schemaOpt.get().parameters().containsKey(rootName)) {
      schemaSource =
          WorkspaceReferenceIndex.getEffectiveSchemaSource(
              doc.uri(), schemaOpt.get(), schemaResolver);
    } else {
      schemaSource = doc.uri();
    }
    return WorkspaceSymbolKey.rootParameter(schemaSource, rootName);
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
      if (!node.span().isKnown()) continue;
      if (node.span().startOffset() > offset) break;
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
}
