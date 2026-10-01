package io.github.minh124199.viettemplate.intellij.editor;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateFileType;
import io.github.minh124199.viettemplate.intellij.lsp.LspLocation;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspClient;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspServerManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GotoDeclarationHandler navigating to symbol definitions located by the Viet Template Language Server.
 */
public class VietTemplateGotoDeclarationHandler implements GotoDeclarationHandler {

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (sourceElement == null || editor == null) return null;

    PsiFile file = sourceElement.getContainingFile();
    if (file == null) return null;

    VirtualFile vf = file.getVirtualFile();
    if (vf == null) return null;

    if (!(file.getFileType() instanceof VietTemplateFileType) &&
        !vf.getName().endsWith(".vtl") &&
        !vf.getName().endsWith(".vm") &&
        !vf.getName().endsWith(".vt")) {
      return null;
    }

    Project project = file.getProject();
    VietTemplateLspServerManager manager = VietTemplateLspServerManager.getInstance(project);
    if (manager == null) return null;

    VietTemplateLspClient client = manager.getClient();
    if (client == null || !client.isRunning()) return null;

    Document document = editor.getDocument();
    int line = document.getLineNumber(offset);
    int character = offset - document.getLineStartOffset(line);

    String uri = new File(vf.getPath()).toURI().toString();
    try {
      List<LspLocation> locations = client.definition(uri, line, character).get(2, TimeUnit.SECONDS);
      if (locations == null || locations.isEmpty()) {
        return null;
      }

      List<PsiElement> targets = new ArrayList<>();
      VirtualFileManager vfm = VirtualFileManager.getInstance();
      PsiManager psiManager = PsiManager.getInstance(project);

      for (LspLocation loc : locations) {
        VirtualFile targetVf = null;
        try {
          Path targetPath = Paths.get(URI.create(loc.uri()));
          targetVf = vfm.findFileByNioPath(targetPath);
        } catch (Exception ignored) {
          targetVf = vfm.findFileByUrl(loc.uri());
        }

        if (targetVf != null) {
          PsiFile targetPsi = psiManager.findFile(targetVf);
          if (targetPsi != null) {
            Document targetDoc = PsiDocumentManager.getInstance(project).getDocument(targetPsi);
            if (targetDoc != null) {
              int tLine = Math.min(loc.range().start().line(), targetDoc.getLineCount() - 1);
              int tOffset = targetDoc.getLineStartOffset(tLine) + loc.range().start().character();
              tOffset = Math.min(tOffset, targetDoc.getTextLength());
              PsiElement targetElem = targetPsi.findElementAt(tOffset);
              targets.add(targetElem != null ? targetElem : targetPsi);
            } else {
              targets.add(targetPsi);
            }
          }
        }
      }

      return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
    } catch (Exception ignored) {}

    return null;
  }
}
