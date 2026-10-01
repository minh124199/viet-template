package io.github.minh124199.viettemplate.intellij.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateFileType;
import io.github.minh124199.viettemplate.intellij.lsp.LspDiagnostic;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspClient;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspServerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * ExternalAnnotator rendering diagnostics (syntax errors, semantic warnings) received from the
 * Viet Template Language Server onto IntelliJ editor ranges.
 */
public class VietTemplateLspAnnotator extends ExternalAnnotator<VietTemplateLspAnnotator.InitialInfo, VietTemplateLspAnnotator.AnnotationResult> {

  public static class InitialInfo {
    final Project project;
    final String uri;
    final String text;
    final int version;
    final Document document;

    public InitialInfo(Project project, String uri, String text, int version, Document document) {
      this.project = project;
      this.uri = uri;
      this.text = text;
      this.version = version;
      this.document = document;
    }
  }

  public static class AnnotationResult {
    final Document document;
    final List<LspDiagnostic> diagnostics;

    public AnnotationResult(Document document, List<LspDiagnostic> diagnostics) {
      this.document = document;
      this.diagnostics = diagnostics;
    }
  }

  @Nullable
  @Override
  public InitialInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    VirtualFile vf = file.getVirtualFile();
    if (vf == null) return null;

    if (!(file.getFileType() instanceof VietTemplateFileType) &&
        !vf.getName().endsWith(".vtl") &&
        !vf.getName().endsWith(".vm") &&
        !vf.getName().endsWith(".vt")) {
      return null;
    }

    Document document = editor.getDocument();
    String uri = new File(vf.getPath()).toURI().toString();
    int version = (int) Math.abs(document.getModificationStamp() % 100_000);
    return new InitialInfo(file.getProject(), uri, document.getText(), version, document);
  }

  @Nullable
  @Override
  public AnnotationResult doAnnotate(InitialInfo info) {
    if (info == null) return null;

    VietTemplateLspServerManager manager = VietTemplateLspServerManager.getInstance(info.project);
    if (manager == null) return null;

    VietTemplateLspClient client = manager.getClient();
    if (client == null || !client.isRunning()) return null;

    // Notify server of the current document text
    client.didOpen(info.uri, info.version, info.text);

    try {
      Thread.sleep(120);
    } catch (InterruptedException ignored) {}

    List<LspDiagnostic> diags = client.getDiagnostics(info.uri);
    return new AnnotationResult(info.document, diags != null ? diags : Collections.emptyList());
  }

  @Override
  public void apply(@NotNull PsiFile file, AnnotationResult result, @NotNull AnnotationHolder holder) {
    if (result == null || result.diagnostics.isEmpty()) return;

    Document document = result.document;
    int docLength = document.getTextLength();

    for (LspDiagnostic d : result.diagnostics) {
      int startLine = Math.max(0, Math.min(d.range().start().line(), document.getLineCount() - 1));
      int lineStartOffset = document.getLineStartOffset(startLine);
      int startOffset = Math.min(docLength, lineStartOffset + d.range().start().character());

      int endLine = Math.max(0, Math.min(d.range().end().line(), document.getLineCount() - 1));
      int endLineStartOffset = document.getLineStartOffset(endLine);
      int endOffset = Math.min(docLength, endLineStartOffset + d.range().end().character());

      if (startOffset > endOffset) {
        endOffset = startOffset;
      }
      if (startOffset == endOffset && startOffset < docLength) {
        endOffset = startOffset + 1;
      }

      TextRange range = new TextRange(startOffset, endOffset);
      HighlightSeverity severity = switch (d.severity()) {
        case LspDiagnostic.SEVERITY_ERROR -> HighlightSeverity.ERROR;
        case LspDiagnostic.SEVERITY_WARNING -> HighlightSeverity.WARNING;
        case LspDiagnostic.SEVERITY_INFORMATION -> HighlightSeverity.WEAK_WARNING;
        default -> HighlightSeverity.INFORMATION;
      };

      String message = d.code().isEmpty() ? d.message() : "[" + d.code() + "] " + d.message();
      holder.newAnnotation(severity, message)
          .range(range)
          .create();
    }
  }
}
