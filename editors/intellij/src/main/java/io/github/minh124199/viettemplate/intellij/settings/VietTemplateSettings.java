package io.github.minh124199.viettemplate.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persistent application-level settings for the Viet Template plugin.
 */
@State(
    name = "VietTemplateSettings",
    storages = @Storage("viet-template.xml")
)
public class VietTemplateSettings implements PersistentStateComponent<VietTemplateSettings.State> {

  public static class State {
    public String javaHome = "";
    public String serverJarPath = "";
    public String trace = "off";
    public List<String> vmArgs = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      State state = (State) o;
      return Objects.equals(javaHome, state.javaHome) &&
             Objects.equals(serverJarPath, state.serverJarPath) &&
             Objects.equals(trace, state.trace) &&
             Objects.equals(vmArgs, state.vmArgs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(javaHome, serverJarPath, trace, vmArgs);
    }
  }

  private State myState = new State();

  public static VietTemplateSettings getInstance() {
    return ApplicationManager.getApplication().getService(VietTemplateSettings.class);
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.myState = state;
  }

  public String getJavaHome() {
    return myState.javaHome;
  }

  public void setJavaHome(String javaHome) {
    myState.javaHome = javaHome != null ? javaHome.trim() : "";
  }

  public String getServerJarPath() {
    return myState.serverJarPath;
  }

  public void setServerJarPath(String serverJarPath) {
    myState.serverJarPath = serverJarPath != null ? serverJarPath.trim() : "";
  }

  public String getTrace() {
    return myState.trace;
  }

  public void setTrace(String trace) {
    myState.trace = trace != null ? trace.trim() : "off";
  }

  public List<String> getVmArgs() {
    return myState.vmArgs;
  }

  public void setVmArgs(List<String> vmArgs) {
    myState.vmArgs = vmArgs != null ? new ArrayList<>(vmArgs) : new ArrayList<>();
  }
}
