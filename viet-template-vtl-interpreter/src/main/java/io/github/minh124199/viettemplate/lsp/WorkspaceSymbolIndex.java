package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
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
 * Thread-safe, memory-efficient index powering workspace-wide schema intelligence and symbol search
 * (LSP {@code workspace/symbol}).
 *
 * <p>Enforces zero filesystem walks at query time, deterministic multi-tiered ranking,
 * reference-counted deduplication, and atomic incremental invalidation.
 */
final class WorkspaceSymbolIndex {

  private static final int MAX_RESULTS = 500;
  private static final long MAX_INDEXABLE_TEMPLATE_SIZE = 10 * 1024 * 1024L; // 10MB

  private static final Set<String> SENSITIVE_PROPERTIES =
      Set.of(
          "class",
          "declaringClass",
          "classLoader",
          "protectionDomain",
          "module",
          "securityManager");

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  // Deduplicated symbols indexed by their semantic identity key
  private final Map<Serializable, WorkspaceSymbolEntry> symbolsByIdentity = new HashMap<>();

  // Reference-count tracking: mapping each symbol identity to contributing sources
  private final Map<Serializable, Set<String>> identityToSources = new HashMap<>();

  // Mapping from source key to set of symbol identities contributed by that source
  private final Map<String, Set<Serializable>> sourceToIdentities = new HashMap<>();

  private final CanonicalSchemaResolver schemaResolver;
  private final WorkspaceSchemaIndex schemaIndex;
  private final WorkspaceJavaSourceLocator javaSourceLocator;
  private volatile MemberAccessPolicy memberAccessPolicy;

  WorkspaceSymbolIndex() {
    this(new CanonicalSchemaResolver(), null, MemberAccessPolicy.standard());
  }

  WorkspaceSymbolIndex(CanonicalSchemaResolver schemaResolver, WorkspaceSchemaIndex schemaIndex) {
    this(schemaResolver, schemaIndex, MemberAccessPolicy.standard());
  }

  WorkspaceSymbolIndex(
      CanonicalSchemaResolver schemaResolver,
      WorkspaceSchemaIndex schemaIndex,
      MemberAccessPolicy memberAccessPolicy) {
    this.schemaResolver = schemaResolver != null ? schemaResolver : new CanonicalSchemaResolver();
    this.schemaIndex =
        schemaIndex != null ? schemaIndex : new WorkspaceSchemaIndex(this.schemaResolver);
    this.javaSourceLocator = this.schemaIndex.javaSourceLocator();
    this.memberAccessPolicy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();
  }

  public void setMemberAccessPolicy(MemberAccessPolicy policy) {
    this.memberAccessPolicy = policy != null ? policy : MemberAccessPolicy.standard();
  }

  public MemberAccessPolicy memberAccessPolicy() {
    return memberAccessPolicy;
  }

  // =========================================================================
  // SCHEMA INDEXING
  // =========================================================================

  public void indexSchema(String schemaSource, CanonicalSchema schema, Optional<Path> schemaPath) {
    if (schemaSource == null || schemaSource.isBlank() || schema == null) {
      return;
    }

    String normSourceKey = CanonicalSchemaResolver.normalizeKey(schemaSource);
    if (schemaPath != null && schemaPath.isPresent()) {
      javaSourceLocator.probeSourceRootsFor(schemaPath.get());
    }

    Map<String, SchemaProvenance> provenanceMap = schemaIndex.getOrComputeProvenance(schemaSource);

    ClassLoader cl =
        schemaResolver.classLoader().orElse(Thread.currentThread().getContextClassLoader());
    if (cl == null) {
      cl = getClass().getClassLoader();
    }

    List<WorkspaceSymbolEntry> entries = new ArrayList<>();
    Set<String> processedTypes = new HashSet<>();

    // 1. Process explicit schema types
    for (Map.Entry<String, TypeDef> typeEntry : schema.types().entrySet()) {
      String typeName = typeEntry.getKey();
      TypeDef typeDef = typeEntry.getValue();
      processedTypes.add(typeName);
      indexTypeDef(typeName, typeDef, normSourceKey, schema, provenanceMap, cl, entries);
    }

    // 2. Process root parameters and their referenced types
    for (Map.Entry<String, ParameterDef> paramEntry : schema.parameters().entrySet()) {
      String paramName = paramEntry.getKey();
      ParameterDef paramDef = paramEntry.getValue();

      // Parameter symbol entry
      Optional<LocationInfo> paramLoc = findParameterProvenanceLocation(provenanceMap, paramName);
      if (paramLoc.isPresent()) {
        RootParameterSymbolKey key = WorkspaceSymbolKey.rootParameter(normSourceKey, paramName);
        String container = schema.templateId().isBlank() ? normSourceKey : schema.templateId();
        entries.add(
            new WorkspaceSymbolEntry(
                paramName,
                SymbolKind.VARIABLE,
                container,
                container + "$" + paramName,
                paramLoc.get(),
                "ROOT_PARAMETER",
                key));
      }

      // If parameter references a class type not yet processed, index it
      String refTypeName = extractTypeName(paramDef.type());
      if (refTypeName != null && !processedTypes.contains(refTypeName)) {
        processedTypes.add(refTypeName);
        indexReferencedType(refTypeName, normSourceKey, provenanceMap, cl, entries);
      }
    }

    updateSourceEntries(normSourceKey, entries);
  }

  private void indexTypeDef(
      String typeName,
      TypeDef typeDef,
      String schemaSourceKey,
      CanonicalSchema schema,
      Map<String, SchemaProvenance> provenanceMap,
      ClassLoader cl,
      List<WorkspaceSymbolEntry> entries) {

    Class<?> clazz = null;
    try {
      clazz = Class.forName(typeName, false, cl);
    } catch (ClassNotFoundException ignored) {
    }

    if (clazz != null) {
      indexJvmTypeAndMembers(clazz, schemaSourceKey, provenanceMap, entries);
    } else {
      // Shape-only schema type
      indexShapeOnlyTypeAndProperties(typeName, typeDef, schemaSourceKey, provenanceMap, entries);
    }
  }

  private void indexReferencedType(
      String typeName,
      String schemaSourceKey,
      Map<String, SchemaProvenance> provenanceMap,
      ClassLoader cl,
      List<WorkspaceSymbolEntry> entries) {

    Class<?> clazz = null;
    try {
      clazz = Class.forName(typeName, false, cl);
    } catch (ClassNotFoundException ignored) {
    }

    if (clazz != null) {
      indexJvmTypeAndMembers(clazz, schemaSourceKey, provenanceMap, entries);
    } else {
      Optional<LocationInfo> typeLoc = findProvenanceLocation(provenanceMap, typeName, null);
      if (typeLoc.isPresent()) {
        SchemaTypeSymbolKey key = WorkspaceTypeSymbolKey.schemaType(schemaSourceKey, typeName);
        String simpleName = CanonicalSchemaModel.simpleName(typeName);
        String container =
            typeName.contains(".")
                ? typeName.substring(0, typeName.lastIndexOf('.'))
                : schemaSourceKey;
        entries.add(
            new WorkspaceSymbolEntry(
                simpleName,
                SymbolKind.CLASS,
                container,
                typeName,
                typeLoc.get(),
                "SCHEMA_TYPE",
                key));
      }
    }
  }

  private void indexJvmTypeAndMembers(
      Class<?> clazz,
      String schemaSourceKey,
      Map<String, SchemaProvenance> provenanceMap,
      List<WorkspaceSymbolEntry> entries) {

    String fqcn = clazz.getName();
    String simpleName = CanonicalSchemaModel.simpleName(fqcn);
    int lastDot = fqcn.lastIndexOf('.');
    String containerName = lastDot > 0 ? fqcn.substring(0, lastDot) : "";

    SymbolKind typeKind;
    if (clazz.isInterface()) {
      typeKind = SymbolKind.INTERFACE;
    } else if (clazz.isEnum()) {
      typeKind = SymbolKind.ENUM;
    } else if (clazz.isRecord()) {
      typeKind = SymbolKind.STRUCT;
    } else {
      typeKind = SymbolKind.CLASS;
    }

    // Locate type
    Optional<LocationInfo> typeLoc = javaSourceLocator.locate(JavaSourceSymbolKey.forType(fqcn));
    if (typeLoc.isEmpty()) {
      typeLoc = findProvenanceLocation(provenanceMap, fqcn, null);
      if (typeLoc.isEmpty()) {
        typeLoc = findProvenanceLocation(provenanceMap, simpleName, null);
      }
    }

    if (typeLoc.isPresent()) {
      JvmTypeSymbolKey typeKey = WorkspaceTypeSymbolKey.jvmType(fqcn);
      entries.add(
          new WorkspaceSymbolEntry(
              simpleName, typeKind, containerName, fqcn, typeLoc.get(), "JVM_TYPE", typeKey));
    }

    // Record components
    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      if (components != null) {
        for (RecordComponent rc : components) {
          String propName = rc.getName();
          if (!isPropertyPermitted(clazz, propName)) {
            continue;
          }
          Optional<LocationInfo> loc =
              javaSourceLocator.locate(JavaSourceSymbolKey.forRecordComponent(fqcn, propName));
          if (loc.isEmpty()) {
            loc = findProvenanceLocation(provenanceMap, fqcn, propName);
          }
          if (loc.isPresent()) {
            JvmMemberSymbolKey key =
                JvmMemberSymbolKey.of(fqcn, JvmMemberSymbolKey.Kind.RECORD_COMPONENT, propName);
            entries.add(
                new WorkspaceSymbolEntry(
                    propName,
                    SymbolKind.PROPERTY,
                    simpleName,
                    fqcn + "." + propName,
                    loc.get(),
                    "JVM_MEMBER",
                    key));
          }
        }
      }
    }

    // Public fields
    for (Field f : clazz.getFields()) {
      if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) {
        continue;
      }
      String fieldName = f.getName();
      if (!isFieldPermitted(clazz, f)) {
        continue;
      }
      Optional<LocationInfo> loc =
          javaSourceLocator.locate(JavaSourceSymbolKey.forField(fqcn, fieldName));
      if (loc.isEmpty()) {
        loc = findProvenanceLocation(provenanceMap, fqcn, fieldName);
      }
      if (loc.isPresent()) {
        JvmMemberSymbolKey key =
            JvmMemberSymbolKey.of(fqcn, JvmMemberSymbolKey.Kind.FIELD, fieldName);
        entries.add(
            new WorkspaceSymbolEntry(
                fieldName,
                SymbolKind.FIELD,
                simpleName,
                fqcn + "." + fieldName,
                loc.get(),
                "JVM_MEMBER",
                key));
      }
    }

    // Methods and JavaBean getters
    for (Method m : clazz.getMethods()) {
      if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
        continue;
      }
      if (m.getDeclaringClass() == Object.class) {
        continue;
      }
      if (!isMethodPermitted(clazz, m)) {
        continue;
      }

      String mName = m.getName();
      String propName = null;
      JvmMemberSymbolKey.Kind memberKind = JvmMemberSymbolKey.Kind.METHOD;
      SymbolKind sKind = SymbolKind.METHOD;

      if (m.getParameterCount() == 0) {
        if (mName.startsWith("get") && mName.length() > 3) {
          propName = Character.toLowerCase(mName.charAt(3)) + mName.substring(4);
          memberKind = JvmMemberSymbolKey.Kind.GETTER;
          sKind = SymbolKind.PROPERTY;
        } else if (mName.startsWith("is") && mName.length() > 2) {
          propName = Character.toLowerCase(mName.charAt(2)) + mName.substring(3);
          memberKind = JvmMemberSymbolKey.Kind.BOOLEAN_GETTER;
          sKind = SymbolKind.PROPERTY;
        }
      }

      Optional<LocationInfo> loc =
          javaSourceLocator.locate(
              JavaSourceSymbolKey.forMethod(fqcn, m.getName(), m.getParameterCount()));
      if (loc.isEmpty() && propName != null) {
        loc = findProvenanceLocation(provenanceMap, fqcn, propName);
      }
      if (loc.isEmpty()) {
        loc = findProvenanceLocation(provenanceMap, fqcn, mName);
      }

      if (loc.isPresent()) {
        String descriptor = WorkspaceReferenceIndex.computeMethodDescriptor(m);
        JvmMemberSymbolKey key =
            WorkspaceSymbolKey.jvmMember(
                fqcn, memberKind, m.getName(), descriptor, m.getParameterCount());
        String displayName = propName != null ? propName : m.getName();
        String qualified = fqcn + (propName != null ? "." + propName : "#" + m.getName());
        entries.add(
            new WorkspaceSymbolEntry(
                displayName, sKind, simpleName, qualified, loc.get(), "JVM_MEMBER", key));
      }
    }
  }

  private void indexShapeOnlyTypeAndProperties(
      String typeName,
      TypeDef typeDef,
      String schemaSourceKey,
      Map<String, SchemaProvenance> provenanceMap,
      List<WorkspaceSymbolEntry> entries) {

    String simpleName = CanonicalSchemaModel.simpleName(typeName);
    int lastDot = typeName.lastIndexOf('.');
    String containerName = lastDot > 0 ? typeName.substring(0, lastDot) : schemaSourceKey;

    SymbolKind typeKind =
        switch (typeDef.kind().toLowerCase(Locale.ROOT)) {
          case "interface" -> SymbolKind.INTERFACE;
          case "enum" -> SymbolKind.ENUM;
          case "struct" -> SymbolKind.STRUCT;
          default -> SymbolKind.CLASS;
        };

    Optional<LocationInfo> typeLoc = findProvenanceLocation(provenanceMap, typeName, null);
    if (typeLoc.isPresent()) {
      SchemaTypeSymbolKey key = WorkspaceTypeSymbolKey.schemaType(schemaSourceKey, typeName);
      entries.add(
          new WorkspaceSymbolEntry(
              simpleName, typeKind, containerName, typeName, typeLoc.get(), "SCHEMA_TYPE", key));
    }

    for (Map.Entry<String, PropertyDef> propEntry : typeDef.properties().entrySet()) {
      String propName = propEntry.getKey();
      if (SENSITIVE_PROPERTIES.contains(propName)) {
        continue;
      }
      if (memberAccessPolicy != null
          && !memberAccessPolicy.isPropertyPermitted(Object.class, propName)) {
        continue;
      }

      Optional<LocationInfo> propLoc = findProvenanceLocation(provenanceMap, typeName, propName);
      if (propLoc.isPresent()) {
        SchemaMemberSymbolKey key =
            WorkspaceSymbolKey.schemaMember(schemaSourceKey, typeName, propName);
        entries.add(
            new WorkspaceSymbolEntry(
                propName,
                SymbolKind.PROPERTY,
                simpleName,
                typeName + "." + propName,
                propLoc.get(),
                "SCHEMA_MEMBER",
                key));
      }
    }

    // Enum constants
    for (String enumConst : typeDef.enumConstants()) {
      Optional<LocationInfo> constLoc = findProvenanceLocation(provenanceMap, typeName, enumConst);
      if (constLoc.isPresent()) {
        SchemaMemberSymbolKey key =
            WorkspaceSymbolKey.schemaMember(schemaSourceKey, typeName, enumConst);
        entries.add(
            new WorkspaceSymbolEntry(
                enumConst,
                SymbolKind.ENUM_MEMBER,
                simpleName,
                typeName + "." + enumConst,
                constLoc.get(),
                "SCHEMA_MEMBER",
                key));
      }
    }
  }

  // =========================================================================
  // TEMPLATE MACROS INDEXING
  // =========================================================================

  public void indexTemplateMacros(TemplateDocument doc) {
    if (doc == null) {
      return;
    }
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

    List<WorkspaceSymbolEntry> entries = new ArrayList<>();
    extractMacrosFromNodes(parsed.template().children(), doc, entries);
    updateSourceEntries(doc.uri(), entries);
  }

  private void extractMacrosFromNodes(
      List<VtlNode> nodes, TemplateDocument doc, List<WorkspaceSymbolEntry> out) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlMacroDefinitionNode macroNode) {
        String macroName = macroNode.name();
        Range nameRange = null;
        if (macroNode.span().isKnown() && doc.content() != null) {
          int start = macroNode.span().startOffset();
          int end = Math.min(doc.content().length(), macroNode.span().endOffset());
          String content = doc.content();
          int parenIdx = content.indexOf('(', start);
          if (parenIdx >= 0 && parenIdx < end) {
            int idStart = parenIdx + 1;
            while (idStart < end && Character.isWhitespace(content.charAt(idStart))) {
              idStart++;
            }
            int idEnd = idStart;
            while (idEnd < end
                && (Character.isLetterOrDigit(content.charAt(idEnd))
                    || content.charAt(idEnd) == '_')) {
              idEnd++;
            }
            if (idEnd > idStart) {
              String extractedName = content.substring(idStart, idEnd);
              if (extractedName.equalsIgnoreCase(macroName)) {
                macroName = extractedName;
                nameRange = Range.of(doc.offsetToPosition(idStart), doc.offsetToPosition(idEnd));
              }
            }
          }
        }
        if (nameRange == null) {
          nameRange = doc.spanToRange(macroNode.span());
        }
        String container = extractTemplateBaseName(doc.uri());
        LocationInfo loc = LocationInfo.of(doc.uri(), nameRange);
        TemplateMacroSymbolKey key = new TemplateMacroSymbolKey(doc.uri(), macroName);
        out.add(
            new WorkspaceSymbolEntry(
                macroName,
                SymbolKind.FUNCTION,
                container,
                container + "#" + macroName,
                loc,
                "TEMPLATE_MACRO",
                key));
        extractMacrosFromNodes(macroNode.body(), doc, out);
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          extractMacrosFromNodes(branch.body(), doc, out);
        }
        if (ifNode.elseBody().isPresent()) {
          extractMacrosFromNodes(ifNode.elseBody().get(), doc, out);
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        extractMacrosFromNodes(feNode.body(), doc, out);
        if (feNode.elseBody().isPresent()) {
          extractMacrosFromNodes(feNode.elseBody().get(), doc, out);
        }
      }
    }
  }

  // =========================================================================
  // REMOVAL & INCREMENTAL INVALIDATION
  // =========================================================================

  public void removeSchema(String schemaSource) {
    if (schemaSource != null) {
      removeSource(CanonicalSchemaResolver.normalizeKey(schemaSource));
    }
  }

  public void removeTemplate(String templateUri) {
    if (templateUri != null) {
      removeSource(templateUri);
    }
  }

  public void removeSource(String sourceKey) {
    if (sourceKey == null) return;
    rwLock.writeLock().lock();
    try {
      Set<Serializable> oldIdentities = sourceToIdentities.remove(sourceKey);
      if (oldIdentities != null) {
        for (Serializable id : oldIdentities) {
          Set<String> sources = identityToSources.get(id);
          if (sources != null) {
            sources.remove(sourceKey);
            if (sources.isEmpty()) {
              identityToSources.remove(id);
              symbolsByIdentity.remove(id);
            }
          }
        }
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void onJavaFileChanged(Path path) {
    if (path == null) return;
    String fileUri = path.toAbsolutePath().normalize().toUri().toString();
    rwLock.writeLock().lock();
    try {
      List<WorkspaceSymbolEntry> toUpdate = new ArrayList<>();
      for (WorkspaceSymbolEntry entry : symbolsByIdentity.values()) {
        if (entry.location().uri().equals(fileUri)) {
          toUpdate.add(entry);
        }
      }
      for (WorkspaceSymbolEntry oldEntry : toUpdate) {
        if (oldEntry.symbolIdentity() instanceof JvmTypeSymbolKey typeKey) {
          Optional<LocationInfo> loc =
              javaSourceLocator.locate(JavaSourceSymbolKey.forType(typeKey.className()));
          if (loc.isPresent()) {
            symbolsByIdentity.put(
                typeKey,
                new WorkspaceSymbolEntry(
                    oldEntry.name(),
                    oldEntry.kind(),
                    oldEntry.containerName(),
                    oldEntry.qualifiedName(),
                    loc.get(),
                    oldEntry.sourceKind(),
                    typeKey));
          }
        } else if (oldEntry.symbolIdentity() instanceof JvmMemberSymbolKey mKey) {
          Optional<LocationInfo> loc =
              switch (mKey.memberKind()) {
                case RECORD_COMPONENT ->
                    javaSourceLocator.locate(
                        JavaSourceSymbolKey.forRecordComponent(
                            mKey.declaringClassName(), mKey.memberName()));
                case FIELD ->
                    javaSourceLocator.locate(
                        JavaSourceSymbolKey.forField(mKey.declaringClassName(), mKey.memberName()));
                default ->
                    javaSourceLocator.locate(
                        JavaSourceSymbolKey.forMethod(
                            mKey.declaringClassName(), mKey.memberName(), mKey.parameterCount()));
              };
          if (loc.isPresent()) {
            symbolsByIdentity.put(
                mKey,
                new WorkspaceSymbolEntry(
                    oldEntry.name(),
                    oldEntry.kind(),
                    oldEntry.containerName(),
                    oldEntry.qualifiedName(),
                    loc.get(),
                    oldEntry.sourceKind(),
                    mKey));
          }
        }
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void onJavaFileDeleted(Path path) {
    if (path == null) return;
    String fileUri = path.toAbsolutePath().normalize().toUri().toString();
    rwLock.writeLock().lock();
    try {
      List<Serializable> toRemove = new ArrayList<>();
      for (Map.Entry<Serializable, WorkspaceSymbolEntry> entry : symbolsByIdentity.entrySet()) {
        if (entry.getValue().location().uri().equals(fileUri)) {
          toRemove.add(entry.getKey());
        }
      }
      for (Serializable id : toRemove) {
        symbolsByIdentity.remove(id);
        identityToSources.remove(id);
        for (Set<Serializable> idSet : sourceToIdentities.values()) {
          idSet.remove(id);
        }
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void clear() {
    rwLock.writeLock().lock();
    try {
      symbolsByIdentity.clear();
      identityToSources.clear();
      sourceToIdentities.clear();
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public int symbolCount() {
    rwLock.readLock().lock();
    try {
      return symbolsByIdentity.size();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  public int sourceCount() {
    rwLock.readLock().lock();
    try {
      return sourceToIdentities.size();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  // =========================================================================
  // WORKSPACE SCANNING
  // =========================================================================

  public void scanWorkspace(Path workspaceRoot) {
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
                String name = dir.getFileName().toString();
                if (WorkspaceJavaSourceLocator.IGNORED_DIRS.contains(name)) {
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
                    indexTemplateMacros(doc);
                  } catch (IOException ignored) {
                  }
                }
              } else if (name.endsWith(".vt-schema.json")
                  || name.endsWith(".schema.json")
                  || name.endsWith(".d.ts")
                  || name.endsWith(".contract")) {
                try {
                  schemaResolver.registerSchemaFile(file);
                  schemaResolver
                      .resolveSchema(file.toUri().toString())
                      .ifPresent(
                          s -> {
                            indexSchema(file.toUri().toString(), s, Optional.of(file));
                          });
                } catch (IOException ignored) {
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
  // SEARCH & RANKING (IN-MEMORY ONLY)
  // =========================================================================

  public List<SymbolInformation> search(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }

    String q = query.trim();
    String qLower = q.toLowerCase(Locale.ROOT);

    rwLock.readLock().lock();
    try {
      List<ScoredSymbol> scored = new ArrayList<>();
      for (WorkspaceSymbolEntry entry : symbolsByIdentity.values()) {
        int tier = computeMatchTier(entry, q, qLower);
        if (tier > 0) {
          scored.add(new ScoredSymbol(entry, tier));
        }
      }

      if (scored.isEmpty()) {
        return List.of();
      }

      Collections.sort(scored, SYMBOL_COMPARATOR);

      int limit = Math.min(scored.size(), MAX_RESULTS);
      List<SymbolInformation> result = new ArrayList<>(limit);
      for (int i = 0; i < limit; i++) {
        result.add(scored.get(i).entry().toSymbolInformation());
      }
      return Collections.unmodifiableList(result);
    } finally {
      rwLock.readLock().unlock();
    }
  }

  static int computeMatchTier(WorkspaceSymbolEntry entry, String query, String queryLower) {
    String name = entry.name();
    String qName = entry.qualifiedName();
    boolean member = isMemberKind(entry.kind());
    boolean hasSep = hasQualifierSeparator(query);

    // Tier 1: exact case-sensitive simple name
    if (name.equals(query)) {
      return 1;
    }
    // Tier 2: exact case-insensitive simple name
    if (name.equalsIgnoreCase(query)) {
      return 2;
    }
    // Tier 3: qualified exact
    if ((!member || hasSep) && qName.equalsIgnoreCase(query)) {
      return 3;
    }
    // Tier 4: prefix simple case-sensitive
    if (name.startsWith(query)) {
      return 4;
    }
    // Tier 5: prefix simple case-insensitive
    if (name.toLowerCase(Locale.ROOT).startsWith(queryLower)) {
      return 5;
    }
    // Tier 6: prefix qualified
    if ((!member || hasSep) && qName.toLowerCase(Locale.ROOT).startsWith(queryLower)) {
      return 6;
    }
    // Tier 7: substring simple
    if (name.toLowerCase(Locale.ROOT).contains(queryLower)) {
      return 7;
    }
    // Tier 8: substring qualified
    if ((!member || hasSep) && qName.toLowerCase(Locale.ROOT).contains(queryLower)) {
      return 8;
    }
    // Tier 9: camelCase
    if (matchesCamelCase(name, query) || ((!member || hasSep) && matchesCamelCase(qName, query))) {
      return 9;
    }

    return 0; // Not a match
  }

  private static boolean isMemberKind(SymbolKind kind) {
    return kind == SymbolKind.PROPERTY
        || kind == SymbolKind.FIELD
        || kind == SymbolKind.METHOD
        || kind == SymbolKind.ENUM_MEMBER;
  }

  private static boolean hasQualifierSeparator(String s) {
    if (s == null) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '.' || c == '#' || c == '$' || c == '/' || c == ':') {
        return true;
      }
    }
    return false;
  }

  static boolean matchesCamelCase(String name, String query) {
    if (name == null || query == null || query.isEmpty() || name.isEmpty()) {
      return false;
    }
    int queryIdx = 0;
    int nameIdx = 0;
    while (queryIdx < query.length() && nameIdx < name.length()) {
      char q = Character.toLowerCase(query.charAt(queryIdx));
      boolean found = false;
      while (nameIdx < name.length()) {
        char n = name.charAt(nameIdx);
        boolean isWordBoundary =
            (nameIdx == 0)
                || Character.isUpperCase(n)
                || (nameIdx > 0 && isWordSeparator(name.charAt(nameIdx - 1)));
        if (isWordBoundary && Character.toLowerCase(n) == q) {
          found = true;
          nameIdx++;
          queryIdx++;
          break;
        }
        nameIdx++;
      }
      if (!found) {
        return false;
      }
    }
    return queryIdx == query.length();
  }

  private static boolean isWordSeparator(char c) {
    return c == '.' || c == '_' || c == '-' || c == '$' || c == '#' || c == ' ' || c == '/';
  }

  record ScoredSymbol(WorkspaceSymbolEntry entry, int matchTier) {}

  static final Comparator<ScoredSymbol> SYMBOL_COMPARATOR =
      (a, b) -> {
        // 1. match tier (lower tier number has priority)
        int c = Integer.compare(a.matchTier(), b.matchTier());
        if (c != 0) return c;

        // 2. kind priority
        c = Integer.compare(kindPriority(a.entry().kind()), kindPriority(b.entry().kind()));
        if (c != 0) return c;

        // 3. simple name length (shorter simple name first)
        c = Integer.compare(a.entry().name().length(), b.entry().name().length());
        if (c != 0) return c;

        // 4. qualified name
        c = a.entry().qualifiedName().compareTo(b.entry().qualifiedName());
        if (c != 0) return c;

        // 5. URI
        c = a.entry().location().uri().compareTo(b.entry().location().uri());
        if (c != 0) return c;

        // 6. Range
        return a.entry().location().range().compareTo(b.entry().location().range());
      };

  static int kindPriority(SymbolKind kind) {
    return switch (kind) {
      case CLASS -> 1;
      case INTERFACE -> 2;
      case STRUCT -> 3;
      case ENUM -> 4;
      case FUNCTION -> 5; // Macro
      case PROPERTY -> 6;
      case FIELD -> 7;
      case METHOD -> 8;
      case VARIABLE -> 9; // Root parameter
      case ENUM_MEMBER -> 10;
      default -> 11;
    };
  }

  // =========================================================================
  // HELPER METHODS
  // =========================================================================

  private void updateSourceEntries(String sourceKey, List<WorkspaceSymbolEntry> newEntries) {
    rwLock.writeLock().lock();
    try {
      Set<Serializable> oldIdentities = sourceToIdentities.remove(sourceKey);
      if (oldIdentities != null) {
        for (Serializable id : oldIdentities) {
          Set<String> sources = identityToSources.get(id);
          if (sources != null) {
            sources.remove(sourceKey);
            if (sources.isEmpty()) {
              identityToSources.remove(id);
              symbolsByIdentity.remove(id);
            }
          }
        }
      }

      if (newEntries != null && !newEntries.isEmpty()) {
        Set<Serializable> identities = new HashSet<>();
        for (WorkspaceSymbolEntry entry : newEntries) {
          Serializable id = entry.symbolIdentity();
          symbolsByIdentity.put(id, entry);
          identityToSources.computeIfAbsent(id, k -> new HashSet<>()).add(sourceKey);
          identities.add(id);
        }
        sourceToIdentities.put(sourceKey, identities);
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  private Optional<LocationInfo> findProvenanceLocation(
      Map<String, SchemaProvenance> provenanceMap, String typeName, String propName) {
    if (provenanceMap == null || typeName == null) {
      return Optional.empty();
    }
    Optional<SchemaProvenance> provOpt;
    if (propName != null) {
      provOpt = SchemaProvenanceExtractor.findProperty(provenanceMap, typeName, propName);
    } else {
      provOpt = SchemaProvenanceExtractor.findType(provenanceMap, typeName);
    }
    if (provOpt.isPresent() && provOpt.get().location().isPresent()) {
      SchemaSourceLocation ssl = provOpt.get().location().get();
      Range r =
          Range.of(
              Math.max(0, ssl.startLine() - 1),
              Math.max(0, ssl.startColumn() - 1),
              Math.max(0, ssl.endLine() - 1),
              Math.max(0, ssl.endColumn() - 1));
      return Optional.of(LocationInfo.of(ssl.filePath().toUri().toString(), r));
    }
    return Optional.empty();
  }

  private Optional<LocationInfo> findParameterProvenanceLocation(
      Map<String, SchemaProvenance> provenanceMap, String paramName) {
    if (provenanceMap == null || paramName == null) {
      return Optional.empty();
    }
    Optional<SchemaProvenance> provOpt =
        SchemaProvenanceExtractor.findParameter(provenanceMap, paramName);
    if (provOpt.isPresent() && provOpt.get().location().isPresent()) {
      SchemaSourceLocation ssl = provOpt.get().location().get();
      Range r =
          Range.of(
              Math.max(0, ssl.startLine() - 1),
              Math.max(0, ssl.startColumn() - 1),
              Math.max(0, ssl.endLine() - 1),
              Math.max(0, ssl.endColumn() - 1));
      return Optional.of(LocationInfo.of(ssl.filePath().toUri().toString(), r));
    }
    return Optional.empty();
  }

  private boolean isPropertyPermitted(Class<?> clazz, String propertyName) {
    if (clazz == null || propertyName == null) return false;
    if (SENSITIVE_PROPERTIES.contains(propertyName)) return false;
    MemberAccessPolicy policy = memberAccessPolicy;
    if (policy != null) {
      if (!policy.isClassPermitted(clazz) || !policy.isPropertyPermitted(clazz, propertyName)) {
        return false;
      }
      Optional<MemberResolutionBridge.MemberResolutionView> viewOpt =
          MemberResolutionBridge.resolveProperty(clazz, propertyName, policy);
      if (viewOpt.isPresent()) {
        MemberResolutionBridge.MemberResolutionView view = viewOpt.get();
        if (!view.isFound()
            || "DYNAMIC".equals(view.kindName())
            || "DENIED".equals(view.kindName())) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean isMethodPermitted(Class<?> clazz, Method m) {
    if (clazz == null || m == null) return false;
    MemberAccessPolicy policy = memberAccessPolicy;
    if (policy != null) {
      return policy.isMethodPermitted(clazz, m);
    }
    return true;
  }

  private boolean isFieldPermitted(Class<?> clazz, Field f) {
    if (clazz == null || f == null) return false;
    MemberAccessPolicy policy = memberAccessPolicy;
    if (policy != null) {
      return policy.isFieldPermitted(clazz, f);
    }
    return true;
  }

  private static String extractTemplateBaseName(String uri) {
    try {
      if (uri.startsWith("file:/")) {
        return Path.of(URI.create(uri)).getFileName().toString();
      }
    } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
    }
    int lastSlash = uri.lastIndexOf('/');
    return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
  }

  private static String extractTypeName(TypeRef type) {
    if (type instanceof ClassTypeRef ctr) {
      return ctr.name();
    }
    if (type instanceof NamedTypeRef ntr) {
      return ntr.name();
    }
    if (type instanceof ParameterizedTypeRef ptr) {
      return ptr.rawType();
    }
    return null;
  }
}
