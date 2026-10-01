package io.github.minh124199.viettemplate.intellij.editor;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateFileType;
import io.github.minh124199.viettemplate.intellij.lsp.LspHoverResult;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspClient;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspServerManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * DocumentationProvider supplying hover documentation from the Viet Template Language Server.
 */
public class VietTemplateDocumentationProvider extends AbstractDocumentationProvider {

  @Nullable
  @Nls
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    PsiElement target = (originalElement != null) ? originalElement : element;
    if (target == null) return null;

    PsiFile file = target.getContainingFile();
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

    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;

    int offset = target.getTextOffset();
    int line = document.getLineNumber(offset);
    int character = offset - document.getLineStartOffset(line);

    String uri = new File(vf.getPath()).toURI().toString();
    try {
      LspHoverResult hover = client.hover(uri, line, character).get(2, TimeUnit.SECONDS);
      if (hover != null && !hover.contents().isBlank()) {
        return formatHoverHtml(hover.contents());
      }
    } catch (Exception ignored) {}

    return null;
  }

  private String formatHoverHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) return null;

    // Convert basic markdown formatting to HTML for IntelliJ documentation popup
    String html = markdown
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");

    // Replace ```blocks
    if (html.contains("```")) {
      html = html.replaceAll("```[a-zA-Z0-9_-]*\\n([\\s\\S]*?)```", "<pre><code>$1</code></pre>");
    }

    // Replace `inline` code
    html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

    // Convert newlines to <br> outside <pre>
    String[] parts = html.split("(?=<pre>)|(?<=</pre>)");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.startsWith("<pre>")) {
        sb.append(part);
      } else {
        sb.append(part.replace("\n", "<br/>"));
      }
    }

    return "<html><body>" + sb + "</body></html>";
  }
}
