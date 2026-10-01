package io.github.minh124199.viettemplate.intellij.platform;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import io.github.minh124199.viettemplate.intellij.VietTemplateLanguage;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateFileType;
import io.github.minh124199.viettemplate.intellij.syntax.VietTemplateCommenter;
import io.github.minh124199.viettemplate.intellij.syntax.VietTemplateLexer;
import io.github.minh124199.viettemplate.intellij.syntax.VietTemplateSyntaxHighlighter;
import io.github.minh124199.viettemplate.intellij.syntax.VietTemplateSyntaxHighlighterFactory;
import io.github.minh124199.viettemplate.intellij.syntax.VietTemplateTokenTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform-level tests verifying file type attributes, syntax highlighter mapping, and commenter registration.
 */
class VietTemplatePlatformTest {

  @Test
  void shouldVerifyFileTypeAttributes() {
    VietTemplateFileType fileType = VietTemplateFileType.INSTANCE;

    assertThat(fileType.getName()).isEqualTo("Viet Template");
    assertThat(fileType.getDescription()).isEqualTo("Viet Template File (*.vtl, *.vm, *.vt)");
    assertThat(fileType.getDefaultExtension()).isEqualTo("vtl");
    assertThat(fileType.getIcon()).isNotNull();
    assertThat(fileType.getLanguage()).isEqualTo(VietTemplateLanguage.INSTANCE);
    assertThat(VietTemplateLanguage.INSTANCE.getID()).isEqualTo("Viet Template");
  }

  @Test
  void shouldVerifySyntaxHighlighterFactoryAndTokenHighlighting() {
    VietTemplateSyntaxHighlighterFactory factory = new VietTemplateSyntaxHighlighterFactory();
    SyntaxHighlighter highlighter = factory.getSyntaxHighlighter(null, null);

    assertThat(highlighter).isNotNull();
    assertThat(highlighter).isInstanceOf(VietTemplateSyntaxHighlighter.class);
    assertThat(highlighter.getHighlightingLexer()).isInstanceOf(VietTemplateLexer.class);

    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.DIRECTIVE)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.REFERENCE)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.COMMENT)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.BLOCK_COMMENT)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.STRING_LITERAL)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.NUMBER)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.OPERATOR)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.IDENTIFIER)).isNotEmpty();
    assertThat(highlighter.getTokenHighlights(VietTemplateTokenTypes.BAD_CHARACTER)).isNotEmpty();
  }

  @Test
  void shouldVerifyCommenterConventions() {
    VietTemplateCommenter commenter = new VietTemplateCommenter();

    assertThat(commenter.getLineCommentPrefix()).isEqualTo("##");
    assertThat(commenter.getBlockCommentPrefix()).isEqualTo("#*");
    assertThat(commenter.getBlockCommentSuffix()).isEqualTo("*#");
    assertThat(commenter.getCommentedBlockCommentPrefix()).isNull();
    assertThat(commenter.getCommentedBlockCommentSuffix()).isNull();
  }
}
