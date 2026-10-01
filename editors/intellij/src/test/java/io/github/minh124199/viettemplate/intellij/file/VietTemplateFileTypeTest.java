package io.github.minh124199.viettemplate.intellij.file;

import io.github.minh124199.viettemplate.intellij.VietTemplateLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietTemplateFileTypeTest {

  @Test
  void shouldExposeCorrectFileTypeAttributes() {
    VietTemplateFileType fileType = VietTemplateFileType.INSTANCE;

    assertThat(fileType.getName()).isEqualTo("Viet Template");
    assertThat(fileType.getDescription()).isEqualTo("Viet Template File (*.vtl, *.vm, *.vt)");
    assertThat(fileType.getDefaultExtension()).isEqualTo("vtl");
    assertThat(fileType.getIcon()).isNotNull();
    assertThat(fileType.getLanguage()).isEqualTo(VietTemplateLanguage.INSTANCE);
  }

  @Test
  void shouldExposeCorrectLanguageAttributes() {
    VietTemplateLanguage language = VietTemplateLanguage.INSTANCE;
    assertThat(language.getID()).isEqualTo("Viet Template");
  }
}
