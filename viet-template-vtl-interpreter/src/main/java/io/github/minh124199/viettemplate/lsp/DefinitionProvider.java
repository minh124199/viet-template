package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition lookup engine navigating between template usages and schema/in-template declarations.
 *
 * <p>Supports exact cross-language navigation to TypeScript (.d.ts), JSON Schema (.schema.json),
 * Viet Template schema (.vt-schema.json), companion contracts (.contract), and Java source files.
 * Java models without source files gracefully return empty definitions without synthesizing fakes.
 */
final class DefinitionProvider {

  private DefinitionProvider() {}

  public static List<LocationInfo> definition(
      TemplateDocument doc, Position position, CanonicalSchemaResolver schemaResolver) {
    return definition(doc, position, schemaResolver, new WorkspaceSchemaIndex(schemaResolver));
  }

  public static List<LocationInfo> definition(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex) {
    return definition(doc, position, schemaResolver, schemaIndex, MemberAccessPolicy.standard());
  }

  public static List<LocationInfo> definition(
      TemplateDocument doc,
      Position position,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");
    WorkspaceSchemaIndex index =
        schemaIndex != null ? schemaIndex : new WorkspaceSchemaIndex(schemaResolver);
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    if (doc.uri().startsWith("file:/")) {
      try {
        index.javaSourceLocator().probeSourceRootsFor(Path.of(URI.create(doc.uri())));
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

    List<LocationInfo> locations = new ArrayList<>();
    List<VtlNode> rootNodes = parsed.template().children();
    findDefinitions(rootNodes, rootNodes, offset, doc, schemaResolver, index, policy, locations);
    Collections.sort(locations);
    return List.copyOf(locations);
  }

  private static void findDefinitions(
      List<VtlNode> currentNodes,
      List<VtlNode> rootNodes,
      int offset,
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy policy,
      List<LocationInfo> out) {
    if (currentNodes == null) return;

    for (VtlNode node : currentNodes) {
      if (node instanceof VtlReferenceOutputNode refOut) {
        resolveReferenceDefinition(
            refOut.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target().span().isKnown()
            && setNode.target().span().startOffset() <= offset
            && offset <= setNode.target().span().endOffset()) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(setNode.target().span())));
          return;
        }
        resolveExpressionDefinition(
            setNode.value(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        if (feNode.loopVariable().span().isKnown()
            && feNode.loopVariable().span().startOffset() <= offset
            && offset <= feNode.loopVariable().span().endOffset()) {
          out.add(LocationInfo.of(doc.uri(), doc.spanToRange(feNode.loopVariable().span())));
          return;
        }
        resolveExpressionDefinition(
            feNode.iterable(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
        findDefinitions(
            feNode.body(), rootNodes, offset, doc, schemaResolver, schemaIndex, policy, out);
        if (feNode.elseBody().isPresent()) {
          findDefinitions(
              feNode.elseBody().get(),
              rootNodes,
              offset,
              doc,
              schemaResolver,
              schemaIndex,
              policy,
              out);
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          resolveExpressionDefinition(
              branch.condition(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
          findDefinitions(
              branch.body(), rootNodes, offset, doc, schemaResolver, schemaIndex, policy, out);
        }
        if (ifNode.elseBody().isPresent()) {
          findDefinitions(
              ifNode.elseBody().get(),
              rootNodes,
              offset,
              doc,
              schemaResolver,
              schemaIndex,
              policy,
              out);
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
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy policy,
      List<LocationInfo> out) {
    if (expr == null || !out.isEmpty()) return;
    if (expr instanceof VtlReferenceExpression refExpr) {
      resolveReferenceDefinition(
          refExpr.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
    } else if (expr instanceof VtlBinaryExpression bin) {
      resolveExpressionDefinition(
          bin.left(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      resolveExpressionDefinition(
          bin.right(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
    } else if (expr instanceof VtlUnaryExpression un) {
      resolveExpressionDefinition(
          un.operand(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
    } else if (expr instanceof VtlGroupedExpression grp) {
      resolveExpressionDefinition(
          grp.expression(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression e : list.elements()) {
        resolveExpressionDefinition(
            e, offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        resolveExpressionDefinition(
            entry.key(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
        resolveExpressionDefinition(
            entry.value(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      resolveExpressionDefinition(
          range.start(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
      resolveExpressionDefinition(
          range.end(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          resolveReferenceDefinition(
              rp.reference(), offset, doc, rootNodes, schemaResolver, schemaIndex, policy, out);
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

    // 2. Check preceding #set directives
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
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy policy,
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
              doc,
              ref.rootName(),
              precedingSteps,
              prop.propertyName(),
              schemaResolver,
              schemaIndex,
              policy,
              out);
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
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent() && schema.get().parameters().containsKey(rootName)) {
      ParameterDef param = schema.get().parameters().get(rootName);
      Optional<SchemaProvenance> prov = schemaIndex.getParameterProvenance(doc.uri(), rootName);
      boolean isJvm =
          schema.get().format() == SchemaFormat.JAVA || (prov.isPresent() && prov.get().jvmBound());

      if (isJvm) {
        String fqcn = extractFqcn(param.type());
        if (fqcn != null) {
          Optional<LocationInfo> javaLoc =
              schemaIndex.javaSourceLocator().locate(JavaSourceSymbolKey.forType(fqcn));
          if (javaLoc.isPresent()) {
            out.add(javaLoc.get());
            return;
          }
        }
        if (schema.get().format() == SchemaFormat.JAVA) {
          // Java reflection without source -> empty list (definition unavailable)
          return;
        }
      }

      Optional<Path> schemaFile = schemaIndex.getAssociatedSchemaPath(doc.uri());
      if (schemaFile.isPresent()) {
        if (prov.isPresent() && prov.get().location().isPresent()) {
          SchemaSourceLocation loc = prov.get().location().get();
          Range range =
              Range.of(
                  Math.max(0, loc.startLine() - 1),
                  Math.max(0, loc.startColumn() - 1),
                  Math.max(0, loc.endLine() - 1),
                  Math.max(0, loc.endColumn() - 1));
          out.add(LocationInfo.of(loc.filePath().toUri().toString(), range));
        } else {
          out.add(LocationInfo.of(schemaFile.get().toUri().toString(), Range.of(0, 0, 0, 0)));
        }
      } else {
        String templateId =
            schema.get().templateId().isEmpty() ? doc.uri() : schema.get().templateId();
        Range range = Range.of(0, 0, 0, 0);
        if (prov.isPresent() && prov.get().location().isPresent()) {
          SchemaSourceLocation loc = prov.get().location().get();
          range =
              Range.of(
                  Math.max(0, loc.startLine() - 1),
                  Math.max(0, loc.startColumn() - 1),
                  Math.max(0, loc.endLine() - 1),
                  Math.max(0, loc.endColumn() - 1));
        }
        out.add(LocationInfo.of("schema://" + templateId + "#parameters/" + rootName, range));
      }
    }
  }

  private static void resolvePropertyDefinition(
      TemplateDocument doc,
      String rootName,
      List<String> precedingSteps,
      String propertyName,
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy policy,
      List<LocationInfo> out) {
    Optional<TypeRef> recType =
        schemaResolver.resolveReceiverType(doc.uri(), rootName, precedingSteps);
    if (recType.isEmpty()) {
      return;
    }
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isEmpty()) {
      return;
    }

    String typeName = extractTypeName(recType.get());
    if (typeName == null) {
      return;
    }

    Optional<SchemaProvenance> prov =
        schemaIndex.getPropertyProvenance(doc.uri(), typeName, propertyName);

    boolean isJvm =
        schema.get().format() == SchemaFormat.JAVA || (prov.isPresent() && prov.get().jvmBound());

    if (!isJvm) {
      Optional<Path> schemaFile = schemaIndex.getAssociatedSchemaPath(doc.uri());
      if (schemaFile.isPresent()) {
        if (prov.isPresent() && prov.get().location().isPresent()) {
          SchemaSourceLocation loc = prov.get().location().get();
          Range range =
              Range.of(
                  Math.max(0, loc.startLine() - 1),
                  Math.max(0, loc.startColumn() - 1),
                  Math.max(0, loc.endLine() - 1),
                  Math.max(0, loc.endColumn() - 1));
          out.add(LocationInfo.of(loc.filePath().toUri().toString(), range));
        } else {
          out.add(LocationInfo.of(schemaFile.get().toUri().toString(), Range.of(0, 0, 0, 0)));
        }
      } else {
        String templateId =
            schema.get().templateId().isEmpty() ? doc.uri() : schema.get().templateId();
        Range range = Range.of(0, 0, 0, 0);
        if (prov.isPresent() && prov.get().location().isPresent()) {
          SchemaSourceLocation loc = prov.get().location().get();
          range =
              Range.of(
                  Math.max(0, loc.startLine() - 1),
                  Math.max(0, loc.startColumn() - 1),
                  Math.max(0, loc.endLine() - 1),
                  Math.max(0, loc.endColumn() - 1));
        }
        out.add(
            LocationInfo.of(
                "schema://" + templateId + "#types/" + typeName + "/properties/" + propertyName,
                range));
      }
      return;
    }

    // 2. If backed by JVM model
    ClassLoader cl = schemaIndex.classLoader();
    if (cl == null) {
      cl = schemaResolver.classLoader().orElse(Thread.currentThread().getContextClassLoader());
    }
    if (cl == null) {
      cl = DefinitionProvider.class.getClassLoader();
    }

    Class<?> clazz = null;
    try {
      clazz = Class.forName(typeName, false, cl);
    } catch (ClassNotFoundException ignored) {
      if (recType.get() instanceof ClassTypeRef ctr) {
        try {
          clazz = Class.forName(ctr.name(), false, cl);
        } catch (ClassNotFoundException ignored2) {
        }
      }
    }

    if (clazz != null) {
      Optional<LocationInfo> loc =
          resolveViaMemberResolver(clazz, propertyName, policy, schemaIndex);
      if (loc.isPresent()) {
        out.add(loc.get());
        return;
      }
    } else {
      Optional<LocationInfo> loc =
          tryLocateInWorkspace(typeName, propertyName, schemaIndex.javaSourceLocator());
      if (loc.isPresent()) {
        out.add(loc.get());
        return;
      }
    }

    // If source missing: if .contract file exists, fall back to .contract file; if pure Java model,
    // return empty list
    Optional<Path> schemaFile = schemaIndex.getAssociatedSchemaPath(doc.uri());
    if (schemaFile.isPresent() && schemaFile.get().toString().endsWith(".contract")) {
      if (prov.isPresent() && prov.get().location().isPresent()) {
        SchemaSourceLocation loc = prov.get().location().get();
        Range range =
            Range.of(
                Math.max(0, loc.startLine() - 1),
                Math.max(0, loc.startColumn() - 1),
                Math.max(0, loc.endLine() - 1),
                Math.max(0, loc.endColumn() - 1));
        out.add(LocationInfo.of(loc.filePath().toUri().toString(), range));
        return;
      } else {
        out.add(LocationInfo.of(schemaFile.get().toUri().toString(), Range.of(0, 0, 0, 0)));
        return;
      }
    }

    // Pure Java model -> definition unavailable
    return;
  }

  private static Optional<LocationInfo> resolveViaMemberResolver(
      Class<?> clazz,
      String propertyName,
      MemberAccessPolicy policy,
      WorkspaceSchemaIndex schemaIndex) {
    try {
      Method resolveProp = MemberResolverBridge.RESOLVE_PROPERTY;
      if (resolveProp == null) {
        return Optional.empty();
      }
      Object res = resolveProp.invoke(null, VType.ClassType.of(clazz), propertyName, policy);
      if (res == null) {
        return Optional.empty();
      }
      boolean isFound = (boolean) MemberResolverBridge.IS_FOUND.invoke(res);
      if (!isFound) {
        return Optional.empty();
      }
      Object kind = MemberResolverBridge.KIND.invoke(res);
      String kindName = kind != null ? ((Enum<?>) kind).name() : "";
      Optional<?> targetMemberOpt = (Optional<?>) MemberResolverBridge.TARGET_MEMBER.invoke(res);

      WorkspaceJavaSourceLocator locator = schemaIndex.javaSourceLocator();
      switch (kindName) {
        case "RECORD_COMPONENT" -> {
          return locator.locate(
              JavaSourceSymbolKey.forRecordComponent(clazz.getName(), propertyName));
        }
        case "GETTER", "BOOLEAN_GETTER" -> {
          if (targetMemberOpt.isPresent() && targetMemberOpt.get() instanceof Method m) {
            String declaringClass = m.getDeclaringClass().getName();
            return locator.locate(JavaSourceSymbolKey.forMethod(declaringClass, m.getName(), 0));
          }
        }
        case "FIELD" -> {
          if (targetMemberOpt.isPresent() && targetMemberOpt.get() instanceof Field f) {
            String declaringClass = f.getDeclaringClass().getName();
            return locator.locate(JavaSourceSymbolKey.forField(declaringClass, f.getName()));
          }
        }
        default -> {}
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return Optional.empty();
  }

  private static final class MemberResolverBridge {
    private static final Method RESOLVE_PROPERTY;
    private static final Method IS_FOUND;
    private static final Method KIND;
    private static final Method TARGET_MEMBER;

    static {
      Method rp = null;
      Method ifFound = null;
      Method kd = null;
      Method tm = null;
      try {
        Class<?> resolverClass =
            Class.forName(
                "io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolver");
        rp =
            resolverClass.getMethod(
                "resolveProperty", VType.class, String.class, MemberAccessPolicy.class);
        Class<?> resolutionClass =
            Class.forName(
                "io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolution");
        ifFound = resolutionClass.getMethod("isFound");
        kd = resolutionClass.getMethod("kind");
        tm = resolutionClass.getMethod("targetMember");
      } catch (ReflectiveOperationException ignored) {
      }
      RESOLVE_PROPERTY = rp;
      IS_FOUND = ifFound;
      KIND = kd;
      TARGET_MEMBER = tm;
    }
  }

  private static Optional<LocationInfo> tryLocateInWorkspace(
      String typeName, String propertyName, WorkspaceJavaSourceLocator locator) {
    // 1. Record component
    Optional<LocationInfo> loc =
        locator.locate(JavaSourceSymbolKey.forRecordComponent(typeName, propertyName));
    if (loc.isPresent()) {
      return loc;
    }

    // 2. Getter: getX()
    String cap = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    loc = locator.locate(JavaSourceSymbolKey.forMethod(typeName, "get" + cap, 0));
    if (loc.isPresent()) {
      return loc;
    }

    // 3. Boolean getter: isX()
    loc = locator.locate(JavaSourceSymbolKey.forMethod(typeName, "is" + cap, 0));
    if (loc.isPresent()) {
      return loc;
    }

    // 4. Exact method: x()
    loc = locator.locate(JavaSourceSymbolKey.forMethod(typeName, propertyName, 0));
    if (loc.isPresent()) {
      return loc;
    }

    // 5. Field: x
    return locator.locate(JavaSourceSymbolKey.forField(typeName, propertyName));
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

  private static String extractFqcn(TypeRef type) {
    if (type instanceof ClassTypeRef ctr) {
      return ctr.name();
    }
    if (type instanceof ParameterizedTypeRef ptr) {
      return ptr.rawType();
    }
    if (type instanceof NamedTypeRef ntr) {
      return ntr.name();
    }
    if (type instanceof ArrayTypeRef atr) {
      return extractFqcn(atr.componentType());
    }
    return null;
  }
}
