package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeCheckingModeTest {

  @Test
  @DisplayName("Default state and isEnabled checks")
  void isEnabledState() {
    assertThat(TypeCheckingMode.OFF.isEnabled()).isFalse();
    assertThat(TypeCheckingMode.WARN.isEnabled()).isTrue();
    assertThat(TypeCheckingMode.ERROR.isEnabled()).isTrue();
  }
}
