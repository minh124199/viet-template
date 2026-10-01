package io.github.minh124199.viettemplate.intellij.file;

import com.intellij.openapi.fileTypes.LanguageFileType;
import io.github.minh124199.viettemplate.intellij.VietTemplateLanguage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * File type definition for Viet Template files (*.vtl, *.vm, *.vt).
 */
public final class VietTemplateFileType extends LanguageFileType {

  public static final VietTemplateFileType INSTANCE = new VietTemplateFileType();

  private VietTemplateFileType() {
    super(VietTemplateLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "Viet Template";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Viet Template File (*.vtl, *.vm, *.vt)";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "vtl";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return VietTemplateIcons.FILE;
  }
}
