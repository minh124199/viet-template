package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.CanonicalSchemaModel.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-dependency parser, registry, and type query engine for Viet Template canonical contract
 * schemas (*.vt-schema.json).
 */
final class CanonicalSchemaResolver {

  public static final String EXPECTED_FORMAT = "viet-template-contract-schema/1";
  public static final int EXPECTED_SCHEMA_VERSION = 1;

  private static final Set<String> SENSITIVE_PROPERTIES =
      Set.of(
          "class",
          "declaringClass",
          "classLoader",
          "protectionDomain",
          "module",
          "securityManager");

  private final Map<String, SchemaEnvelope> schemasById = new ConcurrentHashMap<>();
  private final Map<String, Path> schemaFilePaths = new ConcurrentHashMap<>();
  private volatile Path schemaDirectory = null;

  public void setSchemaDirectory(Path directory) {
    this.schemaDirectory = directory;
  }

  public void registerSchema(String templateIdOrUri, String schemaJson) {
    Objects.requireNonNull(templateIdOrUri, "templateIdOrUri must not be null");
    Objects.requireNonNull(schemaJson, "schemaJson must not be null");
    SchemaEnvelope env = parseSchemaJson(schemaJson);
    schemasById.put(templateIdOrUri, env);
    if (!env.templateId().isBlank()) {
      schemasById.put(env.templateId(), env);
    }
  }

  public void registerSchemaFile(Path schemaPath) throws IOException {
    Objects.requireNonNull(schemaPath, "schemaPath must not be null");
    if (!Files.isRegularFile(schemaPath)) {
      return;
    }
    String json = Files.readString(schemaPath, StandardCharsets.UTF_8);
    SchemaEnvelope env = parseSchemaJson(json);
    String key = schemaPath.toUri().toString();
    schemasById.put(key, env);
    schemaFilePaths.put(key, schemaPath);
    if (!env.templateId().isBlank()) {
      schemasById.put(env.templateId(), env);
      schemaFilePaths.put(env.templateId(), schemaPath);
    }
  }

  public Optional<SchemaEnvelope> resolveSchema(String templateIdOrUri) {
    if (templateIdOrUri == null || templateIdOrUri.isBlank()) {
      return Optional.empty();
    }
    SchemaEnvelope cached = schemasById.get(templateIdOrUri);
    if (cached != null) {
      return Optional.of(cached);
    }

    // Try auto-discovery from document URI
    try {
      if (templateIdOrUri.startsWith("file:/")) {
        Path docPath = Path.of(URI.create(templateIdOrUri));
        // Check sibling <name>.vt-schema.json or <name>.vtl-schema.json
        Path siblingSchema =
            docPath.resolveSibling(docPath.getFileName().toString() + "-schema.json");
        if (!Files.isRegularFile(siblingSchema)) {
          String name = docPath.getFileName().toString();
          int dot = name.lastIndexOf('.');
          String base = dot > 0 ? name.substring(0, dot) : name;
          siblingSchema = docPath.resolveSibling(base + ".vt-schema.json");
        }
        if (Files.isRegularFile(siblingSchema)) {
          registerSchemaFile(siblingSchema);
          SchemaEnvelope env = schemasById.get(siblingSchema.toUri().toString());
          if (env != null) {
            schemasById.put(templateIdOrUri, env);
            schemaFilePaths.put(templateIdOrUri, siblingSchema);
            return Optional.of(env);
          }
        }

        // Check configured schema directory
        if (schemaDirectory != null && Files.isDirectory(schemaDirectory)) {
          String fileName = docPath.getFileName().toString();
          int dot = fileName.lastIndexOf('.');
          String base = dot > 0 ? fileName.substring(0, dot) : fileName;
          Path inDir = schemaDirectory.resolve(base + ".vt-schema.json");
          if (Files.isRegularFile(inDir)) {
            registerSchemaFile(inDir);
            SchemaEnvelope env = schemasById.get(inDir.toUri().toString());
            if (env != null) {
              schemasById.put(templateIdOrUri, env);
              schemaFilePaths.put(templateIdOrUri, inDir);
              return Optional.of(env);
            }
          }
        }
      }
    } catch (IllegalArgumentException | IOException | SecurityException ignored) {
      // Return empty if path resolution fails
    }

    return Optional.empty();
  }

  public Optional<Path> getSchemaFilePath(String templateIdOrUri) {
    return Optional.ofNullable(schemaFilePaths.get(templateIdOrUri));
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
    TypeRef current = rootParam.get().type();
    if (steps == null || steps.isEmpty()) {
      return Optional.of(current);
    }
    if (steps.size() > 64) {
      return Optional.empty();
    }

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
          Class<?> clazz =
              Class.forName(typeName, false, Thread.currentThread().getContextClassLoader());
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

  public Map<String, PropertyDef> resolveMembers(
      TypeRef type, SchemaEnvelope schema, MemberAccessPolicy policy) {
    if (schema == null) {
      return Map.of();
    }
    return getAccessibleProperties(schema.templateId(), type, policy);
  }

  public Optional<Integer> findLineOfParameter(String templateIdOrUri, String paramName) {
    return resolveSchema(templateIdOrUri)
        .map(s -> findDefinitionLine(s.rawJson(), "name", paramName));
  }

  public Optional<Integer> findLineOfProperty(
      String templateIdOrUri, String typeName, String propName) {
    return resolveSchema(templateIdOrUri)
        .map(s -> findDefinitionLine(s.rawJson(), "name", propName));
  }

  public static int findDefinitionLine(String jsonContent, String targetKey, String targetValue) {
    if (jsonContent == null || targetValue == null) {
      return 0;
    }
    String searchNeedle = "\"" + targetKey + "\": \"" + targetValue + "\"";
    String[] lines = jsonContent.split("\r?\n");
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains(searchNeedle)) {
        return i;
      }
    }
    // Fallback: search for just the string value
    String valNeedle = "\"" + targetValue + "\"";
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains(valNeedle)) {
        return i;
      }
    }
    return 0;
  }

  private static String extractTypeName(TypeRef type) {
    if (type instanceof ClassTypeRef ctr) {
      return ctr.name();
    }
    if (type instanceof NamedTypeRef ntr) {
      return ntr.name();
    }
    return null;
  }

  // =========================================================================
  // Zero-Dependency JSON Schema Parser
  // =========================================================================

  static SchemaEnvelope parseSchemaJson(String json) {
    JsonParser parser = new JsonParser(json);
    Object parsed = parser.parse();
    if (!(parsed instanceof Map<?, ?> root)) {
      throw new IllegalArgumentException("Schema root must be a JSON object");
    }

    String format = getString(root, "format");
    if (!EXPECTED_FORMAT.equals(format)) {
      throw new IllegalArgumentException(
          "Unsupported schema format: '" + format + "', expected '" + EXPECTED_FORMAT + "'");
    }

    Number versionNum = getNumber(root, "schemaVersion");
    int version = versionNum != null ? versionNum.intValue() : 0;
    if (version != EXPECTED_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported schema version: " + version + ", expected " + EXPECTED_SCHEMA_VERSION);
    }

    String schemaUrl = getString(root, "$schema");
    String templateId = getString(root, "templateId");
    String fingerprint = getString(root, "contractFingerprint");

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
                TypeRef propType = parseTypeRef(propMap.get("type"));
                if (propName != null && propType != null) {
                  properties.put(propName, new PropertyDef(propName, propType, propNullable));
                }
              }
            }
          } else if (propsObj instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> pentry : propsMap.entrySet()) {
              String propName = pentry.getKey().toString();
              if (pentry.getValue() instanceof Map<?, ?> propMap) {
                boolean propNullable = getBoolean(propMap, "nullable", false);
                TypeRef propType = parseTypeRef(propMap.get("type"));
                if (propType != null) {
                  properties.put(propName, new PropertyDef(propName, propType, propNullable));
                }
              }
            }
          }
          types.put(tName, new TypeDef(kind, properties));
        }
      }
    }

    return new SchemaEnvelope(
        schemaUrl != null ? schemaUrl : "",
        format,
        version,
        templateId != null ? templateId : "",
        fingerprint != null ? fingerprint : "",
        parameters,
        types,
        json);
  }

  private static TypeRef parseTypeRef(Object typeObj) {
    if (!(typeObj instanceof Map<?, ?> map)) {
      return new PrimitiveTypeRef("unknown");
    }
    String kind = getString(map, "kind");
    if (kind == null) {
      return new PrimitiveTypeRef("unknown");
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
      default -> new PrimitiveTypeRef("unknown");
    };
  }

  private static String getString(Map<?, ?> map, String key) {
    return getString(map, key, null);
  }

  private static String getString(Map<?, ?> map, String key, String defaultVal) {
    Object val = map.get(key);
    return val != null ? val.toString() : defaultVal;
  }

  private static Number getNumber(Map<?, ?> map, String key) {
    Object val = map.get(key);
    return val instanceof Number n ? n : null;
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

  // =========================================================================
  // Lightweight JSON Parser
  // =========================================================================

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
