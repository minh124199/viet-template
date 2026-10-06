package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Zero-dependency, offline JSON Schema importer supporting Draft 7 and 2020-12 compatible subsets.
 * Produces deterministic canonical schema models and reports detailed line/column diagnostics.
 */
public final class JsonSchemaImporter implements SchemaImporter {

  public static final String CODE_SYNTAX_ERROR = "JSON_SCHEMA_SYNTAX_ERROR";
  public static final String CODE_UNSUPPORTED_REMOTE_REF = "JSON_SCHEMA_UNSUPPORTED_REMOTE_REF";
  public static final String CODE_UNRESOLVED_REFERENCE = "SCHEMA_UNRESOLVED_REFERENCE";

  @Override
  public SchemaImportResult importSchemas(SchemaImportRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Map<String, CanonicalSchema> schemas = new TreeMap<>();
    List<SchemaDiagnostic> allDiagnostics = new ArrayList<>();

    for (SchemaSource source : request.sources()) {
      ImportContext ctx = new ImportContext(source.path().toString());
      try {
        if (!Files.isRegularFile(source.path())) {
          ctx.diagnostics.add(
              SchemaDiagnostic.error(
                  source.path().toString(),
                  1,
                  1,
                  SchemaFormat.JSON_SCHEMA,
                  CODE_SYNTAX_ERROR,
                  "File does not exist or is not a regular file: " + source.path(),
                  "Check schema file path"));
          allDiagnostics.addAll(ctx.diagnostics);
          continue;
        }

        String rawJson = Files.readString(source.path(), StandardCharsets.UTF_8);
        Optional<CanonicalSchema> schemaOpt =
            importInternal(rawJson, source.templateId().orElse(null), source.path(), ctx);
        schemaOpt.ifPresent(s -> schemas.put(s.templateId(), s));
      } catch (IOException e) {
        ctx.diagnostics.add(
            SchemaDiagnostic.error(
                source.path().toString(),
                1,
                1,
                SchemaFormat.JSON_SCHEMA,
                CODE_SYNTAX_ERROR,
                "I/O error reading schema file: " + e.getMessage(),
                "Ensure file is readable"));
      }
      allDiagnostics.addAll(ctx.diagnostics);
    }

    boolean hasErrors =
        allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    if (request.failOnWarning()
        && allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.WARNING)) {
      hasErrors = true;
    }
    boolean isPartial = hasErrors && !schemas.isEmpty();
    return new SchemaImportResult(schemas, allDiagnostics, hasErrors, isPartial);
  }

  /** Convenience helper to import schema from raw JSON text. */
  public SchemaImportResult importString(String jsonContent, String templateId) {
    Objects.requireNonNull(jsonContent, "jsonContent must not be null");
    ImportContext ctx = new ImportContext("<in-memory>");
    Optional<CanonicalSchema> schemaOpt = importInternal(jsonContent, templateId, null, ctx);
    Map<String, CanonicalSchema> schemas = new TreeMap<>();
    schemaOpt.ifPresent(s -> schemas.put(s.templateId(), s));

    boolean hasErrors =
        ctx.diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    boolean isPartial = hasErrors && !schemas.isEmpty();
    return new SchemaImportResult(schemas, ctx.diagnostics, hasErrors, isPartial);
  }

  /** Convenience helper to import schema from a single file path. */
  public SchemaImportResult importPath(Path schemaPath) {
    return importSource(new SchemaSource(schemaPath, SchemaFormat.JSON_SCHEMA));
  }

  /** Convenience helper to import schema from a single file path with explicit template ID. */
  public SchemaImportResult importPath(Path schemaPath, String templateId) {
    return importSource(new SchemaSource(schemaPath, SchemaFormat.JSON_SCHEMA, templateId));
  }

  // --- Core Import Engine ---

  private Optional<CanonicalSchema> importInternal(
      String rawJson, String explicitTemplateId, Path sourcePath, ImportContext ctx) {
    JsonNode parsedRoot;
    try {
      JsonParser parser = new JsonParser(rawJson);
      parsedRoot = parser.parse();
    } catch (JsonParseException e) {
      ctx.diagnostics.add(
          SchemaDiagnostic.error(
              ctx.sourcePath,
              e.location.line(),
              e.location.column(),
              SchemaFormat.JSON_SCHEMA,
              CODE_SYNTAX_ERROR,
              e.getMessage(),
              "Fix JSON syntax error at line "
                  + e.location.line()
                  + ", column "
                  + e.location.column()));
      return Optional.empty();
    }

    if (!(parsedRoot instanceof JsonObjectNode rootObj)) {
      ctx.diagnostics.add(
          SchemaDiagnostic.error(
              ctx.sourcePath,
              parsedRoot != null ? parsedRoot.location().line() : 1,
              parsedRoot != null ? parsedRoot.location().column() : 1,
              SchemaFormat.JSON_SCHEMA,
              CODE_SYNTAX_ERROR,
              "Schema root must be a JSON object",
              "Enclose schema content in '{ ... }'"));
      return Optional.empty();
    }

    ctx.rootObj = rootObj;

    // Determine templateId
    String templateId = explicitTemplateId;
    if (templateId == null || templateId.isBlank()) {
      templateId = rootObj.getString("$id");
      if (templateId == null || templateId.isBlank()) {
        templateId = rootObj.getString("id");
      }
      if (templateId == null || templateId.isBlank()) {
        templateId = rootObj.getString("title");
      }
      if (templateId == null || templateId.isBlank()) {
        if (sourcePath != null) {
          String fileName = sourcePath.getFileName().toString();
          if (fileName.endsWith(".schema.json")) {
            templateId = fileName.substring(0, fileName.length() - ".schema.json".length());
          } else if (fileName.endsWith(".json")) {
            templateId = fileName.substring(0, fileName.length() - ".json".length());
          } else {
            templateId = fileName;
          }
        } else {
          templateId = "anonymous-schema";
        }
      }
    }

    String fingerprint = computeFingerprint(rawJson);

    // 1. Process definitions ($defs and definitions)
    processDefinitions(rootObj, "$defs", ctx);
    processDefinitions(rootObj, "definitions", ctx);

    // 2. Process root schema parameters
    String rootTitle = rootObj.getString("title");
    if (rootTitle == null || rootTitle.isBlank()) {
      rootTitle = sanitizeTypeName(templateId);
    }
    ctx.rootTitle = rootTitle;
    ctx.resolvingPointers.add("#");

    Map<String, ParameterDef> parameters = new TreeMap<>();
    JsonObjectNode propertiesObj = rootObj.getObject("properties");
    if (propertiesObj != null) {
      for (Map.Entry<String, JsonNode> entry : propertiesObj.properties().entrySet()) {
        String propName = entry.getKey();
        if (entry.getValue() instanceof JsonObjectNode propObj) {
          boolean nullable = isNullable(propObj);
          boolean optional = isOptional(rootObj, propName);
          String doc = propObj.getString("description");
          TypeRef type = parseTypeRef(propObj, capitalize(propName), null, ctx);
          parameters.put(
              propName,
              new ParameterDef(propName, type, nullable, optional, doc != null ? doc : ""));
        }
      }
    }

    // If root was self-referenced, register its TypeDef
    if (ctx.rootReferenced) {
      Map<String, PropertyDef> rootProperties = new TreeMap<>();
      for (Map.Entry<String, ParameterDef> entry : parameters.entrySet()) {
        ParameterDef p = entry.getValue();
        rootProperties.put(
            p.name(),
            new PropertyDef(p.name(), p.type(), p.nullable(), p.optional(), p.documentation()));
      }
      TypeDef rootTypeDef =
          new TypeDef(
              rootTitle,
              "object",
              rootProperties,
              List.of(),
              Optional.empty(),
              rootObj.getString("description"));
      ctx.types.put(rootTitle, rootTypeDef);
    }
    ctx.resolvingPointers.remove("#");

    CanonicalSchema canonicalSchema =
        new CanonicalSchema(
            templateId, SchemaFormat.JSON_SCHEMA, fingerprint, parameters, ctx.types, rawJson);

    return Optional.of(canonicalSchema);
  }

  private void processDefinitions(JsonObjectNode rootObj, String defKey, ImportContext ctx) {
    JsonObjectNode defsObj = rootObj.getObject(defKey);
    if (defsObj == null) {
      return;
    }
    for (Map.Entry<String, JsonNode> entry : defsObj.properties().entrySet()) {
      String defName = entry.getKey();
      String pointer = "#/" + defKey + "/" + defName;
      if (entry.getValue() instanceof JsonObjectNode targetObj) {
        if (!ctx.resolvedDefinitions.containsKey(pointer)
            && !ctx.resolvingPointers.contains(pointer)) {
          resolveDefinition(pointer, targetObj, defName, ctx);
        }
      }
    }
  }

  private TypeRef resolveDefinition(
      String pointer, JsonObjectNode targetObj, String defName, ImportContext ctx) {
    if (ctx.resolvedDefinitions.containsKey(pointer)) {
      return ctx.resolvedDefinitions.get(pointer);
    }
    if (ctx.resolvingPointers.contains(pointer)) {
      return new NamedTypeRef(defName, List.of());
    }

    ctx.resolvingPointers.add(pointer);
    TypeRef resolved = parseTypeRef(targetObj, defName, pointer, ctx);
    ctx.resolvingPointers.remove(pointer);
    ctx.resolvedDefinitions.put(pointer, resolved);
    return resolved;
  }

  private TypeRef parseTypeRef(
      JsonObjectNode schemaObj, String contextName, String currentPointer, ImportContext ctx) {
    if (schemaObj == null) {
      return new DynamicTypeRef();
    }

    // 1. $ref check
    JsonNode refNode = schemaObj.get("$ref");
    if (refNode instanceof JsonStringNode refStrNode) {
      return resolveRef(refStrNode.value(), schemaObj.getPropertyLocation("$ref"), ctx);
    }

    // 2. allOf check (combine schemas)
    JsonArrayNode allOfArr = schemaObj.getArray("allOf");
    if (allOfArr != null && !allOfArr.elements().isEmpty()) {
      return parseAllOf(allOfArr, contextName, ctx);
    }

    // 3. oneOf or anyOf check (union)
    JsonArrayNode oneOfArr = schemaObj.getArray("oneOf");
    if (oneOfArr == null) {
      oneOfArr = schemaObj.getArray("anyOf");
    }
    if (oneOfArr != null && !oneOfArr.elements().isEmpty()) {
      return parseUnion(oneOfArr, contextName, ctx);
    }

    // 4. enum check
    JsonArrayNode enumArr = schemaObj.getArray("enum");
    if (enumArr != null) {
      return parseEnum(enumArr, schemaObj, contextName, ctx);
    }

    // 5. const check
    JsonNode constNode = schemaObj.get("const");
    if (constNode != null) {
      return parseConst(constNode, schemaObj, contextName, ctx);
    }

    // 6. type check
    JsonNode typeNode = schemaObj.get("type");
    if (typeNode instanceof JsonArrayNode typeArr) {
      return parseTypeArray(typeArr, schemaObj, contextName, ctx);
    }

    String typeStr = schemaObj.getString("type");
    if (typeStr == null) {
      // Inferred type from properties, items, additionalProperties
      if (schemaObj.getObject("properties") != null) {
        typeStr = "object";
      } else if (schemaObj.get("items") != null) {
        typeStr = "array";
      } else if (schemaObj.get("additionalProperties") != null) {
        typeStr = "object";
      } else {
        return new DynamicTypeRef();
      }
    }

    return switch (typeStr) {
      case "string" -> new PrimitiveTypeRef("string");
      case "integer" -> new PrimitiveTypeRef("integer");
      case "number" -> new PrimitiveTypeRef("number");
      case "boolean" -> new PrimitiveTypeRef("boolean");
      case "null" -> new PrimitiveTypeRef("null");
      case "array" -> parseArray(schemaObj, contextName, ctx);
      case "object" -> parseObject(schemaObj, contextName, ctx);
      default -> new PrimitiveTypeRef(typeStr);
    };
  }

  private TypeRef resolveRef(String ref, JsonLocation refLoc, ImportContext ctx) {
    // Check remote reference
    if (ref.startsWith("http://")
        || ref.startsWith("https://")
        || ref.startsWith("ftp://")
        || ref.startsWith("//")) {
      ctx.diagnostics.add(
          SchemaDiagnostic.error(
              ctx.sourcePath,
              refLoc.line(),
              refLoc.column(),
              SchemaFormat.JSON_SCHEMA,
              CODE_UNSUPPORTED_REMOTE_REF,
              "Remote schema reference '" + ref + "' is unsupported in offline mode",
              "Use local schema definitions ($defs) or import the referenced schema locally"));
      return new DynamicTypeRef();
    }

    // Check root reference
    if ("#".equals(ref)) {
      ctx.rootReferenced = true;
      String rootName = ctx.rootTitle != null ? ctx.rootTitle : "Root";
      return new NamedTypeRef(rootName, List.of());
    }

    // Check local pointer reference
    if (ref.startsWith("#/")) {
      if (ctx.resolvingPointers.contains(ref)) {
        // Safe cycle break
        String[] parts = ref.substring(2).split("/");
        String typeName = parts[parts.length - 1];
        return new NamedTypeRef(typeName, List.of());
      }

      if (ctx.resolvedDefinitions.containsKey(ref)) {
        return ctx.resolvedDefinitions.get(ref);
      }

      JsonNode target = navigatePointer(ctx.rootObj, ref.substring(2));
      if (target instanceof JsonObjectNode targetObj) {
        String[] parts = ref.substring(2).split("/");
        String defName = parts[parts.length - 1];
        return resolveDefinition(ref, targetObj, defName, ctx);
      } else {
        ctx.diagnostics.add(
            SchemaDiagnostic.error(
                ctx.sourcePath,
                refLoc.line(),
                refLoc.column(),
                SchemaFormat.JSON_SCHEMA,
                CODE_UNRESOLVED_REFERENCE,
                "Unresolved local schema reference: '" + ref + "'",
                "Ensure referenced definition exists in $defs or definitions"));
        return new DynamicTypeRef();
      }
    }

    // Non-local, non-http unrecognized reference
    ctx.diagnostics.add(
        SchemaDiagnostic.error(
            ctx.sourcePath,
            refLoc.line(),
            refLoc.column(),
            SchemaFormat.JSON_SCHEMA,
            CODE_UNRESOLVED_REFERENCE,
            "Unsupported or unresolved reference syntax: '" + ref + "'",
            "Use '#/$defs/...' local references"));
    return new DynamicTypeRef();
  }

  private JsonNode navigatePointer(JsonObjectNode root, String pointer) {
    String[] rawParts = pointer.split("/");
    JsonNode current = root;
    for (String raw : rawParts) {
      String part = raw.replace("~1", "/").replace("~0", "~");
      if (current instanceof JsonObjectNode o) {
        current = o.get(part);
      } else if (current instanceof JsonArrayNode a) {
        try {
          int idx = Integer.parseInt(part);
          if (idx >= 0 && idx < a.elements().size()) {
            current = a.elements().get(idx);
          } else {
            return null;
          }
        } catch (NumberFormatException e) {
          return null;
        }
      } else {
        return null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private TypeRef parseArray(JsonObjectNode schemaObj, String contextName, ImportContext ctx) {
    JsonNode itemsNode = schemaObj.get("items");
    if (itemsNode instanceof JsonObjectNode itemsObj) {
      String childName = contextName != null ? contextName + "Item" : null;
      TypeRef itemType = parseTypeRef(itemsObj, childName, null, ctx);
      return new ArrayTypeRef(itemType);
    }
    return new ArrayTypeRef(new DynamicTypeRef());
  }

  private TypeRef parseObject(JsonObjectNode schemaObj, String contextName, ImportContext ctx) {
    JsonObjectNode propsNode = schemaObj.getObject("properties");
    JsonNode addlPropsNode = schemaObj.get("additionalProperties");
    boolean hasProperties = propsNode != null && !propsNode.properties().isEmpty();

    // Map case: no explicit properties, but has additionalProperties schema
    if (!hasProperties && addlPropsNode != null) {
      TypeRef valueType;
      if (addlPropsNode instanceof JsonObjectNode addlObj) {
        valueType =
            parseTypeRef(addlObj, contextName != null ? contextName + "Value" : null, null, ctx);
      } else {
        valueType = new DynamicTypeRef();
      }
      return new MapTypeRef(new PrimitiveTypeRef("string"), valueType);
    }

    // Standard object case: parse properties
    Map<String, PropertyDef> properties = new TreeMap<>();
    if (propsNode != null) {
      for (Map.Entry<String, JsonNode> entry : propsNode.properties().entrySet()) {
        String pName = entry.getKey();
        if (entry.getValue() instanceof JsonObjectNode pObj) {
          boolean pNullable = isNullable(pObj);
          boolean pOptional = isOptional(schemaObj, pName);
          String pDoc = pObj.getString("description");
          TypeRef pType = parseTypeRef(pObj, capitalize(pName), null, ctx);
          properties.put(
              pName, new PropertyDef(pName, pType, pNullable, pOptional, pDoc != null ? pDoc : ""));
        }
      }
    }

    Optional<TypeRef> mapValueType = Optional.empty();
    if (addlPropsNode instanceof JsonObjectNode addlObj) {
      mapValueType =
          Optional.of(
              parseTypeRef(addlObj, contextName != null ? contextName + "Value" : null, null, ctx));
    }

    String typeName = schemaObj.getString("title");
    if (typeName == null || typeName.isBlank()) {
      typeName = contextName != null ? contextName : "AnonymousObject";
    }

    TypeDef typeDef =
        new TypeDef(
            typeName,
            "object",
            properties,
            List.of(),
            mapValueType,
            schemaObj.getString("description"));
    ctx.types.put(typeName, typeDef);
    return new NamedTypeRef(typeName, List.of());
  }

  private TypeRef parseEnum(
      JsonArrayNode enumArr, JsonObjectNode schemaObj, String contextName, ImportContext ctx) {
    List<String> symbols = new ArrayList<>();
    for (JsonNode elem : enumArr.elements()) {
      if (elem instanceof JsonStringNode s) {
        symbols.add(s.value());
      } else if (elem instanceof JsonNumberNode n) {
        symbols.add(n.value().toString());
      } else if (elem instanceof JsonBooleanNode b) {
        symbols.add(String.valueOf(b.value()));
      }
    }

    String enumName = schemaObj.getString("title");
    if (enumName == null || enumName.isBlank()) {
      enumName = contextName != null ? contextName : "AnonymousEnum";
    }

    TypeDef enumDef =
        new TypeDef(
            enumName,
            "enum",
            Map.of(),
            symbols,
            Optional.empty(),
            schemaObj.getString("description"));
    ctx.types.put(enumName, enumDef);
    return new EnumTypeRef(enumName, symbols);
  }

  private TypeRef parseConst(
      JsonNode constNode, JsonObjectNode schemaObj, String contextName, ImportContext ctx) {
    String symbol = constNode instanceof JsonStringNode s ? s.value() : constNode.toString();
    String enumName = schemaObj.getString("title");
    if (enumName == null || enumName.isBlank()) {
      enumName = contextName != null ? contextName : "ConstEnum";
    }

    TypeDef enumDef =
        new TypeDef(
            enumName,
            "enum",
            Map.of(),
            List.of(symbol),
            Optional.empty(),
            schemaObj.getString("description"));
    ctx.types.put(enumName, enumDef);
    return new EnumTypeRef(enumName, List.of(symbol));
  }

  private TypeRef parseUnion(JsonArrayNode unionArr, String contextName, ImportContext ctx) {
    List<TypeRef> options = new ArrayList<>();
    for (JsonNode item : unionArr.elements()) {
      if (item instanceof JsonObjectNode optObj) {
        JsonNode optType = optObj.get("type");
        if (optType instanceof JsonStringNode s && "null".equals(s.value())) {
          continue; // Null option handled via nullability flag
        }
        options.add(parseTypeRef(optObj, contextName, null, ctx));
      }
    }

    if (options.isEmpty()) {
      return new DynamicTypeRef();
    }
    if (options.size() == 1) {
      return options.get(0);
    }
    return new UnionTypeRef(options);
  }

  private TypeRef parseAllOf(JsonArrayNode allOfArr, String contextName, ImportContext ctx) {
    // Merge all objects into one synthesized object
    Map<String, JsonNode> mergedProps = new LinkedHashMap<>();
    Map<String, JsonLocation> mergedLocations = new LinkedHashMap<>();
    List<JsonNode> mergedRequired = new ArrayList<>();
    String description = null;
    String title = null;

    for (JsonNode item : allOfArr.elements()) {
      JsonObjectNode itemObj = null;
      if (item instanceof JsonObjectNode io) {
        if (io.get("$ref") instanceof JsonStringNode refNode) {
          // Resolve referenced object
          TypeRef refType = resolveRef(refNode.value(), io.getPropertyLocation("$ref"), ctx);
          if (refType instanceof NamedTypeRef ntr && ctx.types.containsKey(ntr.name())) {
            // Include properties from existing type
            TypeDef baseDef = ctx.types.get(ntr.name());
            for (Map.Entry<String, PropertyDef> pe : baseDef.properties().entrySet()) {
              // Convert back to synthetic node if needed
            }
          }
        }
        itemObj = io;
      }

      if (itemObj != null) {
        JsonObjectNode p = itemObj.getObject("properties");
        if (p != null) {
          mergedProps.putAll(p.properties());
          mergedLocations.putAll(p.propertyLocations());
        }
        JsonArrayNode req = itemObj.getArray("required");
        if (req != null) {
          mergedRequired.addAll(req.elements());
        }
        if (itemObj.getString("description") != null) {
          description = itemObj.getString("description");
        }
        if (itemObj.getString("title") != null) {
          title = itemObj.getString("title");
        }
      }
    }

    Map<String, JsonNode> synthRootProps = new LinkedHashMap<>();
    synthRootProps.put("type", new JsonStringNode("object", allOfArr.location()));
    if (!mergedProps.isEmpty()) {
      synthRootProps.put(
          "properties", new JsonObjectNode(mergedProps, mergedLocations, allOfArr.location()));
    }
    if (!mergedRequired.isEmpty()) {
      synthRootProps.put("required", new JsonArrayNode(mergedRequired, allOfArr.location()));
    }
    if (description != null) {
      synthRootProps.put("description", new JsonStringNode(description, allOfArr.location()));
    }
    if (title != null) {
      synthRootProps.put("title", new JsonStringNode(title, allOfArr.location()));
    }

    JsonObjectNode synth =
        new JsonObjectNode(synthRootProps, new LinkedHashMap<>(), allOfArr.location());
    return parseObject(synth, contextName, ctx);
  }

  private TypeRef parseTypeArray(
      JsonArrayNode typeArr, JsonObjectNode schemaObj, String contextName, ImportContext ctx) {
    List<TypeRef> types = new ArrayList<>();
    for (JsonNode elem : typeArr.elements()) {
      if (elem instanceof JsonStringNode s) {
        if (!"null".equals(s.value())) {
          types.add(
              switch (s.value()) {
                case "string" -> new PrimitiveTypeRef("string");
                case "integer" -> new PrimitiveTypeRef("integer");
                case "number" -> new PrimitiveTypeRef("number");
                case "boolean" -> new PrimitiveTypeRef("boolean");
                case "array" -> parseArray(schemaObj, contextName, ctx);
                case "object" -> parseObject(schemaObj, contextName, ctx);
                default -> new PrimitiveTypeRef(s.value());
              });
        }
      }
    }

    if (types.isEmpty()) {
      return new PrimitiveTypeRef("null");
    }
    if (types.size() == 1) {
      return types.get(0);
    }
    return new UnionTypeRef(types);
  }

  // --- Nullability & Optionality Rules ---

  private boolean isNullable(JsonObjectNode schemaObj) {
    if (schemaObj == null) {
      return false;
    }
    JsonNode nullableNode = schemaObj.get("nullable");
    if (nullableNode instanceof JsonBooleanNode b && b.value()) {
      return true;
    }

    JsonNode typeNode = schemaObj.get("type");
    if (typeNode instanceof JsonArrayNode typeArr) {
      for (JsonNode elem : typeArr.elements()) {
        if (elem instanceof JsonStringNode s && "null".equals(s.value())) {
          return true;
        }
      }
    } else if (typeNode instanceof JsonStringNode s && "null".equals(s.value())) {
      return true;
    }

    for (String unionKey : List.of("oneOf", "anyOf")) {
      JsonArrayNode unionArr = schemaObj.getArray(unionKey);
      if (unionArr != null) {
        for (JsonNode option : unionArr.elements()) {
          if (option instanceof JsonObjectNode optObj) {
            JsonNode optType = optObj.get("type");
            if (optType instanceof JsonStringNode s && "null".equals(s.value())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean isOptional(JsonObjectNode parentObj, String propName) {
    if (parentObj == null) {
      return true;
    }
    JsonArrayNode reqArr = parentObj.getArray("required");
    if (reqArr != null) {
      for (JsonNode elem : reqArr.elements()) {
        if (elem instanceof JsonStringNode s && s.value().equals(propName)) {
          return false;
        }
      }
    }
    return true;
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return "Anonymous";
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  private static String sanitizeTypeName(String name) {
    if (name == null || name.isBlank()) {
      return "Root";
    }
    String clean = name.replaceAll("[^a-zA-Z0-9_]", "_");
    return capitalize(clean);
  }

  private static String computeFingerprint(String rawSource) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(rawSource.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder("jsonschema:v1:");
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm missing", e);
    }
  }

  // --- Context Tracking Per Import ---

  private static final class ImportContext {
    final String sourcePath;
    JsonObjectNode rootObj;
    String rootTitle;
    boolean rootReferenced = false;
    final Map<String, TypeDef> types = new TreeMap<>();
    final List<SchemaDiagnostic> diagnostics = new ArrayList<>();
    final Set<String> resolvingPointers = new HashSet<>();
    final Map<String, TypeRef> resolvedDefinitions = new LinkedHashMap<>();

    ImportContext(String sourcePath) {
      this.sourcePath = sourcePath != null ? sourcePath : "";
    }
  }

  // =========================================================================
  // Zero-Dependency JSON AST and Parser with Position Tracking
  // =========================================================================

  record JsonLocation(int line, int column, int offset) implements Serializable {}

  sealed interface JsonNode
      permits JsonObjectNode,
          JsonArrayNode,
          JsonStringNode,
          JsonNumberNode,
          JsonBooleanNode,
          JsonNullNode {
    JsonLocation location();
  }

  record JsonObjectNode(
      Map<String, JsonNode> properties,
      Map<String, JsonLocation> propertyLocations,
      JsonLocation location)
      implements JsonNode {

    public JsonNode get(String key) {
      return properties.get(key);
    }

    public String getString(String key) {
      JsonNode node = get(key);
      return node instanceof JsonStringNode s ? s.value() : null;
    }

    public Boolean getBoolean(String key) {
      JsonNode node = get(key);
      return node instanceof JsonBooleanNode b ? b.value() : null;
    }

    public JsonObjectNode getObject(String key) {
      JsonNode node = get(key);
      return node instanceof JsonObjectNode o ? o : null;
    }

    public JsonArrayNode getArray(String key) {
      JsonNode node = get(key);
      return node instanceof JsonArrayNode a ? a : null;
    }

    public JsonLocation getPropertyLocation(String key) {
      return propertyLocations.getOrDefault(key, location);
    }
  }

  record JsonArrayNode(List<JsonNode> elements, JsonLocation location) implements JsonNode {}

  record JsonStringNode(String value, JsonLocation location) implements JsonNode {}

  record JsonNumberNode(Number value, JsonLocation location) implements JsonNode {}

  record JsonBooleanNode(boolean value, JsonLocation location) implements JsonNode {}

  record JsonNullNode(JsonLocation location) implements JsonNode {}

  private static final class JsonParseException extends Exception {
    private static final long serialVersionUID = 1L;
    final JsonLocation location;

    JsonParseException(String message, JsonLocation location) {
      super(message);
      this.location = location;
    }
  }

  private static final class JsonParser {
    private final String src;
    private final int[] lineOffsets;
    private int pos = 0;

    JsonParser(String src) {
      this.src = src != null ? src : "";
      this.lineOffsets = computeLineOffsets(this.src);
    }

    JsonNode parse() throws JsonParseException {
      skipWhitespace();
      if (pos >= src.length()) {
        throw new JsonParseException("Unexpected end of input: empty document", locationFor(0));
      }
      JsonNode root = parseNode();
      skipWhitespace();
      if (pos < src.length()) {
        throw new JsonParseException(
            "Unexpected character after root JSON entity: '" + src.charAt(pos) + "'",
            locationFor(pos));
      }
      return root;
    }

    private JsonNode parseNode() throws JsonParseException {
      skipWhitespace();
      if (pos >= src.length()) {
        throw new JsonParseException("Unexpected end of input", locationFor(pos));
      }
      char c = src.charAt(pos);
      if (c == '{') return parseObject();
      if (c == '[') return parseArray();
      if (c == '"') return parseStringNode();
      if (c == 't' || c == 'f') return parseBooleanNode();
      if (c == 'n') return parseNullNode();
      if (c == '-' || Character.isDigit(c)) return parseNumberNode();
      throw new JsonParseException("Unexpected character: '" + c + "'", locationFor(pos));
    }

    private JsonObjectNode parseObject() throws JsonParseException {
      int startPos = pos;
      consume('{');
      Map<String, JsonNode> props = new LinkedHashMap<>();
      Map<String, JsonLocation> propLocs = new LinkedHashMap<>();

      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == '}') {
        pos++;
        return new JsonObjectNode(props, propLocs, locationFor(startPos));
      }

      while (pos < src.length()) {
        skipWhitespace();
        if (pos >= src.length()) {
          throw new JsonParseException("Unterminated JSON object", locationFor(startPos));
        }
        if (src.charAt(pos) != '"') {
          throw new JsonParseException(
              "Expected string property key, got '" + src.charAt(pos) + "'", locationFor(pos));
        }
        int keyStart = pos;
        String key = parseRawString();
        JsonLocation keyLoc = locationFor(keyStart);

        skipWhitespace();
        consume(':');
        JsonNode val = parseNode();
        props.put(key, val);
        propLocs.put(key, keyLoc);

        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
          skipWhitespace();
          if (pos < src.length() && src.charAt(pos) == '}') {
            throw new JsonParseException(
                "Trailing comma in object is not permitted", locationFor(pos - 1));
          }
        } else if (pos < src.length() && src.charAt(pos) == '}') {
          pos++;
          break;
        } else {
          char ch = pos < src.length() ? src.charAt(pos) : ' ';
          throw new JsonParseException(
              "Expected ',' or '}' in object, got '" + ch + "'", locationFor(pos));
        }
      }
      return new JsonObjectNode(props, propLocs, locationFor(startPos));
    }

    private JsonArrayNode parseArray() throws JsonParseException {
      int startPos = pos;
      consume('[');
      List<JsonNode> elements = new ArrayList<>();

      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == ']') {
        pos++;
        return new JsonArrayNode(elements, locationFor(startPos));
      }

      while (pos < src.length()) {
        JsonNode item = parseNode();
        elements.add(item);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
          skipWhitespace();
          if (pos < src.length() && src.charAt(pos) == ']') {
            throw new JsonParseException(
                "Trailing comma in array is not permitted", locationFor(pos - 1));
          }
        } else if (pos < src.length() && src.charAt(pos) == ']') {
          pos++;
          break;
        } else {
          char ch = pos < src.length() ? src.charAt(pos) : ' ';
          throw new JsonParseException(
              "Expected ',' or ']' in array, got '" + ch + "'", locationFor(pos));
        }
      }
      return new JsonArrayNode(elements, locationFor(startPos));
    }

    private JsonStringNode parseStringNode() throws JsonParseException {
      int startPos = pos;
      String val = parseRawString();
      return new JsonStringNode(val, locationFor(startPos));
    }

    private String parseRawString() throws JsonParseException {
      int startPos = pos;
      consume('"');
      StringBuilder sb = new StringBuilder();
      while (pos < src.length()) {
        char c = src.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c == '\\') {
          if (pos >= src.length()) {
            throw new JsonParseException("Unterminated escape sequence", locationFor(pos - 1));
          }
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
              if (pos + 4 > src.length()) {
                throw new JsonParseException(
                    "Invalid unicode escape sequence", locationFor(pos - 2));
              }
              String hex = src.substring(pos, pos + 4);
              pos += 4;
              try {
                sb.append((char) Integer.parseInt(hex, 16));
              } catch (NumberFormatException e) {
                throw new JsonParseException(
                    "Invalid hex in unicode escape: " + hex, locationFor(pos - 4));
              }
            }
            default ->
                throw new JsonParseException(
                    "Invalid escape character: \\" + esc, locationFor(pos - 2));
          }
        } else if (c < 0x20) {
          throw new JsonParseException(
              "Unescaped control character in string literal", locationFor(pos - 1));
        } else {
          sb.append(c);
        }
      }
      throw new JsonParseException("Unterminated string literal", locationFor(startPos));
    }

    private JsonBooleanNode parseBooleanNode() throws JsonParseException {
      int startPos = pos;
      if (src.startsWith("true", pos)) {
        pos += 4;
        return new JsonBooleanNode(true, locationFor(startPos));
      }
      if (src.startsWith("false", pos)) {
        pos += 5;
        return new JsonBooleanNode(false, locationFor(startPos));
      }
      throw new JsonParseException("Invalid boolean token", locationFor(startPos));
    }

    private JsonNullNode parseNullNode() throws JsonParseException {
      int startPos = pos;
      if (src.startsWith("null", pos)) {
        pos += 4;
        return new JsonNullNode(locationFor(startPos));
      }
      throw new JsonParseException("Invalid null token", locationFor(startPos));
    }

    private JsonNumberNode parseNumberNode() throws JsonParseException {
      int startPos = pos;
      if (src.charAt(pos) == '-') pos++;
      if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
        throw new JsonParseException("Invalid number format", locationFor(startPos));
      }
      while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      boolean isFloat = false;
      if (pos < src.length() && src.charAt(pos) == '.') {
        isFloat = true;
        pos++;
        if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
          throw new JsonParseException(
              "Decimal point must be followed by digits", locationFor(pos - 1));
        }
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }
      if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
        isFloat = true;
        pos++;
        if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
        if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
          throw new JsonParseException("Exponent must be followed by digits", locationFor(pos - 1));
        }
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }

      String numStr = src.substring(startPos, pos);
      Number val;
      if (isFloat) {
        val = Double.parseDouble(numStr);
      } else {
        try {
          val = Long.parseLong(numStr);
        } catch (NumberFormatException ignored) {
          val = Double.parseDouble(numStr);
        }
      }
      return new JsonNumberNode(val, locationFor(startPos));
    }

    private void consume(char expected) throws JsonParseException {
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != expected) {
        char actual = pos < src.length() ? src.charAt(pos) : ' ';
        throw new JsonParseException(
            "Expected '" + expected + "', got '" + actual + "'", locationFor(pos));
      }
      pos++;
    }

    private void skipWhitespace() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }

    private JsonLocation locationFor(int p) {
      int idx = Arrays.binarySearch(lineOffsets, p);
      int lineIndex = idx >= 0 ? idx : -idx - 2;
      if (lineIndex < 0) lineIndex = 0;
      int line = lineIndex + 1;
      int col = p - lineOffsets[lineIndex] + 1;
      return new JsonLocation(line, col, p);
    }

    private static int[] computeLineOffsets(String text) {
      List<Integer> offsets = new ArrayList<>();
      offsets.add(0);
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '\n') {
          offsets.add(i + 1);
        }
      }
      int[] result = new int[offsets.size()];
      for (int i = 0; i < offsets.size(); i++) {
        result[i] = offsets.get(i);
      }
      return result;
    }
  }
}
