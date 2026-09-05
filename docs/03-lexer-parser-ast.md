# 03 — Lexer, Parser, Source Model and AST

## 1. Objectives

Parser must be deterministic, source-span precise, recoverable, fuzz-testable, safe on adversarial input, and optimized for long literal text spans.

## 2. Source model

```java
public record TemplateSource(
    TemplateId id,
    URI origin,
    Charset charset,
    String content,
    SourceLineMap lineMap,
    SourceHash hash
) {}
```

```java
public record SourceSpan(
    TemplateId template,
    int startOffset,
    int endOffset,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
) {}
```

Offsets are half-open `[start,end)`.

## 3. Lexer strategy

Avoid tokenizing literal HTML character-by-character:

```text
scan until '$' or '#'
emit TEXT_SPAN
inspect introducer
```

Modes:

```text
TEXT
REFERENCE
DIRECTIVE
EXPRESSION
DOUBLE_STRING
RAW_BLOCK
COMMENT
```

Tokens include identifiers, numbers, quoted strings, delimiters/operators, comments, raw blocks and EOF.

## 4. Ambiguity examples

```velocity
$email
$emailsuffix
${email}suffix
$!email
$!{email}
#foo
## comment
#* block *#
#[[ raw ]]#
```

Use longest valid identifier; formal notation provides explicit boundary.

## 5. Grammar sketch

```ebnf
template        ::= element* EOF ;
element         ::= text | reference | directive | comment | rawBlock ;
reference       ::= '$' quiet? referenceBody
                  | '$' quiet? '{' referenceBody alternate? '}' ;
quiet           ::= '!' ;
alternate       ::= '|' expression ;
referenceBody   ::= identifier postfix* ;
postfix         ::= '.' identifier methodArgs? | '[' expression ']' ;
methodArgs      ::= '(' argumentList? ')' ;
argumentList    ::= expression (',' expression)* ;

setDirective    ::= '#set' '(' assignTarget '=' expression ')' ;
ifDirective     ::= '#if' '(' expression ')' template
                    ('#elseif' '(' expression ')' template)*
                    ('#else' template)? '#end' ;
foreachDirective::= '#foreach' '(' variable 'in' expression ')' template
                    ('#else' template)? '#end' ;

expression      ::= logicalOr ;
logicalOr       ::= logicalAnd (('||'|'or') logicalAnd)* ;
logicalAnd      ::= equality (('&&'|'and') equality)* ;
equality        ::= relational (('=='|'!=') relational)* ;
relational      ::= additive (('<'|'<='|'>'|'>=') additive)* ;
additive        ::= multiplicative (('+'|'-') multiplicative)* ;
multiplicative  ::= unary (('*'|'/'|'%') unary)* ;
unary           ::= ('!'|'not'|'-'|'+') unary | primary ;
primary         ::= literal | reference | collection | '(' expression ')' ;
```

This is non-normative until compatibility tests settle edge cases.

## 6. AST

Use sealed immutable types.

```java
public sealed interface AstNode { SourceSpan span(); }
record AstTemplate(List<AstNode> children, SourceSpan span) implements AstNode {}
record AstTextSlice(int start, int end, SourceSpan span) implements AstNode {}
```

Reference:

```java
record AstReference(
    String rootName,
    List<AstAccess> accesses,
    boolean quiet,
    AstExpression alternate,
    ReferenceNotation notation,
    SourceSpan span
) implements AstExpression {}
```

Access nodes: property, method(args), index(expression).

Expression nodes: literal, reference, unary, binary, list/map/range, interpolated string.

Do **not** attach reflected `Method`/`MethodHandle` objects to parser AST.

## 7. Error recovery

Recovery boundaries:

- closing `)`;
- newline for malformed simple directives;
- `#elseif/#else/#end` at current nesting;
- next recognized directive at same depth;
- EOF.

Example:

```text
VTLP1003: expected ')' after #if condition
 --> templates/home.vm:14:22
14 | #if($user.admin &&
   |                      ^ expected expression
help: complete the condition or remove the trailing '&&'
```

## 8. Limits

Suggested defaults:

```text
maxDirectiveDepth 256
maxExpressionDepth 256
maxReferenceChain 128
maxMacroParameters 128
maxTemplateSourceBytes 16 MiB
```

## 9. Implementation choice

Recommendation: handwritten text scanner plus Pratt/precedence-climbing expression parser. It gives precise control over embedded-text scanning, compatibility quirks, diagnostics and error recovery.

## 10. Parser tests

Golden valid/invalid syntax, token boundaries, nesting, comments/escaping, malformed EOF, Unicode, long text spans, extreme nesting, fuzz bytes, and span consistency.
