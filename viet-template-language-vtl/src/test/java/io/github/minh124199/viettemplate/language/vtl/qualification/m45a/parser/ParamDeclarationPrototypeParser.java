package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Focused prototype parser for qualifying candidate parameter declaration syntaxes in M45A.
 *
 * <p>Enforces prologue-only positioning, precise source spans, duplicate parameter detection,
 * generic type decomposition, and bridge conversion to {@link ModelSchema}.
 */
public final class ParamDeclarationPrototypeParser {

  public static final DiagnosticCode CODE_DUPLICATE_PARAM = DiagnosticCode.of("VTLS", "2108");
  public static final DiagnosticCode CODE_UNKNOWN_TYPE = DiagnosticCode.of("VTLS", "2109");
  public static final DiagnosticCode CODE_INVALID_PARAM_SYNTAX = DiagnosticCode.of("VTLS", "2110");
  public static final DiagnosticCode CODE_PARAM_NOT_IN_PROLOGUE = DiagnosticCode.of("VTLS", "2111");

  private final SourceText source;
  private final String content;
  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private final List<VtlParamDeclaration> declarations = new ArrayList<>();
  private final Map<String, VtlParamDeclaration> declaredNames = new LinkedHashMap<>();

  public record ParseResult(
      List<VtlParamDeclaration> declarations, List<Diagnostic> diagnostics, boolean hasErrors) {
    public boolean hasErrors() {
      return !diagnostics.isEmpty();
    }
  }

  public ParamDeclarationPrototypeParser(SourceText source) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.content = source.content();
  }

  public static ParseResult parse(SourceText source, CandidateSyntax syntax) {
    ParamDeclarationPrototypeParser parser = new ParamDeclarationPrototypeParser(source);
    return parser.parseInternal(syntax);
  }

  private ParseResult parseInternal(CandidateSyntax syntax) {
    int pos = 0;
    int len = content.length();
    boolean prologueEnded = false;

    while (pos < len) {
      // 1. Skip whitespace
      if (Character.isWhitespace(content.charAt(pos))) {
        pos++;
        continue;
      }

      // 2. Skip single-line comments ##
      if (pos + 1 < len && content.charAt(pos) == '#' && content.charAt(pos + 1) == '#') {
        int eol = content.indexOf('\n', pos);
        pos = (eol == -1) ? len : eol + 1;
        continue;
      }

      // 3. Skip multi-line comments #* ... *#
      if (pos + 1 < len && content.charAt(pos) == '#' && content.charAt(pos + 1) == '*') {
        int endComment = content.indexOf("*#", pos + 2);
        if (endComment == -1) {
          diagnostics.add(
              Diagnostic.error(
                  DiagnosticCode.of("PARSER", "UNCLOSED_COMMENT"),
                  "Unclosed multi-line comment",
                  source.spanAt(pos, len)));
          break;
        }
        if (syntax == CandidateSyntax.CANDIDATE_F_COMMENT_METADATA) {
          // Check if this comment is a @param declaration
          String commentBody = content.substring(pos + 2, endComment).trim();
          if (commentBody.startsWith("@param")) {
            parseCommentParam(commentBody, pos, endComment + 2);
          }
        }
        pos = endComment + 2;
        continue;
      }

      // 4. Check for #param
      if (isParamDirective(pos)) {
        if (prologueEnded) {
          int dirEnd = findDirectiveEnd(pos);
          diagnostics.add(
              Diagnostic.error(
                  CODE_PARAM_NOT_IN_PROLOGUE,
                  "#param directive must appear in template prologue before any rendering"
                      + " statements",
                  source.spanAt(pos, dirEnd)));
          pos = dirEnd;
          continue;
        }

        pos = parseParamDirective(pos, syntax);
        continue;
      }

      // 5. If we encounter non-prologue content (other directives or text), prologue ends
      prologueEnded = true;
      pos++;
    }

    return new ParseResult(
        Collections.unmodifiableList(declarations),
        Collections.unmodifiableList(diagnostics),
        !diagnostics.isEmpty());
  }

  private boolean isParamDirective(int pos) {
    if (pos >= content.length() || content.charAt(pos) != '#') {
      return false;
    }
    int start = pos + 1;
    boolean braced = false;
    if (start < content.length() && content.charAt(start) == '{') {
      braced = true;
      start++;
    }
    if (content.startsWith("param", start)) {
      int after = start + 5;
      if (braced) {
        return after < content.length() && content.charAt(after) == '}';
      }
      return after >= content.length() || !Character.isJavaIdentifierPart(content.charAt(after));
    }
    return false;
  }

  private int findDirectiveEnd(int startPos) {
    int openParen = content.indexOf('(', startPos);
    if (openParen != -1) {
      int closeParen = content.indexOf(')', openParen);
      if (closeParen != -1) {
        return closeParen + 1;
      }
    }
    int eol = content.indexOf('\n', startPos);
    return eol != -1 ? eol : content.length();
  }

  private int parseParamDirective(int startPos, CandidateSyntax syntax) {
    int cur = startPos + 1; // skip '#'
    boolean braced = false;
    if (cur < content.length() && content.charAt(cur) == '{') {
      braced = true;
      cur++;
    }
    cur += 5; // skip 'param'
    if (braced) {
      if (cur < content.length() && content.charAt(cur) == '}') {
        cur++;
      }
    }

    // Skip whitespace between directive name and '('
    while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
      cur++;
    }

    if (syntax == CandidateSyntax.CANDIDATE_D_SCHEMA_ONLY) {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Inline #param declarations are disabled in companion schema-only mode",
              source.spanAt(startPos, findDirectiveEnd(startPos))));
      return findDirectiveEnd(startPos);
    }

    if (syntax == CandidateSyntax.CANDIDATE_C_BARE_TYPE_VAR) {
      return parseBareParam(startPos, cur);
    }

    if (cur >= content.length() || content.charAt(cur) != '(') {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected '(' after #param directive",
              source.spanAt(startPos, Math.min(startPos + 6, content.length()))));
      return cur;
    }

    int openParen = cur;
    cur++; // skip '('

    // Parse one or more parameters separated by commas inside (...)
    boolean hadParams = false;
    while (cur < content.length() && content.charAt(cur) != ')') {
      // skip whitespace
      while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
        cur++;
      }
      if (cur < content.length() && content.charAt(cur) == ')') {
        break;
      }
      hadParams = true;

      int paramItemStart = cur;
      if (syntax == CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR
          || syntax == CandidateSyntax.CANDIDATE_E_HYBRID) {
        cur = parseSingleParamCandidateA(startPos, cur);
      } else if (syntax == CandidateSyntax.CANDIDATE_B_PAREN_VAR_COLON_TYPE) {
        cur = parseSingleParamCandidateB(startPos, cur);
      } else {
        cur = parseSingleParamCandidateA(startPos, cur);
      }

      // Check for comma separator
      while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
        cur++;
      }
      if (cur < content.length() && content.charAt(cur) == ',') {
        cur++; // consume comma
        while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
          cur++;
        }
        if (cur < content.length() && content.charAt(cur) == ')') {
          diagnostics.add(
              Diagnostic.error(
                  CODE_INVALID_PARAM_SYNTAX,
                  "Trailing comma in #param parameter list",
                  source.spanAt(cur - 1, cur)));
          break;
        }
      } else if (cur < content.length() && content.charAt(cur) != ')') {
        diagnostics.add(
            Diagnostic.error(
                CODE_INVALID_PARAM_SYNTAX,
                "Expected ',' or ')' in #param parameter list",
                source.spanAt(cur, cur + 1)));
        // synchronize to next comma or ')'
        while (cur < content.length() && content.charAt(cur) != ',' && content.charAt(cur) != ')') {
          cur++;
        }
        if (cur < content.length() && content.charAt(cur) == ',') {
          cur++;
        }
      }
    }

    if (!hadParams) {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Empty parameter list in #param directive",
              source.spanAt(startPos, cur < content.length() ? cur + 1 : content.length())));
    }

    if (cur < content.length() && content.charAt(cur) == ')') {
      cur++; // consume ')'
    } else {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Unclosed '(' in #param directive",
              source.spanAt(startPos, Math.min(cur, content.length()))));
    }

    return cur;
  }

  /** Parses single parameter declaration in Candidate A: Type $name [= default] */
  private int parseSingleParamCandidateA(int directiveStart, int cur) {
    int typeStart = cur;
    // Type can include generics: List<String>, Map<String, Employee>, String[]
    int typeEnd = scanTypeExpression(cur);
    if (typeEnd == typeStart) {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected type name in #param declaration",
              source.spanAt(cur, Math.min(cur + 1, content.length()))));
      return cur + 1;
    }

    String rawTypeText = content.substring(typeStart, typeEnd).trim();
    boolean isNullable = false;
    if (rawTypeText.endsWith("?")) {
      isNullable = true;
      rawTypeText = rawTypeText.substring(0, rawTypeText.length() - 1).trim();
    }
    SourceSpan typeSpan = source.spanAt(typeStart, typeEnd);
    cur = typeEnd;

    // skip whitespace
    while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
      cur++;
    }

    // Expect variable reference: $name
    if (cur >= content.length() || content.charAt(cur) != '$') {
      int errStart = cur;
      while (cur < content.length()
          && !Character.isWhitespace(content.charAt(cur))
          && content.charAt(cur) != ','
          && content.charAt(cur) != ')') {
        cur++;
      }
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected parameter variable starting with '$' after type '" + rawTypeText + "'",
              source.spanAt(errStart, Math.max(errStart + 1, cur))));
      return cur;
    }

    int varStart = cur;
    cur++; // consume '$'
    int nameStart = cur;
    while (cur < content.length() && Character.isJavaIdentifierPart(content.charAt(cur))) {
      cur++;
    }
    if (cur == nameStart) {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected identifier after '$'",
              source.spanAt(varStart, Math.min(varStart + 1, content.length()))));
      return cur;
    }

    String paramName = content.substring(nameStart, cur);
    SourceSpan nameSpan = source.spanAt(varStart, cur);

    // Optional default value: = defaultExpr
    String defaultValue = null;
    int checkCur = cur;
    while (checkCur < content.length() && Character.isWhitespace(content.charAt(checkCur))) {
      checkCur++;
    }
    if (checkCur < content.length() && content.charAt(checkCur) == '=') {
      checkCur++; // consume '='
      while (checkCur < content.length() && Character.isWhitespace(content.charAt(checkCur))) {
        checkCur++;
      }
      int defStart = checkCur;
      checkCur = scanDefaultExpression(checkCur);
      defaultValue = content.substring(defStart, checkCur).trim();
      cur = checkCur;
    }

    SourceSpan fullSpan = source.spanAt(typeStart, cur);
    registerParam(paramName, rawTypeText, isNullable, defaultValue, fullSpan, typeSpan, nameSpan);
    return cur;
  }

  /** Parses single parameter declaration in Candidate B: $name: Type [= default] */
  private int parseSingleParamCandidateB(int directiveStart, int cur) {
    if (cur >= content.length() || content.charAt(cur) != '$') {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected parameter variable starting with '$' in #param",
              source.spanAt(cur, Math.min(cur + 1, content.length()))));
      return cur + 1;
    }

    int varStart = cur;
    cur++; // consume '$'
    int nameStart = cur;
    while (cur < content.length() && Character.isJavaIdentifierPart(content.charAt(cur))) {
      cur++;
    }
    String paramName = content.substring(nameStart, cur);
    SourceSpan nameSpan = source.spanAt(varStart, cur);

    while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
      cur++;
    }
    if (cur >= content.length() || content.charAt(cur) != ':') {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected ':' after parameter '$" + paramName + "'",
              source.spanAt(cur, Math.min(cur + 1, content.length()))));
      return cur;
    }
    cur++; // consume ':'
    while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
      cur++;
    }

    int typeStart = cur;
    int typeEnd = scanTypeExpression(cur);
    if (typeEnd == typeStart) {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected type name after ':' for parameter '$" + paramName + "'",
              source.spanAt(cur, Math.min(cur + 1, content.length()))));
      return cur;
    }

    String rawTypeText = content.substring(typeStart, typeEnd).trim();
    boolean isNullable = false;
    if (rawTypeText.endsWith("?")) {
      isNullable = true;
      rawTypeText = rawTypeText.substring(0, rawTypeText.length() - 1).trim();
    }
    SourceSpan typeSpan = source.spanAt(typeStart, typeEnd);
    cur = typeEnd;

    // Optional default value
    String defaultValue = null;
    int checkCur = cur;
    while (checkCur < content.length() && Character.isWhitespace(content.charAt(checkCur))) {
      checkCur++;
    }
    if (checkCur < content.length() && content.charAt(checkCur) == '=') {
      checkCur++;
      while (checkCur < content.length() && Character.isWhitespace(content.charAt(checkCur))) {
        checkCur++;
      }
      int defStart = checkCur;
      checkCur = scanDefaultExpression(checkCur);
      defaultValue = content.substring(defStart, checkCur).trim();
      cur = checkCur;
    }

    SourceSpan fullSpan = source.spanAt(varStart, cur);
    registerParam(paramName, rawTypeText, isNullable, defaultValue, fullSpan, typeSpan, nameSpan);
    return cur;
  }

  private int parseBareParam(int directiveStart, int cur) {
    int typeStart = cur;
    int typeEnd = scanTypeExpression(cur);
    String rawTypeText = content.substring(typeStart, typeEnd).trim();
    SourceSpan typeSpan = source.spanAt(typeStart, typeEnd);
    cur = typeEnd;

    while (cur < content.length() && Character.isWhitespace(content.charAt(cur))) {
      cur++;
    }
    if (cur >= content.length() || content.charAt(cur) != '$') {
      diagnostics.add(
          Diagnostic.error(
              CODE_INVALID_PARAM_SYNTAX,
              "Expected parameter variable '$name' in #param",
              source.spanAt(cur, Math.min(cur + 1, content.length()))));
      return cur;
    }
    int varStart = cur;
    cur++;
    int nameStart = cur;
    while (cur < content.length() && Character.isJavaIdentifierPart(content.charAt(cur))) {
      cur++;
    }
    String paramName = content.substring(nameStart, cur);
    SourceSpan nameSpan = source.spanAt(varStart, cur);
    SourceSpan fullSpan = source.spanAt(directiveStart, cur);
    registerParam(paramName, rawTypeText, false, null, fullSpan, typeSpan, nameSpan);
    return cur;
  }

  private void parseCommentParam(String commentBody, int commentStart, int commentEnd) {
    // Format: @param Type $var
    String rest = commentBody.substring(6).trim();
    int dollarIdx = rest.indexOf('$');
    if (dollarIdx > 0) {
      String typeStr = rest.substring(0, dollarIdx).trim();
      String varStr = rest.substring(dollarIdx + 1).trim();
      int endId = 0;
      while (endId < varStr.length() && Character.isJavaIdentifierPart(varStr.charAt(endId))) {
        endId++;
      }
      String name = varStr.substring(0, endId);
      SourceSpan span = source.spanAt(commentStart, commentEnd);
      registerParam(name, typeStr, false, null, span, span, span);
    }
  }

  private int scanDefaultExpression(int start) {
    int cur = start;
    int parenDepth = 0;
    int bracketDepth = 0;
    int braceDepth = 0;
    while (cur < content.length()) {
      char c = content.charAt(cur);
      if (c == '\'' || c == '"') {
        char quote = c;
        cur++;
        while (cur < content.length()) {
          char qc = content.charAt(cur);
          if (qc == '\\' && cur + 1 < content.length()) {
            cur += 2;
          } else if (qc == quote) {
            cur++;
            break;
          } else {
            cur++;
          }
        }
        continue;
      }
      if (c == '(') {
        parenDepth++;
      } else if (c == ')') {
        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
          break; // Closing ')' of the #param(...) directive
        }
        if (parenDepth > 0) parenDepth--;
      } else if (c == '[') {
        bracketDepth++;
      } else if (c == ']') {
        if (bracketDepth > 0) bracketDepth--;
      } else if (c == '{') {
        braceDepth++;
      } else if (c == '}') {
        if (braceDepth > 0) braceDepth--;
      } else if (c == ',') {
        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
          break; // Comma separator between #param parameters
        }
      }
      cur++;
    }
    return cur;
  }

  private int scanTypeExpression(int start) {
    int cur = start;
    int depth = 0;
    while (cur < content.length()) {
      char c = content.charAt(cur);
      if (c == '<') {
        depth++;
        cur++;
      } else if (c == '>') {
        depth--;
        cur++;
        if (depth == 0) {
          // Check for array suffix or nullable marker: e.g. List<String>[] or List<String>?
          while (cur < content.length()
              && (content.charAt(cur) == '['
                  || content.charAt(cur) == ']'
                  || content.charAt(cur) == '?')) {
            cur++;
          }
          break;
        }
      } else if (depth == 0) {
        if (Character.isWhitespace(c) || c == '$' || c == ',' || c == ')' || c == ':' || c == '=') {
          break;
        }
        cur++;
      } else {
        cur++;
      }
    }
    return cur;
  }

  private void registerParam(
      String name,
      String typeText,
      boolean nullable,
      String defaultValue,
      SourceSpan span,
      SourceSpan typeSpan,
      SourceSpan nameSpan) {
    if (declaredNames.containsKey(name)) {
      diagnostics.add(
          Diagnostic.error(
              CODE_DUPLICATE_PARAM,
              "Duplicate parameter declaration for '$" + name + "'",
              nameSpan));
      return;
    }
    VtlParamDeclaration decl =
        VtlParamDeclaration.of(name, typeText, nullable, defaultValue, span, typeSpan, nameSpan);
    declarations.add(decl);
    declaredNames.put(name, decl);
  }

  /** Bridges parsed parameter declarations to an authoritative {@link ModelSchema}. */
  public static ModelSchema toModelSchema(
      List<VtlParamDeclaration> declarations, Map<String, Class<?>> knownClasses) {
    Map<String, ModelParameter> params = new LinkedHashMap<>();
    for (VtlParamDeclaration decl : declarations) {
      VType type = resolveType(decl.typeText(), decl.nullable(), knownClasses);
      params.put(decl.name(), ModelParameter.of(decl.name(), type));
    }
    return ModelSchema.of(params.values().toArray(new ModelParameter[0]));
  }

  /**
   * Merges inline declarations with a companion {@link ModelSchema} according to the 4-tier
   * precedence rule: inline declaration > companion schema > dynamic fallback.
   */
  public static ModelSchema toHybridModelSchema(
      List<VtlParamDeclaration> inlineDecls,
      ModelSchema companionSchema,
      Map<String, Class<?>> knownClasses) {
    Map<String, ModelParameter> merged = new LinkedHashMap<>();
    if (companionSchema != null) {
      for (ModelParameter p : companionSchema.parameters().values()) {
        merged.put(p.name(), p);
      }
    }
    for (VtlParamDeclaration decl : inlineDecls) {
      VType type = resolveType(decl.typeText(), decl.nullable(), knownClasses);
      merged.put(decl.name(), ModelParameter.of(decl.name(), type));
    }
    return ModelSchema.of(merged.values().toArray(new ModelParameter[0]));
  }

  private static VType resolveType(
      String typeText, boolean isNullable, Map<String, Class<?>> knownClasses) {
    Nullability nullability = isNullable ? Nullability.NULLABLE : Nullability.NON_NULL;
    String trimmed = typeText.trim();

    if (trimmed.endsWith("?")) {
      nullability = Nullability.NULLABLE;
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }

    // Check wildcards: ? extends T, ? super T, ?
    if (trimmed.startsWith("?")) {
      if (trimmed.startsWith("? extends ")) {
        String bound = trimmed.substring(10).trim();
        VType boundType = resolveType(bound, false, knownClasses);
        return boundType.withNullability(nullability);
      }
      if (trimmed.startsWith("? super ")) {
        return VTypes.OBJECT.withNullability(nullability);
      }
      return VTypes.DYNAMIC.withNullability(nullability);
    }

    // Check array suffix: Type[]
    if (trimmed.endsWith("[]")) {
      String compText = trimmed.substring(0, trimmed.length() - 2).trim();
      VType compType = resolveType(compText, false, knownClasses);
      return new VType.ArrayType(compType, nullability);
    }

    // Check generic: List<T>, Map<K, V>
    int angleIdx = trimmed.indexOf('<');
    if (angleIdx != -1 && trimmed.endsWith(">")) {
      String rawName = trimmed.substring(0, angleIdx).trim();
      String argsStr = trimmed.substring(angleIdx + 1, trimmed.length() - 1).trim();
      List<VType> typeArgs = new ArrayList<>();
      for (String arg : splitGenericArgs(argsStr)) {
        typeArgs.add(resolveType(arg.trim(), false, knownClasses));
      }

      Class<?> rawClass = resolveRawClass(rawName, knownClasses);
      if (rawClass != null) {
        return VType.ClassType.of(rawClass, typeArgs, nullability);
      }
      return VType.ClassType.of(rawName, typeArgs, nullability);
    }

    // Check primitives
    for (PrimitiveKind pk : PrimitiveKind.values()) {
      if (pk.keyword().equals(trimmed)) {
        return new VType.PrimitiveType(pk);
      }
    }

    // Standard Java types
    return switch (trimmed) {
      case "String" -> VTypes.STRING.withNullability(nullability);
      case "Object" -> VTypes.OBJECT.withNullability(nullability);
      case "Boolean" -> VType.ClassType.of(Boolean.class, nullability);
      case "Integer", "int" -> VType.ClassType.of(Integer.class, nullability);
      case "Long", "long" -> VType.ClassType.of(Long.class, nullability);
      case "Double", "double" -> VType.ClassType.of(Double.class, nullability);
      case "Float", "float" -> VType.ClassType.of(Float.class, nullability);
      case "BigDecimal" -> VTypes.BIG_DECIMAL.withNullability(nullability);
      case "BigInteger" -> VTypes.BIG_INTEGER.withNullability(nullability);
      case "List" -> VType.ClassType.of(List.class, List.of(VTypes.DYNAMIC), nullability);
      case "Map" ->
          VType.ClassType.of(Map.class, List.of(VTypes.DYNAMIC, VTypes.DYNAMIC), nullability);
      default -> {
        Class<?> c = resolveRawClass(trimmed, knownClasses);
        if (c != null) {
          yield VType.ClassType.of(c, nullability);
        }
        yield VType.ClassType.of(trimmed, List.of(), nullability);
      }
    };
  }

  private static Class<?> resolveRawClass(String name, Map<String, Class<?>> knownClasses) {
    if (knownClasses != null && knownClasses.containsKey(name)) {
      return knownClasses.get(name);
    }
    return switch (name) {
      case "List", "java.util.List" -> List.class;
      case "Map", "java.util.Map" -> Map.class;
      case "Set", "java.util.Set" -> java.util.Set.class;
      case "Collection", "java.util.Collection" -> java.util.Collection.class;
      case "String", "java.lang.String" -> String.class;
      case "Object", "java.lang.Object" -> Object.class;
      default -> {
        if (name.length() == 1 && Character.isUpperCase(name.charAt(0))) {
          // Type variables like T, E, K, V degrade to Object / Dynamic
          yield Object.class;
        }
        try {
          yield Class.forName(name);
        } catch (ClassNotFoundException e) {
          yield null;
        }
      }
    };
  }

  private static List<String> splitGenericArgs(String args) {
    List<String> list = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < args.length(); i++) {
      char c = args.charAt(i);
      if (c == '<') depth++;
      else if (c == '>') depth--;
      else if (c == ',' && depth == 0) {
        list.add(args.substring(start, i).trim());
        start = i + 1;
      }
    }
    if (start < args.length()) {
      list.add(args.substring(start).trim());
    }
    return list;
  }
}
