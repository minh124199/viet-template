package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UndefinedReferencePolicyTest {

  @Test
  @DisplayName("UndefinedReferencePolicy defines SILENT, WARN, and ERROR in order")
  void enumConstantsAreDefined() {
    assertThat(UndefinedReferencePolicy.values())
        .containsExactly(
            UndefinedReferencePolicy.SILENT,
            UndefinedReferencePolicy.WARN,
            UndefinedReferencePolicy.ERROR);
  }

  @Test
  @DisplayName("valueOf resolves valid constants and rejects invalid or null")
  void valueOfResolvesCorrectly() {
    assertThat(UndefinedReferencePolicy.valueOf("SILENT"))
        .isEqualTo(UndefinedReferencePolicy.SILENT);
    assertThat(UndefinedReferencePolicy.valueOf("WARN")).isEqualTo(UndefinedReferencePolicy.WARN);
    assertThat(UndefinedReferencePolicy.valueOf("ERROR")).isEqualTo(UndefinedReferencePolicy.ERROR);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> UndefinedReferencePolicy.valueOf("UNKNOWN"));
    assertThatNullPointerException().isThrownBy(() -> UndefinedReferencePolicy.valueOf(null));
  }
}
