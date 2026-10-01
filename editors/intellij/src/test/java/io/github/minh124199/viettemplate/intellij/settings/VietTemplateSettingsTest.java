package io.github.minh124199.viettemplate.intellij.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VietTemplateSettingsTest {

  @Test
  void shouldHaveSensibleDefaults() {
    VietTemplateSettings settings = new VietTemplateSettings();
    assertThat(settings.getJavaHome()).isEmpty();
    assertThat(settings.getServerJarPath()).isEmpty();
    assertThat(settings.getTrace()).isEqualTo("off");
    assertThat(settings.getVmArgs()).isEmpty();
  }

  @Test
  void shouldSafelyUpdateAndTrimProperties() {
    VietTemplateSettings settings = new VietTemplateSettings();

    settings.setJavaHome("  /opt/jdk-21  ");
    assertThat(settings.getJavaHome()).isEqualTo("/opt/jdk-21");

    settings.setServerJarPath("  /path/to/server.jar  ");
    assertThat(settings.getServerJarPath()).isEqualTo("/path/to/server.jar");

    settings.setTrace("  verbose  ");
    assertThat(settings.getTrace()).isEqualTo("verbose");

    settings.setVmArgs(List.of("-Xmx512m", "-Dcustom=val"));
    assertThat(settings.getVmArgs()).containsExactly("-Xmx512m", "-Dcustom=val");

    // Null safety
    settings.setJavaHome(null);
    assertThat(settings.getJavaHome()).isEmpty();

    settings.setServerJarPath(null);
    assertThat(settings.getServerJarPath()).isEmpty();

    settings.setTrace(null);
    assertThat(settings.getTrace()).isEqualTo("off");

    settings.setVmArgs(null);
    assertThat(settings.getVmArgs()).isEmpty();
  }

  @Test
  void shouldPreserveStateOnLoad() {
    VietTemplateSettings settings = new VietTemplateSettings();
    VietTemplateSettings.State state = new VietTemplateSettings.State();
    state.javaHome = "/custom/jdk";
    state.serverJarPath = "/custom/server.jar";
    state.trace = "messages";
    state.vmArgs = List.of("-Xms64m");

    settings.loadState(state);

    assertThat(settings.getState().javaHome).isEqualTo("/custom/jdk");
    assertThat(settings.getState().serverJarPath).isEqualTo("/custom/server.jar");
    assertThat(settings.getState().trace).isEqualTo("messages");
    assertThat(settings.getState().vmArgs).containsExactly("-Xms64m");
  }
}
