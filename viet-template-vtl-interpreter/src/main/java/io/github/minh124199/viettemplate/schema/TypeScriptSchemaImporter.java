package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Zero-dependency, offline TypeScript declaration (*.d.ts) importer.
 *
 * <p>Emits deterministic canonical schema models and detailed line/column diagnostics. Supports
 * interface and type declarations, primitive scalar types, nested object literals, arrays, maps
 * (Record/index signatures), enums, string literal unions, and nullability/optionality variants.
 */
public final class TypeScriptSchemaImporter implements SchemaImporter {

  public static final String CODE_SYNTAX_ERROR = "TS_SCHEMA_SYNTAX_ERROR";
  public static final String CODE_UNSUPPORTED_CONDITIONAL_TYPE =
      "TS_SCHEMA_UNSUPPORTED_CONDITIONAL_TYPE";
  public static final String CODE_UNSUPPORTED_MAPPED_TYPE = "TS_SCHEMA_UNSUPPORTED_MAPPED_TYPE";
  public static final String CODE_UNSUPPORTED_FUNCTION_TYPE = "TS_SCHEMA_UNSUPPORTED_FUNCTION_TYPE";
  public static final String CODE_UNSUPPORTED_TEMPLATE_LITERAL =
      "TS_SCHEMA_UNSUPPORTED_TEMPLATE_LITERAL";
  public static final String CODE_UNSUPPORTED_NPM_IMPORT = "TS_SCHEMA_UNSUPPORTED_NPM_IMPORT";

  @Override
  public SchemaImportResult importSchemas(SchemaImportRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Map<String, CanonicalSchema> schemas = new TreeMap<>();
    List<SchemaDiagnostic> allDiagnostics = new ArrayList<>();

    for (SchemaSource source : request.sources()) {
      String sourcePathStr = source.path().toString();
      try {
        if (!Files.isRegularFile(source.path())) {
          allDiagnostics.add(
              SchemaDiagnostic.error(
                  sourcePathStr,
                  1,
                  1,
                  SchemaFormat.TYPESCRIPT,
                  CODE_SYNTAX_ERROR,
                  "File does not exist or is not a regular file: " + source.path(),
                  "Check declaration file path"));
          continue;
        }

        String rawContent = Files.readString(source.path(), StandardCharsets.UTF_8);
        String templateId =
            source.templateId().orElseGet(() -> deriveTemplateIdFromPath(source.path()));

        SchemaImportResult singleResult =
            importStringInternal(rawContent, templateId, sourcePathStr);
        schemas.putAll(singleResult.schemas());
        allDiagnostics.addAll(singleResult.diagnostics());
      } catch (IOException e) {
        allDiagnostics.add(
            SchemaDiagnostic.error(
                sourcePathStr,
                1,
                1,
                SchemaFormat.TYPESCRIPT,
                CODE_SYNTAX_ERROR,
                "I/O error reading declaration file: " + e.getMessage(),
                "Ensure file is readable"));
      }
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

  /** Imports TypeScript declaration from in-memory string. */
  public SchemaImportResult importString(String dtsContent, String templateId) {
    Objects.requireNonNull(dtsContent, "dtsContent must not be null");
    String resolvedTemplateId =
        (templateId == null || templateId.isBlank()) ? "anonymous-schema" : templateId;
    return importStringInternal(dtsContent, resolvedTemplateId, "<in-memory>");
  }

  /** Imports TypeScript declaration from a file path. */
  public SchemaImportResult importPath(Path dtsPath) {
    Objects.requireNonNull(dtsPath, "dtsPath must not be null");
    return importSource(new SchemaSource(dtsPath, SchemaFormat.TYPESCRIPT));
  }

  /** Imports TypeScript declaration from a file path with explicit template ID. */
  public SchemaImportResult importPath(Path dtsPath, String templateId) {
    Objects.requireNonNull(dtsPath, "dtsPath must not be null");
    return importSource(new SchemaSource(dtsPath, SchemaFormat.TYPESCRIPT, templateId));
  }

  private static String deriveTemplateIdFromPath(Path path) {
    String name = path.getFileName().toString();
    if (name.endsWith(".d.ts")) {
      return name.substring(0, name.length() - ".d.ts".length());
    }
    if (name.endsWith(".ts")) {
      return name.substring(0, name.length() - ".ts".length());
    }
    return name;
  }

  private SchemaImportResult importStringInternal(
      String rawContent, String templateId, String sourcePath) {
    ParserContext ctx = new ParserContext(sourcePath, rawContent);
    Lexer lexer = new Lexer(rawContent, ctx);
    List<Token> tokens = lexer.tokenize();

    Parser parser = new Parser(tokens, ctx, templateId, rawContent);
    Optional<CanonicalSchema> schemaOpt = parser.parse();

    Map<String, CanonicalSchema> schemas = new TreeMap<>();
    schemaOpt.ifPresent(s -> schemas.put(s.templateId(), s));

    boolean hasErrors =
        ctx.diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    boolean isPartial = hasErrors && !schemas.isEmpty();
    return new SchemaImportResult(schemas, ctx.diagnostics, hasErrors, isPartial);
  }

  // --- Diagnostics Context ---

  private static final class ParserContext {
    final String sourcePath;
    final String rawContent;
    final List<SchemaDiagnostic> diagnostics = new ArrayList<>();
    String extractedFingerprint = null;

    ParserContext(String sourcePath, String rawContent) {
      this.sourcePath = sourcePath;
      this.rawContent = rawContent;
    }

    void addDiagnostic(
        int line,
        int column,
        String code,
        DiagnosticSeverity severity,
        String message,
        String action) {
      diagnostics.add(
          new SchemaDiagnostic(
              sourcePath, line, column, SchemaFormat.TYPESCRIPT, code, severity, message, action));
    }

    void error(int line, int column, String code, String message, String action) {
      addDiagnostic(line, column, code, DiagnosticSeverity.ERROR, message, action);
    }
  }

  // --- Lexer & Tokens ---

  enum TokenType {
    KEYWORD_INTERFACE,
    KEYWORD_TYPE,
    KEYWORD_ENUM,
    KEYWORD_EXPORT,
    KEYWORD_DECLARE,
    KEYWORD_EXTENDS,
    KEYWORD_READONLY,
    KEYWORD_NULL,
    KEYWORD_UNDEFINED,
    KEYWORD_STRING,
    KEYWORD_NUMBER,
    KEYWORD_BOOLEAN,
    KEYWORD_BIGINT,
    KEYWORD_ANY,
    KEYWORD_UNKNOWN,
    KEYWORD_NEVER,
    KEYWORD_VOID,
    KEYWORD_ARRAY,
    KEYWORD_RECORD,
    KEYWORD_READONLY_ARRAY,
    KEYWORD_IMPORT,
    KEYWORD_FROM,
    KEYWORD_IN,
    KEYWORD_KEYOF,
    KEYWORD_AS,
    IDENTIFIER,
    STRING_LITERAL,
    NUMBER_LITERAL,
    TEMPLATE_LITERAL,
    LBRACE, // {
    RBRACE, // }
    LBRACKET, // [
    RBRACKET, // ]
    LPAREN, // (
    RPAREN, // )
    COLON, // :
    SEMICOLON, // ;
    COMMA, // ,
    QUESTION, // ?
    PIPE, // |
    AMPERSAND, // &
    LANGLE, // <
    RANGLE, // >
    EQUALS, // =
    ARROW, // =>
    EOF
  }

  record Token(TokenType type, String text, int line, int column, String docComment) {}

  private static final class Lexer {
    private final String src;
    private final ParserContext ctx;
    private final int len;
    private int pos = 0;
    private int line = 1;
    private int col = 1;
    private String pendingDocComment = "";

    Lexer(String src, ParserContext ctx) {
      this.src = src;
      this.ctx = ctx;
      this.len = src.length();
    }

    List<Token> tokenize() {
      List<Token> tokens = new ArrayList<>();
      while (pos < len) {
        char ch = peek();
        if (Character.isWhitespace(ch)) {
          advance();
          continue;
        }

        // Single-line or block comment
        if (ch == '/' && pos + 1 < len) {
          char next = src.charAt(pos + 1);
          if (next == '/') {
            handleSingleLineComment();
            continue;
          } else if (next == '*') {
            handleBlockComment();
            continue;
          }
        }

        int tokLine = line;
        int tokCol = col;
        String doc = pendingDocComment;
        pendingDocComment = "";

        if (ch == '{') {
          advance();
          tokens.add(new Token(TokenType.LBRACE, "{", tokLine, tokCol, doc));
        } else if (ch == '}') {
          advance();
          tokens.add(new Token(TokenType.RBRACE, "}", tokLine, tokCol, doc));
        } else if (ch == '[') {
          advance();
          tokens.add(new Token(TokenType.LBRACKET, "[", tokLine, tokCol, doc));
        } else if (ch == ']') {
          advance();
          tokens.add(new Token(TokenType.RBRACKET, "]", tokLine, tokCol, doc));
        } else if (ch == '(') {
          advance();
          tokens.add(new Token(TokenType.LPAREN, "(", tokLine, tokCol, doc));
        } else if (ch == ')') {
          advance();
          tokens.add(new Token(TokenType.RPAREN, ")", tokLine, tokCol, doc));
        } else if (ch == ':') {
          advance();
          tokens.add(new Token(TokenType.COLON, ":", tokLine, tokCol, doc));
        } else if (ch == ';') {
          advance();
          tokens.add(new Token(TokenType.SEMICOLON, ";", tokLine, tokCol, doc));
        } else if (ch == ',') {
          advance();
          tokens.add(new Token(TokenType.COMMA, ",", tokLine, tokCol, doc));
        } else if (ch == '?') {
          advance();
          tokens.add(new Token(TokenType.QUESTION, "?", tokLine, tokCol, doc));
        } else if (ch == '|') {
          advance();
          tokens.add(new Token(TokenType.PIPE, "|", tokLine, tokCol, doc));
        } else if (ch == '&') {
          advance();
          tokens.add(new Token(TokenType.AMPERSAND, "&", tokLine, tokCol, doc));
        } else if (ch == '<') {
          advance();
          tokens.add(new Token(TokenType.LANGLE, "<", tokLine, tokCol, doc));
        } else if (ch == '>') {
          advance();
          tokens.add(new Token(TokenType.RANGLE, ">", tokLine, tokCol, doc));
        } else if (ch == '=') {
          advance();
          if (peek() == '>') {
            advance();
            tokens.add(new Token(TokenType.ARROW, "=>", tokLine, tokCol, doc));
          } else {
            tokens.add(new Token(TokenType.EQUALS, "=", tokLine, tokCol, doc));
          }
        } else if (ch == '`') {
          tokens.add(readTemplateLiteral(tokLine, tokCol, doc));
        } else if (ch == '"' || ch == '\'') {
          tokens.add(readStringLiteral(ch, tokLine, tokCol, doc));
        } else if (Character.isDigit(ch)) {
          tokens.add(readNumberLiteral(tokLine, tokCol, doc));
        } else if (isIdentifierStart(ch)) {
          tokens.add(readIdentifierOrKeyword(tokLine, tokCol, doc));
        } else {
          advance();
          ctx.error(
              tokLine,
              tokCol,
              CODE_SYNTAX_ERROR,
              "Unexpected character: '" + ch + "'",
              "Remove or replace invalid character");
        }
      }

      tokens.add(new Token(TokenType.EOF, "", line, col, pendingDocComment));
      return tokens;
    }

    private void handleSingleLineComment() {
      int start = pos;
      advance(); // /
      advance(); // /
      while (pos < len && peek() != '\n') {
        advance();
      }
      String commentText = src.substring(start, pos).trim();
      if (commentText.contains("Contract fingerprint:")) {
        int idx = commentText.indexOf("Contract fingerprint:");
        String fp = commentText.substring(idx + "Contract fingerprint:".length()).trim();
        if (!fp.isEmpty()) {
          ctx.extractedFingerprint = fp;
        }
      }
    }

    private void handleBlockComment() {
      advance(); // /
      advance(); // *
      boolean isDoc = (pos < len && peek() == '*');
      int contentStart = pos;
      while (pos + 1 < len && !(peek() == '*' && src.charAt(pos + 1) == '/')) {
        advance();
      }
      int contentEnd = pos;
      if (pos + 1 < len) {
        advance(); // *
        advance(); // /
      }

      if (isDoc && contentEnd >= contentStart) {
        String rawDoc = src.substring(contentStart, contentEnd);
        pendingDocComment = cleanDocComment(rawDoc);
      }
    }

    private String cleanDocComment(String raw) {
      String[] lines = raw.split("\\R");
      StringBuilder sb = new StringBuilder();
      for (String l : lines) {
        String trimmed = l.trim();
        if (trimmed.startsWith("*")) {
          trimmed = trimmed.substring(1).trim();
        }
        if (!trimmed.isEmpty()) {
          if (sb.length() > 0) {
            sb.append(" ");
          }
          sb.append(trimmed);
        }
      }
      return sb.toString();
    }

    private Token readTemplateLiteral(int tokLine, int tokCol, String doc) {
      advance(); // `
      StringBuilder sb = new StringBuilder();
      while (pos < len && peek() != '`') {
        sb.append(advance());
      }
      if (pos < len && peek() == '`') {
        advance(); // closing `
      } else {
        ctx.error(
            tokLine,
            tokCol,
            CODE_SYNTAX_ERROR,
            "Unclosed template literal",
            "Add closing backtick '`'");
      }
      return new Token(TokenType.TEMPLATE_LITERAL, sb.toString(), tokLine, tokCol, doc);
    }

    private Token readStringLiteral(char quote, int tokLine, int tokCol, String doc) {
      advance(); // quote
      StringBuilder sb = new StringBuilder();
      while (pos < len && peek() != quote) {
        char c = advance();
        if (c == '\\' && pos < len) {
          char esc = advance();
          switch (esc) {
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            case '\\' -> sb.append('\\');
            case '\'' -> sb.append('\'');
            case '"' -> sb.append('"');
            default -> sb.append(esc);
          }
        } else {
          sb.append(c);
        }
      }
      if (pos < len && peek() == quote) {
        advance();
      } else {
        ctx.error(
            tokLine,
            tokCol,
            CODE_SYNTAX_ERROR,
            "Unclosed string literal",
            "Add matching quote '" + quote + "'");
      }
      return new Token(TokenType.STRING_LITERAL, sb.toString(), tokLine, tokCol, doc);
    }

    private Token readNumberLiteral(int tokLine, int tokCol, String doc) {
      StringBuilder sb = new StringBuilder();
      while (pos < len && (Character.isDigit(peek()) || peek() == '.')) {
        sb.append(advance());
      }
      return new Token(TokenType.NUMBER_LITERAL, sb.toString(), tokLine, tokCol, doc);
    }

    private Token readIdentifierOrKeyword(int tokLine, int tokCol, String doc) {
      StringBuilder sb = new StringBuilder();
      while (pos < len && isIdentifierPart(peek())) {
        sb.append(advance());
      }
      String word = sb.toString();
      TokenType type =
          switch (word) {
            case "interface" -> TokenType.KEYWORD_INTERFACE;
            case "type" -> TokenType.KEYWORD_TYPE;
            case "enum" -> TokenType.KEYWORD_ENUM;
            case "export" -> TokenType.KEYWORD_EXPORT;
            case "declare" -> TokenType.KEYWORD_DECLARE;
            case "extends" -> TokenType.KEYWORD_EXTENDS;
            case "readonly" -> TokenType.KEYWORD_READONLY;
            case "null" -> TokenType.KEYWORD_NULL;
            case "undefined" -> TokenType.KEYWORD_UNDEFINED;
            case "string" -> TokenType.KEYWORD_STRING;
            case "number" -> TokenType.KEYWORD_NUMBER;
            case "boolean" -> TokenType.KEYWORD_BOOLEAN;
            case "bigint" -> TokenType.KEYWORD_BIGINT;
            case "any" -> TokenType.KEYWORD_ANY;
            case "unknown" -> TokenType.KEYWORD_UNKNOWN;
            case "never" -> TokenType.KEYWORD_NEVER;
            case "void" -> TokenType.KEYWORD_VOID;
            case "Array" -> TokenType.KEYWORD_ARRAY;
            case "Record" -> TokenType.KEYWORD_RECORD;
            case "ReadonlyArray" -> TokenType.KEYWORD_READONLY_ARRAY;
            case "import" -> TokenType.KEYWORD_IMPORT;
            case "from" -> TokenType.KEYWORD_FROM;
            case "in" -> TokenType.KEYWORD_IN;
            case "keyof" -> TokenType.KEYWORD_KEYOF;
            case "as" -> TokenType.KEYWORD_AS;
            default -> TokenType.IDENTIFIER;
          };
      return new Token(type, word, tokLine, tokCol, doc);
    }

    private boolean isIdentifierStart(char c) {
      return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isIdentifierPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private char peek() {
      return pos < len ? src.charAt(pos) : '\0';
    }

    private char advance() {
      char c = src.charAt(pos++);
      if (c == '\n') {
        line++;
        col = 1;
      } else {
        col++;
      }
      return c;
    }
  }

  // --- Recursive Descent Parser ---

  private static final class Parser {
    private final List<Token> tokens;
    private final ParserContext ctx;
    private final String templateId;
    private final String rawSource;
    private int cursor = 0;

    private final Map<String, TypeDef> registeredTypes = new TreeMap<>();
    private final Map<String, List<String>> interfaceInheritance = new LinkedHashMap<>();
    private final Map<String, List<String>> enumValues = new LinkedHashMap<>();
    private final Set<String> topLevelInterfaces = new HashSet<>();
    private int synthesizedTypeCounter = 1;

    Parser(List<Token> tokens, ParserContext ctx, String templateId, String rawSource) {
      this.tokens = tokens;
      this.ctx = ctx;
      this.templateId = templateId;
      this.rawSource = rawSource;
    }

    Optional<CanonicalSchema> parse() {
      while (!isAtEnd()) {
        parseDeclaration();
      }

      // Resolve inheritance across interfaces
      resolveInterfaceInheritance();

      // Extract template parameters
      Map<String, ParameterDef> parameters = new TreeMap<>();
      TypeDef templateParametersDef = registeredTypes.remove("TemplateParameters");

      if (templateParametersDef != null) {
        for (PropertyDef prop : templateParametersDef.properties().values()) {
          parameters.put(
              prop.name(),
              new ParameterDef(
                  prop.name(),
                  prop.type(),
                  prop.nullable(),
                  prop.optional(),
                  prop.documentation()));
        }
      } else {
        // Fallback: top-level interfaces populate parameters
        for (String ifaceName : topLevelInterfaces) {
          TypeDef typeDef = registeredTypes.get(ifaceName);
          if (typeDef != null) {
            String decap = decapitalize(ifaceName);
            TypeRef namedRef =
                enumValues.containsKey(ifaceName)
                    ? new EnumTypeRef(ifaceName, enumValues.get(ifaceName))
                    : new NamedTypeRef(ifaceName);

            // Populate decapitalized parameter name and dollar-prefixed parameter name
            parameters.put(
                decap, new ParameterDef(decap, namedRef, false, false, typeDef.documentation()));
            parameters.put(
                "$" + decap,
                new ParameterDef("$" + decap, namedRef, false, false, typeDef.documentation()));
          }
        }
      }

      String fingerprint = ctx.extractedFingerprint;
      if (fingerprint == null || fingerprint.isBlank()) {
        fingerprint = computeFingerprint(rawSource);
      }

      CanonicalSchema schema =
          new CanonicalSchema(
              templateId,
              SchemaFormat.TYPESCRIPT,
              fingerprint,
              parameters,
              registeredTypes,
              rawSource);

      return Optional.of(schema);
    }

    private void parseDeclaration() {
      // Collect optional export / declare doc comment
      String modDoc = "";
      while (check(TokenType.KEYWORD_EXPORT) || check(TokenType.KEYWORD_DECLARE)) {
        Token mod = advance();
        if (!mod.docComment().isEmpty()) {
          modDoc = mod.docComment();
        }
      }

      Token current = peek();
      if (current.type() == TokenType.KEYWORD_INTERFACE) {
        parseInterfaceDeclaration(modDoc);
      } else if (current.type() == TokenType.KEYWORD_TYPE) {
        parseTypeAliasDeclaration(modDoc);
      } else if (current.type() == TokenType.KEYWORD_ENUM) {
        parseEnumDeclaration(modDoc);
      } else if (current.type() == TokenType.KEYWORD_IMPORT) {
        advance();
        ctx.error(
            current.line(),
            current.column(),
            CODE_UNSUPPORTED_NPM_IMPORT,
            "External or npm package imports are not supported (offline guarantee)",
            "Inline type declarations or use self-contained .d.ts files");
        skipToDeclarationBoundary();
      } else if (current.type() == TokenType.SEMICOLON) {
        advance();
      } else if (current.type() == TokenType.EOF) {
        // end
      } else {
        ctx.error(
            current.line(),
            current.column(),
            CODE_SYNTAX_ERROR,
            "Unexpected declaration token: '" + current.text() + "'",
            "Expected 'interface', 'type', 'enum', or 'export'");
        advance();
        skipToDeclarationBoundary();
      }
    }

    private void skipToDeclarationBoundary() {
      while (!isAtEnd()) {
        TokenType t = peek().type();
        if (t == TokenType.SEMICOLON) {
          advance();
          return;
        }
        if (t == TokenType.KEYWORD_INTERFACE
            || t == TokenType.KEYWORD_TYPE
            || t == TokenType.KEYWORD_ENUM
            || t == TokenType.KEYWORD_EXPORT) {
          return;
        }
        advance();
      }
    }

    // --- Enum Parsing ---

    private void parseEnumDeclaration(String modDoc) {
      Token enumTok = advance(); // enum
      Token nameTok = expectIdentifier("Expected enum name after 'enum'");
      if (nameTok == null) {
        skipToDeclarationBoundary();
        return;
      }
      String name = nameTok.text();
      String doc = enumTok.docComment().isEmpty() ? modDoc : enumTok.docComment();

      if (!expect(TokenType.LBRACE, "Expected '{' to start enum body")) {
        skipToDeclarationBoundary();
        return;
      }

      List<String> constants = new ArrayList<>();
      while (!check(TokenType.RBRACE) && !isAtEnd()) {
        Token memberTok = expectIdentifier("Expected enum member name");
        if (memberTok == null) {
          break;
        }
        constants.add(memberTok.text());

        // Optional initializer = "val" or = 1
        if (match(TokenType.EQUALS)) {
          if (check(TokenType.STRING_LITERAL)
              || check(TokenType.NUMBER_LITERAL)
              || check(TokenType.IDENTIFIER)) {
            advance();
          }
        }
        match(TokenType.COMMA);
      }
      expect(TokenType.RBRACE, "Expected '}' closing enum body");

      registeredTypes.put(
          name, new TypeDef(name, "enum", Map.of(), constants, Optional.empty(), doc));
      enumValues.put(name, constants);
    }

    // --- Type Alias Parsing ---

    private void parseTypeAliasDeclaration(String modDoc) {
      Token typeTok = advance(); // type
      Token nameTok = expectIdentifier("Expected type name after 'type'");
      if (nameTok == null) {
        skipToDeclarationBoundary();
        return;
      }
      String name = nameTok.text();
      String doc = typeTok.docComment().isEmpty() ? modDoc : typeTok.docComment();

      // Skip generic type parameters: type Foo<T>
      if (match(TokenType.LANGLE)) {
        skipToMatching(TokenType.RANGLE);
      }

      if (!expect(TokenType.EQUALS, "Expected '=' after type alias name")) {
        skipToDeclarationBoundary();
        return;
      }

      // Check for string literal union (e.g. "OPEN" | "CLOSED")
      if (isStringLiteralUnion()) {
        List<String> symbols = parseStringLiteralUnion();
        registeredTypes.put(
            name, new TypeDef(name, "enum", Map.of(), symbols, Optional.empty(), doc));
        enumValues.put(name, symbols);
        match(TokenType.SEMICOLON);
        return;
      }

      // Parse general type expression
      ParsedType parsed = parseTypeExpression(name);
      match(TokenType.SEMICOLON);

      if (parsed.synthesizedTypeDef != null) {
        // Register synthesized object type under the alias name
        TypeDef td =
            new TypeDef(
                name,
                parsed.synthesizedTypeDef.kind(),
                parsed.synthesizedTypeDef.properties(),
                parsed.synthesizedTypeDef.enumConstants(),
                parsed.synthesizedTypeDef.mapValueType(),
                doc);
        registeredTypes.put(name, td);
      }
    }

    private boolean isStringLiteralUnion() {
      int save = cursor;
      match(TokenType.PIPE); // optional leading |
      if (check(TokenType.STRING_LITERAL)) {
        advance();
        boolean isUnion =
            check(TokenType.PIPE) || check(TokenType.SEMICOLON) || check(TokenType.EOF);
        cursor = save;
        return isUnion;
      }
      cursor = save;
      return false;
    }

    private List<String> parseStringLiteralUnion() {
      List<String> symbols = new ArrayList<>();
      match(TokenType.PIPE); // optional leading |
      while (check(TokenType.STRING_LITERAL)) {
        Token lit = advance();
        symbols.add(lit.text());
        if (!match(TokenType.PIPE)) {
          break;
        }
      }
      return symbols;
    }

    // --- Interface Parsing ---

    private void parseInterfaceDeclaration(String modDoc) {
      Token ifaceTok = advance(); // interface
      Token nameTok = expectIdentifier("Expected interface name after 'interface'");
      if (nameTok == null) {
        skipToDeclarationBoundary();
        return;
      }
      String name = nameTok.text();
      String doc = ifaceTok.docComment().isEmpty() ? modDoc : ifaceTok.docComment();

      // Skip generic type parameters: interface Foo<T>
      if (match(TokenType.LANGLE)) {
        skipToMatching(TokenType.RANGLE);
      }

      List<String> supers = new ArrayList<>();
      if (match(TokenType.KEYWORD_EXTENDS)) {
        do {
          Token superName = expectIdentifier("Expected super interface name after 'extends'");
          if (superName != null) {
            supers.add(superName.text());
          }
        } while (match(TokenType.COMMA));
      }
      if (!supers.isEmpty()) {
        interfaceInheritance.put(name, supers);
      }

      if (!expect(TokenType.LBRACE, "Expected '{' to start interface body")) {
        skipToDeclarationBoundary();
        return;
      }

      Map<String, PropertyDef> properties = new TreeMap<>();
      Optional<TypeRef> mapValueType = Optional.empty();

      while (!check(TokenType.RBRACE) && !isAtEnd()) {
        // Check for index signature: [key: string]: Value
        if (check(TokenType.LBRACKET)) {
          int startLine = peek().line();
          int startCol = peek().column();
          advance(); // [
          if (check(TokenType.IDENTIFIER) && peekAhead(1).type() == TokenType.KEYWORD_IN) {
            // Mapped type: [K in Keys]
            ctx.error(
                startLine,
                startCol,
                CODE_UNSUPPORTED_MAPPED_TYPE,
                "Mapped types ('[K in ...]') are not supported in template schemas",
                "Use explicit properties or Record<K, V>");
            skipToMatching(TokenType.RBRACKET);
            if (check(TokenType.COLON)) {
              advance();
              parseTypeExpression(name + "_Index");
            }
            match(TokenType.SEMICOLON);
            match(TokenType.COMMA);
            continue;
          }

          // Normal index signature: [key: string]: Value
          expectIdentifier("Expected index variable name");
          expect(TokenType.COLON, "Expected ':' after index key name");
          expect(TokenType.KEYWORD_STRING, "Expected 'string' index key type");
          expect(TokenType.RBRACKET, "Expected ']' closing index signature");
          expect(TokenType.COLON, "Expected ':' before index value type");
          ParsedType valType = parseTypeExpression(name + "_IndexValue");
          mapValueType = Optional.of(valType.typeRef);
          match(TokenType.SEMICOLON);
          match(TokenType.COMMA);
          continue;
        }

        // Check for function signature: name(...)
        if (check(TokenType.IDENTIFIER) && peekAhead(1).type() == TokenType.LPAREN) {
          Token fnName = advance();
          ctx.error(
              fnName.line(),
              fnName.column(),
              CODE_UNSUPPORTED_FUNCTION_TYPE,
              "Function and method signatures are not supported in template schemas",
              "Use data property signatures instead of functions");
          skipToMatching(TokenType.RPAREN);
          if (check(TokenType.COLON)) {
            advance();
            parseTypeExpression(name + "_fn");
          }
          match(TokenType.SEMICOLON);
          match(TokenType.COMMA);
          continue;
        }

        // Standard property signature
        match(TokenType.KEYWORD_READONLY); // optional readonly
        Token propTok = advance();
        if (propTok.type() != TokenType.IDENTIFIER && propTok.type() != TokenType.STRING_LITERAL) {
          ctx.error(
              propTok.line(),
              propTok.column(),
              CODE_SYNTAX_ERROR,
              "Expected property name in interface body",
              "Define property name");
          skipToNextProperty();
          continue;
        }
        String propName = propTok.text();
        String propDoc = propTok.docComment();

        boolean optional = match(TokenType.QUESTION);
        if (!expect(TokenType.COLON, "Expected ':' after property name '" + propName + "'")) {
          skipToNextProperty();
          continue;
        }

        ParsedType propTypeParsed = parseTypeExpression(name + "_" + capitalize(propName));
        match(TokenType.SEMICOLON);
        match(TokenType.COMMA);

        boolean nullable = propTypeParsed.nullable;
        if (propTypeParsed.optional) {
          optional = true;
        }

        properties.put(
            propName,
            new PropertyDef(propName, propTypeParsed.typeRef, nullable, optional, propDoc));
      }

      expect(TokenType.RBRACE, "Expected '}' closing interface body");
      match(TokenType.SEMICOLON);

      TypeDef typeDef = new TypeDef(name, "interface", properties, List.of(), mapValueType, doc);
      registeredTypes.put(name, typeDef);
      if (!name.equals("TemplateParameters")) {
        topLevelInterfaces.add(name);
      }
    }

    private void skipToNextProperty() {
      while (!isAtEnd() && !check(TokenType.RBRACE) && !check(TokenType.SEMICOLON)) {
        advance();
      }
      match(TokenType.SEMICOLON);
    }

    private void skipToMatching(TokenType closeType) {
      int depth = 1;
      while (!isAtEnd() && depth > 0) {
        Token t = advance();
        if (t.type() == closeType) {
          depth--;
        }
      }
    }

    // --- Type Expression Parsing ---

    private record ParsedType(
        TypeRef typeRef,
        boolean nullable,
        boolean optional,
        TypeDef synthesizedTypeDef,
        boolean isNullSentinel,
        boolean isUndefinedSentinel) {
      ParsedType(TypeRef typeRef, boolean nullable, boolean optional, TypeDef synthesizedTypeDef) {
        this(typeRef, nullable, optional, synthesizedTypeDef, false, false);
      }
    }

    private ParsedType parseTypeExpression(String contextName) {
      // Check for union types separated by |
      List<ParsedType> unionParts = new ArrayList<>();
      match(TokenType.PIPE); // optional leading |

      do {
        ParsedType part = parseIntersectionType(contextName);
        unionParts.add(part);
      } while (match(TokenType.PIPE));

      // Check for conditional type: T extends U ? X : Y
      if (match(TokenType.KEYWORD_EXTENDS)) {
        Token condTok = previous();
        ctx.error(
            condTok.line(),
            condTok.column(),
            CODE_UNSUPPORTED_CONDITIONAL_TYPE,
            "Conditional types ('T extends U ? X : Y') are not supported in template schemas",
            "Use concrete types or union types");
        parseTypeExpression(contextName + "_ExtendsBound");
        if (match(TokenType.QUESTION)) {
          parseTypeExpression(contextName + "_TrueBranch");
          if (match(TokenType.COLON)) {
            parseTypeExpression(contextName + "_FalseBranch");
          }
        }
        return new ParsedType(new DynamicTypeRef(), false, false, null);
      }

      boolean nullable = false;
      boolean optional = false;
      List<TypeRef> validOptions = new ArrayList<>();

      for (ParsedType p : unionParts) {
        if (p.nullable || p.isNullSentinel) {
          nullable = true;
        }
        if (p.optional || p.isUndefinedSentinel) {
          optional = true;
        }
        if (!p.isNullSentinel && !p.isUndefinedSentinel) {
          validOptions.add(p.typeRef);
        }
      }

      TypeRef resultType;
      if (validOptions.isEmpty()) {
        resultType = new DynamicTypeRef();
      } else if (validOptions.size() == 1) {
        resultType = validOptions.get(0);
      } else {
        resultType = new UnionTypeRef(validOptions);
      }

      return new ParsedType(resultType, nullable, optional, null);
    }

    private ParsedType parseIntersectionType(String contextName) {
      List<ParsedType> parts = new ArrayList<>();
      do {
        parts.add(parsePostfixType(contextName));
      } while (match(TokenType.AMPERSAND));

      if (parts.size() == 1) {
        return parts.get(0);
      }

      // Merge intersection of object types
      Map<String, PropertyDef> mergedProps = new TreeMap<>();
      TypeDef synthesized = null;
      for (ParsedType part : parts) {
        if (part.synthesizedTypeDef != null) {
          mergedProps.putAll(part.synthesizedTypeDef.properties());
        } else if (part.typeRef instanceof NamedTypeRef nt) {
          TypeDef existing = registeredTypes.get(nt.name());
          if (existing != null) {
            mergedProps.putAll(existing.properties());
          }
        }
      }

      String synthName = contextName + "_Intersection_" + (synthesizedTypeCounter++);
      synthesized = new TypeDef(synthName, "object", mergedProps);
      registeredTypes.put(synthName, synthesized);

      return new ParsedType(new NamedTypeRef(synthName), false, false, synthesized);
    }

    private ParsedType parsePostfixType(String contextName) {
      ParsedType base = parsePrimaryType(contextName);
      TypeRef current = base.typeRef;

      // Handle array postfix: T[]
      while (match(TokenType.LBRACKET)) {
        if (expect(TokenType.RBRACKET, "Expected ']' after '[' for array type")) {
          current = new ArrayTypeRef(current);
        }
      }

      return new ParsedType(
          current,
          base.nullable,
          base.optional,
          base.synthesizedTypeDef,
          base.isNullSentinel,
          base.isUndefinedSentinel);
    }

    private ParsedType parsePrimaryType(String contextName) {
      Token current = peek();

      // Primitives
      if (match(TokenType.KEYWORD_STRING)) {
        return new ParsedType(new PrimitiveTypeRef("string"), false, false, null);
      }
      if (match(TokenType.KEYWORD_NUMBER)) {
        return new ParsedType(new PrimitiveTypeRef("number"), false, false, null);
      }
      if (match(TokenType.KEYWORD_BOOLEAN)) {
        return new ParsedType(new PrimitiveTypeRef("boolean"), false, false, null);
      }
      if (match(TokenType.KEYWORD_BIGINT)) {
        return new ParsedType(new PrimitiveTypeRef("bigint"), false, false, null);
      }
      if (match(TokenType.KEYWORD_ANY)
          || match(TokenType.KEYWORD_UNKNOWN)
          || match(TokenType.KEYWORD_VOID)) {
        return new ParsedType(new DynamicTypeRef(), false, false, null);
      }
      if (match(TokenType.KEYWORD_NEVER)) {
        return new ParsedType(new UnionTypeRef(List.of()), false, false, null);
      }
      if (match(TokenType.KEYWORD_NULL)) {
        return new ParsedType(new DynamicTypeRef(), true, false, null, true, false);
      }
      if (match(TokenType.KEYWORD_UNDEFINED)) {
        return new ParsedType(new DynamicTypeRef(), false, true, null, false, true);
      }

      // Template literal type: `prefix_${string}`
      if (current.type() == TokenType.TEMPLATE_LITERAL) {
        Token lit = advance();
        ctx.error(
            lit.line(),
            lit.column(),
            CODE_UNSUPPORTED_TEMPLATE_LITERAL,
            "Template literal types are not supported in template schemas",
            "Use string type instead of template literal types");
        return new ParsedType(new DynamicTypeRef(), false, false, null);
      }

      // Built-in generics: Array<T>, ReadonlyArray<T>
      if (match(TokenType.KEYWORD_ARRAY) || match(TokenType.KEYWORD_READONLY_ARRAY)) {
        if (expect(TokenType.LANGLE, "Expected '<' after Array")) {
          ParsedType elem = parseTypeExpression(contextName + "Item");
          expect(TokenType.RANGLE, "Expected '>' closing Array type");
          return new ParsedType(new ArrayTypeRef(elem.typeRef), false, false, null);
        }
        return new ParsedType(new ArrayTypeRef(new DynamicTypeRef()), false, false, null);
      }

      // Record<K, V>
      if (match(TokenType.KEYWORD_RECORD)) {
        if (expect(TokenType.LANGLE, "Expected '<' after Record")) {
          ParsedType keyType = parseTypeExpression(contextName + "Key");
          expect(TokenType.COMMA, "Expected ',' in Record<Key, Value>");
          ParsedType valType = parseTypeExpression(contextName + "Value");
          expect(TokenType.RANGLE, "Expected '>' closing Record type");
          return new ParsedType(
              new MapTypeRef(keyType.typeRef, valType.typeRef), false, false, null);
        }
        return new ParsedType(
            new MapTypeRef(new PrimitiveTypeRef("string"), new DynamicTypeRef()),
            false,
            false,
            null);
      }

      // Parenthesized type: (T) or function type: () => void
      if (match(TokenType.LPAREN)) {
        int startLine = current.line();
        int startCol = current.column();

        // Check empty parens: () => void
        if (match(TokenType.RPAREN)) {
          if (match(TokenType.ARROW)) {
            ctx.error(
                startLine,
                startCol,
                CODE_UNSUPPORTED_FUNCTION_TYPE,
                "Function and method types are not supported in template schemas",
                "Use data types instead of functions");
            parseTypeExpression(contextName + "_ret");
            return new ParsedType(new DynamicTypeRef(), false, false, null);
          }
          return new ParsedType(new DynamicTypeRef(), false, false, null);
        }

        // Check if function parameter: (arg: string) => void
        if (check(TokenType.IDENTIFIER) && peekAhead(1).type() == TokenType.COLON) {
          skipToMatching(TokenType.RPAREN);
          if (match(TokenType.ARROW)) {
            ctx.error(
                startLine,
                startCol,
                CODE_UNSUPPORTED_FUNCTION_TYPE,
                "Function and method types are not supported in template schemas",
                "Use data types instead of functions");
            parseTypeExpression(contextName + "_ret");
            return new ParsedType(new DynamicTypeRef(), false, false, null);
          }
          return new ParsedType(new DynamicTypeRef(), false, false, null);
        }

        ParsedType inner = parseTypeExpression(contextName);
        expect(TokenType.RPAREN, "Expected ')' closing parenthesized type");

        // Arrow after parens: (a: string) => void
        if (match(TokenType.ARROW)) {
          ctx.error(
              startLine,
              startCol,
              CODE_UNSUPPORTED_FUNCTION_TYPE,
              "Function and method types are not supported in template schemas",
              "Use data types instead of functions");
          parseTypeExpression(contextName + "_ret");
          return new ParsedType(new DynamicTypeRef(), false, false, null);
        }

        return inner;
      }

      // Nested object literal: { prop: type; }
      if (match(TokenType.LBRACE)) {
        return parseObjectLiteralType(contextName);
      }

      // String literal: "OPEN"
      if (match(TokenType.STRING_LITERAL)) {
        return new ParsedType(new PrimitiveTypeRef("string"), false, false, null);
      }

      // Number literal
      if (match(TokenType.NUMBER_LITERAL)) {
        return new ParsedType(new PrimitiveTypeRef("number"), false, false, null);
      }

      // Named type identifier: User, SimpleRecord, etc.
      if (match(TokenType.IDENTIFIER)) {
        String typeName = previous().text();
        List<TypeRef> typeArgs = List.of();
        if (match(TokenType.LANGLE)) {
          typeArgs = new ArrayList<>();
          do {
            typeArgs.add(parseTypeExpression(typeName + "Arg").typeRef);
          } while (match(TokenType.COMMA));
          expect(TokenType.RANGLE, "Expected '>' closing generic type arguments");
        }

        TypeRef ref;
        if (enumValues.containsKey(typeName) && typeArgs.isEmpty()) {
          ref = new EnumTypeRef(typeName, enumValues.get(typeName));
        } else if (!typeArgs.isEmpty()) {
          ref = new NamedTypeRef(typeName, typeArgs);
        } else {
          ref = new NamedTypeRef(typeName);
        }
        return new ParsedType(ref, false, false, null);
      }

      ctx.error(
          current.line(),
          current.column(),
          CODE_SYNTAX_ERROR,
          "Unexpected token in type expression: '" + current.text() + "'",
          "Specify a valid type expression");
      advance();
      return new ParsedType(new DynamicTypeRef(), false, false, null);
    }

    private ParsedType parseObjectLiteralType(String contextName) {
      String synthName = contextName + "_Obj_" + (synthesizedTypeCounter++);
      Map<String, PropertyDef> properties = new TreeMap<>();
      Optional<TypeRef> mapValueType = Optional.empty();

      while (!check(TokenType.RBRACE) && !isAtEnd()) {
        // Index signature in object literal: { [key: string]: V }
        if (check(TokenType.LBRACKET)) {
          int startLine = peek().line();
          int startCol = peek().column();
          advance(); // [
          if (check(TokenType.IDENTIFIER) && peekAhead(1).type() == TokenType.KEYWORD_IN) {
            ctx.error(
                startLine,
                startCol,
                CODE_UNSUPPORTED_MAPPED_TYPE,
                "Mapped types ('[K in ...]') are not supported in template schemas",
                "Use explicit properties or Record<K, V>");
            skipToMatching(TokenType.RBRACKET);
            if (check(TokenType.COLON)) {
              advance();
              parseTypeExpression(synthName + "_Index");
            }
            match(TokenType.SEMICOLON);
            match(TokenType.COMMA);
            continue;
          }

          expectIdentifier("Expected index variable name");
          expect(TokenType.COLON, "Expected ':' after index key name");
          expect(TokenType.KEYWORD_STRING, "Expected 'string' index key type");
          expect(TokenType.RBRACKET, "Expected ']' closing index signature");
          expect(TokenType.COLON, "Expected ':' before index value type");
          ParsedType valType = parseTypeExpression(synthName + "_IndexValue");
          mapValueType = Optional.of(valType.typeRef);
          match(TokenType.SEMICOLON);
          match(TokenType.COMMA);
          continue;
        }

        match(TokenType.KEYWORD_READONLY);
        Token propTok = advance();
        if (propTok.type() != TokenType.IDENTIFIER && propTok.type() != TokenType.STRING_LITERAL) {
          ctx.error(
              propTok.line(),
              propTok.column(),
              CODE_SYNTAX_ERROR,
              "Expected property name in object literal",
              "Define property name");
          skipToNextProperty();
          continue;
        }
        String pName = propTok.text();
        String pDoc = propTok.docComment();

        boolean optional = match(TokenType.QUESTION);
        if (!expect(TokenType.COLON, "Expected ':' after property name '" + pName + "'")) {
          skipToNextProperty();
          continue;
        }

        ParsedType pTypeParsed = parseTypeExpression(synthName + "_" + capitalize(pName));
        match(TokenType.SEMICOLON);
        match(TokenType.COMMA);

        boolean nullable = pTypeParsed.nullable;
        if (pTypeParsed.optional) {
          optional = true;
        }

        properties.put(
            pName, new PropertyDef(pName, pTypeParsed.typeRef, nullable, optional, pDoc));
      }

      expect(TokenType.RBRACE, "Expected '}' closing object literal");

      // If object literal only has index signature, treat as Map
      if (properties.isEmpty() && mapValueType.isPresent()) {
        return new ParsedType(
            new MapTypeRef(new PrimitiveTypeRef("string"), mapValueType.get()), false, false, null);
      }

      TypeDef synthTypeDef =
          new TypeDef(synthName, "object", properties, List.of(), mapValueType, "");
      registeredTypes.put(synthName, synthTypeDef);

      return new ParsedType(new NamedTypeRef(synthName), false, false, synthTypeDef);
    }

    private void resolveInterfaceInheritance() {
      for (Map.Entry<String, List<String>> entry : interfaceInheritance.entrySet()) {
        String childName = entry.getKey();
        TypeDef childDef = registeredTypes.get(childName);
        if (childDef == null) {
          continue;
        }

        Map<String, PropertyDef> merged = new TreeMap<>(childDef.properties());
        for (String superName : entry.getValue()) {
          TypeDef superDef = registeredTypes.get(superName);
          if (superDef != null) {
            for (Map.Entry<String, PropertyDef> propEntry : superDef.properties().entrySet()) {
              merged.putIfAbsent(propEntry.getKey(), propEntry.getValue());
            }
          }
        }

        registeredTypes.put(
            childName,
            new TypeDef(
                childName,
                childDef.kind(),
                merged,
                childDef.enumConstants(),
                childDef.mapValueType(),
                childDef.documentation()));
      }
    }

    // --- Parser Helpers ---

    private boolean isAtEnd() {
      return peek().type() == TokenType.EOF;
    }

    private Token peek() {
      return tokens.get(cursor);
    }

    private Token peekAhead(int n) {
      int idx = cursor + n;
      return idx < tokens.size() ? tokens.get(idx) : tokens.get(tokens.size() - 1);
    }

    private Token previous() {
      return tokens.get(cursor - 1);
    }

    private Token advance() {
      if (!isAtEnd()) {
        cursor++;
      }
      return previous();
    }

    private boolean check(TokenType type) {
      if (isAtEnd()) {
        return type == TokenType.EOF;
      }
      return peek().type() == type;
    }

    private boolean match(TokenType type) {
      if (check(type)) {
        advance();
        return true;
      }
      return false;
    }

    private boolean expect(TokenType type, String errorMsg) {
      if (check(type)) {
        advance();
        return true;
      }
      Token t = peek();
      ctx.error(t.line(), t.column(), CODE_SYNTAX_ERROR, errorMsg, "Insert expected token");
      return false;
    }

    private Token expectIdentifier(String errorMsg) {
      Token t = peek();
      if (t.type() == TokenType.IDENTIFIER
          || t.type() == TokenType.KEYWORD_RECORD
          || t.type() == TokenType.KEYWORD_ARRAY) {
        return advance();
      }
      ctx.error(t.line(), t.column(), CODE_SYNTAX_ERROR, errorMsg, "Provide a valid identifier");
      return null;
    }
  }

  // --- String Utilities ---

  private static String decapitalize(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  private static String computeFingerprint(String rawSource) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(rawSource.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder("typescript:v1:");
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm missing", e);
    }
  }
}
