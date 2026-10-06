package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Deterministic Ahead-Of-Time projector from canonical Viet Template contract schemas
 * (*.vt-schema.json) into TypeScript declaration files (*.d.ts).
 *
 * <p>Strict architectural invariants:
 *
 * <ul>
 *   <li>Consumes only the serialized schema AST; never accesses Java reflection or compiler
 *       internals.
 *   <li>Preserves nullability, optionality, recursion, generic type arguments, and arrays.
 *   <li>Guarantees 100% byte-for-byte deterministic output with UTF-8 encoding and LF line endings.
 * </ul>
 */
public final class TypeScriptDeclarationProjector {

  public static final String EXPECTED_FORMAT = "viet-template-contract-schema/1";
  public static final int EXPECTED_SCHEMA_VERSION = 1;
  public static final String DTS_FILE_EXTENSION = ".d.ts";

  private static final Set<String> VALID_PRIMITIVES =
      Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double", "void");

  private static final Set<String> KNOWN_JAVA_NAMES =
      Set.of(
          "Object",
          "String",
          "Number",
          "Boolean",
          "Byte",
          "Short",
          "Integer",
          "Long",
          "Float",
          "Double",
          "Character",
          "Void",
          "CharSequence",
          "List",
          "Set",
          "Map",
          "Collection",
          "Iterable",
          "Optional",
          "Date",
          "UUID",
          "Instant",
          "LocalDate",
          "LocalDateTime",
          "LocalTime",
          "OffsetDateTime",
          "ZonedDateTime",
          "BigDecimal",
          "BigInteger");

  private static final Set<String> TS_RESERVED_WORDS =
      Set.of(
          "break",
          "case",
          "catch",
          "class",
          "const",
          "continue",
          "debugger",
          "default",
          "delete",
          "do",
          "else",
          "enum",
          "export",
          "extends",
          "false",
          "finally",
          "for",
          "function",
          "if",
          "import",
          "in",
          "instanceof",
          "new",
          "null",
          "return",
          "super",
          "switch",
          "this",
          "throw",
          "true",
          "try",
          "typeof",
          "var",
          "void",
          "while",
          "with",
          "yield",
          "let",
          "static",
          "implements",
          "interface",
          "package",
          "private",
          "protected",
          "public",
          "as",
          "async",
          "await",
          "constructor",
          "declare",
          "from",
          "get",
          "is",
          "of",
          "set",
          "type",
          "namespace",
          "module",
          "abstract",
          "any",
          "boolean",
          "number",
          "string",
          "symbol",
          "never",
          "unknown",
          "readonly",
          "keyof",
          "unique",
          "infer",
          "override",
          "satisfies",
          "record",
          "map",
          "array");

  private TypeScriptDeclarationProjector() {}

  // =========================================================================
  // Public Projection API
  // =========================================================================

  /**
   * Projects canonical schema JSON text into a deterministic TypeScript declaration string
   * (*.d.ts).
   *
   * @param schemaJson the raw schema JSON text
   * @return deterministic *.d.ts content
   * @throws IllegalArgumentException if the schema is malformed, invalid, or unsupported
   */
  public static String project(String schemaJson) {
    Objects.requireNonNull(schemaJson, "schemaJson must not be null");
    SchemaEnvelope envelope = SchemaParser.parse(schemaJson);
    return projectEnvelope(envelope);
  }

  /**
   * Projects a canonical schema file into a deterministic TypeScript declaration string (*.d.ts).
   *
   * @param schemaFile path to the *.vt-schema.json file
   * @return deterministic *.d.ts content
   * @throws IOException if reading the file fails
   * @throws IllegalArgumentException if the schema is malformed, invalid, or unsupported
   */
  public static String project(Path schemaFile) throws IOException {
    Objects.requireNonNull(schemaFile, "schemaFile must not be null");
    String json = Files.readString(schemaFile, StandardCharsets.UTF_8);
    return project(json);
  }

  /**
   * Projects a canonical schema file and writes the resulting *.d.ts file into the output
   * directory.
   *
   * @param schemaFile path to the *.vt-schema.json file
   * @param outputDirectory base output directory
   * @return the path of the generated *.d.ts file
   * @throws IOException if file I/O fails
   */
  public static Path projectToFile(Path schemaFile, Path outputDirectory) throws IOException {
    Objects.requireNonNull(schemaFile, "schemaFile must not be null");
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

    String json = Files.readString(schemaFile, StandardCharsets.UTF_8);
    SchemaEnvelope envelope = SchemaParser.parse(json);
    String dts = projectEnvelope(envelope);

    Path targetFile = deriveDeclarationFilePath(outputDirectory, envelope.templateId());
    Path parent = targetFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(targetFile, dts, StandardCharsets.UTF_8);
    return targetFile;
  }

  /**
   * Projects a canonical schema instance into a deterministic TypeScript declaration string
   * (*.d.ts).
   *
   * @param schema canonical schema
   * @return deterministic *.d.ts content
   */
  public static String project(CanonicalSchema schema) {
    Objects.requireNonNull(schema, "schema must not be null");

    Map<String, String> typeSymbolTable = resolveTypeNames(schema.types().keySet());

    StringBuilder sb = new StringBuilder(1024);
    String formatStr =
        schema.format() == SchemaFormat.CONTRACT
            ? "viet-template-contract-schema/1"
            : schema.format().name().toLowerCase(Locale.ROOT).replace('_', '-');
    sb.append("// Generated by Viet Template M28.\n");
    sb.append("// Source schema format: ").append(formatStr).append("\n");
    sb.append("// Schema version: 1\n");
    sb.append("// Contract fingerprint: ").append(schema.contractFingerprint()).append("\n");
    sb.append("// DO NOT EDIT.\n\n");

    List<Map.Entry<String, CanonicalSchemaModel.TypeDef>> sortedTypes =
        new ArrayList<>(schema.types().entrySet());
    sortedTypes.sort(Comparator.comparing(e -> typeSymbolTable.get(e.getKey())));

    for (Map.Entry<String, CanonicalSchemaModel.TypeDef> entry : sortedTypes) {
      CanonicalSchemaModel.TypeDef typeDef = entry.getValue();
      String tsName = typeSymbolTable.get(entry.getKey());

      if ("enum".equalsIgnoreCase(typeDef.kind()) || !typeDef.enumConstants().isEmpty()) {
        sb.append("export type ").append(tsName).append(" = ");
        if (typeDef.enumConstants().isEmpty()) {
          sb.append("string;\n\n");
        } else {
          String union =
              typeDef.enumConstants().stream()
                  .map(s -> "\"" + escapeTsString(s) + "\"")
                  .collect(Collectors.joining(" | "));
          sb.append(union).append(";\n\n");
        }
      } else {
        sb.append("export interface ").append(tsName).append(" {\n");
        List<CanonicalSchemaModel.PropertyDef> sortedProps =
            new ArrayList<>(typeDef.properties().values());
        sortedProps.sort(Comparator.comparing(CanonicalSchemaModel.PropertyDef::name));

        for (CanonicalSchemaModel.PropertyDef prop : sortedProps) {
          String propName = formatPropertyName(prop.name());
          String propType = projectCanonicalTypeRef(prop.type(), typeSymbolTable);
          sb.append("  ").append(propName);
          if (prop.optional()) {
            sb.append("?");
          }
          sb.append(": ").append(propType);
          if (prop.nullable()) {
            sb.append(" | null");
          }
          sb.append(";\n");
        }
        sb.append("}\n\n");
      }
    }

    sb.append("export interface TemplateParameters");
    if (schema.parameters().isEmpty()) {
      sb.append(" {}\n");
    } else {
      sb.append(" {\n");
      List<CanonicalSchemaModel.ParameterDef> sortedParams =
          new ArrayList<>(schema.parameters().values());
      sortedParams.sort(Comparator.comparing(CanonicalSchemaModel.ParameterDef::name));

      for (CanonicalSchemaModel.ParameterDef param : sortedParams) {
        String paramName = formatPropertyName(param.name());
        String paramType = projectCanonicalTypeRef(param.type(), typeSymbolTable);

        sb.append("  ").append(paramName);
        if (param.optional()) {
          sb.append("?");
        }
        sb.append(": ").append(paramType);
        if (param.nullable()) {
          sb.append(" | null");
        }
        sb.append(";\n");
      }
      sb.append("}\n");
    }

    return sb.toString();
  }

  /**
   * Projects a canonical schema instance and writes the resulting *.d.ts file into the output
   * directory.
   *
   * @param schema canonical schema
   * @param outputDirectory base output directory
   * @return the path of the generated *.d.ts file
   * @throws IOException if file I/O fails
   */
  public static Path projectToFile(CanonicalSchema schema, Path outputDirectory)
      throws IOException {
    Objects.requireNonNull(schema, "schema must not be null");
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

    String dts = project(schema);
    Path targetFile = deriveDeclarationFilePath(outputDirectory, schema.templateId());
    Path parent = targetFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(targetFile, dts, StandardCharsets.UTF_8);
    return targetFile;
  }

  static String projectCanonicalTypeRef(
      CanonicalSchemaModel.TypeRef type, Map<String, String> typeSymbolTable) {
    if (type instanceof CanonicalSchemaModel.PrimitiveTypeRef pt) {
      return switch (pt.name().toLowerCase(Locale.ROOT)) {
        case "boolean" -> "boolean";
        case "byte", "short", "int", "integer", "long", "float", "double", "number" -> "number";
        case "char", "character", "string" -> "string";
        case "void" -> "void";
        default -> "unknown";
      };
    }

    if (type instanceof CanonicalSchemaModel.ClassTypeRef ct) {
      String fqcn = ct.name();
      if (typeSymbolTable.containsKey(fqcn)) {
        return typeSymbolTable.get(fqcn);
      }
      return switch (fqcn) {
        case "java.lang.Boolean" -> "boolean";
        case "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Number",
            "java.math.BigDecimal",
            "java.math.BigInteger" ->
            "number";
        case "java.lang.Character" -> "string";
        case "java.lang.Void" -> "void";
        case "java.lang.String", "java.lang.CharSequence" -> "string";
        case "java.lang.Object" -> "unknown";
        case "java.time.Instant",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime",
            "java.util.Date",
            "java.util.UUID" ->
            "string";
        case "java.util.List",
            "java.util.Collection",
            "java.lang.Iterable",
            "java.util.Iterable",
            "java.util.ArrayList" ->
            "unknown[]";
        case "java.util.Set", "java.util.HashSet" -> "Set<unknown>";
        case "java.util.Map", "java.util.HashMap" -> "Record<string, unknown>";
        default -> {
          if (typeSymbolTable.containsValue(fqcn)) {
            yield fqcn;
          }
          for (Map.Entry<String, String> e : typeSymbolTable.entrySet()) {
            if (extractSimpleName(e.getKey()).equals(fqcn)) {
              yield e.getValue();
            }
          }
          yield sanitizeTypeName(extractSimpleName(fqcn));
        }
      };
    }

    if (type instanceof CanonicalSchemaModel.NamedTypeRef nt) {
      String baseName;
      if (typeSymbolTable.containsKey(nt.name())) {
        baseName = typeSymbolTable.get(nt.name());
      } else {
        String found = null;
        for (Map.Entry<String, String> e : typeSymbolTable.entrySet()) {
          if (extractSimpleName(e.getKey()).equals(nt.name())) {
            found = e.getValue();
            break;
          }
        }
        baseName = found != null ? found : sanitizeTypeName(nt.name());
      }
      if (nt.arguments().isEmpty()) {
        return baseName;
      }
      List<String> argStrings = new ArrayList<>();
      for (CanonicalSchemaModel.TypeRef a : nt.arguments()) {
        argStrings.add(projectCanonicalTypeRef(a, typeSymbolTable));
      }
      return baseName + "<" + String.join(", ", argStrings) + ">";
    }

    if (type instanceof CanonicalSchemaModel.ArrayTypeRef at) {
      String comp = projectCanonicalTypeRef(at.componentType(), typeSymbolTable);
      return wrapArrayElement(comp) + "[]";
    }

    if (type instanceof CanonicalSchemaModel.MapTypeRef mt) {
      String keyType = projectCanonicalTypeRef(mt.keyType(), typeSymbolTable);
      String valType = projectCanonicalTypeRef(mt.valueType(), typeSymbolTable);
      if ("number".equals(keyType)) {
        return "Record<number, " + valType + ">";
      }
      return "Record<string, " + valType + ">";
    }

    if (type instanceof CanonicalSchemaModel.EnumTypeRef et) {
      if (typeSymbolTable.containsKey(et.name())) {
        return typeSymbolTable.get(et.name());
      }
      return sanitizeTypeName(CanonicalSchemaModel.simpleName(et.name()));
    }

    if (type instanceof CanonicalSchemaModel.ParameterizedTypeRef pt) {
      String raw = pt.rawType();
      List<CanonicalSchemaModel.TypeRef> args = pt.arguments();
      if (isListType(raw)) {
        if (args.isEmpty()) return "unknown[]";
        return wrapArrayElement(projectCanonicalTypeRef(args.get(0), typeSymbolTable)) + "[]";
      }
      if (isSetType(raw)) {
        if (args.isEmpty()) return "Set<unknown>";
        return "Set<" + projectCanonicalTypeRef(args.get(0), typeSymbolTable) + ">";
      }
      if (isMapType(raw)) {
        if (args.size() >= 2) {
          String kt = projectCanonicalTypeRef(args.get(0), typeSymbolTable);
          String vt = projectCanonicalTypeRef(args.get(1), typeSymbolTable);
          if ("number".equals(kt)) return "Record<number, " + vt + ">";
          return "Record<string, " + vt + ">";
        }
        return "Record<string, unknown>";
      }
      String baseName = typeSymbolTable.getOrDefault(raw, sanitizeTypeName(extractSimpleName(raw)));
      if (args.isEmpty()) return baseName;
      List<String> argStrings = new ArrayList<>();
      for (CanonicalSchemaModel.TypeRef a : args) {
        argStrings.add(projectCanonicalTypeRef(a, typeSymbolTable));
      }
      return baseName + "<" + String.join(", ", argStrings) + ">";
    }

    if (type instanceof CanonicalSchemaModel.UnionTypeRef ut) {
      if (ut.options().isEmpty()) return "never";
      return ut.options().stream()
          .map(o -> projectCanonicalTypeRef(o, typeSymbolTable))
          .collect(Collectors.joining(" | "));
    }

    if (type instanceof CanonicalSchemaModel.DynamicTypeRef) {
      return "unknown";
    }

    if (type instanceof CanonicalSchemaModel.WildcardTypeRef wt) {
      return wt.bound().map(b -> projectCanonicalTypeRef(b, typeSymbolTable)).orElse("unknown");
    }

    return "unknown";
  }

  /**
   * Derives the target *.d.ts file path relative to a base directory given a template ID.
   *
   * @param baseOutputDir base output directory
   * @param templateIdValue relative template ID path (e.g. "users/simple-user.vtl")
   * @return resolved and normalized Path
   */
  public static Path deriveDeclarationFilePath(Path baseOutputDir, String templateIdValue) {
    Objects.requireNonNull(baseOutputDir, "baseOutputDir must not be null");
    Objects.requireNonNull(templateIdValue, "templateIdValue must not be null");

    String normalized = templateIdValue.replace('\\', '/').trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    int dot = normalized.lastIndexOf('.');
    String baseName = (dot > 0) ? normalized.substring(0, dot) : normalized;
    String dtsRelativePath = baseName + DTS_FILE_EXTENSION;
    Path resolved = baseOutputDir.resolve(dtsRelativePath).normalize();
    Path normalizedBase = baseOutputDir.toAbsolutePath().normalize();
    if (!resolved.toAbsolutePath().normalize().startsWith(normalizedBase)) {
      throw new IllegalArgumentException(
          "Path traversal detected in templateId: " + templateIdValue);
    }
    return resolved;
  }

  // =========================================================================
  // Semantic Projection Engine
  // =========================================================================

  private static String projectEnvelope(SchemaEnvelope envelope) {
    // 1. Build FQCN -> TypeScript Type Name symbol table with collision resolution
    Map<String, String> typeSymbolTable = resolveTypeNames(envelope.types().keySet());

    // 2. Discover generic type variables per declared type
    Map<String, List<String>> typeParametersPerType = new HashMap<>();
    Set<String> declaredFqcns = envelope.types().keySet();
    for (Map.Entry<String, TypeDef> entry : envelope.types().entrySet()) {
      Set<String> typeVars = new TreeSet<>();
      for (PropertyDef prop : entry.getValue().properties()) {
        collectTypeVariables(prop.type(), typeVars, declaredFqcns, typeSymbolTable);
      }
      typeParametersPerType.put(entry.getKey(), new ArrayList<>(typeVars));
    }

    // 3. Discover generic type variables on root parameters
    Set<String> rootTypeVars = new TreeSet<>();
    for (ParameterDef param : envelope.parameters()) {
      collectTypeVariables(param.type(), rootTypeVars, declaredFqcns, typeSymbolTable);
    }
    List<String> rootTypeParameters = new ArrayList<>(rootTypeVars);

    // 4. Generate TypeScript content
    StringBuilder sb = new StringBuilder(1024);

    // Header
    sb.append("// Generated by Viet Template M28.\n");
    sb.append("// Source schema format: ").append(envelope.format()).append("\n");
    sb.append("// Schema version: ").append(envelope.schemaVersion()).append("\n");
    sb.append("// Contract fingerprint: ").append(envelope.contractFingerprint()).append("\n");
    sb.append("// DO NOT EDIT.\n\n");

    // Declared complex types (sorted by generated TypeScript interface name)
    List<Map.Entry<String, TypeDef>> sortedTypes = new ArrayList<>(envelope.types().entrySet());
    sortedTypes.sort(Comparator.comparing(e -> typeSymbolTable.get(e.getKey())));

    for (Map.Entry<String, TypeDef> entry : sortedTypes) {
      String fqcn = entry.getKey();
      TypeDef typeDef = entry.getValue();
      String tsName = typeSymbolTable.get(fqcn);
      List<String> typeParams = typeParametersPerType.getOrDefault(fqcn, List.of());

      sb.append("export interface ").append(tsName);
      if (!typeParams.isEmpty()) {
        sb.append("<");
        for (int i = 0; i < typeParams.size(); i++) {
          if (i > 0) sb.append(", ");
          sb.append(typeParams.get(i)).append(" = unknown");
        }
        sb.append(">");
      }
      sb.append(" {\n");

      List<PropertyDef> sortedProps = new ArrayList<>(typeDef.properties());
      sortedProps.sort(Comparator.comparing(PropertyDef::name));

      for (PropertyDef prop : sortedProps) {
        String propName = formatPropertyName(prop.name());
        String propType = projectTypeRef(prop.type(), typeSymbolTable);
        sb.append("  ").append(propName);
        if (prop.optional()) {
          sb.append("?");
        }
        sb.append(": ").append(propType);
        if (prop.nullable()) {
          sb.append(" | null");
        }
        sb.append(";\n");
      }

      sb.append("}\n\n");
    }

    // Root TemplateParameters interface
    sb.append("export interface TemplateParameters");
    if (!rootTypeParameters.isEmpty()) {
      sb.append("<");
      for (int i = 0; i < rootTypeParameters.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(rootTypeParameters.get(i)).append(" = unknown");
      }
      sb.append(">");
    }
    if (envelope.parameters().isEmpty()) {
      sb.append(" {}\n");
    } else {
      sb.append(" {\n");
      List<ParameterDef> sortedParams = new ArrayList<>(envelope.parameters());
      sortedParams.sort(Comparator.comparing(ParameterDef::name));

      for (ParameterDef param : sortedParams) {
        String paramName = formatPropertyName(param.name());
        String paramType = projectTypeRef(param.type(), typeSymbolTable);

        sb.append("  ").append(paramName);
        if (param.optional()) {
          sb.append("?");
        }
        sb.append(": ").append(paramType);
        if (param.nullable()) {
          sb.append(" | null");
        }
        sb.append(";\n");
      }
      sb.append("}\n");
    }

    return sb.toString();
  }

  private static void collectTypeVariables(
      TypeRef type,
      Set<String> typeVars,
      Set<String> declaredFqcns,
      Map<String, String> typeSymbolTable) {
    if (type instanceof NamedTypeRef nt) {
      if (isTypeVariable(nt, declaredFqcns, typeSymbolTable)) {
        typeVars.add(sanitizeTypeName(nt.name()));
      }
      for (TypeRef arg : nt.arguments()) {
        collectTypeVariables(arg, typeVars, declaredFqcns, typeSymbolTable);
      }
    } else if (type instanceof ParameterizedTypeRef pt) {
      for (TypeRef arg : pt.arguments()) {
        collectTypeVariables(arg, typeVars, declaredFqcns, typeSymbolTable);
      }
    } else if (type instanceof ArrayTypeRef at) {
      collectTypeVariables(at.componentType(), typeVars, declaredFqcns, typeSymbolTable);
    } else if (type instanceof WildcardTypeRef wt) {
      wt.bound().ifPresent(b -> collectTypeVariables(b, typeVars, declaredFqcns, typeSymbolTable));
    }
  }

  static boolean isTypeVariable(
      NamedTypeRef nt, Set<String> declaredFqcns, Map<String, String> typeSymbolTable) {
    if (!nt.arguments().isEmpty()) {
      return false;
    }
    String name = nt.name();
    if (name == null || name.isBlank() || name.contains(".")) {
      return false;
    }
    if (declaredFqcns.contains(name) || typeSymbolTable.containsValue(name)) {
      return false;
    }
    for (String fqcn : declaredFqcns) {
      if (extractSimpleName(fqcn).equals(name)) {
        return false;
      }
    }
    if (VALID_PRIMITIVES.contains(name) || KNOWN_JAVA_NAMES.contains(name)) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  // =========================================================================
  // Type Name Normalization and Collision Disambiguation
  // =========================================================================

  static Map<String, String> resolveTypeNames(Set<String> fqcns) {
    Map<String, String> candidateNames = new LinkedHashMap<>();
    Map<String, List<String>> byCandidate = new LinkedHashMap<>();

    List<String> sortedFqcns = new ArrayList<>(fqcns);
    Collections.sort(sortedFqcns);

    for (String fqcn : sortedFqcns) {
      String candidate = extractSimpleName(fqcn);
      candidateNames.put(fqcn, candidate);
      byCandidate.computeIfAbsent(candidate, k -> new ArrayList<>()).add(fqcn);
    }

    Map<String, String> resolved = new HashMap<>();
    Set<String> assignedNames = new HashSet<>();
    assignedNames.add("TemplateParameters");

    for (String fqcn : sortedFqcns) {
      String candidate = candidateNames.get(fqcn);
      List<String> collisions = byCandidate.get(candidate);

      String finalName;
      if (collisions.size() == 1 && !"TemplateParameters".equals(candidate)) {
        finalName = sanitizeTypeName(candidate);
      } else {
        // Disambiguate by converting package/nesting separators into valid identifier
        finalName = sanitizeTypeName(fqcn.replace('.', '_').replace('$', '_'));
      }

      // Ensure global uniqueness in case sanitized full names collide
      String uniqueName = finalName;
      int counter = 1;
      while (assignedNames.contains(uniqueName)) {
        uniqueName = finalName + "_" + counter++;
      }
      assignedNames.add(uniqueName);
      resolved.put(fqcn, uniqueName);
    }

    return resolved;
  }

  static String extractSimpleName(String fqcn) {
    int dollar = fqcn.lastIndexOf('$');
    if (dollar >= 0 && dollar < fqcn.length() - 1) {
      return fqcn.substring(dollar + 1);
    }
    int dot = fqcn.lastIndexOf('.');
    if (dot >= 0 && dot < fqcn.length() - 1) {
      return fqcn.substring(dot + 1);
    }
    return fqcn;
  }

  static String sanitizeTypeName(String raw) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    if (sb.isEmpty()) {
      return "_Type";
    }
    if (Character.isDigit(sb.charAt(0))) {
      sb.insert(0, '_');
    }
    String name = sb.toString();
    if (TS_RESERVED_WORDS.contains(name)
        || TS_RESERVED_WORDS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
      return "_" + name;
    }
    return name;
  }

  static String formatPropertyName(String name) {
    if (isValidUnquotedPropertyName(name)) {
      return name;
    }
    return "\"" + escapeTsString(name) + "\"";
  }

  static boolean isValidUnquotedPropertyName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (TS_RESERVED_WORDS.contains(name)) {
      return false;
    }
    char first = name.charAt(0);
    if (!Character.isLetter(first) && first != '_' && first != '$') {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
        return false;
      }
    }
    return true;
  }

  private static String escapeTsString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  // =========================================================================
  // Type Reference Projection
  // =========================================================================

  static String projectTypeRef(TypeRef type, Map<String, String> typeSymbolTable) {
    if (type instanceof PrimitiveTypeRef pt) {
      return switch (pt.name()) {
        case "boolean" -> "boolean";
        case "byte", "short", "int", "long", "float", "double" -> "number";
        case "char" -> "string";
        case "void" -> "void";
        default -> "unknown";
      };
    }

    if (type instanceof ClassTypeRef ct) {
      String fqcn = ct.name();
      return switch (fqcn) {
        case "java.lang.Boolean" -> "boolean";
        case "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Number",
            "java.math.BigDecimal",
            "java.math.BigInteger" ->
            "number";
        case "java.lang.Character" -> "string";
        case "java.lang.Void" -> "void";
        case "java.lang.String", "java.lang.CharSequence" -> "string";
        case "java.lang.Object" -> "unknown";
        case "java.time.Instant",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime",
            "java.util.Date",
            "java.util.UUID" ->
            "string";
        case "java.util.List",
            "java.util.Collection",
            "java.lang.Iterable",
            "java.util.Iterable",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.SequencedCollection" ->
            "unknown[]";
        case "java.util.Set",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.SortedSet",
            "java.util.NavigableSet",
            "java.util.SequencedSet" ->
            "Set<unknown>";
        case "java.util.Map",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.ConcurrentHashMap",
            "java.util.SortedMap",
            "java.util.NavigableMap",
            "java.util.SequencedMap" ->
            "Record<string, unknown>";
        case "java.util.Optional" -> "unknown | null";
        default -> {
          if (typeSymbolTable.containsKey(fqcn)) {
            yield typeSymbolTable.get(fqcn);
          }
          if (typeSymbolTable.containsValue(fqcn)) {
            yield fqcn;
          }
          // Unknown reference class fallback
          yield "unknown";
        }
      };
    }

    if (type instanceof ParameterizedTypeRef pt) {
      String raw = pt.rawType();
      List<TypeRef> args = pt.arguments();

      if (isListType(raw)) {
        if (args.isEmpty()) {
          return "unknown[]";
        }
        String elem = projectTypeRef(args.get(0), typeSymbolTable);
        return wrapArrayElement(elem) + "[]";
      }

      if (isSetType(raw)) {
        if (args.isEmpty()) {
          return "Set<unknown>";
        }
        return "Set<" + projectTypeRef(args.get(0), typeSymbolTable) + ">";
      }

      if (isMapType(raw)) {
        if (args.size() >= 2) {
          String keyType = projectTypeRef(args.get(0), typeSymbolTable);
          String valType = projectTypeRef(args.get(1), typeSymbolTable);
          if ("string".equals(keyType)) {
            return "Record<string, " + valType + ">";
          }
          if ("number".equals(keyType)) {
            return "Record<number, " + valType + ">";
          }
          return "Map<" + keyType + ", " + valType + ">";
        }
        return "Record<string, unknown>";
      }

      if ("java.util.Optional".equals(raw)) {
        if (args.isEmpty()) {
          return "unknown | null";
        }
        return projectTypeRef(args.get(0), typeSymbolTable) + " | null";
      }

      if ("java.util.Map$Entry".equals(raw) || "java.util.Map.Entry".equals(raw)) {
        if (args.size() >= 2) {
          String k = projectTypeRef(args.get(0), typeSymbolTable);
          String v = projectTypeRef(args.get(1), typeSymbolTable);
          return "{"
              + formatPropertyName("key")
              + ": "
              + k
              + "; "
              + formatPropertyName("value")
              + ": "
              + v
              + "}";
        }
        return "{"
            + formatPropertyName("key")
            + ": unknown; "
            + formatPropertyName("value")
            + ": unknown}";
      }

      if (typeSymbolTable.containsKey(raw)) {
        String baseName = typeSymbolTable.get(raw);
        if (args.isEmpty()) {
          return baseName;
        }
        List<String> argStrings = new ArrayList<>();
        for (TypeRef a : args) {
          argStrings.add(projectTypeRef(a, typeSymbolTable));
        }
        return baseName + "<" + String.join(", ", argStrings) + ">";
      }

      if (typeSymbolTable.containsValue(raw)) {
        if (args.isEmpty()) {
          return raw;
        }
        List<String> argStrings = new ArrayList<>();
        for (TypeRef a : args) {
          argStrings.add(projectTypeRef(a, typeSymbolTable));
        }
        return raw + "<" + String.join(", ", argStrings) + ">";
      }

      // Unrecognized parameterized type fallback
      return "unknown";
    }

    if (type instanceof ArrayTypeRef at) {
      String comp = projectTypeRef(at.componentType(), typeSymbolTable);
      return wrapArrayElement(comp) + "[]";
    }

    if (type instanceof WildcardTypeRef wt) {
      return switch (wt.boundKind()) {
        case "extends" -> wt.bound().map(b -> projectTypeRef(b, typeSymbolTable)).orElse("unknown");
        case "super", "unbounded" -> "unknown";
        default -> "unknown";
      };
    }

    if (type instanceof NamedTypeRef nt) {
      if (nt.arguments().isEmpty()) {
        if (typeSymbolTable.containsKey(nt.name())) {
          return typeSymbolTable.get(nt.name());
        }
        for (Map.Entry<String, String> entry : typeSymbolTable.entrySet()) {
          if (extractSimpleName(entry.getKey()).equals(nt.name())) {
            return entry.getValue();
          }
        }
        return sanitizeTypeName(nt.name());
      }
      String baseName;
      if (typeSymbolTable.containsKey(nt.name())) {
        baseName = typeSymbolTable.get(nt.name());
      } else {
        String match = null;
        for (Map.Entry<String, String> entry : typeSymbolTable.entrySet()) {
          if (extractSimpleName(entry.getKey()).equals(nt.name())) {
            match = entry.getValue();
            break;
          }
        }
        baseName = match != null ? match : sanitizeTypeName(nt.name());
      }
      List<String> argStrings = new ArrayList<>();
      for (TypeRef a : nt.arguments()) {
        argStrings.add(projectTypeRef(a, typeSymbolTable));
      }
      return baseName + "<" + String.join(", ", argStrings) + ">";
    }

    if (type instanceof DynamicTypeRef) {
      return "unknown";
    }

    throw new IllegalArgumentException("Unsupported TypeRef kind: " + type.getClass().getName());
  }

  private static boolean isListType(String raw) {
    return "java.util.List".equals(raw)
        || "java.util.Collection".equals(raw)
        || "java.lang.Iterable".equals(raw)
        || "java.util.Iterable".equals(raw)
        || "java.util.ArrayList".equals(raw)
        || "java.util.LinkedList".equals(raw)
        || "java.util.SequencedCollection".equals(raw);
  }

  private static boolean isSetType(String raw) {
    return "java.util.Set".equals(raw)
        || "java.util.HashSet".equals(raw)
        || "java.util.LinkedHashSet".equals(raw)
        || "java.util.TreeSet".equals(raw)
        || "java.util.SortedSet".equals(raw)
        || "java.util.NavigableSet".equals(raw)
        || "java.util.SequencedSet".equals(raw);
  }

  private static boolean isMapType(String raw) {
    return "java.util.Map".equals(raw)
        || "java.util.HashMap".equals(raw)
        || "java.util.LinkedHashMap".equals(raw)
        || "java.util.TreeMap".equals(raw)
        || "java.util.ConcurrentHashMap".equals(raw)
        || "java.util.SortedMap".equals(raw)
        || "java.util.NavigableMap".equals(raw)
        || "java.util.SequencedMap".equals(raw);
  }

  private static String wrapArrayElement(String elemType) {
    if (elemType.startsWith("(") && elemType.endsWith(")")) {
      return elemType;
    }
    if (elemType.contains(" | ")) {
      return "(" + elemType + ")";
    }
    return elemType;
  }

  // =========================================================================
  // Internal Schema AST Model
  // =========================================================================

  sealed interface TypeRef
      permits PrimitiveTypeRef,
          ClassTypeRef,
          ParameterizedTypeRef,
          ArrayTypeRef,
          WildcardTypeRef,
          NamedTypeRef,
          DynamicTypeRef {}

  record PrimitiveTypeRef(String name) implements TypeRef {}

  record ClassTypeRef(String name) implements TypeRef {}

  record ParameterizedTypeRef(String rawType, List<TypeRef> arguments) implements TypeRef {
    public ParameterizedTypeRef {
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
  }

  record ArrayTypeRef(TypeRef componentType) implements TypeRef {}

  record WildcardTypeRef(String boundKind, Optional<TypeRef> bound) implements TypeRef {}

  record NamedTypeRef(String name, List<TypeRef> arguments) implements TypeRef {
    public NamedTypeRef {
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
  }

  record DynamicTypeRef() implements TypeRef {}

  record PropertyDef(String name, TypeRef type, boolean nullable, boolean optional) {
    public PropertyDef(String name, TypeRef type, boolean nullable) {
      this(name, type, nullable, false);
    }
  }

  record TypeDef(String kind, List<PropertyDef> properties) {
    public TypeDef {
      properties = properties == null ? List.of() : List.copyOf(properties);
    }
  }

  record ParameterDef(String name, TypeRef type, boolean nullable, boolean optional) {}

  record SchemaEnvelope(
      String format,
      int schemaVersion,
      String templateId,
      String contractFingerprint,
      List<ParameterDef> parameters,
      Map<String, TypeDef> types) {
    public SchemaEnvelope {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
      types = types == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(types));
    }
  }

  // =========================================================================
  // Zero-Dependency Schema JSON Parser & Strict Validator
  // =========================================================================

  static final class SchemaParser {

    private final String src;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    private SchemaParser(String src) {
      this.src = src;
    }

    public static SchemaEnvelope parse(String json) {
      SchemaParser parser = new SchemaParser(json);
      JsonValue root = parser.parseValue();
      parser.skipWhitespace();
      if (!parser.isEof()) {
        throw parser.error("Unexpected trailing character after JSON root");
      }
      return SchemaValidator.validate(root);
    }

    private JsonValue parseValue() {
      skipWhitespace();
      if (isEof()) {
        throw error("Unexpected end of input");
      }
      char c = peek();
      if (c == '{') return parseObject();
      if (c == '[') return parseArray();
      if (c == '"') return parseString();
      if (c == 't' || c == 'f') return parseBoolean();
      if (c == 'n') return parseNull();
      if (c == '-' || Character.isDigit(c)) return parseNumber();
      throw error("Unexpected character: '" + c + "'");
    }

    private JsonObject parseObject() {
      expect('{');
      Map<String, JsonValue> map = new LinkedHashMap<>();
      skipWhitespace();
      if (peek() == '}') {
        consume();
        return new JsonObject(map);
      }
      while (true) {
        skipWhitespace();
        if (peek() != '"') {
          throw error("Expected string key in object");
        }
        JsonString key = parseString();
        skipWhitespace();
        expect(':');
        JsonValue val = parseValue();
        if (map.containsKey(key.value())) {
          throw error("Duplicate key in object: '" + key.value() + "'");
        }
        map.put(key.value(), val);
        skipWhitespace();
        char next = peek();
        if (next == '}') {
          consume();
          break;
        } else if (next == ',') {
          consume();
          skipWhitespace();
          if (peek() == '}') {
            throw error("Trailing comma in object");
          }
        } else {
          throw error("Expected ',' or '}' in object, found '" + next + "'");
        }
      }
      return new JsonObject(map);
    }

    private JsonArray parseArray() {
      expect('[');
      List<JsonValue> list = new ArrayList<>();
      skipWhitespace();
      if (peek() == ']') {
        consume();
        return new JsonArray(list);
      }
      while (true) {
        list.add(parseValue());
        skipWhitespace();
        char next = peek();
        if (next == ']') {
          consume();
          break;
        } else if (next == ',') {
          consume();
          skipWhitespace();
          if (peek() == ']') {
            throw error("Trailing comma in array");
          }
        } else {
          throw error("Expected ',' or ']' in array, found '" + next + "'");
        }
      }
      return new JsonArray(list);
    }

    private JsonString parseString() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (!isEof()) {
        char c = consume();
        if (c == '"') {
          return new JsonString(sb.toString());
        }
        if (c == '\\') {
          if (isEof()) throw error("Unterminated escape sequence");
          char esc = consume();
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
              int u = 0;
              for (int i = 0; i < 4; i++) {
                if (isEof()) throw error("Incomplete unicode escape");
                char hex = consume();
                int digit = Character.digit(hex, 16);
                if (digit < 0) throw error("Invalid hex character in unicode escape: " + hex);
                u = (u << 4) | digit;
              }
              sb.append((char) u);
            }
            default -> throw error("Invalid escape sequence: \\" + esc);
          }
        } else if (c < 0x20) {
          throw error("Unescaped control character in string: " + (int) c);
        } else {
          sb.append(c);
        }
      }
      throw error("Unterminated string literal");
    }

    private JsonBoolean parseBoolean() {
      if (match("true")) return new JsonBoolean(true);
      if (match("false")) return new JsonBoolean(false);
      throw error("Expected boolean literal");
    }

    private JsonNull parseNull() {
      if (match("null")) return JsonNull.INSTANCE;
      throw error("Expected null literal");
    }

    private JsonNumber parseNumber() {
      int start = pos;
      if (peek() == '-') consume();
      if (peek() == '0') {
        consume();
      } else if (Character.isDigit(peek())) {
        while (Character.isDigit(peek())) consume();
      } else {
        throw error("Invalid number");
      }
      if (peek() == '.') {
        consume();
        if (!Character.isDigit(peek())) throw error("Decimal point must be followed by digits");
        while (Character.isDigit(peek())) consume();
      }
      if (peek() == 'e' || peek() == 'E') {
        consume();
        if (peek() == '+' || peek() == '-') consume();
        if (!Character.isDigit(peek())) throw error("Exponent must be followed by digits");
        while (Character.isDigit(peek())) consume();
      }
      String numStr = src.substring(start, pos);
      try {
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
          return new JsonNumber(Double.parseDouble(numStr));
        } else {
          return new JsonNumber(Long.parseLong(numStr));
        }
      } catch (NumberFormatException e) {
        throw error("Invalid number format: " + numStr);
      }
    }

    private void skipWhitespace() {
      while (!isEof()) {
        char c = peek();
        if (c == ' ' || c == '\t' || c == '\r') {
          consume();
        } else if (c == '\n') {
          consume();
        } else {
          break;
        }
      }
    }

    private boolean match(String expected) {
      if (src.startsWith(expected, pos)) {
        for (int i = 0; i < expected.length(); i++) consume();
        return true;
      }
      return false;
    }

    private void expect(char c) {
      if (isEof() || peek() != c) {
        throw error("Expected '" + c + "', found '" + (isEof() ? "EOF" : peek()) + "'");
      }
      consume();
    }

    private char peek() {
      return isEof() ? '\0' : src.charAt(pos);
    }

    private char consume() {
      char c = src.charAt(pos++);
      if (c == '\n') {
        line++;
        col = 1;
      } else {
        col++;
      }
      return c;
    }

    private boolean isEof() {
      return pos >= src.length();
    }

    private IllegalArgumentException error(String msg) {
      return new IllegalArgumentException(
          String.format("Malformed JSON at line %d, column %d: %s", line, col, msg));
    }
  }

  // =========================================================================
  // Strict Schema Validator
  // =========================================================================

  static final class SchemaValidator {

    public static SchemaEnvelope validate(JsonValue root) {
      if (!(root instanceof JsonObject obj)) {
        throw new IllegalArgumentException("Schema root must be a JSON object");
      }

      // 1. format
      JsonValue formatVal = obj.get("format");
      if (formatVal == null || !(formatVal instanceof JsonString fs)) {
        throw new IllegalArgumentException("Missing required schema field: 'format'");
      }
      String format = fs.value();
      if (!EXPECTED_FORMAT.equals(format)) {
        throw new IllegalArgumentException(
            String.format(
                "Unsupported schema format: '%s' (expected '%s')", format, EXPECTED_FORMAT));
      }

      // 2. schemaVersion
      JsonValue verVal = obj.get("schemaVersion");
      if (verVal == null || !(verVal instanceof JsonNumber jn)) {
        throw new IllegalArgumentException("Missing required schema field: 'schemaVersion'");
      }
      int ver = (int) jn.value().longValue();
      if (ver != EXPECTED_SCHEMA_VERSION) {
        throw new IllegalArgumentException(
            String.format(
                "Unsupported schema version: %d (expected %d)", ver, EXPECTED_SCHEMA_VERSION));
      }

      // 3. templateId
      JsonValue tidVal = obj.get("templateId");
      if (tidVal == null || !(tidVal instanceof JsonString ts) || ts.value().isBlank()) {
        throw new IllegalArgumentException("Missing or empty required schema field: 'templateId'");
      }
      String templateId = ts.value();

      // 4. contractFingerprint
      JsonValue fpVal = obj.get("contractFingerprint");
      if (fpVal == null || !(fpVal instanceof JsonString fps) || fps.value().isBlank()) {
        throw new IllegalArgumentException(
            "Missing or empty required schema field: 'contractFingerprint'");
      }
      String fingerprint = fps.value();

      // 5. parameters
      JsonValue paramsVal = obj.get("parameters");
      if (paramsVal == null || !(paramsVal instanceof JsonArray pa)) {
        throw new IllegalArgumentException("Missing required schema field: 'parameters'");
      }
      List<ParameterDef> parameters = new ArrayList<>();
      Set<String> paramNames = new HashSet<>();
      for (JsonValue elem : pa.elements()) {
        if (!(elem instanceof JsonObject pobj)) {
          throw new IllegalArgumentException("Each parameter entry must be a JSON object");
        }
        String pName = requireString(pobj, "name", "parameter name");
        validateParameterName(pName);
        if (paramNames.contains(pName)) {
          throw new IllegalArgumentException("Duplicate parameter name in schema: '" + pName + "'");
        }
        paramNames.add(pName);

        TypeRef pType = validateTypeRef(requireObject(pobj, "type", "parameter type"));
        boolean pNullable = requireBoolean(pobj, "nullable", "parameter nullable");
        if (pType instanceof PrimitiveTypeRef pt && pNullable) {
          throw new IllegalArgumentException(
              "Impossible nullability state: primitive type '"
                  + pt.name()
                  + "' cannot be nullable for parameter '"
                  + pName
                  + "'");
        }
        boolean pOptional = pobj.get("optional") instanceof JsonBoolean jb ? jb.value() : false;

        parameters.add(new ParameterDef(pName, pType, pNullable, pOptional));
      }

      // 6. types
      JsonValue typesVal = obj.get("types");
      if (typesVal == null || !(typesVal instanceof JsonObject tobj)) {
        throw new IllegalArgumentException("Missing required schema field: 'types'");
      }
      Map<String, TypeDef> types = new TreeMap<>();
      for (Map.Entry<String, JsonValue> entry : tobj.members().entrySet()) {
        String fqcn = entry.getKey();
        if (!(entry.getValue() instanceof JsonObject defObj)) {
          throw new IllegalArgumentException(
              "Type definition for '" + fqcn + "' must be an object");
        }
        String kind = requireString(defObj, "kind", "type kind");
        if (!"record".equals(kind) && !"bean".equals(kind) && !"interface".equals(kind)) {
          throw new IllegalArgumentException(
              "Unsupported type definition kind: '" + kind + "' for type '" + fqcn + "'");
        }
        JsonValue propsVal = defObj.get("properties");
        List<PropertyDef> properties = new ArrayList<>();
        if (propsVal instanceof JsonArray propsArr) {
          Set<String> propNames = new HashSet<>();
          for (JsonValue pe : propsArr.elements()) {
            if (!(pe instanceof JsonObject propObj)) {
              throw new IllegalArgumentException("Property entry must be an object in " + fqcn);
            }
            String propName = requireString(propObj, "name", "property name");
            if (propName.isBlank()) {
              throw new IllegalArgumentException(
                  "Property name must not be blank in type '" + fqcn + "'");
            }
            if (propNames.contains(propName)) {
              throw new IllegalArgumentException(
                  "Duplicate property name '" + propName + "' in type '" + fqcn + "'");
            }
            propNames.add(propName);
            TypeRef propType = validateTypeRef(requireObject(propObj, "type", "property type"));
            boolean propNullable = requireBoolean(propObj, "nullable", "property nullable");
            if (propType instanceof PrimitiveTypeRef pt && propNullable) {
              throw new IllegalArgumentException(
                  "Impossible nullability state: primitive type '"
                      + pt.name()
                      + "' cannot be nullable for property '"
                      + propName
                      + "' in type '"
                      + fqcn
                      + "'");
            }
            boolean propOptional =
                propObj.get("optional") instanceof JsonBoolean jb ? jb.value() : false;
            properties.add(new PropertyDef(propName, propType, propNullable, propOptional));
          }
        }
        types.put(fqcn, new TypeDef(kind, properties));
      }

      return new SchemaEnvelope(format, ver, templateId, fingerprint, parameters, types);
    }

    private static void validateParameterName(String name) {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Parameter name must not be blank");
      }
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c < 0x20 || c == '"' || c == '\\') {
          throw new IllegalArgumentException("Invalid parameter name: '" + name + "'");
        }
      }
    }

    private static TypeRef validateTypeRef(JsonObject obj) {
      String kind = requireString(obj, "kind", "type kind");
      return switch (kind) {
        case "primitive" -> {
          String name = requireString(obj, "name", "primitive name");
          if (!VALID_PRIMITIVES.contains(name)) {
            throw new IllegalArgumentException("Invalid primitive type name: '" + name + "'");
          }
          yield new PrimitiveTypeRef(name);
        }
        case "class" -> {
          String name = requireString(obj, "name", "class name");
          yield new ClassTypeRef(name);
        }
        case "parameterized" -> {
          String raw = requireString(obj, "rawType", "parameterized rawType");
          List<TypeRef> args = new ArrayList<>();
          if (obj.get("arguments") instanceof JsonArray ja) {
            for (JsonValue v : ja.elements()) {
              if (v instanceof JsonObject argObj) {
                args.add(validateTypeRef(argObj));
              } else {
                throw new IllegalArgumentException("Type argument must be a JSON object");
              }
            }
          }
          yield new ParameterizedTypeRef(raw, args);
        }
        case "array" -> {
          JsonObject comp = requireObject(obj, "componentType", "array componentType");
          yield new ArrayTypeRef(validateTypeRef(comp));
        }
        case "wildcard" -> {
          String boundKind = requireString(obj, "boundKind", "wildcard boundKind");
          if (!"extends".equals(boundKind)
              && !"super".equals(boundKind)
              && !"unbounded".equals(boundKind)) {
            throw new IllegalArgumentException("Invalid wildcard boundKind: '" + boundKind + "'");
          }
          Optional<TypeRef> bound = Optional.empty();
          if (obj.get("bound") instanceof JsonObject bo) {
            bound = Optional.of(validateTypeRef(bo));
          } else if ("extends".equals(boundKind) || "super".equals(boundKind)) {
            throw new IllegalArgumentException(
                "Wildcard with boundKind '" + boundKind + "' requires 'bound' object");
          }
          yield new WildcardTypeRef(boundKind, bound);
        }
        case "named" -> {
          String name = requireString(obj, "name", "named type name");
          List<TypeRef> args = new ArrayList<>();
          if (obj.get("arguments") instanceof JsonArray ja) {
            for (JsonValue v : ja.elements()) {
              if (v instanceof JsonObject argObj) {
                args.add(validateTypeRef(argObj));
              }
            }
          }
          yield new NamedTypeRef(name, args);
        }
        case "dynamic" -> new DynamicTypeRef();
        default ->
            throw new IllegalArgumentException("Unsupported schema type kind: '" + kind + "'");
      };
    }

    private static String requireString(JsonObject obj, String key, String desc) {
      JsonValue v = obj.get(key);
      if (v instanceof JsonString s && !s.value().isEmpty()) {
        return s.value();
      }
      throw new IllegalArgumentException(
          "Missing or invalid string field '" + key + "' (" + desc + ")");
    }

    private static JsonObject requireObject(JsonObject obj, String key, String desc) {
      JsonValue v = obj.get(key);
      if (v instanceof JsonObject o) {
        return o;
      }
      throw new IllegalArgumentException(
          "Missing or invalid object field '" + key + "' (" + desc + ")");
    }

    private static boolean requireBoolean(JsonObject obj, String key, String desc) {
      JsonValue v = obj.get(key);
      if (v instanceof JsonBoolean b) {
        return b.value();
      }
      throw new IllegalArgumentException(
          "Missing or invalid boolean field '" + key + "' (" + desc + ")");
    }
  }

  // =========================================================================
  // Minimal AST for Zero-Dependency JSON Parsing
  // =========================================================================

  sealed interface JsonValue {}

  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    public JsonValue get(String key) {
      return members.get(key);
    }
  }

  record JsonArray(List<JsonValue> elements) implements JsonValue {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(Number value) implements JsonValue {}

  record JsonBoolean(boolean value) implements JsonValue {}

  enum JsonNull implements JsonValue {
    INSTANCE
  }
}
