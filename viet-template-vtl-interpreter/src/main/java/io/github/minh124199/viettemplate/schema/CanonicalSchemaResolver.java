package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal resolver and registry for canonical template schemas across multiple formats:
 *
 * <ul>
 *   <li>Viet Template Canonical contract schemas ({@code *.vt-schema.json})
 *   <li>JSON Schema Draft 7 / 2020-12 ({@code *.schema.json})
 *   <li>TypeScript declarations ({@code *.d.ts})
 *   <li>Viet Template companion contracts ({@code *.contract})
 * </ul>
 */
public final class CanonicalSchemaResolver {

  public static final String EXPECTED_VT_SCHEMA_FORMAT = "viet-template-contract-schema/1";
  public static final int EXPECTED_VT_SCHEMA_VERSION = 1;

  private static final Set<String> SENSITIVE_PROPERTIES =
      Set.of(
          "class",
          "declaringClass",
          "classLoader",
          "protectionDomain",
          "module",
          "securityManager");

  private final Map<String, CanonicalSchema> schemasById = new ConcurrentHashMap<>();
  private final Map<String, Path> schemaFilePaths = new ConcurrentHashMap<>();
  private volatile Path schemaDirectory = null;
  private final ClassLoader classLoader;
  private final JsonSchemaImporter jsonSchemaImporter = new JsonSchemaImporter();
  private final TypeScriptSchemaImporter typeScriptSchemaImporter = new TypeScriptSchemaImporter();
  private final JavaModelSchemaImporter javaModelSchemaImporter = new JavaModelSchemaImporter();

  public CanonicalSchemaResolver() {
    this(null);
  }

  public CanonicalSchemaResolver(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public void setSchemaDirectory(Path directory) {
    this.schemaDirectory = directory;
  }

  public Optional<Path> schemaDirectory() {
    return Optional.ofNullable(schemaDirectory);
  }

  public void registerSchema(String templateIdOrUri, CanonicalSchema schema) {
    Objects.requireNonNull(templateIdOrUri, "templateIdOrUri must not be null");
    Objects.requireNonNull(schema, "schema must not be null");
    String normKey = normalizeKey(templateIdOrUri);
    schemasById.put(normKey, schema);
    if (!schema.templateId().isBlank()) {
      schemasById.put(normalizeKey(schema.templateId()), schema);
    }
  }

  public void registerSchema(TemplateId templateId, CanonicalSchema schema) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    registerSchema(templateId.value(), schema);
  }

  public void registerSchemaFile(Path schemaPath) throws IOException {
    registerSchemaFile(schemaPath, null);
  }

  public void registerSchemaFile(Path schemaPath, String explicitTemplateId) throws IOException {
    Objects.requireNonNull(schemaPath, "schemaPath must not be null");
    if (!Files.isRegularFile(schemaPath)) {
      return;
    }

    String fileName = schemaPath.getFileName().toString();
    CanonicalSchema schema = null;

    if (fileName.endsWith(".vt-schema.json")) {
      String content = Files.readString(schemaPath, StandardCharsets.UTF_8);
      schema = parseVtSchemaJson(content, explicitTemplateId, schemaPath);
    } else if (fileName.endsWith(".schema.json") || fileName.endsWith(".json")) {
      SchemaImportResult res = jsonSchemaImporter.importPath(schemaPath, explicitTemplateId);
      if (!res.schemas().isEmpty()) {
        schema = res.schemas().values().iterator().next();
      }
    } else if (fileName.endsWith(".d.ts")) {
      SchemaImportResult res = typeScriptSchemaImporter.importPath(schemaPath, explicitTemplateId);
      if (!res.schemas().isEmpty()) {
        schema = res.schemas().values().iterator().next();
      }
    } else if (fileName.endsWith(".contract")) {
      TemplateId tId =
          TemplateId.of(
              explicitTemplateId != null && !explicitTemplateId.isBlank()
                  ? explicitTemplateId
                  : stripExtension(fileName));
      TemplateContract contract = readContractFile(schemaPath, tId, classLoader);
      SchemaImportResult res = javaModelSchemaImporter.importContract(contract);
      if (!res.schemas().isEmpty()) {
        schema = res.schemas().values().iterator().next();
      }
    }

    if (schema != null) {
      String key = normalizeKey(schemaPath.toUri().toString());
      schemasById.put(key, schema);
      schemaFilePaths.put(key, schemaPath);

      if (explicitTemplateId != null && !explicitTemplateId.isBlank()) {
        String expKey = normalizeKey(explicitTemplateId);
        schemasById.put(expKey, schema);
        schemaFilePaths.put(expKey, schemaPath);
      }

      if (!schema.templateId().isBlank()) {
        String tidKey = normalizeKey(schema.templateId());
        schemasById.put(tidKey, schema);
        schemaFilePaths.put(tidKey, schemaPath);
      }

      String base = stripExtension(fileName);
      schemasById.putIfAbsent(normalizeKey(base), schema);
      schemaFilePaths.putIfAbsent(normalizeKey(base), schemaPath);
      schemasById.putIfAbsent(normalizeKey(base + ".vtl"), schema);
      schemaFilePaths.putIfAbsent(normalizeKey(base + ".vtl"), schemaPath);
    }
  }

  public void registerSource(SchemaSource source) {
    Objects.requireNonNull(source, "source must not be null");
    try {
      registerSchemaFile(source.path(), source.templateId().orElse(null));
    } catch (IOException e) {
      // Ignored during bulk registration
    }
  }

  public Optional<CanonicalSchema> resolveSchema(String templateIdOrUri) {
    if (templateIdOrUri == null || templateIdOrUri.isBlank()) {
      return Optional.empty();
    }
    String normKey = normalizeKey(templateIdOrUri);
    CanonicalSchema cached = schemasById.get(normKey);
    if (cached != null) {
      return Optional.of(cached);
    }

    // Try auto-discovery from URI or path
    try {
      if (templateIdOrUri.startsWith("file:/")) {
        Path docPath = Path.of(URI.create(templateIdOrUri));
        return resolveSchema(TemplateId.of(docPath.getFileName().toString()), docPath);
      }
    } catch (IllegalArgumentException | SecurityException ignored) {
    }

    // Check configured schema directory by templateId
    if (schemaDirectory != null && Files.isDirectory(schemaDirectory)) {
      String tid = templateIdOrUri;
      int dot = tid.lastIndexOf('.');
      String base = dot > 0 ? tid.substring(0, dot) : tid;
      for (String ext : List.of(".vt-schema.json", ".schema.json", ".d.ts", ".contract")) {
        Path candidate = schemaDirectory.resolve(base + ext);
        if (Files.isRegularFile(candidate)) {
          try {
            registerSchemaFile(candidate, templateIdOrUri);
            CanonicalSchema s = schemasById.get(normKey);
            if (s != null) {
              return Optional.of(s);
            }
          } catch (IOException ignored) {
          }
        }
      }
    }

    return Optional.empty();
  }

  public Optional<CanonicalSchema> resolveSchema(TemplateId templateId) {
    if (templateId == null) {
      return Optional.empty();
    }
    return resolveSchema(templateId.value());
  }

  public Optional<CanonicalSchema> resolveSchema(TemplateId templateId, Path templateFile) {
    if (templateId != null) {
      String key = normalizeKey(templateId.value());
      CanonicalSchema cached = schemasById.get(key);
      if (cached != null) {
        return Optional.of(cached);
      }
    }

    if (templateFile != null) {
      String normFileKey = normalizeKey(templateFile.toUri().toString());
      CanonicalSchema fileCached = schemasById.get(normFileKey);
      if (fileCached != null) {
        return Optional.of(fileCached);
      }

      // Check siblings in order:
      // 1. <base>.vt-schema.json or <name>.vt-schema.json
      // 2. <base>.schema.json or <name>.schema.json
      // 3. <base>.d.ts or <name>.d.ts
      // 4. <base>.contract or <name>.contract
      String fileName = templateFile.getFileName().toString();
      int dot = fileName.lastIndexOf('.');
      String base = dot > 0 ? fileName.substring(0, dot) : fileName;
      String tidStr = templateId != null ? templateId.value() : fileName;

      List<String> candidates =
          List.of(
              base + ".vt-schema.json",
              fileName + "-schema.json",
              fileName + ".vt-schema.json",
              base + ".schema.json",
              fileName + ".schema.json",
              base + ".d.ts",
              fileName + ".d.ts",
              base + ".contract",
              fileName + ".contract");

      for (String cName : candidates) {
        Path sibling = templateFile.resolveSibling(cName);
        if (Files.isRegularFile(sibling)) {
          try {
            registerSchemaFile(sibling, tidStr);
            CanonicalSchema env = schemasById.get(normalizeKey(sibling.toUri().toString()));
            if (env != null) {
              if (templateId != null) {
                schemasById.put(normalizeKey(templateId.value()), env);
                schemaFilePaths.put(normalizeKey(templateId.value()), sibling);
              }
              schemasById.put(normFileKey, env);
              schemaFilePaths.put(normFileKey, sibling);
              return Optional.of(env);
            }
          } catch (IOException ignored) {
          }
        }
      }

      // Check configured schema directory
      if (schemaDirectory != null && Files.isDirectory(schemaDirectory)) {
        for (String cName : candidates) {
          Path inDir = schemaDirectory.resolve(cName);
          if (Files.isRegularFile(inDir)) {
            try {
              registerSchemaFile(inDir, tidStr);
              CanonicalSchema env = schemasById.get(normalizeKey(inDir.toUri().toString()));
              if (env != null) {
                if (templateId != null) {
                  schemasById.put(normalizeKey(templateId.value()), env);
                  schemaFilePaths.put(normalizeKey(templateId.value()), inDir);
                }
                schemasById.put(normFileKey, env);
                schemaFilePaths.put(normFileKey, inDir);
                return Optional.of(env);
              }
            } catch (IOException ignored) {
            }
          }
        }
      }
    }

    return Optional.empty();
  }

  public Optional<Path> getSchemaFilePath(String templateIdOrUri) {
    if (templateIdOrUri == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(schemaFilePaths.get(normalizeKey(templateIdOrUri)));
  }

  public Optional<Path> getSchemaFilePath(TemplateId templateId) {
    if (templateId == null) {
      return Optional.empty();
    }
    return getSchemaFilePath(templateId.value());
  }

  public Optional<ParameterDef> getParameter(String templateIdOrUri, String paramName) {
    return resolveSchema(templateIdOrUri).map(s -> s.parameters().get(paramName));
  }

  public Optional<TypeDef> getTypeDef(String templateIdOrUri, String typeName) {
    return resolveSchema(templateIdOrUri).map(s -> s.types().get(typeName));
  }

  public Optional<TypeRef> resolveReceiverType(
      String templateIdOrUri, String rootName, List<String> steps) {
    Optional<ParameterDef> rootParam = getParameter(templateIdOrUri, rootName);
    if (rootParam.isEmpty()) {
      return Optional.empty();
    }
    return resolveChainedType(templateIdOrUri, rootParam.get().type(), steps);
  }

  public Optional<TypeRef> resolveChainedType(
      String templateIdOrUri, TypeRef baseType, List<String> steps) {
    if (baseType == null) {
      return Optional.empty();
    }
    if (steps == null || steps.isEmpty()) {
      return Optional.of(baseType);
    }
    if (steps.size() > 64) {
      return Optional.empty();
    }

    TypeRef current = baseType;
    for (String step : steps) {
      String typeKey = extractTypeName(current);
      if (typeKey == null) {
        return Optional.empty();
      }

      Optional<TypeDef> typeDef = getTypeDef(templateIdOrUri, typeKey);
      if (typeDef.isEmpty()) {
        return Optional.empty();
      }
      PropertyDef prop = typeDef.get().properties().get(step);
      if (prop == null) {
        return Optional.empty();
      }
      current = prop.type();
    }
    return Optional.of(current);
  }

  public Map<String, PropertyDef> getAccessibleProperties(
      String templateIdOrUri, TypeRef receiverType, MemberAccessPolicy policy) {
    String typeName = extractTypeName(receiverType);
    if (typeName == null) {
      return Map.of();
    }
    Optional<TypeDef> typeDef = getTypeDef(templateIdOrUri, typeName);
    if (typeDef.isEmpty()) {
      return Map.of();
    }

    Map<String, PropertyDef> result = new TreeMap<>();
    for (Map.Entry<String, PropertyDef> entry : typeDef.get().properties().entrySet()) {
      String propName = entry.getKey();
      if (SENSITIVE_PROPERTIES.contains(propName)) {
        continue;
      }
      if (policy != null) {
        try {
          ClassLoader cl =
              classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
          Class<?> clazz =
              Class.forName(typeName, false, cl != null ? cl : getClass().getClassLoader());
          if (!policy.isClassPermitted(clazz) || !policy.isPropertyPermitted(clazz, propName)) {
            continue;
          }
        } catch (ClassNotFoundException ignored) {
          if (!policy.isClassPermitted(Object.class)
              || !policy.isPropertyPermitted(Object.class, propName)) {
            continue;
          }
        }
      }
      result.put(propName, entry.getValue());
    }
    return Collections.unmodifiableMap(result);
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

  private static String stripExtension(String fileName) {
    int idx = fileName.indexOf('.');
    return idx > 0 ? fileName.substring(0, idx) : fileName;
  }

  public static String normalizeKey(String uriOrId) {
    if (uriOrId == null) {
      return "";
    }
    String s = uriOrId.trim();
    if (s.startsWith("file:/")) {
      try {
        URI u = URI.create(s);
        Path p = Path.of(u).toAbsolutePath().normalize();
        return p.toUri().toString().toLowerCase(Locale.ROOT);
      } catch (IllegalArgumentException | SecurityException ignored) {
      }
    }
    return s.toLowerCase(Locale.ROOT);
  }

  // --- Contract File Parsing ---

  private static TemplateContract readContractFile(
      Path contractFile, TemplateId templateId, ClassLoader classLoader) throws IOException {
    ClassLoader loader =
        classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = CanonicalSchemaResolver.class.getClassLoader();
    }
    TemplateContract.Builder builder = TemplateContract.builder(templateId);
    try (BufferedReader reader = Files.newBufferedReader(contractFile, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();

        if ("class".equalsIgnoreCase(key) || "model".equalsIgnoreCase(key)) {
          try {
            Class<?> clazz = Class.forName(val, false, loader);
            return TemplateContract.fromClass(templateId, clazz);
          } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot resolve model class: " + val, e);
          }
        } else if ("record".equalsIgnoreCase(key)) {
          try {
            Class<?> clazz = Class.forName(val, false, loader);
            return TemplateContract.fromRecord(templateId, clazz);
          } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot resolve model record: " + val, e);
          }
        } else {
          boolean nullable = false;
          String paramName = key;
          if (paramName.startsWith("nullable ")) {
            nullable = true;
            paramName = paramName.substring("nullable ".length()).trim();
          }
          TemplateParameter param = parseContractParameter(paramName, val, nullable, loader);
          builder.parameter(param);
        }
      }
    }
    return builder.build();
  }

  private static TemplateParameter parseContractParameter(
      String name, String typeDesc, boolean nullable, ClassLoader loader) {
    int angle = typeDesc.indexOf('<');
    if (angle < 0) {
      try {
        Class<?> raw = resolveClass(typeDesc.trim(), loader);
        return TemplateParameter.of(name, raw, nullable);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(
            "Cannot resolve parameter type '" + typeDesc + "' for '" + name + "'", e);
      }
    }
    String rawName = typeDesc.substring(0, angle).trim();
    String argsPart = typeDesc.substring(angle + 1, typeDesc.lastIndexOf('>')).trim();
    try {
      Class<?> raw = resolveClass(rawName, loader);
      List<Class<?>> typeArgs = new ArrayList<>();
      for (String arg : splitContractArgs(argsPart)) {
        typeArgs.add(resolveTypeArgument(arg, loader));
      }
      return TemplateParameter.of(name, raw, typeArgs, nullable);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "Cannot resolve parameter type '" + typeDesc + "' for '" + name + "'", e);
    }
  }

  private static List<String> splitContractArgs(String argsPart) {
    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < argsPart.length(); i++) {
      char c = argsPart.charAt(i);
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (c == ',' && depth == 0) {
        result.add(argsPart.substring(start, i).trim());
        start = i + 1;
      }
    }
    if (start < argsPart.length()) {
      result.add(argsPart.substring(start).trim());
    }
    return result;
  }

  private static Class<?> resolveClass(String name, ClassLoader loader)
      throws ClassNotFoundException {
    return switch (name) {
      case "int" -> int.class;
      case "long" -> long.class;
      case "double" -> double.class;
      case "float" -> float.class;
      case "boolean" -> boolean.class;
      case "byte" -> byte.class;
      case "short" -> short.class;
      case "char" -> char.class;
      case "String" -> String.class;
      case "Integer" -> Integer.class;
      case "Long" -> Long.class;
      case "Double" -> Double.class;
      case "Float" -> Float.class;
      case "Boolean" -> Boolean.class;
      case "List" -> List.class;
      case "Map" -> Map.class;
      case "Set" -> Set.class;
      case "Object" -> Object.class;
      default -> Class.forName(name, false, loader);
    };
  }

  private static Class<?> resolveTypeArgument(String arg, ClassLoader loader)
      throws ClassNotFoundException {
    String trimmed = arg.trim();
    if (trimmed.equals("?")) {
      return Object.class;
    }
    if (trimmed.startsWith("? extends ")) {
      return resolveClass(trimmed.substring("? extends ".length()).trim(), loader);
    }
    if (trimmed.startsWith("? super ")) {
      return resolveClass(trimmed.substring("? super ".length()).trim(), loader);
    }
    return resolveClass(trimmed, loader);
  }

  // --- Zero-Dependency *.vt-schema.json Parser ---

  static CanonicalSchema parseVtSchemaJson(
      String json, String explicitTemplateId, Path sourcePath) {
    JsonParser parser = new JsonParser(json);
    Object parsed = parser.parse();
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new IllegalArgumentException("Schema root must be a JSON object");
    }

    String templateId = getString(root, "templateId");
    if (templateId == null || templateId.isBlank()) {
      templateId = explicitTemplateId;
      if (templateId == null || templateId.isBlank()) {
        if (sourcePath != null) {
          templateId = stripExtension(sourcePath.getFileName().toString());
        } else {
          templateId = "anonymous-schema";
        }
      }
    }

    String fingerprint = getString(root, "contractFingerprint");
    if (fingerprint == null) {
      fingerprint = "";
    }

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Object paramsObj = root.get("parameters");
    if (paramsObj instanceof List<?> paramsList) {
      for (Object item : paramsList) {
        if (item instanceof Map<?, ?> pmap) {
          String pName = getString(pmap, "name");
          boolean nullable = getBoolean(pmap, "nullable", false);
          boolean optional = getBoolean(pmap, "optional", false);
          if (pmap.containsKey("required")) {
            optional = !getBoolean(pmap, "required", true);
          }
          TypeRef type = parseTypeRef(pmap.get("type"));
          String doc = getString(pmap, "documentation", "");
          if (pName != null && type != null) {
            parameters.put(pName, new ParameterDef(pName, type, nullable, optional, doc));
          }
        }
      }
    } else if (paramsObj instanceof Map<?, ?> paramsMap) {
      for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
        String pName = entry.getKey().toString();
        if (entry.getValue() instanceof Map<?, ?> pmap) {
          boolean nullable = getBoolean(pmap, "nullable", false);
          boolean optional = getBoolean(pmap, "optional", false);
          if (pmap.containsKey("required")) {
            optional = !getBoolean(pmap, "required", true);
          }
          TypeRef type = parseTypeRef(pmap.get("type"));
          String doc = getString(pmap, "documentation", "");
          if (type != null) {
            parameters.put(pName, new ParameterDef(pName, type, nullable, optional, doc));
          }
        }
      }
    }

    Map<String, TypeDef> types = new TreeMap<>();
    Map<?, ?> typesMap = getMap(root, "types");
    if (typesMap != null) {
      for (Map.Entry<?, ?> entry : typesMap.entrySet()) {
        String tName = entry.getKey().toString();
        if (entry.getValue() instanceof Map<?, ?> tmap) {
          String kind = getString(tmap, "kind");
          kind = kind == null ? "record" : kind;
          Map<String, PropertyDef> properties = new TreeMap<>();
          Object propsObj = tmap.get("properties");
          if (propsObj instanceof List<?> propsList) {
            for (Object pitem : propsList) {
              if (pitem instanceof Map<?, ?> propMap) {
                String propName = getString(propMap, "name");
                boolean propNullable = getBoolean(propMap, "nullable", false);
                boolean propOptional = getBoolean(propMap, "optional", false);
                String propDoc = getString(propMap, "documentation", "");
                TypeRef propType = parseTypeRef(propMap.get("type"));
                if (propName != null && propType != null) {
                  properties.put(
                      propName,
                      new PropertyDef(propName, propType, propNullable, propOptional, propDoc));
                }
              }
            }
          } else if (propsObj instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> pentry : propsMap.entrySet()) {
              String propName = pentry.getKey().toString();
              if (pentry.getValue() instanceof Map<?, ?> propMap) {
                boolean propNullable = getBoolean(propMap, "nullable", false);
                boolean propOptional = getBoolean(propMap, "optional", false);
                String propDoc = getString(propMap, "documentation", "");
                TypeRef propType = parseTypeRef(propMap.get("type"));
                if (propType != null) {
                  properties.put(
                      propName,
                      new PropertyDef(propName, propType, propNullable, propOptional, propDoc));
                }
              }
            }
          }
          types.put(tName, new TypeDef(tName, kind, properties));
        }
      }
    }

    return new CanonicalSchema(
        templateId, SchemaFormat.CONTRACT, fingerprint, parameters, types, json);
  }

  private static TypeRef parseTypeRef(Object typeObj) {
    if (!(typeObj instanceof Map<?, ?> map)) {
      return new DynamicTypeRef();
    }
    String kind = getString(map, "kind");
    if (kind == null) {
      return new DynamicTypeRef();
    }
    return switch (kind) {
      case "primitive" -> {
        String name = getString(map, "primitiveKind", getString(map, "name", "unknown"));
        yield new PrimitiveTypeRef(name);
      }
      case "class" -> {
        String name = getString(map, "className", getString(map, "name", "unknown"));
        yield new ClassTypeRef(name);
      }
      case "parameterized" -> {
        String raw = getString(map, "rawType", "unknown");
        List<TypeRef> args = new ArrayList<>();
        List<?> argList = getList(map, "arguments");
        if (argList == null) {
          argList = getList(map, "typeArguments");
        }
        if (argList != null) {
          for (Object a : argList) {
            args.add(parseTypeRef(a));
          }
        }
        yield new ParameterizedTypeRef(raw, args);
      }
      case "array" -> new ArrayTypeRef(parseTypeRef(map.get("componentType")));
      case "map" -> {
        TypeRef k = parseTypeRef(map.get("keyType"));
        TypeRef v = parseTypeRef(map.get("valueType"));
        yield new MapTypeRef(k, v);
      }
      case "enum" -> {
        String name = getString(map, "name", "unknown");
        List<String> symbols = new ArrayList<>();
        List<?> sList = getList(map, "symbols");
        if (sList != null) {
          for (Object s : sList) {
            symbols.add(s.toString());
          }
        }
        yield new EnumTypeRef(name, symbols);
      }
      case "wildcard" -> {
        String boundKind = getString(map, "boundKind", "extends");
        Object boundObj = map.get("bound");
        Optional<TypeRef> bound =
            boundObj != null ? Optional.of(parseTypeRef(boundObj)) : Optional.empty();
        yield new WildcardTypeRef(boundKind, bound);
      }
      case "named" -> {
        String name = getString(map, "name", "unknown");
        List<TypeRef> args = new ArrayList<>();
        List<?> argList = getList(map, "arguments");
        if (argList == null) {
          argList = getList(map, "typeArguments");
        }
        if (argList != null) {
          for (Object a : argList) {
            args.add(parseTypeRef(a));
          }
        }
        yield new NamedTypeRef(name, args);
      }
      case "dynamic" -> new DynamicTypeRef();
      default -> new DynamicTypeRef();
    };
  }

  private static String getString(Map<?, ?> map, String key) {
    return getString(map, key, null);
  }

  private static String getString(Map<?, ?> map, String key, String defaultVal) {
    Object val = map.get(key);
    return val != null ? val.toString() : defaultVal;
  }

  private static boolean getBoolean(Map<?, ?> map, String key, boolean defaultVal) {
    Object val = map.get(key);
    return val instanceof Boolean b ? b : defaultVal;
  }

  private static List<?> getList(Map<?, ?> map, String key) {
    Object val = map.get(key);
    return val instanceof List<?> l ? l : null;
  }

  private static Map<?, ?> getMap(Map<?, ?> map, String key) {
    Object val = map.get(key);
    return val instanceof Map<?, ?> m ? m : null;
  }

  private static final class JsonParser {
    private final String src;
    private int pos = 0;

    JsonParser(String src) {
      this.src = src != null ? src : "";
    }

    Object parse() {
      skipWhitespace();
      if (pos >= src.length()) {
        return null;
      }
      Object result = parseValue();
      skipWhitespace();
      return result;
    }

    private Object parseValue() {
      skipWhitespace();
      if (pos >= src.length()) {
        throw error("Unexpected end of input");
      }
      char c = src.charAt(pos);
      if (c == '{') return parseObject();
      if (c == '[') return parseArray();
      if (c == '"') return parseString();
      if (c == 't' || c == 'f') return parseBoolean();
      if (c == 'n') return parseNull();
      if (c == '-' || Character.isDigit(c)) return parseNumber();
      throw error("Unexpected character: '" + c + "'");
    }

    private Map<String, Object> parseObject() {
      consume('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == '}') {
        pos++;
        return map;
      }
      while (pos < src.length()) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        consume(':');
        Object value = parseValue();
        map.put(key, value);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else if (pos < src.length() && src.charAt(pos) == '}') {
          pos++;
          break;
        } else {
          throw error("Expected ',' or '}' in object");
        }
      }
      return map;
    }

    private List<Object> parseArray() {
      consume('[');
      List<Object> list = new ArrayList<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == ']') {
        pos++;
        return list;
      }
      while (pos < src.length()) {
        Object item = parseValue();
        list.add(item);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else if (pos < src.length() && src.charAt(pos) == ']') {
          pos++;
          break;
        } else {
          throw error("Expected ',' or ']' in array");
        }
      }
      return list;
    }

    private String parseString() {
      consume('"');
      StringBuilder sb = new StringBuilder();
      while (pos < src.length()) {
        char c = src.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c == '\\') {
          if (pos >= src.length()) throw error("Unterminated escape sequence");
          char esc = src.charAt(pos++);
          switch (esc) {
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case '/' -> sb.append('/');
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
              if (pos + 4 > src.length()) throw error("Invalid unicode escape");
              String hex = src.substring(pos, pos + 4);
              pos += 4;
              sb.append((char) Integer.parseInt(hex, 16));
            }
            default -> sb.append(esc);
          }
        } else {
          sb.append(c);
        }
      }
      throw error("Unterminated string");
    }

    private Boolean parseBoolean() {
      if (src.startsWith("true", pos)) {
        pos += 4;
        return Boolean.TRUE;
      }
      if (src.startsWith("false", pos)) {
        pos += 5;
        return Boolean.FALSE;
      }
      throw error("Invalid boolean literal");
    }

    private Object parseNull() {
      if (src.startsWith("null", pos)) {
        pos += 4;
        return null;
      }
      throw error("Invalid null literal");
    }

    private Number parseNumber() {
      int start = pos;
      if (src.charAt(pos) == '-') pos++;
      while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      boolean isFloat = false;
      if (pos < src.length() && src.charAt(pos) == '.') {
        isFloat = true;
        pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }
      if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
        isFloat = true;
        pos++;
        if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }
      String numStr = src.substring(start, pos);
      if (isFloat) {
        return Double.parseDouble(numStr);
      }
      try {
        return Long.parseLong(numStr);
      } catch (NumberFormatException ignored) {
        return Double.parseDouble(numStr);
      }
    }

    private void consume(char expected) {
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != expected) {
        throw error("Expected '" + expected + "'");
      }
      pos++;
    }

    private void skipWhitespace() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }

    private IllegalArgumentException error(String msg) {
      return new IllegalArgumentException(msg + " at character " + pos);
    }
  }
}
