package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Maps Viet Template lexical tokens to IntelliJ editor text attribute keys.
 */
public class VietTemplateSyntaxHighlighter extends SyntaxHighlighterBase {

  public static final TextAttributesKey DIRECTIVE =
      createTextAttributesKey("VIET_TEMPLATE_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey REFERENCE =
      createTextAttributesKey("VIET_TEMPLATE_REFERENCE", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

  public static final TextAttributesKey COMMENT =
      createTextAttributesKey("VIET_TEMPLATE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey BLOCK_COMMENT =
      createTextAttributesKey("VIET_TEMPLATE_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);

  public static final TextAttributesKey STRING =
      createTextAttributesKey("VIET_TEMPLATE_STRING", DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey NUMBER =
      createTextAttributesKey("VIET_TEMPLATE_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

  public static final TextAttributesKey OPERATOR =
      createTextAttributesKey("VIET_TEMPLATE_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

  public static final TextAttributesKey IDENTIFIER =
      createTextAttributesKey("VIET_TEMPLATE_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

  public static final TextAttributesKey BAD_CHARACTER =
      createTextAttributesKey("VIET_TEMPLATE_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  private static final TextAttributesKey[] DIRECTIVE_KEYS = new TextAttributesKey[]{DIRECTIVE};
  private static final TextAttributesKey[] REFERENCE_KEYS = new TextAttributesKey[]{REFERENCE};
  private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
  private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = new TextAttributesKey[]{BLOCK_COMMENT};
  private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
  private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
  private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[]{OPERATOR};
  private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
  private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
  private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new VietTemplateLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    if (tokenType.equals(VietTemplateTokenTypes.DIRECTIVE)) {
      return DIRECTIVE_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.REFERENCE)) {
      return REFERENCE_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.COMMENT)) {
      return COMMENT_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.BLOCK_COMMENT)) {
      return BLOCK_COMMENT_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.STRING_LITERAL)) {
      return STRING_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.NUMBER)) {
      return NUMBER_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.OPERATOR)) {
      return OPERATOR_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.IDENTIFIER)) {
      return IDENTIFIER_KEYS;
    }
    if (tokenType.equals(VietTemplateTokenTypes.BAD_CHARACTER)) {
      return BAD_CHAR_KEYS;
    }
    return EMPTY_KEYS;
  }
}
