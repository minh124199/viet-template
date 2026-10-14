package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.PropertyDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Production Ahead-Of-Time projector from canonical Viet Template contracts (#param, companion
 * contracts, Java record/bean models, and canonical schemas) into deterministic TypeScript
 * declaration files (*.vtl.d.ts).
 *
 * <p>Strict architectural invariants (ADR-0022):
 *
 * <ul>
 *   <li>100% pure Java (zero runtime Node/npm requirement for generation).
 *   <li>100% byte-for-byte determinism with UTF-8 encoding and LF line endings.
 *   <li>No timestamps, usernames, or machine paths in generated headers.
 *   <li>Describes client-visible serialized JSON shape (matching SimpleJsonSerializer).
 *   <li>Safe incremental write avoidance (skips rewrite if bytes identical, preserving mtime).
 *   <li>Safe stale output cleanup (only deletes owned outputs tracked in manifest).
 *   <li>Path traversal and symlink escape prevention.
 *   <li>Deterministic collision disambiguation across packages.
 *   <li>Security: MemberAccessPolicy enforced (no getClass, ClassLoader, Thread internals).
 * </ul>
 */
public final class TypeScriptContractProjector {

  public static final String MANIFEST_FILE_NAME = ".viet-template-typescript-manifest.json";

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

  private final JavaModelSchemaImporter schemaImporter;
  private final MemberAccessPolicy memberAccessPolicy;

  public TypeScriptContractProjector() {
    this(MemberAccessPolicy.standard());
  }

  public TypeScriptContractProjector(MemberAccessPolicy memberAccessPolicy) {
    this.memberAccessPolicy =
        Objects.requireNonNull(memberAccessPolicy, "memberAccessPolicy must not be null");
    this.schemaImporter = new JavaModelSchemaImporter();
  }

  public static TypeScriptContractProjector create() {
    return new TypeScriptContractProjector();
  }

  public static TypeScriptContractProjector create(MemberAccessPolicy memberAccessPolicy) {
    return new TypeScriptContractProjector(memberAccessPolicy);
  }

  /** Projects a {@link TemplateContract} into deterministic TypeScript declarations. */
  public String projectContract(TemplateContract contract) {
    Objects.requireNonNull(contract, "contract must not be null");
    SchemaImportResult importResult = schemaImporter.importContract(contract, memberAccessPolicy);
    CanonicalSchema schema = importResult.schemas().get(contract.templateId().value());
    if (schema == null && !importResult.schemas().isEmpty()) {
      schema = importResult.schemas().values().iterator().next();
    }
    if (schema == null) {
      throw new IllegalStateException(
          "Failed to import canonical schema for template: " + contract.templateId().value());
    }
    return projectSchema(schema);
  }

  /**
   * Projects a Java class (record, bean, enum) directly into deterministic TypeScript declarations.
   */
  public String projectClass(Class<?> clazz, String templateId) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(templateId, "templateId must not be null");
    SchemaImportResult importResult =
        schemaImporter.importClass(clazz, templateId, memberAccessPolicy);
    CanonicalSchema schema = importResult.schemas().get(templateId);
    if (schema == null && !importResult.schemas().isEmpty()) {
      schema = importResult.schemas().values().iterator().next();
    }
    if (schema == null) {
      throw new IllegalStateException(
          "Failed to import canonical schema for class: " + clazz.getName());
    }
    return projectSchema(schema);
  }

  /** Projects a {@link CanonicalSchema} into deterministic TypeScript declarations. */
  public String projectSchema(CanonicalSchema schema) {
    Objects.requireNonNull(schema, "schema must not be null");

    String templateParamsName = deriveTemplateParametersName(schema.templateId());

    // Deduplicate simple-name alias entries where FQCN exists
    Set<String> fqcnSimpleNames = new HashSet<>();
    for (String key : schema.types().keySet()) {
      if (key.contains(".")) {
        fqcnSimpleNames.add(extractSimpleName(key));
        int dollar = key.lastIndexOf('$');
        if (dollar >= 0 && dollar < key.length() - 1) {
          fqcnSimpleNames.add(key.substring(dollar + 1));
        }
      }
    }
    Map<String, TypeDef> canonicalTypes = new LinkedHashMap<>();
    for (Map.Entry<String, TypeDef> entry : schema.types().entrySet()) {
      String key = entry.getKey();
      if (!key.contains(".") && fqcnSimpleNames.contains(key)) {
        continue; // Discard simple name alias duplicate
      }
      canonicalTypes.put(key, entry.getValue());
    }

    Map<String, String> typeSymbolTable =
        resolveTypeNames(canonicalTypes.keySet(), Set.of("TemplateParameters", templateParamsName));
    // Map simple name aliases into symbol table only when the simple name is unique
    Map<String, List<String>> fqcnsBySimpleName = new LinkedHashMap<>();
    for (String key : typeSymbolTable.keySet()) {
      fqcnsBySimpleName.computeIfAbsent(extractSimpleName(key), k -> new ArrayList<>()).add(key);
    }

    Map<String, String> simpleNameAliases = new LinkedHashMap<>();
    for (String key : schema.types().keySet()) {
      if (!key.contains(".")) {
        List<String> matchingFqcns = fqcnsBySimpleName.get(key);
        if (matchingFqcns != null && matchingFqcns.size() == 1) {
          simpleNameAliases.put(key, typeSymbolTable.get(matchingFqcns.get(0)));
        } else {
          for (String fqcn : typeSymbolTable.keySet()) {
            if (fqcn.endsWith("$" + key)) {
              simpleNameAliases.put(key, typeSymbolTable.get(fqcn));
              break;
            }
          }
        }
      }
    }
    typeSymbolTable.putAll(simpleNameAliases);

    StringBuilder sb = new StringBuilder(1024);
    sb.append("// Generated by Viet Template M46.\n");
    sb.append("// Contract fingerprint: ").append(schema.contractFingerprint()).append("\n");
    sb.append("// DO NOT EDIT MANUALLY.\n\n");

    // 1. Project declared complex types and enums (lexically sorted by TS type name)
    List<Map.Entry<String, TypeDef>> sortedTypes = new ArrayList<>(canonicalTypes.entrySet());
    sortedTypes.sort(Comparator.comparing(e -> typeSymbolTable.get(e.getKey())));

    for (Map.Entry<String, TypeDef> entry : sortedTypes) {
      TypeDef typeDef = entry.getValue();
      String tsName = typeSymbolTable.get(entry.getKey());

      if ("enum".equalsIgnoreCase(typeDef.kind()) || !typeDef.enumConstants().isEmpty()) {
        sb.append("export type ").append(tsName).append(" = ");
        if (typeDef.enumConstants().isEmpty()) {
          sb.append("string;\n\n");
        } else {
          String union =
              typeDef.enumConstants().stream()
                  .sorted()
                  .map(s -> "\"" + escapeTsString(s) + "\"")
                  .collect(Collectors.joining(" | "));
          sb.append(union).append(";\n\n");
        }
      } else {
        sb.append("export interface ").append(tsName).append(" {\n");
        List<PropertyDef> sortedProps = new ArrayList<>(typeDef.properties().values());
        sortedProps.sort(Comparator.comparing(PropertyDef::name));

        for (PropertyDef prop : sortedProps) {
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

    // 2. Project Root TemplateParameters interface
    sb.append("export interface ").append(templateParamsName);
    if (schema.parameters().isEmpty()) {
      sb.append(" {}\n");
    } else {
      sb.append(" {\n");
      List<ParameterDef> sortedParams = new ArrayList<>(schema.parameters().values());
      sortedParams.sort(Comparator.comparing(ParameterDef::name));

      for (ParameterDef param : sortedParams) {
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

    if (!"TemplateParameters".equals(templateParamsName)) {
      sb.append("\nexport interface TemplateParameters extends ")
          .append(templateParamsName)
          .append(" {}\n");
    }

    return sb.toString();
  }

  /**
   * Projects templates from a source directory into a TypeScript declaration directory, maintaining
   * deterministic ownership manifest and clean stale cleanup.
   *
   * @param sourceDirectory root directory containing templates
   * @param outputDirectory output directory for generated declarations
   * @param includes file pattern includes
   * @param excludes file pattern excludes
   * @param encoding template text encoding
   * @param classLoader optional class loader for resolving model types
   * @param cleanStaleOutputs whether to remove owned stale declarations
   * @return summary counts: generated, written, skipped, deleted
   * @throws IOException on I/O error
   */
  public Map<String, Integer> projectTemplates(
      Path sourceDirectory,
      Path outputDirectory,
      List<String> includes,
      List<String> excludes,
      Charset encoding,
      ClassLoader classLoader,
      boolean cleanStaleOutputs)
      throws IOException {
    return projectTemplates(
        sourceDirectory,
        outputDirectory,
        includes,
        excludes,
        encoding,
        classLoader,
        cleanStaleOutputs,
        null,
        null,
        null);
  }

  /**
   * Projects templates with optional pre-loaded contracts, pre-loaded schemas, and companion schema
   * directory.
   */
  public Map<String, Integer> projectTemplates(
      Path sourceDirectory,
      Path outputDirectory,
      List<String> includes,
      List<String> excludes,
      Charset encoding,
      ClassLoader classLoader,
      boolean cleanStaleOutputs,
      Map<TemplateId, TemplateContract> explicitContracts,
      Map<TemplateId, CanonicalSchema> explicitSchemas,
      Path schemaDirectory)
      throws IOException {
    Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    Charset actualEncoding = encoding != null ? encoding : StandardCharsets.UTF_8;
    List<String> actualIncludes =
        (includes != null && !includes.isEmpty())
            ? includes
            : List.of("**/*.vtl", "**/*.vm", "**/*.vtl.html");
    List<String> actualExcludes = excludes != null ? excludes : List.of();

    Path normalizedOutputDir = outputDirectory.toAbsolutePath().normalize();
    Files.createDirectories(normalizedOutputDir);

    // 1. Read previous manifest if present
    Path manifestFile = normalizedOutputDir.resolve(MANIFEST_FILE_NAME);
    Set<Path> previousOwnedOutputs = loadManifestOwnedFiles(normalizedOutputDir, manifestFile);

    if (!Files.isDirectory(sourceDirectory)) {
      int deleted = 0;
      if (cleanStaleOutputs) {
        deleted = cleanStaleOutputs(normalizedOutputDir, Set.of(), previousOwnedOutputs);
      }
      writeManifest(manifestFile, Map.of());
      return Map.of("generated", 0, "written", 0, "skipped", 0, "deleted", deleted);
    }

    // 2. Scan discovered templates
    List<Path> discoveredFiles = scanTemplates(sourceDirectory, actualIncludes, actualExcludes);
    if (discoveredFiles.isEmpty()) {
      int deleted = 0;
      if (cleanStaleOutputs) {
        deleted = cleanStaleOutputs(normalizedOutputDir, Set.of(), previousOwnedOutputs);
      }
      writeManifest(manifestFile, Map.of());
      return Map.of("generated", 0, "written", 0, "skipped", 0, "deleted", deleted);
    }

    CanonicalSchemaResolver schemaResolver = new CanonicalSchemaResolver(classLoader);
    if (schemaDirectory != null && Files.isDirectory(schemaDirectory)) {
      schemaResolver.setSchemaDirectory(schemaDirectory);
    }

    // 3. Compute full projection in memory (transactional / atomic)
    Map<Path, String> pendingWrites = new LinkedHashMap<>();
    Set<Path> currentOutputs = new HashSet<>();
    Map<String, String> newManifestEntries = new TreeMap<>();

    for (Path templateFile : discoveredFiles) {
      String relPath = sourceDirectory.relativize(templateFile).toString().replace('\\', '/');
      TemplateId templateId = TemplateId.of(relPath);

      TemplateContract contract = null;
      CanonicalSchema canonicalSchema = null;

      // a. Explicit contracts
      if (explicitContracts != null) {
        contract = explicitContracts.get(templateId);
        if (contract == null) {
          contract = explicitContracts.get(TemplateId.of(stripLeadingSlash(relPath)));
        }
      }

      // b. Companion contract file (.contract / .vtl.contract)
      if (contract == null) {
        contract =
            TemplateContractReader.findCompanion(templateFile, templateId, classLoader)
                .orElse(null);
      }

      // c. Explicit schemas
      if (contract == null && explicitSchemas != null) {
        canonicalSchema = explicitSchemas.get(templateId);
        if (canonicalSchema == null) {
          canonicalSchema = explicitSchemas.get(TemplateId.of(stripLeadingSlash(relPath)));
        }
      }

      // d. Sibling schema file (.vt-schema.json / .schema.json / .d.ts)
      if (contract == null && canonicalSchema == null) {
        canonicalSchema = schemaResolver.resolveSchema(templateId, templateFile).orElse(null);
      }

      // e. Template source inline #param directives
      if (contract == null && canonicalSchema == null) {
        contract =
            TemplateContractReader.extractFromTemplate(
                    templateFile, templateId, actualEncoding, classLoader)
                .orElse(null);
      }

      // f. Fallback to empty contract
      if (contract == null && canonicalSchema == null) {
        contract = TemplateContract.empty(templateId);
      }

      String dtsContent;
      String fingerprint;
      if (contract != null) {
        SchemaImportResult importResult =
            schemaImporter.importContract(contract, memberAccessPolicy);
        if (importResult.hasErrors()) {
          throw new IllegalStateException(
              "Failed to import schema for contract "
                  + relPath
                  + ": "
                  + importResult.diagnostics());
        }
        CanonicalSchema s = importResult.schemas().get(contract.templateId().value());
        if (s == null && !importResult.schemas().isEmpty()) {
          s = importResult.schemas().values().iterator().next();
        }
        if (s == null) {
          throw new IllegalStateException("No canonical schema produced for contract: " + relPath);
        }
        dtsContent = projectSchema(s);
        fingerprint = s.contractFingerprint();
      } else {
        dtsContent = projectSchema(canonicalSchema);
        fingerprint = canonicalSchema.contractFingerprint();
      }

      Path targetFile = deriveDeclarationFilePath(normalizedOutputDir, relPath);
      Path normTarget = targetFile.toAbsolutePath().normalize();
      if (!normTarget.startsWith(normalizedOutputDir)) {
        throw new IllegalArgumentException("Path traversal detected in template path: " + relPath);
      }

      pendingWrites.put(normTarget, dtsContent);
      currentOutputs.add(normTarget);
      String relOut = normalizedOutputDir.relativize(normTarget).toString().replace('\\', '/');
      newManifestEntries.put(relPath, relOut + "|" + fingerprint);
    }

    // 4. Commit writes
    int writtenCount = 0;
    int skippedCount = 0;
    for (Map.Entry<Path, String> entry : pendingWrites.entrySet()) {
      boolean written = writeIfChanged(entry.getKey(), entry.getValue());
      if (written) {
        writtenCount++;
      } else {
        skippedCount++;
      }
    }

    // 5. Clean stale owned files
    int deletedCount = 0;
    if (cleanStaleOutputs) {
      deletedCount = cleanStaleOutputs(normalizedOutputDir, currentOutputs, previousOwnedOutputs);
    }

    // 6. Commit manifest
    writeManifest(manifestFile, newManifestEntries);

    Map<String, Integer> result = new LinkedHashMap<>();
    result.put("generated", pendingWrites.size());
    result.put("written", writtenCount);
    result.put("skipped", skippedCount);
    result.put("deleted", deletedCount);
    return Collections.unmodifiableMap(result);
  }

  private static String stripLeadingSlash(String s) {
    while (s.startsWith("/")) {
      s = s.substring(1);
    }
    return s;
  }

  private static List<Path> scanTemplates(
      Path sourceDirectory, List<String> includes, List<String> excludes) throws IOException {
    List<Path> matched = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(sourceDirectory)) {
      for (Path file : stream.filter(Files::isRegularFile).toList()) {
        Path rel = sourceDirectory.relativize(file);
        boolean included = false;
        for (String inc : includes) {
          if (matchesGlob(rel, inc)) {
            included = true;
            break;
          }
        }
        if (!included) {
          continue;
        }
        boolean excluded = false;
        for (String exc : excludes) {
          if (matchesGlob(rel, exc)) {
            excluded = true;
            break;
          }
        }
        if (!excluded) {
          matched.add(file);
        }
      }
    }
    matched.sort(Comparator.comparing(p -> sourceDirectory.relativize(p).toString()));
    return matched;
  }

  private static boolean matchesGlob(Path relativePath, String pattern) {
    java.nio.file.FileSystem fs = FileSystems.getDefault();
    if (fs.getPathMatcher("glob:" + pattern).matches(relativePath)) {
      return true;
    }
    if (pattern.startsWith("**/")) {
      String subPattern = pattern.substring(3);
      if (fs.getPathMatcher("glob:" + subPattern).matches(relativePath)) {
        return true;
      }
      if (subPattern.contains("/**/")) {
        if (fs.getPathMatcher("glob:" + subPattern.replace("/**/", "/")).matches(relativePath)) {
          return true;
        }
      }
    }
    if (pattern.contains("/**/")) {
      if (fs.getPathMatcher("glob:" + pattern.replace("/**/", "/")).matches(relativePath)) {
        return true;
      }
    }
    return false;
  }

  private static Set<Path> loadManifestOwnedFiles(Path outputDir, Path manifestFile) {
    Set<Path> owned = new HashSet<>();
    if (!Files.isRegularFile(manifestFile)) {
      return owned;
    }
    try {
      String json = Files.readString(manifestFile, StandardCharsets.UTF_8);
      Pattern filePattern = Pattern.compile("\"output\"\\s*:\\s*\"([^\"]+)\"");
      Matcher m = filePattern.matcher(json);
      while (m.find()) {
        String relOut = m.group(1);
        Path target = outputDir.resolve(relOut).normalize();
        if (target.startsWith(outputDir)
            && (target.getFileName().toString().endsWith(".vtl.d.ts")
                || target.getFileName().toString().endsWith(".d.ts"))) {
          owned.add(target);
        }
      }
    } catch (Exception ignored) {
    }
    return owned;
  }

  private static void writeManifest(Path manifestFile, Map<String, String> entries)
      throws IOException {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\n");
    sb.append("  \"schemaVersion\": 1,\n");
    sb.append("  \"files\": {");
    if (entries.isEmpty()) {
      sb.append("}\n}\n");
    } else {
      sb.append("\n");
      List<Map.Entry<String, String>> sorted = new ArrayList<>(entries.entrySet());
      sorted.sort(Map.Entry.comparingByKey());
      for (int i = 0; i < sorted.size(); i++) {
        Map.Entry<String, String> entry = sorted.get(i);
        String templateKey = entry.getKey();
        String val = entry.getValue();
        int pipe = val.indexOf('|');
        String outPath = pipe > 0 ? val.substring(0, pipe) : val;
        String fp = pipe > 0 ? val.substring(pipe + 1) : "";

        sb.append("    \"").append(escapeTsString(templateKey)).append("\": {\n");
        sb.append("      \"output\": \"").append(escapeTsString(outPath)).append("\",\n");
        sb.append("      \"fingerprint\": \"").append(escapeTsString(fp)).append("\"\n");
        sb.append("    }");
        if (i < sorted.size() - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  }\n");
      sb.append("}\n");
    }
    writeIfChanged(manifestFile, sb.toString());
  }

  /**
   * Writes content to file only if changed, preserving mtime on identical content.
   *
   * @return true if written/created, false if identical content was already present
   */
  public static boolean writeIfChanged(Path file, String content) throws IOException {
    Objects.requireNonNull(file, "file must not be null");
    Objects.requireNonNull(content, "content must not be null");

    byte[] newBytes = content.getBytes(StandardCharsets.UTF_8);
    if (Files.exists(file)) {
      byte[] existingBytes = Files.readAllBytes(file);
      if (MessageDigest.isEqual(existingBytes, newBytes)) {
        return false; // Skip rewrite, preserve mtime
      }
    }

    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(file, newBytes);
    return true;
  }

  /**
   * Safely deletes stale owned output files from a previous run, strictly ignoring unowned files.
   *
   * @return number of stale files deleted
   */
  public static int cleanStaleOutputs(
      Path outputDir, Set<Path> currentOutputs, Set<Path> previousOwnedOutputs) throws IOException {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
    Objects.requireNonNull(currentOutputs, "currentOutputs must not be null");
    Objects.requireNonNull(previousOwnedOutputs, "previousOwnedOutputs must not be null");

    int deletedCount = 0;
    Path normalizedOutputDir = outputDir.toAbsolutePath().normalize();

    for (Path prev : previousOwnedOutputs) {
      Path normalized = prev.toAbsolutePath().normalize();
      if (!normalized.startsWith(normalizedOutputDir)) {
        // Outside outputDir: security violation, skip
        continue;
      }
      String fileName = normalized.getFileName().toString();
      if (!fileName.endsWith(".vtl.d.ts") && !fileName.endsWith(".d.ts")) {
        // Only clean up declaration files
        continue;
      }
      // Symlink safeguard
      if (Files.isSymbolicLink(normalized)) {
        try {
          Path real = normalized.toRealPath();
          if (!real.startsWith(normalizedOutputDir.toRealPath())) {
            continue;
          }
        } catch (IOException e) {
          continue;
        }
      }
      if (!currentOutputs.contains(normalized) && Files.exists(normalized)) {
        Files.delete(normalized);
        deletedCount++;
      }
    }
    return deletedCount;
  }

  /**
   * Derives declaration file path safely relative to base output directory, guarding against
   * traversal. Preserves .vtl.d.ts extension per ADR-0022 and M46 specification.
   */
  public static Path deriveDeclarationFilePath(Path baseOutputDir, String templatePath) {
    Objects.requireNonNull(baseOutputDir, "baseOutputDir must not be null");
    Objects.requireNonNull(templatePath, "templatePath must not be null");

    String normalized = templatePath.replace('\\', '/').trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    String dtsRelativePath;
    if (normalized.endsWith(".vtl.d.ts")) {
      dtsRelativePath = normalized;
    } else if (normalized.endsWith(".vtl.html")) {
      String baseName = normalized.substring(0, normalized.length() - ".vtl.html".length());
      dtsRelativePath = baseName + ".vtl.d.ts";
    } else if (normalized.endsWith(".vtl")) {
      dtsRelativePath = normalized + ".d.ts";
    } else {
      int dot = normalized.lastIndexOf('.');
      String baseName = (dot > 0) ? normalized.substring(0, dot) : normalized;
      dtsRelativePath = baseName + ".vtl.d.ts";
    }

    Path resolved = baseOutputDir.resolve(dtsRelativePath).normalize();
    Path normalizedBase = baseOutputDir.toAbsolutePath().normalize();
    if (!resolved.toAbsolutePath().normalize().startsWith(normalizedBase)) {
      throw new IllegalArgumentException(
          "Path traversal detected in templatePath: " + templatePath);
    }
    return resolved;
  }

  /**
   * Derives deterministic template parameter interface name from template path. e.g.
   * "employees/index.vtl" -> "EmployeesIndexTemplateParameters"
   */
  public static String deriveTemplateParametersName(String templateId) {
    if (templateId == null || templateId.isBlank()) {
      return "TemplateParameters";
    }
    String normalized = templateId.replace('\\', '/').trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.endsWith(".vtl.html")) {
      normalized = normalized.substring(0, normalized.length() - ".vtl.html".length());
    } else if (normalized.endsWith(".vtl")) {
      normalized = normalized.substring(0, normalized.length() - ".vtl".length());
    } else {
      int dot = normalized.lastIndexOf('.');
      if (dot > 0) {
        normalized = normalized.substring(0, dot);
      }
    }
    String[] parts = normalized.split("[/_\\-\\.]");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (!part.isBlank()) {
        sb.append(Character.toUpperCase(part.charAt(0)));
        if (part.length() > 1) {
          sb.append(part.substring(1));
        }
      }
    }
    if (sb.isEmpty()) {
      return "TemplateParameters";
    }
    return sb.toString() + "TemplateParameters";
  }

  /** Projects a canonical TypeRef into TypeScript type syntax. */
  public static String projectCanonicalTypeRef(TypeRef type, Map<String, String> typeSymbolTable) {
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
      if (typeSymbolTable != null && typeSymbolTable.containsKey(fqcn)) {
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
        case "java.lang.Character", "java.lang.String", "java.lang.CharSequence" -> "string";
        case "java.lang.Void" -> "void";
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
        case "java.util.Optional" -> "unknown | null";
        case "java.util.List",
            "java.util.Collection",
            "java.lang.Iterable",
            "java.util.Iterable",
            "java.util.ArrayList" ->
            "unknown[]";
        // JSON serializer parity: Sets serialize as JSON arrays, NOT JavaScript Sets!
        case "java.util.Set", "java.util.HashSet" -> "unknown[]";
        case "java.util.Map", "java.util.HashMap" -> "Record<string, unknown>";
        default -> {
          if (typeSymbolTable != null && typeSymbolTable.containsValue(fqcn)) {
            yield fqcn;
          }
          if (typeSymbolTable != null) {
            for (Map.Entry<String, String> e : typeSymbolTable.entrySet()) {
              if (extractSimpleName(e.getKey()).equals(fqcn)) {
                yield e.getValue();
              }
            }
          }
          yield sanitizeTypeName(extractSimpleName(fqcn));
        }
      };
    }

    if (type instanceof CanonicalSchemaModel.NamedTypeRef nt) {
      String baseName;
      if (typeSymbolTable != null && typeSymbolTable.containsKey(nt.name())) {
        baseName = typeSymbolTable.get(nt.name());
      } else {
        String found = null;
        if (typeSymbolTable != null) {
          for (Map.Entry<String, String> e : typeSymbolTable.entrySet()) {
            if (extractSimpleName(e.getKey()).equals(nt.name())) {
              found = e.getValue();
              break;
            }
          }
        }
        baseName = found != null ? found : sanitizeTypeName(nt.name());
      }
      if (nt.arguments().isEmpty()) {
        return baseName;
      }
      List<String> argStrings = new ArrayList<>();
      for (TypeRef a : nt.arguments()) {
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
      if (typeSymbolTable != null && typeSymbolTable.containsKey(et.name())) {
        return typeSymbolTable.get(et.name());
      }
      return sanitizeTypeName(CanonicalSchemaModel.simpleName(et.name()));
    }

    if (type instanceof CanonicalSchemaModel.ParameterizedTypeRef pt) {
      String raw = pt.rawType();
      List<TypeRef> args = pt.arguments();
      if ("java.util.Optional".equals(raw)) {
        if (args.isEmpty()) return "unknown | null";
        return projectCanonicalTypeRef(args.get(0), typeSymbolTable) + " | null";
      }
      if (isListType(raw)) {
        if (args.isEmpty()) return "unknown[]";
        return wrapArrayElement(projectCanonicalTypeRef(args.get(0), typeSymbolTable)) + "[]";
      }
      // JSON serializer parity: Sets serialize as JSON arrays, NOT JavaScript Sets!
      if (isSetType(raw)) {
        if (args.isEmpty()) return "unknown[]";
        return wrapArrayElement(projectCanonicalTypeRef(args.get(0), typeSymbolTable)) + "[]";
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
      String baseName =
          typeSymbolTable != null
              ? typeSymbolTable.getOrDefault(raw, sanitizeTypeName(extractSimpleName(raw)))
              : sanitizeTypeName(extractSimpleName(raw));
      if (args.isEmpty()) return baseName;
      List<String> argStrings = new ArrayList<>();
      for (TypeRef a : args) {
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
      if ("extends".equalsIgnoreCase(wt.boundKind()) && wt.bound().isPresent()) {
        return projectCanonicalTypeRef(wt.bound().get(), typeSymbolTable);
      }
      return "unknown";
    }

    return "unknown";
  }

  /** Disambiguates simple type name collisions across packages deterministically. */
  public static Map<String, String> resolveTypeNames(Set<String> fqcns) {
    return resolveTypeNames(fqcns, Set.of("TemplateParameters"));
  }

  /**
   * Disambiguates simple type name collisions across packages deterministically with reserved
   * names.
   */
  public static Map<String, String> resolveTypeNames(Set<String> fqcns, Set<String> reservedNames) {
    Map<String, String> candidateNames = new LinkedHashMap<>();
    Map<String, List<String>> byCandidate = new LinkedHashMap<>();

    List<String> sortedFqcns = new ArrayList<>(fqcns);
    Collections.sort(sortedFqcns);

    for (String fqcn : sortedFqcns) {
      String candidate = extractSimpleName(fqcn);
      candidateNames.put(fqcn, candidate);
      byCandidate.computeIfAbsent(candidate, k -> new ArrayList<>()).add(fqcn);
    }

    Map<String, String> resolved = new LinkedHashMap<>();
    Set<String> assignedNames = new HashSet<>(reservedNames);

    for (String fqcn : sortedFqcns) {
      String candidate = candidateNames.get(fqcn);
      List<String> collisions = byCandidate.get(candidate);

      String finalName;
      if (collisions.size() == 1 && !reservedNames.contains(candidate)) {
        finalName = sanitizeTypeName(candidate);
      } else {
        finalName = deriveDisambiguatedName(fqcn, collisions);
      }

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

  static String deriveDisambiguatedName(String fqcn, List<String> collidingFqcns) {
    int lastDot = fqcn.lastIndexOf('.');
    if (lastDot < 0) {
      return sanitizeTypeName(fqcn);
    }
    String pkg = fqcn.substring(0, lastDot);
    String simpleName = extractSimpleName(fqcn);
    String[] segments = pkg.split("\\.");

    int minSegs = (segments.length > 1 && segments[segments.length - 1].length() <= 1) ? 2 : 1;

    for (int numSegs = minSegs; numSegs <= segments.length; numSegs++) {
      String prefix = buildPascalCasePrefix(segments, numSegs);
      String candidate = prefix + "_" + simpleName;
      boolean unique = true;
      for (String other : collidingFqcns) {
        if (!other.equals(fqcn)) {
          int otherDot = other.lastIndexOf('.');
          if (otherDot >= 0) {
            String otherPkg = other.substring(0, otherDot);
            String[] otherSegs = otherPkg.split("\\.");
            String otherPrefix = buildPascalCasePrefix(otherSegs, numSegs);
            if (prefix.equals(otherPrefix)) {
              unique = false;
              break;
            }
          }
        }
      }
      if (unique) {
        return sanitizeTypeName(candidate);
      }
    }
    return sanitizeTypeName(fqcn.replace('.', '_').replace('$', '_'));
  }

  private static String buildPascalCasePrefix(String[] segments, int count) {
    int start = Math.max(0, segments.length - count);
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < segments.length; i++) {
      String seg = segments[i];
      if (!seg.isEmpty()) {
        sb.append(Character.toUpperCase(seg.charAt(0)));
        if (seg.length() > 1) {
          sb.append(seg.substring(1));
        }
      }
    }
    return sb.toString();
  }

  public static String extractSimpleName(String fqcn) {
    int dot = fqcn.lastIndexOf('.');
    String afterDot = (dot >= 0 && dot < fqcn.length() - 1) ? fqcn.substring(dot + 1) : fqcn;
    return afterDot.replace('$', '_');
  }

  public static String sanitizeTypeName(String raw) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    String s = sb.toString();
    if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
      s = "_" + s;
    }
    if (TS_RESERVED_WORDS.contains(s)) {
      s = s + "Type";
    }
    return s;
  }

  public static String formatPropertyName(String raw) {
    if (isValidTsIdentifier(raw)) {
      return raw;
    }
    return "\"" + escapeTsString(raw) + "\"";
  }

  private static boolean isValidTsIdentifier(String s) {
    if (s == null || s.isEmpty()) return false;
    if (TS_RESERVED_WORDS.contains(s)) return false;
    char first = s.charAt(0);
    if (!Character.isLetter(first) && first != '_' && first != '$') return false;
    for (int i = 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') return false;
    }
    return true;
  }

  private static String escapeTsString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private static String wrapArrayElement(String type) {
    if (type.contains(" ") || type.contains("|") || type.contains("&")) {
      return "(" + type + ")";
    }
    return type;
  }

  private static boolean isListType(String fqcn) {
    return "java.util.List".equals(fqcn)
        || "java.util.Collection".equals(fqcn)
        || "java.lang.Iterable".equals(fqcn)
        || "java.util.Iterable".equals(fqcn)
        || "java.util.ArrayList".equals(fqcn)
        || "java.util.LinkedList".equals(fqcn);
  }

  private static boolean isSetType(String fqcn) {
    return "java.util.Set".equals(fqcn)
        || "java.util.HashSet".equals(fqcn)
        || "java.util.LinkedHashSet".equals(fqcn)
        || "java.util.TreeSet".equals(fqcn);
  }

  private static boolean isMapType(String fqcn) {
    return "java.util.Map".equals(fqcn)
        || "java.util.HashMap".equals(fqcn)
        || "java.util.LinkedHashMap".equals(fqcn)
        || "java.util.TreeMap".equals(fqcn)
        || "java.util.concurrent.ConcurrentHashMap".equals(fqcn);
  }
}
