package io.github.minh124199.viettemplate.intellij;

import com.intellij.lang.Language;

/**
 * Definition of the Viet Template language within IntelliJ IDEA.
 */
public final class VietTemplateLanguage extends Language {

  public static final VietTemplateLanguage INSTANCE = new VietTemplateLanguage();

  private VietTemplateLanguage() {
    super("Viet Template");
  }
}
