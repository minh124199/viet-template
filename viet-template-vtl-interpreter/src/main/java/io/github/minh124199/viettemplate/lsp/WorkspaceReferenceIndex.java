package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.SourceSpan;
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
import java.io.IOException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, memory-efficient inverted reference graph indexing semantic references across
 * workspace template documents.
 *
 * <p>Supports atomic incremental template updates, template removal, and sub-millisecond reference
 * queries by {@link WorkspaceSymbolKey}.
 */
final class WorkspaceReferenceIndex {

  private static final long MAX_INDEXABLE_TEMPLATE_SIZE = 10 * 1024 * 1024L; // 10MB

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Map<WorkspaceSymbolKey, Set<ResolvedTemplateReference>> symbolToRefs =
      new HashMap<>();
  private final Map<String, Set<ResolvedTemplateReference>> templateToRefs = new HashMap<>();

  WorkspaceReferenceIndex() {}

  /**
   * Indexes or atomically re-indexes the given template document.
   *
   * <p>If the template has syntax or parse errors, any previously indexed references for this
   * document URI are atomically cleared and the method returns cleanly.
   */
  public void indexTemplate(
      TemplateDocument doc,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy memberAccessPolicy) {
    Objects.requireNonNull(doc, "doc must not be null");
    CanonicalSchemaResolver resolver =
        schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    MemberAccessPolicy policy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();

    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
      removeTemplate(doc.uri());
      return;
    }

    if (parsed == null || parsed.hasErrors()) {
      removeTemplate(doc.uri());
      return;
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

    Set<ResolvedTemplateReference> extracted =
        extractReferences(doc, parsed.template(), analysis, resolver, policy);

    rwLock.writeLock().lock();
    try {
      // 1. Remove old template records
      Set<ResolvedTemplateReference> oldRefs = templateToRefs.remove(doc.uri());
      if (oldRefs != null) {
        for (ResolvedTemplateReference oldRef : oldRefs) {
          Set<ResolvedTemplateReference> set = symbolToRefs.get(oldRef.symbolKey());
          if (set != null) {
            set.remove(oldRef);
            if (set.isEmpty()) {
              symbolToRefs.remove(oldRef.symbolKey());
            }
          }
        }
      }

      // 2. Insert new template records
      if (!extracted.isEmpty()) {
        Set<ResolvedTemplateReference> docRefs = Collections.unmodifiableSet(extracted);
        templateToRefs.put(doc.uri(), docRefs);
        for (ResolvedTemplateReference ref : docRefs) {
          symbolToRefs.computeIfAbsent(ref.symbolKey(), k -> new HashSet<>()).add(ref);
        }
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /** Cleanly removes all references recorded for the given template document URI. */
  public void removeTemplate(String templateUri) {
    if (templateUri == null || templateUri.isBlank()) {
      return;
    }
    rwLock.writeLock().lock();
    try {
      Set<ResolvedTemplateReference> oldRefs = templateToRefs.remove(templateUri);
      if (oldRefs != null) {
        for (ResolvedTemplateReference oldRef : oldRefs) {
          Set<ResolvedTemplateReference> set = symbolToRefs.get(oldRef.symbolKey());
          if (set != null) {
            set.remove(oldRef);
            if (set.isEmpty()) {
              symbolToRefs.remove(oldRef.symbolKey());
            }
          }
        }
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * Finds all references in the workspace matching the given canonical symbol key. Includes
   * directive target declarations.
   */
  public List<LocationInfo> findReferences(WorkspaceSymbolKey key) {
    return findReferences(key, true);
  }

  /**
   * Finds all references in the workspace matching the given canonical symbol key, optionally
   * filtering out directive declaration targets.
   */
  public List<LocationInfo> findReferences(WorkspaceSymbolKey key, boolean includeDeclaration) {
    if (key == null) {
      return List.of();
    }

    rwLock.readLock().lock();
    try {
      Set<ResolvedTemplateReference> refs = getMatchingReferences(key);
      if (refs == null || refs.isEmpty()) {
        return List.of();
      }

      List<LocationInfo> locations = new ArrayList<>();
      for (ResolvedTemplateReference ref : refs) {
        if (!includeDeclaration && ref.kind() == ResolvedTemplateReference.Kind.DIRECTIVE_TARGET) {
          continue;
        }
        locations.add(ref.toLocationInfo());
      }

      Collections.sort(locations);
      return List.copyOf(new LinkedHashSet<>(locations));
    } finally {
      rwLock.readLock().unlock();
    }
  }

  private Set<ResolvedTemplateReference> getMatchingReferences(WorkspaceSymbolKey key) {
    Set<ResolvedTemplateReference> exact = symbolToRefs.get(key);
    if (exact != null && !exact.isEmpty()) {
      return exact;
    }

    // Fallback: If JvmMemberSymbolKey has empty descriptor, match by name & parameter count
    if (key instanceof JvmMemberSymbolKey jvmKey && jvmKey.descriptor().isEmpty()) {
      Set<ResolvedTemplateReference> merged = new HashSet<>();
      for (Map.Entry<WorkspaceSymbolKey, Set<ResolvedTemplateReference>> entry :
          symbolToRefs.entrySet()) {
        if (entry.getKey() instanceof JvmMemberSymbolKey target) {
          if (target.declaringClassName().equals(jvmKey.declaringClassName())
              && target.memberKind() == jvmKey.memberKind()
              && target.memberName().equals(jvmKey.memberName())
              && target.parameterCount() == jvmKey.parameterCount()) {
            merged.addAll(entry.getValue());
          }
        }
      }
      return merged;
    }

    return Set.of();
  }

  /** Clears all stored references and symbol mappings. */
  public void clear() {
    rwLock.writeLock().lock();
    try {
      symbolToRefs.clear();
      templateToRefs.clear();
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public int symbolCount() {
    rwLock.readLock().lock();
    try {
      return symbolToRefs.size();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public int templateCount() {
    rwLock.readLock().lock();
    try {
      return templateToRefs.size();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public Set<ResolvedTemplateReference> getReferencesForTemplate(String uri) {
    if (uri == null) return Set.of();
    rwLock.readLock().lock();
    try {
      Set<ResolvedTemplateReference> set = templateToRefs.get(uri);
      return set != null ? Set.copyOf(set) : Set.of();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public Set<ResolvedTemplateReference> getReferencesForSymbol(WorkspaceSymbolKey key) {
    if (key == null) return Set.of();
    rwLock.readLock().lock();
    try {
      Set<ResolvedTemplateReference> set = getMatchingReferences(key);
      return set != null ? Set.copyOf(set) : Set.of();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  /** Scans and incrementally indexes all VTL templates under the given workspace root directory. */
  public void scanWorkspaceTemplates(
      Path workspaceRoot,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy memberAccessPolicy) {
    if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
      return;
    }

    Path root = workspaceRoot.toAbsolutePath().normalize();
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (dir.getFileName() != null) {
                String dirName = dir.getFileName().toString();
                if (WorkspaceJavaSourceLocator.IGNORED_DIRS.contains(dirName)) {
                  return FileVisitResult.SKIP_SUBTREE;
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
              if (name.endsWith(".vtl") || name.endsWith(".vm") || name.endsWith(".vt")) {
                if (attrs.size() <= MAX_INDEXABLE_TEMPLATE_SIZE) {
                  try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    TemplateDocument doc =
                        new TemplateDocument(file.toUri().toString(), 1, content);
                    indexTemplate(doc, schemaResolver, memberAccessPolicy);
                  } catch (IOException ignored) {
                  }
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException ignored) {
    }
  }

  // =========================================================================
  // AST REFERENCE EXTRACTION
  // =========================================================================

  private static Set<ResolvedTemplateReference> extractReferences(
      TemplateDocument doc,
      VtlTemplate template,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {
    Set<ResolvedTemplateReference> result = new HashSet<>();
    List<VtlNode> rootNodes = template.children();
    traverseNodes(rootNodes, rootNodes, doc, analysis, schemaResolver, policy, result);
    return result;
  }

  private static void traverseNodes(
      List<VtlNode> currentNodes,
      List<VtlNode> rootNodes,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<ResolvedTemplateReference> out) {
    if (currentNodes == null) return;

    for (VtlNode node : currentNodes) {
      if (node instanceof VtlReferenceOutputNode refOut) {
        extractFromReference(
            refOut.reference(), rootNodes, doc, analysis, schemaResolver, policy, out);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          VtlReference targetRef = refTarget.reference();
          if (targetRef.steps().isEmpty()) {
            String varName = targetRef.rootName();
            if (!varName.isBlank() && setNode.target().span().isKnown()) {
              WorkspaceSymbolKey key =
                  WorkspaceSymbolKey.templateLocal(
                      doc.uri(),
                      varName,
                      setNode.target().span().startOffset(),
                      setNode.target().span().endOffset());
              Range range = doc.spanToRange(setNode.target().span());
              out.add(
                  new ResolvedTemplateReference(
                      key,
                      doc.uri(),
                      range,
                      setNode.target().span(),
                      ResolvedTemplateReference.Kind.DIRECTIVE_TARGET));
            }
          } else {
            extractFromReference(targetRef, rootNodes, doc, analysis, schemaResolver, policy, out);
          }
        }
        traverseExpression(setNode.value(), rootNodes, doc, analysis, schemaResolver, policy, out);
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        String varName = feNode.loopVariable().rootName();
        if (!varName.isBlank() && feNode.loopVariable().span().isKnown()) {
          WorkspaceSymbolKey key =
              WorkspaceSymbolKey.templateLocal(
                  doc.uri(),
                  varName,
                  feNode.loopVariable().span().startOffset(),
                  feNode.loopVariable().span().endOffset());
          Range range = doc.spanToRange(feNode.loopVariable().span());
          out.add(
              new ResolvedTemplateReference(
                  key,
                  doc.uri(),
                  range,
                  feNode.loopVariable().span(),
                  ResolvedTemplateReference.Kind.DIRECTIVE_TARGET));
        }
        traverseExpression(
            feNode.iterable(), rootNodes, doc, analysis, schemaResolver, policy, out);
        traverseNodes(feNode.body(), rootNodes, doc, analysis, schemaResolver, policy, out);
        if (feNode.elseBody().isPresent()) {
          traverseNodes(
              feNode.elseBody().get(), rootNodes, doc, analysis, schemaResolver, policy, out);
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          traverseExpression(
              branch.condition(), rootNodes, doc, analysis, schemaResolver, policy, out);
          traverseNodes(branch.body(), rootNodes, doc, analysis, schemaResolver, policy, out);
        }
        if (ifNode.elseBody().isPresent()) {
          traverseNodes(
              ifNode.elseBody().get(), rootNodes, doc, analysis, schemaResolver, policy, out);
        }
      } else if (node instanceof VtlDirectiveCallNode dirCall) {
        for (VtlExpression arg : dirCall.arguments()) {
          traverseExpression(arg, rootNodes, doc, analysis, schemaResolver, policy, out);
        }
      } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
        for (VtlExpression arg : blockCall.arguments()) {
          traverseExpression(arg, rootNodes, doc, analysis, schemaResolver, policy, out);
        }
        traverseNodes(blockCall.body(), rootNodes, doc, analysis, schemaResolver, policy, out);
      }
    }
  }

  private static void traverseExpression(
      VtlExpression expr,
      List<VtlNode> rootNodes,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<ResolvedTemplateReference> out) {
    if (expr == null) return;

    if (expr instanceof VtlReferenceExpression refExpr) {
      extractFromReference(
          refExpr.reference(), rootNodes, doc, analysis, schemaResolver, policy, out);
    } else if (expr instanceof VtlBinaryExpression bin) {
      traverseExpression(bin.left(), rootNodes, doc, analysis, schemaResolver, policy, out);
      traverseExpression(bin.right(), rootNodes, doc, analysis, schemaResolver, policy, out);
    } else if (expr instanceof VtlUnaryExpression un) {
      traverseExpression(un.operand(), rootNodes, doc, analysis, schemaResolver, policy, out);
    } else if (expr instanceof VtlGroupedExpression grp) {
      traverseExpression(grp.expression(), rootNodes, doc, analysis, schemaResolver, policy, out);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression elem : list.elements()) {
        traverseExpression(elem, rootNodes, doc, analysis, schemaResolver, policy, out);
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        traverseExpression(entry.key(), rootNodes, doc, analysis, schemaResolver, policy, out);
        traverseExpression(entry.value(), rootNodes, doc, analysis, schemaResolver, policy, out);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      traverseExpression(range.start(), rootNodes, doc, analysis, schemaResolver, policy, out);
      traverseExpression(range.end(), rootNodes, doc, analysis, schemaResolver, policy, out);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          extractFromReference(
              rp.reference(), rootNodes, doc, analysis, schemaResolver, policy, out);
        }
      }
    }
  }

  private static void extractFromReference(
      VtlReference ref,
      List<VtlNode> rootNodes,
      TemplateDocument doc,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<ResolvedTemplateReference> out) {
    if (!ref.span().isKnown()) {
      return;
    }

    String rootName = ref.rootName();
    if (!rootName.isBlank()) {
      Range rootRange;
      SourceSpan rootSpan;
      if (ref.steps().isEmpty()) {
        rootRange = doc.spanToRange(ref.span());
        rootSpan = ref.span();
      } else {
        int rootStart = ref.span().startOffset();
        int rootEnd = ref.steps().get(0).span().startOffset();
        rootRange = Range.of(doc.offsetToPosition(rootStart), doc.offsetToPosition(rootEnd));
        rootSpan = doc.rangeToSpan(rootRange);
      }

      int refOffset = ref.span().startOffset();

      // Check lexical scope for local variable
      VtlForeachDirectiveNode fe = findEnclosingForeach(rootNodes, refOffset, rootName);
      if (fe != null && fe.loopVariable().span().isKnown()) {
        WorkspaceSymbolKey key =
            WorkspaceSymbolKey.templateLocal(
                doc.uri(),
                rootName,
                fe.loopVariable().span().startOffset(),
                fe.loopVariable().span().endOffset());
        out.add(
            new ResolvedTemplateReference(
                key,
                doc.uri(),
                rootRange,
                rootSpan,
                ResolvedTemplateReference.Kind.LOCAL_VARIABLE));
      } else {
        VtlSetDirectiveNode set = findPrecedingSet(rootNodes, refOffset, rootName);
        if (set != null && set.target().span().isKnown()) {
          WorkspaceSymbolKey key =
              WorkspaceSymbolKey.templateLocal(
                  doc.uri(),
                  rootName,
                  set.target().span().startOffset(),
                  set.target().span().endOffset());
          out.add(
              new ResolvedTemplateReference(
                  key,
                  doc.uri(),
                  rootRange,
                  rootSpan,
                  ResolvedTemplateReference.Kind.LOCAL_VARIABLE));
        } else {
          // Root parameter
          Optional<CanonicalSchema> schemaOpt = schemaResolver.resolveSchema(doc.uri());
          String schemaSource;
          if (schemaOpt.isPresent() && schemaOpt.get().parameters().containsKey(rootName)) {
            schemaSource = getEffectiveSchemaSource(doc.uri(), schemaOpt.get(), schemaResolver);
          } else {
            schemaSource = doc.uri();
          }
          WorkspaceSymbolKey key = WorkspaceSymbolKey.rootParameter(schemaSource, rootName);
          out.add(
              new ResolvedTemplateReference(
                  key,
                  doc.uri(),
                  rootRange,
                  rootSpan,
                  ResolvedTemplateReference.Kind.ROOT_VARIABLE));
        }
      }
    }

    // Process steps
    List<VtlAccessStep> steps = ref.steps();
    for (int i = 0; i < steps.size(); i++) {
      VtlAccessStep step = steps.get(i);
      if (step instanceof VtlAccessStep.PropertyAccess prop) {
        int dotOffset = prop.span().startOffset();
        int nameStart = doc.content().indexOf(prop.propertyName(), dotOffset);
        if (nameStart < 0 || nameStart >= prop.span().endOffset()) {
          nameStart = dotOffset + 1;
        }
        int nameEnd = nameStart + prop.propertyName().length();
        Range propRange = Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
        SourceSpan propSpan = doc.rangeToSpan(propRange);

        WorkspaceSymbolKey propKey =
            resolvePropertySymbolKey(doc, ref, prop, i, analysis, schemaResolver, policy);
        if (propKey != null) {
          out.add(
              new ResolvedTemplateReference(
                  propKey,
                  doc.uri(),
                  propRange,
                  propSpan,
                  ResolvedTemplateReference.Kind.PROPERTY_ACCESS));
        }
      } else if (step instanceof VtlAccessStep.MethodCall call) {
        int dotOffset = call.span().startOffset();
        int nameStart = doc.content().indexOf(call.methodName(), dotOffset);
        if (nameStart < 0 || nameStart >= call.span().endOffset()) {
          nameStart = dotOffset + 1;
        }
        int nameEnd = nameStart + call.methodName().length();
        Range methodRange =
            Range.of(doc.offsetToPosition(nameStart), doc.offsetToPosition(nameEnd));
        SourceSpan methodSpan = doc.rangeToSpan(methodRange);

        WorkspaceSymbolKey methodKey = resolveMethodSymbolKey(call, analysis);
        if (methodKey != null) {
          out.add(
              new ResolvedTemplateReference(
                  methodKey,
                  doc.uri(),
                  methodRange,
                  methodSpan,
                  ResolvedTemplateReference.Kind.METHOD_CALL));
        }

        for (VtlExpression arg : call.arguments()) {
          traverseExpression(arg, rootNodes, doc, analysis, schemaResolver, policy, out);
        }
      } else if (step instanceof VtlAccessStep.IndexAccess idx) {
        traverseExpression(
            idx.indexExpression(), rootNodes, doc, analysis, schemaResolver, policy, out);
      }
    }

    if (ref.alternateValue().isPresent()) {
      traverseExpression(
          ref.alternateValue().get(), rootNodes, doc, analysis, schemaResolver, policy, out);
    }
  }

  static WorkspaceSymbolKey resolvePropertySymbolKey(
      TemplateDocument doc,
      VtlReference ref,
      VtlAccessStep.PropertyAccess prop,
      int stepIndex,
      SemanticAnalysisResult analysis,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy) {

    // 1. Check analyzer resolution
    if (analysis != null) {
      Optional<?> mResOpt = analysis.memberResolutionOf(prop);
      if (mResOpt.isPresent()) {
        Optional<MemberResolutionBridge.MemberResolutionView> viewOpt =
            MemberResolutionBridge.inspectMemberResolution(mResOpt.get());
        if (viewOpt.isPresent()) {
          MemberResolutionBridge.MemberResolutionView res = viewOpt.get();
          if (!res.isFound()
              || "DYNAMIC".equals(res.kindName())
              || "DENIED".equals(res.kindName())) {
            return null;
          }
          if (res.targetMember().isPresent()) {
            Member m = res.targetMember().get();
            WorkspaceSymbolKey key = toJvmSymbolKey(m, res.kindName(), prop.propertyName());
            if (key != null) {
              return key;
            }
          }
        }
      }
    }

    // 2. Check schema receiver type
    List<String> precedingSteps = new ArrayList<>();
    for (int j = 0; j < stepIndex; j++) {
      if (ref.steps().get(j) instanceof VtlAccessStep.PropertyAccess p) {
        precedingSteps.add(p.propertyName());
      }
    }

    Optional<TypeRef> recType =
        schemaResolver.resolveReceiverType(doc.uri(), ref.rootName(), precedingSteps);
    if (recType.isEmpty()) {
      return null;
    }

    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isEmpty()) {
      return null;
    }

    String typeName = extractTypeName(recType.get());
    if (typeName == null) {
      return null;
    }

    // Try JVM reflection
    ClassLoader cl =
        schemaResolver.classLoader().orElse(Thread.currentThread().getContextClassLoader());
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
              toJvmSymbolKey(res.targetMember().get(), res.kindName(), prop.propertyName());
          if (key != null) {
            return key;
          }
        }
      }
    }

    // Otherwise shape-only schema property
    String schemaSource = getEffectiveSchemaSource(doc.uri(), schema.get(), schemaResolver);
    return WorkspaceSymbolKey.schemaMember(schemaSource, typeName, prop.propertyName());
  }

  static WorkspaceSymbolKey resolveMethodSymbolKey(
      VtlAccessStep.MethodCall call, SemanticAnalysisResult analysis) {
    if (analysis == null) return null;
    Optional<?> mResOpt = analysis.methodResolutionOf(call);
    if (mResOpt.isEmpty()) return null;
    Optional<MemberResolutionBridge.MethodResolutionView> viewOpt =
        MemberResolutionBridge.inspectMethodResolution(mResOpt.get());
    if (viewOpt.isEmpty()) return null;
    MemberResolutionBridge.MethodResolutionView mRes = viewOpt.get();
    if (!mRes.isResolved() || mRes.targetMethod().isEmpty()) return null;
    Method m = mRes.targetMethod().get();
    String declaringClass = m.getDeclaringClass().getName();
    String descriptor = computeMethodDescriptor(m);
    return WorkspaceSymbolKey.jvmMember(
        declaringClass,
        JvmMemberSymbolKey.Kind.METHOD,
        m.getName(),
        descriptor,
        m.getParameterCount());
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

  static String computeMethodDescriptor(Method m) {
    if (m == null) return "";
    try {
      return java.lang.invoke.MethodType.methodType(m.getReturnType(), m.getParameterTypes())
          .toMethodDescriptorString();
    } catch (IllegalArgumentException | NullPointerException e) {
      return "";
    }
  }

  static String getEffectiveSchemaSource(
      String uri, CanonicalSchema schema, CanonicalSchemaResolver schemaResolver) {
    Optional<Path> schemaPath = schemaResolver.getSchemaFilePath(uri);
    if (schemaPath.isPresent()) {
      return schemaPath.get().toAbsolutePath().normalize().toString();
    }
    return schema.templateId().isEmpty() ? uri : schema.templateId();
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
}
