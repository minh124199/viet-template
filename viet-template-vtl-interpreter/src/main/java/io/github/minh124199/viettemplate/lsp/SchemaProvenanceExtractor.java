package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeDef;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and indexes rich declaration provenance and source locations for canonical schemas
 * across TypeScript (.d.ts), JSON Schema (.schema.json), Viet Template schema (.vt-schema.json),
 * companion contracts (.contract), and Java models.
 */
final class SchemaProvenanceExtractor {

  private SchemaProvenanceExtractor() {}

  static Map<String, SchemaProvenance> extractAll(
      CanonicalSchema schema, Optional<Path> sourcePath, ClassLoader classLoader) {
    Objects.requireNonNull(schema, "schema must not be null");
    Map<String, SchemaProvenance> result = new LinkedHashMap<>();
    SchemaFormat format = schema.format();
    ClassLoader loader =
        classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = SchemaProvenanceExtractor.class.getClassLoader();
    }

    String content = null;
    Path path = sourcePath.orElse(null);
    if (path != null && Files.isRegularFile(path)) {
      try {
        content = Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException ignored) {
      }
    }
    if ((content == null || content.isEmpty()) && !schema.rawSource().isEmpty()) {
      content = schema.rawSource();
    }

    String fileName = path != null ? path.getFileName().toString() : "";

    if (fileName.endsWith(".d.ts") || format == SchemaFormat.TYPESCRIPT) {
      extractTypeScript(content, path, schema, result);
    } else if (fileName.endsWith(".schema.json")
        || (format == SchemaFormat.JSON_SCHEMA && !fileName.endsWith(".vt-schema.json"))) {
      extractJsonSchema(content, path, schema, result);
    } else if (fileName.endsWith(".vt-schema.json")
        || (format == SchemaFormat.CONTRACT
            && (fileName.endsWith(".json")
                || (content != null && content.trim().startsWith("{"))))) {
      extractVtSchemaJson(content, path, schema, result);
    } else if (fileName.endsWith(".contract") || format == SchemaFormat.CONTRACT) {
      extractContract(content, path, schema, loader, result);
    } else {
      extractJavaModel(schema, loader, result);
    }

    return result;
  }

  static Optional<SchemaProvenance> findParameter(
      Map<String, SchemaProvenance> index, String paramName) {
    if (index == null || paramName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(index.get("param:" + paramName));
  }

  static Optional<SchemaProvenance> findProperty(
      Map<String, SchemaProvenance> index, String typeName, String propName) {
    if (index == null || typeName == null || propName == null) {
      return Optional.empty();
    }
    SchemaProvenance p = index.get("prop:" + typeName + "." + propName);
    if (p != null) {
      return Optional.of(p);
    }
    int lastDot = typeName.lastIndexOf('.');
    if (lastDot >= 0) {
      p = index.get("prop:" + typeName.substring(lastDot + 1) + "." + propName);
      if (p != null) {
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }

  static Optional<SchemaProvenance> findType(Map<String, SchemaProvenance> index, String typeName) {
    if (index == null || typeName == null) {
      return Optional.empty();
    }
    SchemaProvenance p = index.get("type:" + typeName);
    if (p != null) {
      return Optional.of(p);
    }
    int lastDot = typeName.lastIndexOf('.');
    if (lastDot >= 0) {
      p = index.get("type:" + typeName.substring(lastDot + 1));
      if (p != null) {
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }

  // --- TypeScript .d.ts Extractor ---

  private static final Pattern TS_INTERFACE_PATTERN =
      Pattern.compile("(?:export\\s+)?interface\\s+([A-Za-z0-9_]+)");
  private static final Pattern TS_TYPE_PATTERN =
      Pattern.compile("(?:export\\s+)?type\\s+([A-Za-z0-9_]+)\\s*=");
  private static final Pattern TS_PROP_PATTERN =
      Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*(\\??)\\s*:");

  private static void extractTypeScript(
      String content, Path path, CanonicalSchema schema, Map<String, SchemaProvenance> out) {
    if (content == null || content.isEmpty()) {
      return;
    }

    String[] lines = content.split("\r?\n");
    String currentType = null;
    int braceDepth = 0;

    for (int i = 0; i < lines.length; i++) {
      int lineNum = i + 1;
      String line = lines[i];

      // Strip single-line comments
      int commentIdx = line.indexOf("//");
      String cleanLine = commentIdx >= 0 ? line.substring(0, commentIdx) : line;

      if (braceDepth == 0) {
        Matcher ifaceMatcher = TS_INTERFACE_PATTERN.matcher(cleanLine);
        if (ifaceMatcher.find()) {
          currentType = ifaceMatcher.group(1);
          int startCol = cleanLine.indexOf(currentType) + 1;
          int endCol = startCol + currentType.length();
          SchemaSourceLocation loc =
              path != null
                  ? SchemaSourceLocation.of(path, lineNum, startCol, lineNum, endCol)
                  : null;
          SchemaProvenance prov =
              new SchemaProvenance(
                  SchemaFormat.TYPESCRIPT,
                  Optional.ofNullable(path),
                  currentType,
                  Optional.empty(),
                  Optional.ofNullable(loc),
                  false,
                  Optional.empty());
          out.put("type:" + currentType, prov);
        } else {
          Matcher typeMatcher = TS_TYPE_PATTERN.matcher(cleanLine);
          if (typeMatcher.find()) {
            currentType = typeMatcher.group(1);
            int startCol = cleanLine.indexOf(currentType) + 1;
            int endCol = startCol + currentType.length();
            SchemaSourceLocation loc =
                path != null
                    ? SchemaSourceLocation.of(path, lineNum, startCol, lineNum, endCol)
                    : null;
            SchemaProvenance prov =
                new SchemaProvenance(
                    SchemaFormat.TYPESCRIPT,
                    Optional.ofNullable(path),
                    currentType,
                    Optional.empty(),
                    Optional.ofNullable(loc),
                    false,
                    Optional.empty());
            out.put("type:" + currentType, prov);
          }
        }
      }

      for (int c = 0; c < cleanLine.length(); c++) {
        char ch = cleanLine.charAt(c);
        if (ch == '{') braceDepth++;
        else if (ch == '}') {
          braceDepth--;
          if (braceDepth <= 0) {
            braceDepth = 0;
            currentType = null;
          }
        }
      }

      if (braceDepth >= 1 && currentType != null) {
        Matcher propMatcher = TS_PROP_PATTERN.matcher(cleanLine);
        if (propMatcher.find()) {
          String propName = propMatcher.group(1);
          int startCol = cleanLine.indexOf(propName) + 1;
          int endCol = startCol + propName.length();
          SchemaSourceLocation loc =
              path != null
                  ? SchemaSourceLocation.of(path, lineNum, startCol, lineNum, endCol)
                  : null;
          SchemaProvenance prov =
              new SchemaProvenance(
                  SchemaFormat.TYPESCRIPT,
                  Optional.ofNullable(path),
                  currentType,
                  Optional.of(propName),
                  Optional.ofNullable(loc),
                  false,
                  Optional.empty());
          out.put("prop:" + currentType + "." + propName, prov);

          if ("TemplateParameters".equals(currentType)
              || schema.parameters().containsKey(propName)) {
            out.put("param:" + propName, prov);
          }
        }
      }
    }

    // Ensure all schema parameters have at least fallback provenances
    for (String paramName : schema.parameters().keySet()) {
      out.computeIfAbsent(
          "param:" + paramName,
          k ->
              new SchemaProvenance(
                  SchemaFormat.TYPESCRIPT,
                  Optional.ofNullable(path),
                  "TemplateParameters",
                  Optional.of(paramName),
                  Optional.empty(),
                  false,
                  Optional.empty()));
    }
  }

  // --- JSON Parser for .schema.json and .vt-schema.json ---

  private static void extractJsonSchema(
      String content, Path path, CanonicalSchema schema, Map<String, SchemaProvenance> out) {
    if (content == null || content.isEmpty()) {
      return;
    }
    parseJsonLocations(content, path, schema, SchemaFormat.JSON_SCHEMA, out);
  }

  private static void extractVtSchemaJson(
      String content, Path path, CanonicalSchema schema, Map<String, SchemaProvenance> out) {
    if (content == null || content.isEmpty()) {
      return;
    }
    parseJsonLocations(content, path, schema, SchemaFormat.CONTRACT, out);
  }

  private static void parseJsonLocations(
      String json,
      Path path,
      CanonicalSchema schema,
      SchemaFormat format,
      Map<String, SchemaProvenance> out) {
    Path effectivePath =
        path != null
            ? path
            : Path.of(
                schema != null && !schema.templateId().isBlank()
                    ? schema.templateId() + ".vt-schema.json"
                    : "schema.json");
    int[] lineOffsets = computeLineOffsets(json);
    List<JsonToken> tokens = tokenizeJson(json, lineOffsets);

    int pos = 0;
    int len = tokens.size();
    List<String> pathStack = new ArrayList<>();

    while (pos < len) {
      JsonToken tok = tokens.get(pos);

      if (tok.kind == TokenKind.STRING
          && pos + 1 < len
          && tokens.get(pos + 1).kind == TokenKind.COLON) {
        String key = tok.value;
        JsonToken keyTok = tok;
        pos += 2; // skip key and colon

        // Record key on current path
        String parentKey = pathStack.isEmpty() ? "" : pathStack.get(pathStack.size() - 1);
        String grandParentKey = pathStack.size() >= 2 ? pathStack.get(pathStack.size() - 2) : "";

        // Top-level properties or parameters -> template parameters
        if ("properties".equals(parentKey) && pathStack.size() == 1) {
          SchemaSourceLocation loc =
              SchemaSourceLocation.of(
                  effectivePath, keyTok.line, keyTok.col, keyTok.line, keyTok.col + key.length());
          SchemaProvenance prov =
              new SchemaProvenance(
                  format,
                  Optional.ofNullable(path),
                  "TemplateParameters",
                  Optional.of(key),
                  Optional.of(loc),
                  false,
                  Optional.empty());
          out.put("param:" + key, prov);
        } else if ("parameters".equals(parentKey) && pathStack.size() == 1) {
          SchemaSourceLocation loc =
              SchemaSourceLocation.of(
                  effectivePath, keyTok.line, keyTok.col, keyTok.line, keyTok.col + key.length());
          SchemaProvenance prov =
              new SchemaProvenance(
                  format,
                  Optional.ofNullable(path),
                  "TemplateParameters",
                  Optional.of(key),
                  Optional.of(loc),
                  false,
                  Optional.empty());
          out.put("param:" + key, prov);
        } else if (("definitions".equals(parentKey)
                || "$defs".equals(parentKey)
                || "types".equals(parentKey))
            && pathStack.size() == 1) {
          // Type definition
          SchemaSourceLocation loc =
              SchemaSourceLocation.of(
                  effectivePath, keyTok.line, keyTok.col, keyTok.line, keyTok.col + key.length());
          SchemaProvenance prov =
              new SchemaProvenance(
                  format,
                  Optional.ofNullable(path),
                  key,
                  Optional.empty(),
                  Optional.of(loc),
                  false,
                  Optional.empty());
          out.put("type:" + key, prov);
        } else if ("properties".equals(parentKey) && pathStack.size() >= 3) {
          // Property of a defined type: e.g. definitions -> User -> properties -> name
          String typeName = grandParentKey;
          SchemaSourceLocation loc =
              SchemaSourceLocation.of(
                  effectivePath, keyTok.line, keyTok.col, keyTok.line, keyTok.col + key.length());
          SchemaProvenance prov =
              new SchemaProvenance(
                  format,
                  Optional.ofNullable(path),
                  typeName,
                  Optional.of(key),
                  Optional.of(loc),
                  false,
                  Optional.empty());
          out.put("prop:" + typeName + "." + key, prov);
        }

        // If value is an object or array, push key to stack
        if (pos < len) {
          JsonToken nextTok = tokens.get(pos);
          if (nextTok.kind == TokenKind.LBRACE || nextTok.kind == TokenKind.LBRACKET) {
            pathStack.add(key);
          }
        }
        continue;
      }

      if (tok.kind == TokenKind.RBRACE || tok.kind == TokenKind.RBRACKET) {
        if (!pathStack.isEmpty()) {
          pathStack.remove(pathStack.size() - 1);
        }
      }

      pos++;
    }
  }

  // --- Contract (.contract) Extractor ---

  private static void extractContract(
      String content,
      Path path,
      CanonicalSchema schema,
      ClassLoader classLoader,
      Map<String, SchemaProvenance> out) {
    if (content == null || content.isEmpty()) {
      return;
    }

    try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
      String line;
      int lineNum = 0;
      while ((line = reader.readLine()) != null) {
        lineNum++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
          continue;
        }

        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
          continue;
        }

        String key = trimmed.substring(0, eq).trim();
        String val = trimmed.substring(eq + 1).trim();

        if ("class".equalsIgnoreCase(key)
            || "model".equalsIgnoreCase(key)
            || "record".equalsIgnoreCase(key)) {
          try {
            Class<?> clazz = Class.forName(val, false, classLoader);
            expandJavaClass(clazz, out);
          } catch (ClassNotFoundException ignored) {
          }
          continue;
        }

        String paramName = key;
        if (paramName.startsWith("nullable ")) {
          paramName = paramName.substring("nullable ".length()).trim();
        }

        int startCol = line.indexOf(paramName) + 1;
        int endCol = startCol + paramName.length();
        SchemaSourceLocation loc =
            path != null ? SchemaSourceLocation.of(path, lineNum, startCol, lineNum, endCol) : null;

        SchemaProvenance prov =
            new SchemaProvenance(
                SchemaFormat.CONTRACT,
                Optional.ofNullable(path),
                "TemplateContract",
                Optional.of(paramName),
                Optional.ofNullable(loc),
                true,
                Optional.of(paramName));
        out.put("param:" + paramName, prov);

        // If val points to a Java class, expand its properties
        try {
          Class<?> clazz = Class.forName(val, false, classLoader);
          expandJavaClass(clazz, out);
        } catch (ClassNotFoundException ignored) {
        }
      }
    } catch (IOException ignored) {
    }
  }

  // --- Java Model Extractor ---

  private static void extractJavaModel(
      CanonicalSchema schema, ClassLoader classLoader, Map<String, SchemaProvenance> out) {
    for (Map.Entry<String, ParameterDef> entry : schema.parameters().entrySet()) {
      String paramName = entry.getKey();
      SchemaProvenance prov =
          new SchemaProvenance(
              SchemaFormat.JAVA,
              Optional.empty(),
              "JavaModel",
              Optional.of(paramName),
              Optional.empty(),
              true,
              Optional.of(paramName));
      out.put("param:" + paramName, prov);
    }

    for (Map.Entry<String, TypeDef> entry : schema.types().entrySet()) {
      String typeName = entry.getKey();
      TypeDef typeDef = entry.getValue();

      Class<?> clazz = null;
      try {
        clazz = Class.forName(typeName, false, classLoader);
      } catch (ClassNotFoundException ignored) {
      }

      if (clazz != null) {
        expandJavaClass(clazz, out);
      } else {
        // Fallback for shape-only or unmatched JVM types
        for (String propName : typeDef.properties().keySet()) {
          SchemaProvenance prov =
              new SchemaProvenance(
                  SchemaFormat.JAVA,
                  Optional.empty(),
                  typeName,
                  Optional.of(propName),
                  Optional.empty(),
                  true,
                  Optional.of(propName));
          out.put("prop:" + typeName + "." + propName, prov);
        }
      }
    }
  }

  private static void expandJavaClass(Class<?> clazz, Map<String, SchemaProvenance> out) {
    String simpleName = clazz.getSimpleName();
    String fqcn = clazz.getName();

    SchemaProvenance typeProv =
        new SchemaProvenance(
            SchemaFormat.JAVA,
            Optional.empty(),
            simpleName,
            Optional.empty(),
            Optional.empty(),
            true,
            Optional.of(simpleName));
    out.put("type:" + simpleName, typeProv);
    out.put("type:" + fqcn, typeProv);

    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      if (components != null) {
        for (RecordComponent comp : components) {
          String propName = comp.getName();
          SchemaProvenance prov =
              new SchemaProvenance(
                  SchemaFormat.JAVA,
                  Optional.empty(),
                  simpleName,
                  Optional.of(propName),
                  Optional.empty(),
                  true,
                  Optional.of(propName + "()"));
          out.put("prop:" + simpleName + "." + propName, prov);
          out.put("prop:" + fqcn + "." + propName, prov);
        }
      }
    } else {
      Method[] methods = clazz.getMethods();
      for (Method m : methods) {
        if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
          continue;
        }
        if (m.getDeclaringClass() == Object.class) {
          continue;
        }
        String mName = m.getName();
        String propName = null;
        if (mName.startsWith("get") && mName.length() > 3) {
          propName = Character.toLowerCase(mName.charAt(3)) + mName.substring(4);
        } else if (mName.startsWith("is") && mName.length() > 2) {
          propName = Character.toLowerCase(mName.charAt(2)) + mName.substring(3);
        }
        if (propName != null) {
          SchemaProvenance prov =
              new SchemaProvenance(
                  SchemaFormat.JAVA,
                  Optional.empty(),
                  simpleName,
                  Optional.of(propName),
                  Optional.empty(),
                  true,
                  Optional.of(mName + "()"));
          out.put("prop:" + simpleName + "." + propName, prov);
          out.put("prop:" + fqcn + "." + propName, prov);
        }
      }
    }
  }

  // --- Lightweight JSON Tokenizer for Line/Col Tracking ---

  private enum TokenKind {
    STRING,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    COLON,
    COMMA,
    OTHER
  }

  private record JsonToken(TokenKind kind, String value, int line, int col) {}

  private static List<JsonToken> tokenizeJson(String json, int[] lineOffsets) {
    List<JsonToken> tokens = new ArrayList<>();
    int i = 0;
    int len = json.length();

    while (i < len) {
      char c = json.charAt(i);
      if (Character.isWhitespace(c) || c == '\uFEFF') {
        i++;
        continue;
      }

      int line = getLine(lineOffsets, i);
      int col = getCol(lineOffsets, i, line);

      if (c == '{') {
        tokens.add(new JsonToken(TokenKind.LBRACE, "{", line, col));
        i++;
      } else if (c == '}') {
        tokens.add(new JsonToken(TokenKind.RBRACE, "}", line, col));
        i++;
      } else if (c == '[') {
        tokens.add(new JsonToken(TokenKind.LBRACKET, "[", line, col));
        i++;
      } else if (c == ']') {
        tokens.add(new JsonToken(TokenKind.RBRACKET, "]", line, col));
        i++;
      } else if (c == ':') {
        tokens.add(new JsonToken(TokenKind.COLON, ":", line, col));
        i++;
      } else if (c == ',') {
        tokens.add(new JsonToken(TokenKind.COMMA, ",", line, col));
        i++;
      } else if (c == '"') {
        int strStart = i;
        i++; // skip opening quote
        int textCol = col + 1;
        StringBuilder sb = new StringBuilder();
        while (i < len && json.charAt(i) != '"') {
          char ch = json.charAt(i);
          if (ch == '\\' && i + 1 < len) {
            i++;
            char esc = json.charAt(i);
            sb.append(esc == 'n' ? '\n' : (esc == 't' ? '\t' : esc));
          } else {
            sb.append(ch);
          }
          i++;
        }
        if (i < len && json.charAt(i) == '"') {
          i++; // skip closing quote
        }
        tokens.add(new JsonToken(TokenKind.STRING, sb.toString(), line, textCol));
      } else {
        // Number, boolean, null
        int start = i;
        while (i < len
            && !Character.isWhitespace(json.charAt(i))
            && json.charAt(i) != ','
            && json.charAt(i) != '}'
            && json.charAt(i) != ']') {
          i++;
        }
        tokens.add(new JsonToken(TokenKind.OTHER, json.substring(start, i), line, col));
      }
    }

    return tokens;
  }

  private static int[] computeLineOffsets(String text) {
    List<Integer> offsets = new ArrayList<>();
    offsets.add(0);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\r') {
        if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        offsets.add(i + 1);
      } else if (c == '\n') {
        offsets.add(i + 1);
      }
    }
    int[] result = new int[offsets.size()];
    for (int i = 0; i < offsets.size(); i++) {
      result[i] = offsets.get(i);
    }
    return result;
  }

  private static int getLine(int[] lineOffsets, int pos) {
    int idx = Arrays.binarySearch(lineOffsets, pos);
    int lineIdx = idx >= 0 ? idx : -idx - 2;
    return Math.max(1, lineIdx + 1);
  }

  private static int getCol(int[] lineOffsets, int pos, int line) {
    int lineStart = lineOffsets[line - 1];
    return Math.max(1, pos - lineStart + 1);
  }
}
