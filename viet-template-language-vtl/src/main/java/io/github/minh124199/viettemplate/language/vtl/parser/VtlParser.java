package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ast.ReferenceNotation;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAssignmentTarget;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryOperator;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBlockDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBooleanLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBreakDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDecimalLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDefineDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlErrorExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlErrorNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlEvaluateDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlForeachDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlGroupedExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfBranch;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIncludeDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIntegerLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlListLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroDefinitionNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroParameter;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapEntry;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNullLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlParseDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlRangeExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlRawTextNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReference;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceOutputNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlSetDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStopDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStringLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTextNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryOperator;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexResult;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexer;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexerOptions;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlToken;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlTokenKind;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Handwritten recursive-descent and Pratt precedence parser for the VTL frontend.
 *
 * <p>Constructs an immutable, source-span-precise AST ({@link VtlTemplate}) with robust
 * synchronization error recovery and defensive nesting limits.
 */
public final class VtlParser {

  private static final String DIAG_CATEGORY = "PARSER";

  private final SourceText source;
  private final VtlParserOptions options;
  private final VtlTokenCursor cursor;
  private final List<Diagnostic> diagnostics;
  private int nestingDepth = 0;

  private VtlParser(
      SourceText source,
      VtlParserOptions options,
      VtlTokenCursor cursor,
      List<Diagnostic> initialDiagnostics) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.cursor = Objects.requireNonNull(cursor, "cursor must not be null");
    this.diagnostics =
        new ArrayList<>(
            Objects.requireNonNull(initialDiagnostics, "initialDiagnostics must not be null"));
  }

  public static VtlParseResult parse(SourceText source) {
    return parse(source, VtlParserOptions.DEFAULT);
  }

  public static VtlParseResult parse(SourceText source, VtlParserOptions options) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(options, "options must not be null");
    VtlLexerOptions lexOptions = new VtlLexerOptions(false, options.allowHyphenatedIdentifiers());
    VtlLexResult lexResult = VtlLexer.lex(source, lexOptions);
    return parse(source, lexResult, options);
  }

  public static VtlParseResult parse(
      SourceText source, VtlLexResult lexResult, VtlParserOptions options) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(lexResult, "lexResult must not be null");
    Objects.requireNonNull(options, "options must not be null");

    List<VtlToken> nonTrivia = new ArrayList<>();
    for (VtlToken token : lexResult.tokens()) {
      if (!token.isTrivia()) {
        nonTrivia.add(token);
      }
    }

    VtlTokenCursor cursor = new VtlTokenCursor(nonTrivia, source);
    VtlParser parser = new VtlParser(source, options, cursor, lexResult.diagnostics());
    VtlTemplate template = parser.parseTemplate();
    return new VtlParseResult(template, parser.diagnostics, source);
  }

  // =========================================================================
  // TOP-LEVEL PARSING
  // =========================================================================

  private VtlTemplate parseTemplate() {
    List<VtlNode> children = new ArrayList<>();
    while (!cursor.isAtEnd()) {
      VtlNode node = parseTemplateItem();
      if (node != null) {
        children.add(node);
      }
    }
    SourceSpan span = source.fullSpan();
    return new VtlTemplate(source.templateId(), span, children);
  }

  private VtlNode parseTemplateItem() {
    if (cursor.check(VtlTokenKind.TEXT)) {
      VtlToken token = cursor.advance();
      return new VtlTextNode(token.span());
    }

    if (cursor.check(VtlTokenKind.RAW_TEXT)) {
      VtlToken token = cursor.advance();
      return new VtlRawTextNode(token.span());
    }

    if (cursor.check(VtlTokenKind.DOLLAR)) {
      VtlReference ref = parseReference();
      return new VtlReferenceOutputNode(ref, ref.span());
    }

    if (cursor.check(VtlTokenKind.HASH)) {
      return parseDirective();
    }

    // Unrecognized or unexpected token at template level
    VtlToken unexpected = cursor.advance();
    reportError(
        "UNEXPECTED_TOKEN",
        "Unexpected token in template context: " + unexpected.kind(),
        unexpected.span());
    return new VtlErrorNode("Unexpected: " + unexpected.kind(), unexpected.span());
  }

  // =========================================================================
  // REFERENCES
  // =========================================================================

  private VtlReference parseReference() {
    int startOffset = cursor.current().span().startOffset();
    cursor.advance(); // consume DOLLAR

    boolean quiet = cursor.match(VtlTokenKind.BANG);
    boolean formal = cursor.match(VtlTokenKind.LEFT_BRACE);

    if (!cursor.check(VtlTokenKind.IDENTIFIER)) {
      SourceSpan errSpan = cursor.current().span();
      reportError("EXPECTED_IDENTIFIER", "Expected identifier in reference", errSpan);
      return new VtlReference(
          ReferenceNotation.of(quiet, formal),
          "",
          List.of(),
          Optional.empty(),
          source.spanAt(startOffset, errSpan.endOffset()));
    }

    VtlToken rootToken = cursor.advance();
    String rootName = cursor.text(rootToken);

    List<VtlAccessStep> steps = new ArrayList<>();
    Optional<VtlExpression> alternateValue = Optional.empty();

    while (!cursor.isAtEnd()) {
      if (cursor.match(VtlTokenKind.DOT)) {
        int dotStart = cursor.previous().span().startOffset();
        if (cursor.check(VtlTokenKind.IDENTIFIER)) {
          VtlToken idTok = cursor.advance();
          String memberName = cursor.text(idTok);
          if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
            List<VtlExpression> args = parseMethodArguments();
            int end = cursor.previous().span().endOffset();
            steps.add(new VtlAccessStep.MethodCall(memberName, args, source.spanAt(dotStart, end)));
          } else {
            steps.add(
                new VtlAccessStep.PropertyAccess(
                    memberName, source.spanAt(dotStart, idTok.span().endOffset())));
          }
        } else {
          reportError(
              "EXPECTED_PROPERTY_NAME",
              "Expected property or method name after '.'",
              cursor.current().span());
          break;
        }
      } else if (cursor.match(VtlTokenKind.LEFT_BRACKET)) {
        int bracketStart = cursor.previous().span().startOffset();
        VtlExpression indexExpr = parseExpression(0);
        expect(VtlTokenKind.RIGHT_BRACKET, "Expected ']' after index expression");
        int end = cursor.previous().span().endOffset();
        steps.add(new VtlAccessStep.IndexAccess(indexExpr, source.spanAt(bracketStart, end)));
      } else if (formal && cursor.check(VtlTokenKind.PIPE)) {
        cursor.advance(); // consume PIPE
        if (cursor.check(VtlTokenKind.RIGHT_BRACE)) {
          // Empty alternate value e.g. ${foo|}
          alternateValue = Optional.of(new VtlStringLiteralExpression("", cursor.current().span()));
        } else {
          alternateValue = Optional.of(parseExpression(0));
        }
        break; // alternate value is followed directly by closing '}'
      } else {
        break;
      }
    }

    if (formal) {
      if (cursor.match(VtlTokenKind.RIGHT_BRACE)) {
        // cleanly closed
      } else {
        reportError(
            "UNCLOSED_FORMAL_REFERENCE",
            "Unclosed formal reference: missing '}'",
            cursor.current().span());
      }
    }

    int endOffset = cursor.previous().span().endOffset();
    SourceSpan refSpan = source.spanAt(startOffset, endOffset);
    return new VtlReference(
        ReferenceNotation.of(quiet, formal), rootName, steps, alternateValue, refSpan);
  }

  private List<VtlExpression> parseMethodArguments() {
    List<VtlExpression> args = new ArrayList<>();
    if (!cursor.check(VtlTokenKind.RIGHT_PAREN) && !cursor.isAtEnd()) {
      do {
        if (cursor.check(VtlTokenKind.RIGHT_PAREN) || cursor.isAtEnd()) {
          break;
        }
        args.add(parseExpression(0));
      } while (cursor.match(VtlTokenKind.COMMA));
    }
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after method arguments");
    return args;
  }

  // =========================================================================
  // DIRECTIVES
  // =========================================================================

  private VtlNode parseDirective() {
    int startOffset = cursor.advance().span().startOffset(); // consume HASH

    if (cursor.match(VtlTokenKind.AT)) {
      // Block macro call: #@panel(...) body #end
      if (!cursor.check(VtlTokenKind.IDENTIFIER)) {
        reportError(
            "EXPECTED_MACRO_NAME",
            "Expected macro name identifier after '#@'",
            cursor.current().span());
        return new VtlErrorNode("Missing block macro name", cursor.current().span());
      }
      VtlToken nameTok = cursor.advance();
      String macroName = cursor.text(nameTok);
      List<VtlExpression> args = List.of();
      if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
        args = parseDirectiveArguments();
      }
      List<VtlNode> body = parseBlockUntil("end");
      expectEndDirective();
      SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
      return new VtlBlockDirectiveCallNode(macroName, args, body, span);
    }

    boolean braced = cursor.match(VtlTokenKind.LEFT_BRACE);
    if (!cursor.check(VtlTokenKind.IDENTIFIER)) {
      reportError(
          "EXPECTED_DIRECTIVE_NAME", "Expected directive name after '#'", cursor.current().span());
      return new VtlErrorNode("Missing directive name", cursor.current().span());
    }

    VtlToken nameToken = cursor.advance();
    String dirName = cursor.text(nameToken).toLowerCase();
    if (braced) {
      expect(VtlTokenKind.RIGHT_BRACE, "Expected '}' after braced directive name");
    }

    return switch (dirName) {
      case "set" -> parseSetDirective(startOffset);
      case "if" -> parseIfDirective(startOffset);
      case "foreach" -> parseForeachDirective(startOffset);
      case "include" -> parseIncludeDirective(startOffset);
      case "parse" -> parseParseDirective(startOffset);
      case "break" -> parseBreakDirective(startOffset);
      case "stop" -> parseStopDirective(startOffset);
      case "evaluate" -> parseEvaluateDirective(startOffset);
      case "define" -> parseDefineDirective(startOffset);
      case "macro" -> parseMacroDirective(startOffset);
      case "end", "else", "elseif" -> {
        // Orphan block terminator at template top-level
        SourceSpan errSpan = source.spanAt(startOffset, cursor.previous().span().endOffset());
        reportError(
            "UNMATCHED_DIRECTIVE",
            "Unexpected #" + dirName + " directive without matching opening directive",
            errSpan);
        yield new VtlErrorNode("Unmatched #" + dirName, errSpan);
      }
      default -> parseGenericDirectiveCall(dirName, startOffset);
    };
  }

  private VtlNode parseSetDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #set");
    VtlAssignmentTarget target = parseAssignmentTarget();
    expect(VtlTokenKind.EQUAL, "Expected '=' in #set assignment");
    VtlExpression valueExpr = parseExpression(0);
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' to close #set directive");
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlSetDirectiveNode(target, valueExpr, span);
  }

  private VtlAssignmentTarget parseAssignmentTarget() {
    if (cursor.check(VtlTokenKind.DOLLAR)) {
      VtlReference ref = parseReference();
      return new VtlAssignmentTarget.ReferenceTarget(ref, ref.span());
    }
    reportError(
        "INVALID_ASSIGNMENT_TARGET",
        "Left-hand side of #set must be a reference (e.g. $var, $var.prop, $var[0])",
        cursor.current().span());
    VtlExpression errExpr = parseExpression(0);
    VtlReference synth =
        new VtlReference(
            ReferenceNotation.NORMAL, "$error", List.of(), Optional.empty(), errExpr.span());
    return new VtlAssignmentTarget.ReferenceTarget(synth, errExpr.span());
  }

  private VtlNode parseIfDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #if");
    VtlExpression condition = parseExpression(0);
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #if condition");

    int ifBranchStart = startOffset;
    List<VtlNode> body = parseBlockUntil("elseif", "else", "end");
    SourceSpan branchSpan = source.spanAt(ifBranchStart, cursor.previous().span().endOffset());

    List<VtlIfBranch> branches = new ArrayList<>();
    branches.add(new VtlIfBranch(condition, body, branchSpan));

    while (isDirective("elseif")) {
      int branchStart = cursor.current().span().startOffset();
      consumeDirectiveHeader("elseif");
      expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #elseif");
      VtlExpression eiCond = parseExpression(0);
      expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #elseif condition");
      List<VtlNode> eiBody = parseBlockUntil("elseif", "else", "end");
      SourceSpan eiSpan = source.spanAt(branchStart, cursor.previous().span().endOffset());
      branches.add(new VtlIfBranch(eiCond, eiBody, eiSpan));
    }

    Optional<List<VtlNode>> elseBody = Optional.empty();
    if (isDirective("else")) {
      consumeDirectiveHeader("else");
      elseBody = Optional.of(parseBlockUntil("end"));
    }

    expectEndDirective();
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlIfDirectiveNode(branches, elseBody, span);
  }

  private VtlNode parseForeachDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #foreach");
    if (!cursor.check(VtlTokenKind.DOLLAR)) {
      reportError(
          "EXPECTED_REFERENCE",
          "Expected loop variable reference starting with '$' in #foreach",
          cursor.current().span());
    }
    VtlReference loopVar = parseReference();

    if (!cursor.match(VtlTokenKind.IN)) {
      if (cursor.check(VtlTokenKind.IDENTIFIER) && "in".equalsIgnoreCase(cursor.currentText())) {
        cursor.advance();
      } else {
        reportError(
            "EXPECTED_IN",
            "Expected 'in' keyword in #foreach declaration",
            cursor.current().span());
      }
    }

    VtlExpression iterable = parseExpression(0);
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' to close #foreach declaration");

    List<VtlNode> body = parseBlockUntil("else", "end");
    Optional<List<VtlNode>> elseBody = Optional.empty();
    if (isDirective("else")) {
      consumeDirectiveHeader("else");
      elseBody = Optional.of(parseBlockUntil("end"));
    }

    expectEndDirective();
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlForeachDirectiveNode(loopVar, iterable, body, elseBody, span);
  }

  private VtlNode parseIncludeDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #include");
    List<VtlExpression> args = parseDirectiveArguments();
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlIncludeDirectiveNode(args, span);
  }

  private VtlNode parseParseDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #parse");
    VtlExpression templateExpr = parseExpression(0);
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #parse template expression");
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlParseDirectiveNode(templateExpr, span);
  }

  private VtlNode parseBreakDirective(int startOffset) {
    Optional<VtlExpression> scopeExpr = Optional.empty();
    if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
      if (!cursor.check(VtlTokenKind.RIGHT_PAREN)) {
        scopeExpr = Optional.of(parseExpression(0));
      }
      expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #break argument");
    }
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlBreakDirectiveNode(scopeExpr, span);
  }

  private VtlNode parseStopDirective(int startOffset) {
    Optional<VtlExpression> msgExpr = Optional.empty();
    if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
      if (!cursor.check(VtlTokenKind.RIGHT_PAREN)) {
        msgExpr = Optional.of(parseExpression(0));
      }
      expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #stop argument");
    }
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlStopDirectiveNode(msgExpr, span);
  }

  private VtlNode parseEvaluateDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #evaluate");
    VtlExpression evalExpr = parseExpression(0);
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #evaluate expression");
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlEvaluateDirectiveNode(evalExpr, span);
  }

  private VtlNode parseDefineDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #define");
    if (!cursor.check(VtlTokenKind.DOLLAR)) {
      reportError(
          "EXPECTED_REFERENCE",
          "Expected target block reference starting with '$' in #define",
          cursor.current().span());
    }
    VtlReference targetRef = parseReference();
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after #define target reference");

    List<VtlNode> body = parseBlockUntil("end");
    expectEndDirective();
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlDefineDirectiveNode(targetRef, body, span);
  }

  private VtlNode parseMacroDirective(int startOffset) {
    expect(VtlTokenKind.LEFT_PAREN, "Expected '(' after #macro");
    if (!cursor.check(VtlTokenKind.IDENTIFIER)) {
      reportError(
          "EXPECTED_MACRO_NAME",
          "Expected macro name identifier in #macro declaration",
          cursor.current().span());
    }
    VtlToken nameTok = cursor.advance();
    String macroName = cursor.text(nameTok);

    List<VtlMacroParameter> params = new ArrayList<>();
    while (!cursor.check(VtlTokenKind.RIGHT_PAREN) && !cursor.isAtEnd()) {
      cursor.match(VtlTokenKind.COMMA);
      if (cursor.check(VtlTokenKind.RIGHT_PAREN)) {
        break;
      }
      if (!cursor.check(VtlTokenKind.DOLLAR)) {
        reportError(
            "EXPECTED_PARAMETER_REFERENCE",
            "Expected parameter reference starting with '$'",
            cursor.current().span());
        break;
      }
      int pStart = cursor.current().span().startOffset();
      VtlReference pRef = parseReference();
      Optional<VtlExpression> defVal = Optional.empty();
      if (cursor.match(VtlTokenKind.EQUAL)) {
        defVal = Optional.of(parseExpression(0));
      }
      int pEnd = cursor.previous().span().endOffset();
      params.add(new VtlMacroParameter(pRef.rootName(), defVal, source.spanAt(pStart, pEnd)));
      cursor.match(VtlTokenKind.COMMA);
    }
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after macro parameters");

    List<VtlNode> body = parseBlockUntil("end");
    expectEndDirective();
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlMacroDefinitionNode(macroName, params, body, span);
  }

  private VtlNode parseGenericDirectiveCall(String name, int startOffset) {
    List<VtlExpression> args = List.of();
    if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
      args = parseDirectiveArguments();
    }
    SourceSpan span = source.spanAt(startOffset, cursor.previous().span().endOffset());
    return new VtlDirectiveCallNode(name, args, span);
  }

  private List<VtlExpression> parseDirectiveArguments() {
    List<VtlExpression> args = new ArrayList<>();
    while (!cursor.check(VtlTokenKind.RIGHT_PAREN) && !cursor.isAtEnd()) {
      cursor.match(VtlTokenKind.COMMA);
      if (cursor.check(VtlTokenKind.RIGHT_PAREN)) {
        break;
      }
      args.add(parseExpression(0));
      cursor.match(VtlTokenKind.COMMA);
    }
    expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' after directive arguments");
    return args;
  }

  // =========================================================================
  // BLOCK UTILITIES & NESTING
  // =========================================================================

  private List<VtlNode> parseBlockUntil(String... terminators) {
    nestingDepth++;
    try {
      if (nestingDepth > options.maxNestingDepth()) {
        reportError(
            "MAX_NESTING_EXCEEDED",
            "Maximum nesting depth of " + options.maxNestingDepth() + " exceeded",
            cursor.current().span());
        return List.of();
      }

      List<VtlNode> nodes = new ArrayList<>();
      while (!cursor.isAtEnd()) {
        if (isDirective(terminators)) {
          break;
        }
        VtlNode node = parseTemplateItem();
        if (node != null) {
          nodes.add(node);
        }
      }
      return nodes;
    } finally {
      nestingDepth--;
    }
  }

  private boolean isDirective(String... names) {
    if (!cursor.check(VtlTokenKind.HASH)) {
      return false;
    }
    VtlToken next = cursor.peek(1);
    String dirName = null;
    if (next.kind() == VtlTokenKind.IDENTIFIER) {
      dirName = cursor.text(next);
    } else if (next.kind() == VtlTokenKind.LEFT_BRACE) {
      VtlToken idTok = cursor.peek(2);
      if (idTok.kind() == VtlTokenKind.IDENTIFIER) {
        dirName = cursor.text(idTok);
      }
    }
    if (dirName == null) {
      return false;
    }
    for (String name : names) {
      if (name.equalsIgnoreCase(dirName)) {
        return true;
      }
    }
    return false;
  }

  private void consumeDirectiveHeader(String expectedName) {
    cursor.advance(); // consume HASH
    if (cursor.match(VtlTokenKind.LEFT_BRACE)) {
      cursor.advance(); // consume IDENTIFIER
      expect(VtlTokenKind.RIGHT_BRACE, "Expected '}' after braced directive header");
    } else {
      cursor.advance(); // consume IDENTIFIER
    }
  }

  private void expectEndDirective() {
    if (cursor.check(VtlTokenKind.HASH)) {
      VtlToken next = cursor.peek(1);
      if (next.kind() == VtlTokenKind.IDENTIFIER && "end".equalsIgnoreCase(cursor.text(next))) {
        cursor.advance(); // HASH
        cursor.advance(); // IDENTIFIER
        return;
      }
      if (next.kind() == VtlTokenKind.LEFT_BRACE) {
        VtlToken idTok = cursor.peek(2);
        if (idTok.kind() == VtlTokenKind.IDENTIFIER && "end".equalsIgnoreCase(cursor.text(idTok))) {
          cursor.advance(); // HASH
          cursor.advance(); // LEFT_BRACE
          cursor.advance(); // IDENTIFIER
          expect(VtlTokenKind.RIGHT_BRACE, "Expected '}' after #{end}");
          return;
        }
      }
    }
    reportError(
        "UNCLOSED_DIRECTIVE", "Expected '#end' to close directive block", cursor.current().span());
  }

  // =========================================================================
  // PRATT PRECEDENCE EXPRESSION PARSER
  // =========================================================================

  private VtlExpression parseExpression(int minBp) {
    nestingDepth++;
    try {
      if (nestingDepth > options.maxNestingDepth()) {
        reportError(
            "MAX_NESTING_EXCEEDED",
            "Maximum nesting depth of " + options.maxNestingDepth() + " exceeded",
            cursor.current().span());
        return new VtlErrorExpression("Max nesting exceeded", cursor.current().span());
      }

      VtlExpression left = parsePrefixExpression();

      while (!cursor.isAtEnd()) {
        InfixOp op = getInfixOp(cursor.current().kind());
        if (op == null || op.leftBp < minBp) {
          break;
        }
        cursor.advance(); // consume operator token
        VtlExpression right = parseExpression(op.rightBp);
        SourceSpan binSpan = source.spanAt(left.span().startOffset(), right.span().endOffset());
        left = new VtlBinaryExpression(left, op.operator, right, binSpan);
      }

      return left;
    } finally {
      nestingDepth--;
    }
  }

  private VtlExpression parsePrefixExpression() {
    // Unary operators (Prefix BP 14)
    if (cursor.match(VtlTokenKind.LOGICAL_NOT) || cursor.match(VtlTokenKind.NOT)) {
      SourceSpan opSpan = cursor.previous().span();
      VtlExpression operand = parseExpression(14);
      return new VtlUnaryExpression(
          VtlUnaryOperator.NOT,
          operand,
          source.spanAt(opSpan.startOffset(), operand.span().endOffset()));
    }
    if (cursor.match(VtlTokenKind.MINUS)) {
      SourceSpan opSpan = cursor.previous().span();
      VtlExpression operand = parseExpression(14);
      return new VtlUnaryExpression(
          VtlUnaryOperator.MINUS,
          operand,
          source.spanAt(opSpan.startOffset(), operand.span().endOffset()));
    }
    if (cursor.match(VtlTokenKind.PLUS)) {
      SourceSpan opSpan = cursor.previous().span();
      VtlExpression operand = parseExpression(14);
      return new VtlUnaryExpression(
          VtlUnaryOperator.PLUS,
          operand,
          source.spanAt(opSpan.startOffset(), operand.span().endOffset()));
    }

    // Literals
    if (cursor.check(VtlTokenKind.INTEGER)) {
      VtlToken tok = cursor.advance();
      String txt = cursor.text(tok);
      return new VtlIntegerLiteralExpression(new BigInteger(txt), txt, tok.span());
    }

    if (cursor.check(VtlTokenKind.FLOAT)) {
      VtlToken tok = cursor.advance();
      String txt = cursor.text(tok);
      return new VtlDecimalLiteralExpression(new BigDecimal(txt), txt, tok.span());
    }

    if (cursor.match(VtlTokenKind.TRUE)) {
      return new VtlBooleanLiteralExpression(true, cursor.previous().span());
    }

    if (cursor.match(VtlTokenKind.FALSE)) {
      return new VtlBooleanLiteralExpression(false, cursor.previous().span());
    }

    if (cursor.check(VtlTokenKind.NULL)) {
      VtlToken tok = cursor.advance();
      if (options.allowBareNullLiteral()) {
        return new VtlNullLiteralExpression(tok.span());
      }
      reportError(
          "BARE_NULL_DISALLOWED",
          "Bare 'null' is not valid VTL syntax; use '$null', quiet reference '$!var', or enable"
              + " allowBareNullLiteral",
          tok.span());
      return new VtlErrorExpression("Bare null not allowed", tok.span());
    }

    if (cursor.check(VtlTokenKind.STRING_SINGLE)) {
      VtlToken tok = cursor.advance();
      String raw = cursor.text(tok);
      String unescaped = stripAndUnescapeSingleQuotes(raw);
      return new VtlStringLiteralExpression(unescaped, tok.span());
    }

    if (cursor.check(VtlTokenKind.STRING_DOUBLE)) {
      VtlToken tok = cursor.advance();
      return InterpolatedStringScanner.scan(
          tok.span(), source, options.allowHyphenatedIdentifiers());
    }

    // References
    if (cursor.check(VtlTokenKind.DOLLAR)) {
      VtlReference ref = parseReference();
      return new VtlReferenceExpression(ref, ref.span());
    }

    // Grouped expression
    if (cursor.match(VtlTokenKind.LEFT_PAREN)) {
      int start = cursor.previous().span().startOffset();
      VtlExpression inner = parseExpression(0);
      expect(VtlTokenKind.RIGHT_PAREN, "Expected ')' to close grouped expression");
      int end = cursor.previous().span().endOffset();
      return new VtlGroupedExpression(inner, source.spanAt(start, end));
    }

    // Range or List literal: [...]
    if (cursor.match(VtlTokenKind.LEFT_BRACKET)) {
      int start = cursor.previous().span().startOffset();
      if (cursor.match(VtlTokenKind.RIGHT_BRACKET)) {
        return new VtlListLiteralExpression(
            List.of(), source.spanAt(start, cursor.previous().span().endOffset()));
      }
      VtlExpression first = parseExpression(0);
      if (cursor.match(VtlTokenKind.RANGE)) {
        VtlExpression second = parseExpression(0);
        expect(VtlTokenKind.RIGHT_BRACKET, "Expected ']' to close range expression");
        return new VtlRangeExpression(
            first, second, source.spanAt(start, cursor.previous().span().endOffset()));
      } else {
        List<VtlExpression> items = new ArrayList<>();
        items.add(first);
        while (cursor.match(VtlTokenKind.COMMA)
            && !cursor.check(VtlTokenKind.RIGHT_BRACKET)
            && !cursor.isAtEnd()) {
          items.add(parseExpression(0));
        }
        expect(VtlTokenKind.RIGHT_BRACKET, "Expected ']' to close list literal");
        return new VtlListLiteralExpression(
            items, source.spanAt(start, cursor.previous().span().endOffset()));
      }
    }

    // Map literal: {...}
    if (cursor.match(VtlTokenKind.LEFT_BRACE)) {
      int start = cursor.previous().span().startOffset();
      List<VtlMapEntry> entries = new ArrayList<>();
      while (!cursor.check(VtlTokenKind.RIGHT_BRACE) && !cursor.isAtEnd()) {
        VtlExpression key = parseExpression(0);
        expect(VtlTokenKind.COLON, "Expected ':' after map key");
        VtlExpression val = parseExpression(0);
        SourceSpan entrySpan = source.spanAt(key.span().startOffset(), val.span().endOffset());
        entries.add(new VtlMapEntry(key, val, entrySpan));
        cursor.match(VtlTokenKind.COMMA);
      }
      expect(VtlTokenKind.RIGHT_BRACE, "Expected '}' to close map literal");
      return new VtlMapLiteralExpression(
          entries, source.spanAt(start, cursor.previous().span().endOffset()));
    }

    // Bare identifier in expression (e.g. unquoted string in map keys or expressions)
    if (cursor.check(VtlTokenKind.IDENTIFIER)) {
      VtlToken tok = cursor.advance();
      return new VtlStringLiteralExpression(cursor.text(tok), tok.span());
    }

    // Fallback error expression
    VtlToken bad = cursor.advance();
    reportError("UNEXPECTED_TOKEN", "Unexpected token in expression: " + bad.kind(), bad.span());
    return new VtlErrorExpression("Unexpected: " + bad.kind(), bad.span());
  }

  private static String stripAndUnescapeSingleQuotes(String raw) {
    if (raw.length() >= 2 && raw.charAt(0) == '\'' && raw.charAt(raw.length() - 1) == '\'') {
      raw = raw.substring(1, raw.length() - 1);
    }
    StringBuilder sb = new StringBuilder(raw.length());
    int i = 0;
    while (i < raw.length()) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char next = raw.charAt(i + 1);
        if (next == '\'' || next == '\\') {
          sb.append(next);
          i += 2;
          continue;
        }
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }

  // =========================================================================
  // OPERATOR BINDING POWERS
  // =========================================================================

  private record InfixOp(VtlBinaryOperator operator, int leftBp, int rightBp) {}

  private static InfixOp getInfixOp(VtlTokenKind kind) {
    return switch (kind) {
      case LOGICAL_OR, OR -> new InfixOp(VtlBinaryOperator.LOGICAL_OR, 1, 2);
      case LOGICAL_AND, AND -> new InfixOp(VtlBinaryOperator.LOGICAL_AND, 3, 4);
      case EQUAL_EQUAL, EQ -> new InfixOp(VtlBinaryOperator.EQUAL, 5, 6);
      case NOT_EQUAL, NE -> new InfixOp(VtlBinaryOperator.NOT_EQUAL, 5, 6);
      case LESS, LT -> new InfixOp(VtlBinaryOperator.LESS_THAN, 7, 8);
      case LESS_EQUAL, LE -> new InfixOp(VtlBinaryOperator.LESS_THAN_OR_EQUAL, 7, 8);
      case GREATER, GT -> new InfixOp(VtlBinaryOperator.GREATER_THAN, 7, 8);
      case GREATER_EQUAL, GE -> new InfixOp(VtlBinaryOperator.GREATER_THAN_OR_EQUAL, 7, 8);
      case PLUS -> new InfixOp(VtlBinaryOperator.ADD, 9, 10);
      case MINUS -> new InfixOp(VtlBinaryOperator.SUBTRACT, 9, 10);
      case STAR -> new InfixOp(VtlBinaryOperator.MULTIPLY, 11, 12);
      case SLASH -> new InfixOp(VtlBinaryOperator.DIVIDE, 11, 12);
      case PERCENT -> new InfixOp(VtlBinaryOperator.MODULO, 11, 12);
      default -> null;
    };
  }

  // =========================================================================
  // UTILITIES & DIAGNOSTICS
  // =========================================================================

  private boolean expect(VtlTokenKind kind, String errorMessage) {
    if (cursor.check(kind)) {
      cursor.advance();
      return true;
    }
    reportError("EXPECTED_TOKEN", errorMessage, cursor.current().span());
    return false;
  }

  private void reportError(String code, String message, SourceSpan span) {
    diagnostics.add(Diagnostic.error(DiagnosticCode.of(DIAG_CATEGORY, code), message, span));
  }
}
