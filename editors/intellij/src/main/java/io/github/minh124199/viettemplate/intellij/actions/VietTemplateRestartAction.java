package io.github.minh124199.viettemplate.intellij.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.minh124199.viettemplate.intellij.file.VietTemplateIcons;
import io.github.minh124199.viettemplate.intellij.lsp.VietTemplateLspServerManager;
import org.jetbrains.annotations.NotNull;

/**
 * Action that terminates and restarts the background Viet Template Language Server process.
 */
public class VietTemplateRestartAction extends AnAction {

  public VietTemplateRestartAction() {
    super("Restart Viet Template Language Server", "Restarts the Viet Template Language Server process", VietTemplateIcons.FILE);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    VietTemplateLspServerManager manager = VietTemplateLspServerManager.getInstance(project);
    if (manager != null) {
      manager.restart();
      Messages.showInfoMessage(project, "Viet Template Language Server has been restarted.", "Viet Template");
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
