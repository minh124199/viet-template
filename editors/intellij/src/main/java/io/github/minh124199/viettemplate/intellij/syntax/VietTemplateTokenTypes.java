package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Standard token types for lexical scanning of Viet Template files.
 */
public interface VietTemplateTokenTypes {

  IElementType DIRECTIVE = new VietTemplateTokenType("DIRECTIVE");
  IElementType REFERENCE = new VietTemplateTokenType("REFERENCE");
  IElementType COMMENT = new VietTemplateTokenType("COMMENT");
  IElementType BLOCK_COMMENT = new VietTemplateTokenType("BLOCK_COMMENT");
  IElementType STRING_LITERAL = new VietTemplateTokenType("STRING_LITERAL");
  IElementType NUMBER = new VietTemplateTokenType("NUMBER");
  IElementType OPERATOR = new VietTemplateTokenType("OPERATOR");
  IElementType IDENTIFIER = new VietTemplateTokenType("IDENTIFIER");
  IElementType TEXT = new VietTemplateTokenType("TEXT");
  IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
}
