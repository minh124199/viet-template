package io.github.minh124199.viettemplate.intellij.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Settings UI configurable for Viet Template language server configuration.
 */
public class VietTemplateConfigurable implements SearchableConfigurable {

  private JPanel mainPanel;
  private TextFieldWithBrowseButton javaHomeField;
  private TextFieldWithBrowseButton serverJarPathField;
  private ComboBox<String> traceComboBox;
  private JTextField vmArgsField;

  @NotNull
  @Override
  public String getId() {
    return "io.github.minh124199.viettemplate.intellij.settings.VietTemplateConfigurable";
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return "Viet Template";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    javaHomeField = new TextFieldWithBrowseButton();
    javaHomeField.addBrowseFolderListener(
        "Select Java Home",
        "Select JDK 21+ home directory containing bin/java",
        null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
    );

    serverJarPathField = new TextFieldWithBrowseButton();
    serverJarPathField.addBrowseFolderListener(
        "Select Server JAR",
        "Select custom viet-template-lsp.jar (leave empty to use bundled server)",
        null,
        FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
    );

    traceComboBox = new ComboBox<>(new String[]{"off", "messages", "verbose"});
    vmArgsField = new JTextField();

    mainPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Java Home (JDK 21+):", javaHomeField)
        .addLabeledComponent("LSP Server JAR path:", serverJarPathField)
        .addLabeledComponent("LSP Trace Level:", traceComboBox)
        .addLabeledComponent("JVM Arguments (e.g. -Xmx512m):", vmArgsField)
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();

    reset();
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    VietTemplateSettings settings = VietTemplateSettings.getInstance();
    if (settings == null) return false;

    boolean javaHomeModified = !Objects.equals(javaHomeField.getText().trim(), settings.getJavaHome());
    boolean jarPathModified = !Objects.equals(serverJarPathField.getText().trim(), settings.getServerJarPath());
    String selectedTrace = (String) traceComboBox.getSelectedItem();
    boolean traceModified = !Objects.equals(selectedTrace, settings.getTrace());
    String currentVmArgs = String.join(" ", settings.getVmArgs());
    boolean vmArgsModified = !Objects.equals(vmArgsField.getText().trim(), currentVmArgs);

    return javaHomeModified || jarPathModified || traceModified || vmArgsModified;
  }

  @Override
  public void apply() {
    VietTemplateSettings settings = VietTemplateSettings.getInstance();
    if (settings == null) return;

    settings.setJavaHome(javaHomeField.getText().trim());
    settings.setServerJarPath(serverJarPathField.getText().trim());
    String selectedTrace = (String) traceComboBox.getSelectedItem();
    settings.setTrace(selectedTrace != null ? selectedTrace : "off");

    String vmArgsText = vmArgsField.getText().trim();
    if (vmArgsText.isEmpty()) {
      settings.setVmArgs(List.of());
    } else {
      List<String> args = Arrays.stream(vmArgsText.split("\\s+"))
          .filter(s -> !s.isBlank())
          .collect(Collectors.toList());
      settings.setVmArgs(args);
    }
  }

  @Override
  public void reset() {
    VietTemplateSettings settings = VietTemplateSettings.getInstance();
    if (settings == null) return;

    javaHomeField.setText(settings.getJavaHome());
    serverJarPathField.setText(settings.getServerJarPath());
    traceComboBox.setSelectedItem(settings.getTrace());
    vmArgsField.setText(String.join(" ", settings.getVmArgs()));
  }

  @Override
  public void disposeUIResources() {
    mainPanel = null;
    javaHomeField = null;
    serverJarPathField = null;
    traceComboBox = null;
    vmArgsField = null;
  }
}
