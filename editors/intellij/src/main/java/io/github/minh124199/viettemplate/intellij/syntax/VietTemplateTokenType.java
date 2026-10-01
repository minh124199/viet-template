package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.psi.tree.IElementType;
import io.github.minh124199.viettemplate.intellij.VietTemplateLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Token element type for the Viet Template language.
 */
public class VietTemplateTokenType extends IElementType {

  public VietTemplateTokenType(@NotNull @NonNls String debugName) {
    super(debugName, VietTemplateLanguage.INSTANCE);
  }
}
