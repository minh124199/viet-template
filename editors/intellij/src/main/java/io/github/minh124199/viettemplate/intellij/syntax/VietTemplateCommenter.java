package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/**
 * Defines line (##) and block (#* *#) comment conventions for Viet Template.
 */
public class VietTemplateCommenter implements Commenter {

  @Nullable
  @Override
  public String getLineCommentPrefix() {
    return "##";
  }

  @Nullable
  @Override
  public String getBlockCommentPrefix() {
    return "#*";
  }

  @Nullable
  @Override
  public String getBlockCommentSuffix() {
    return "*#";
  }

  @Nullable
  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Nullable
  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }
}
