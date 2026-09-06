package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SourceSpanTest {

  @Test
  void createsValidSpan() {
    SourceSpan span = SourceSpan.of(0, 10, 1, 1, 1, 11);
    assertThat(span.startOffset()).isEqualTo(0);
    assertThat(span.endOffset()).isEqualTo(10);
    assertThat(span.startLine()).isEqualTo(1);
    assertThat(span.startColumn()).isEqualTo(1);
    assertThat(span.endLine()).isEqualTo(1);
    assertThat(span.endColumn()).isEqualTo(11);
    assertThat(span.isKnown()).isTrue();
    assertThat(span.length()).isEqualTo(10);
  }

  @Test
  void supportsUnknownSpanConstant() {
    SourceSpan unknown = SourceSpan.UNKNOWN;
    assertThat(unknown.isKnown()).isFalse();
    assertThat(unknown.length()).isEqualTo(0);
  }

  @Test
  void rejectsNegativeOffsets() {
    assertThatThrownBy(() -> SourceSpan.of(-2, 5, 1, 1, 1, 6))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsStartOffsetGreaterThanEndOffset() {
    assertThatThrownBy(() -> SourceSpan.of(10, 5, 1, 1, 1, 6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startOffset (10) cannot exceed endOffset (5)");
  }

  @Test
  void rejectsEndPositionBeforeStartPosition() {
    assertThatThrownBy(() -> SourceSpan.of(0, 5, 2, 1, 1, 6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Start position [2:1] cannot follow end position [1:6]");
  }
}
