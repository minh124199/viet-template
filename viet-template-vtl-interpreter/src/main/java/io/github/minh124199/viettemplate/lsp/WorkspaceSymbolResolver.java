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
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.internal.CanonicalModelSchemaConverter;
import java.lang.reflect.Member;
import java.util.*;

/**
 * Shared AST and semantic resolution engine discovering the target symbol, exact token range, and
 * semantic classification at a given cursor offset.
 */
final class WorkspaceSymbolResolver {

  enum SymbolKind {
    TEMPLATE_LOCAL,
    SCHEMA_MEMBER,
    JVM_MEMBER,
    METHOD,
    ROOT_PARAMETER,
    DYNAMIC,
    DENIED,
    UNKNOWN
  }

  record ResolvedCursorSymbol(
      WorkspaceSymbolKey symbolKey, Range range, String placeholder, SymbolKind kind) {
    ResolvedCursorSymbol {
      Objects.requireNonNull(range, "range must not be null");
      Objects.requireNonNull(placeholder, "placeholder must not be null");
      Objects.requireNonNull(kind, "kind must not be null");
    }
  }

  private WorkspaceSymbolResolver() {}

  public static Optional<ResolvedCursorSymbol> resolve(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    CanonicalSchemaResolver resolver =
        schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    int offset = doc.positionToOffset(position);
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      return Optional.empty();
    }

    if (parsed == null || parsed.hasErrors()) {
      return Optional.empty();
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
    return Optional.ofNullable(
        findSymbolAtOffset(rootNodes, rootNodes, offset, doc, analysis, resolver, policy));
  }

  private static ResolvedCursorSymbol findSymbolAtOffset(
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
          return resolveReferenceSymbol(
              refOut.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        }
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target().span().isKnown()
            && setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()) {
          if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
            VtlReference ref = refTarget.reference();
            if (ref.steps().isEmpty()) {
              String varName = ref.rootName();
              int nameStart = doc.content().indexOf(varName, setNode.target().span().startOffset());
              if (nameStart < 0 || nameStart >= setNode.target().span().endOffset()) {
                nameStart = setNode.target().span().startOffset() + 1;
              }
              int nameEnd = nameStart + varName.length();
              Range range =
                  Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
              WorkspaceSymbolKey key =
                  WorkspaceSymbolKey.templateLocal(
                      doc.uri(),
                      varName,
                      setNode.target().span().startOffset(),
                      setNode.target().span().endOffset());
              return new ResolvedCursorSymbol(key, range, varName, SymbolKind.TEMPLATE_LOCAL);
            } else {
              return resolveReferenceSymbol(
                  ref, offset, rootNodes, doc, analysis, schemaResolver, policy);
            }
          }
        }
        ResolvedCursorSymbol valSym =
            findSymbolInExpression(
                setNode.value(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (valSym != null) return valSym;
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().isKnown()
            && feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          String varName = feNode.loopVariable().rootName();
          int nameStart =
              doc.content().indexOf(varName, feNode.loopVariable().span().startOffset());
          if (nameStart < 0 || nameStart >= feNode.loopVariable().span().endOffset()) {
            nameStart = feNode.loopVariable().span().startOffset() + 1;
          }
          int nameEnd = nameStart + varName.length();
          Range range = Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
          WorkspaceSymbolKey key =
              WorkspaceSymbolKey.templateLocal(
                  doc.uri(),
                  varName,
                  feNode.loopVariable().span().startOffset(),
                  feNode.loopVariable().span().endOffset());
          return new ResolvedCursorSymbol(key, range, varName, SymbolKind.TEMPLATE_LOCAL);
        }
        ResolvedCursorSymbol iterSym =
            findSymbolInExpression(
                feNode.iterable(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (iterSym != null) return iterSym;

        ResolvedCursorSymbol bodySym =
            findSymbolAtOffset(
                feNode.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
        if (bodySym != null) return bodySym;

        if (feNode.elseBody().isPresent()) {
          ResolvedCursorSymbol elseSym =
              findSymbolAtOffset(
                  feNode.elseBody().get(),
                  rootNodes,
                  offset,
                  doc,
                  analysis,
                  schemaResolver,
                  policy);
          if (elseSym != null) return elseSym;
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          ResolvedCursorSymbol condSym =
              findSymbolInExpression(
                  branch.condition(), offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (condSym != null) return condSym;

          ResolvedCursorSymbol bodySym =
              findSymbolAtOffset(
                  branch.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
          if (bodySym != null) return bodySym;
        }
        if (ifNode.elseBody().isPresent()) {
          ResolvedCursorSymbol elseSym =
              findSymbolAtOffset(
                  ifNode.elseBody().get(),
                  rootNodes,
                  offset,
                  doc,
                  analysis,
                  schemaResolver,
                  policy);
          if (elseSym != null) return elseSym;
        }
      } else if (node instanceof VtlDirectiveCallNode dirCall) {
        for (VtlExpression arg : dirCall.arguments()) {
          ResolvedCursorSymbol argSym =
              findSymbolInExpression(arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (argSym != null) return argSym;
        }
      } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
        for (VtlExpression arg : blockCall.arguments()) {
          ResolvedCursorSymbol argSym =
              findSymbolInExpression(arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
          if (argSym != null) return argSym;
        }
        ResolvedCursorSymbol bodySym =
            findSymbolAtOffset(
                blockCall.body(), rootNodes, offset, doc, analysis, schemaResolver, policy);
        if (bodySym != null) return bodySym;
      }
    }
    return null;
  }

  private static ResolvedCursorSymbol findSymbolInExpression(
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
        return resolveReferenceSymbol(
            refExpr.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
      }
    } else if (expr instanceof VtlBinaryExpression bin) {
      ResolvedCursorSymbol l =
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
        ResolvedCursorSymbol k =
            findSymbolInExpression(e, offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (k != null) return k;
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        ResolvedCursorSymbol k =
            findSymbolInExpression(
                entry.key(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (k != null) return k;
        ResolvedCursorSymbol v =
            findSymbolInExpression(
                entry.value(), offset, rootNodes, doc, analysis, schemaResolver, policy);
        if (v != null) return v;
      }
    } else if (expr instanceof VtlRangeExpression range) {
      ResolvedCursorSymbol s =
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
            return resolveReferenceSymbol(
                rp.reference(), offset, rootNodes, doc, analysis, schemaResolver, policy);
          }
        }
      }
    }
    return null;
  }

  private static ResolvedCursorSymbol resolveReferenceSymbol(
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
          int dotOffset = prop.span().startOffset();
          int nameStart = doc.content().indexOf(prop.propertyName(), dotOffset);
          if (nameStart < 0 || nameStart >= prop.span().endOffset()) {
            nameStart = dotOffset + 1;
          }
          int nameEnd = nameStart + prop.propertyName().length();
          Range propRange =
              Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
          String placeholder = prop.propertyName();

          // 1. Check analyzer resolution
          if (analysis != null) {
            Optional<?> mResOpt = analysis.memberResolutionOf(prop);
            if (mResOpt.isPresent()) {
              Optional<MemberResolutionBridge.MemberResolutionView> viewOpt =
                  MemberResolutionBridge.inspectMemberResolution(mResOpt.get());
              if (viewOpt.isPresent()) {
                MemberResolutionBridge.MemberResolutionView res = viewOpt.get();
                if ("DYNAMIC".equals(res.kindName())) {
                  return new ResolvedCursorSymbol(null, propRange, placeholder, SymbolKind.DYNAMIC);
                }
                if ("DENIED".equals(res.kindName())) {
                  return new ResolvedCursorSymbol(null, propRange, placeholder, SymbolKind.DENIED);
                }
                if (res.isFound() && res.targetMember().isPresent()) {
                  WorkspaceSymbolKey key =
                      toJvmSymbolKey(res.targetMember().get(), res.kindName(), prop.propertyName());
                  if (key != null) {
                    return new ResolvedCursorSymbol(
                        key, propRange, placeholder, SymbolKind.JVM_MEMBER);
                  }
                }
              }
            }
          }

          // 2. Check schema receiver type
          List<String> precedingSteps = new ArrayList<>();
          for (int j = 0; j < i; j++) {
            if (ref.steps().get(j) instanceof VtlAccessStep.PropertyAccess p) {
              precedingSteps.add(p.propertyName());
            }
          }

          Optional<TypeRef> recType =
              schemaResolver.resolveReceiverType(doc.uri(), ref.rootName(), precedingSteps);
          if (recType.isPresent()) {
            Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
            if (schema.isPresent()) {
              String typeName = extractTypeName(recType.get());
              if (typeName != null) {
                // Try JVM reflection
                ClassLoader cl =
                    schemaResolver
                        .classLoader()
                        .orElse(Thread.currentThread().getContextClassLoader());
                Class<?> clazz = null;
                try {
                  clazz = Class.forName(typeName, false, cl);
                } catch (ClassNotFoundException ignored) {
                }

                if (clazz != null) {
                  Optional<MemberResolutionBridge.MemberResolutionView> viewOpt =
                      MemberResolutionBridge.resolveProperty(clazz, prop.propertyName(), policy);
                  if (viewOpt.isPresent()) {
                    MemberResolutionBridge.MemberResolutionView res = viewOpt.get();
                    if (res.isFound() && res.targetMember().isPresent()) {
                      WorkspaceSymbolKey key =
                          toJvmSymbolKey(
                              res.targetMember().get(), res.kindName(), prop.propertyName());
                      if (key != null) {
                        return new ResolvedCursorSymbol(
                            key, propRange, placeholder, SymbolKind.JVM_MEMBER);
                      }
                    }
                  }
                }

                // Shape-only schema property
                String schemaSource =
                    WorkspaceReferenceIndex.getEffectiveSchemaSource(
                        doc.uri(), schema.get(), schemaResolver);
                WorkspaceSymbolKey key =
                    WorkspaceSymbolKey.schemaMember(schemaSource, typeName, prop.propertyName());
                return new ResolvedCursorSymbol(
                    key, propRange, placeholder, SymbolKind.SCHEMA_MEMBER);
              }
            }
          }

          return new ResolvedCursorSymbol(null, propRange, placeholder, SymbolKind.DYNAMIC);
        } else if (step instanceof VtlAccessStep.MethodCall call) {
          int dotOffset = call.span().startOffset();
          int nameStart = doc.content().indexOf(call.methodName(), dotOffset);
          if (nameStart < 0 || nameStart >= call.span().endOffset()) {
            nameStart = dotOffset + 1;
          }
          int nameEnd = nameStart + call.methodName().length();
          if (offset <= nameEnd) {
            Range methodRange =
                Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
            WorkspaceSymbolKey key = WorkspaceReferenceIndex.resolveMethodSymbolKey(call, analysis);
            return new ResolvedCursorSymbol(key, methodRange, call.methodName(), SymbolKind.METHOD);
          }
          // Inside arguments
          for (VtlExpression arg : call.arguments()) {
            ResolvedCursorSymbol argSym =
                findSymbolInExpression(
                    arg, offset, rootNodes, doc, analysis, schemaResolver, policy);
            if (argSym != null) return argSym;
          }
          Range methodRange =
              Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
          WorkspaceSymbolKey key = WorkspaceReferenceIndex.resolveMethodSymbolKey(call, analysis);
          return new ResolvedCursorSymbol(key, methodRange, call.methodName(), SymbolKind.METHOD);
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

    int rootStart = ref.span().startOffset();
    int rootEnd =
        ref.steps().isEmpty() ? ref.span().endOffset() : ref.steps().get(0).span().startOffset();
    int nameStart = doc.content().indexOf(rootName, rootStart);
    if (nameStart < 0 || nameStart >= rootEnd) {
      nameStart =
          rootStart
              + (rootStart < doc.content().length() && doc.content().charAt(rootStart) == '$'
                  ? 1
                  : 0);
    }
    int nameEnd = nameStart + rootName.length();
    Range rootRange = Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));

    // Check lexical scope for local variable
    VtlForeachDirectiveNode fe = findEnclosingForeach(rootNodes, rootStart, rootName);
    if (fe != null && fe.loopVariable().span().isKnown()) {
      WorkspaceSymbolKey key =
          WorkspaceSymbolKey.templateLocal(
              doc.uri(),
              rootName,
              fe.loopVariable().span().startOffset(),
              fe.loopVariable().span().endOffset());
      return new ResolvedCursorSymbol(key, rootRange, rootName, SymbolKind.TEMPLATE_LOCAL);
    }

    VtlSetDirectiveNode set = findPrecedingSet(rootNodes, rootStart, rootName);
    if (set != null && set.target().span().isKnown()) {
      WorkspaceSymbolKey key =
          WorkspaceSymbolKey.templateLocal(
              doc.uri(),
              rootName,
              set.target().span().startOffset(),
              set.target().span().endOffset());
      return new ResolvedCursorSymbol(key, rootRange, rootName, SymbolKind.TEMPLATE_LOCAL);
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
    WorkspaceSymbolKey key = WorkspaceSymbolKey.rootParameter(schemaSource, rootName);
    return new ResolvedCursorSymbol(key, rootRange, rootName, SymbolKind.ROOT_PARAMETER);
  }

  private static WorkspaceSymbolKey toJvmSymbolKey(
      Member member, String kindName, String propertyName) {
    String declaringClass = member.getDeclaringClass().getName();
    return switch (kindName) {
      case "RECORD_COMPONENT" ->
          WorkspaceSymbolKey.jvmMember(
              declaringClass, JvmMemberSymbolKey.Kind.RECORD_COMPONENT, propertyName, "", 0);
      case "GETTER" ->
          WorkspaceSymbolKey.jvmMember(
              declaringClass, JvmMemberSymbolKey.Kind.GETTER, member.getName(), "", 0);
      case "BOOLEAN_GETTER" ->
          WorkspaceSymbolKey.jvmMember(
              declaringClass, JvmMemberSymbolKey.Kind.BOOLEAN_GETTER, member.getName(), "", 0);
      case "FIELD" ->
          WorkspaceSymbolKey.jvmMember(
              declaringClass, JvmMemberSymbolKey.Kind.FIELD, member.getName(), "", 0);
      default -> null;
    };
  }

  private static String extractTypeName(TypeRef recType) {
    if (recType instanceof ClassTypeRef ctr) {
      return ctr.name();
    }
    if (recType instanceof NamedTypeRef ntr) {
      return ntr.name();
    }
    if (recType instanceof ParameterizedTypeRef ptr) {
      return ptr.rawType();
    }
    return null;
  }

  static VtlForeachDirectiveNode findEnclosingForeach(
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

  static VtlSetDirectiveNode findPrecedingSet(List<VtlNode> nodes, int offset, String varName) {
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
