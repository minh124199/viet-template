package io.github.minh124199.viettemplate.intellij.editor;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateFileType;
import io.github.minh124199.viettemplate.intellij.lsp.LspCompletionItem;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspClient;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspServerManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CompletionContributor delegating code completion suggestions to the Viet Template Language Server.
 */
public class VietTemplateCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    VirtualFile vf = file.getVirtualFile();
    if (vf == null) return;

    if (!(file.getFileType() instanceof VietTemplateFileType) &&
        !vf.getName().endsWith(".vtl") &&
        !vf.getName().endsWith(".vm") &&
        !vf.getName().endsWith(".vt")) {
      return;
    }

    Project project = file.getProject();
    VietTemplateLspServerManager manager = VietTemplateLspServerManager.getInstance(project);
    if (manager == null) return;

    VietTemplateLspClient client = manager.getClient();
    if (client == null || !client.isRunning()) return;

    Document document = parameters.getEditor().getDocument();
    int offset = parameters.getOffset();
    int line = document.getLineNumber(offset);
    int character = offset - document.getLineStartOffset(line);

    String uri = new File(vf.getPath()).toURI().toString();
    int version = (int) Math.abs(document.getModificationStamp() % 100_000);

    // Sync latest text before querying completion
    client.didChange(uri, version, document.getText());

    try {
      List<LspCompletionItem> items = client.completion(uri, line, character).get(2, TimeUnit.SECONDS);
      for (LspCompletionItem ci : items) {
        LookupElementBuilder element = LookupElementBuilder.create(ci.label())
            .withTypeText(ci.detail())
            .withTailText(ci.documentation().isBlank() ? "" : " - " + ci.documentation(), true);
        result.addElement(element);
      }
    } catch (Exception ignored) {}
  }
}
