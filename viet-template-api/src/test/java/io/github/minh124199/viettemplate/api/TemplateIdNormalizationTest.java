package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateIdNormalizationTest {

  @Test
  void normalizesForwardAndBackslashes() {
    TemplateId id = TemplateId.normalize("views\\users/list.vm");
    assertThat(id.value()).isEqualTo("views/users/list.vm");
  }

  @Test
  void stripsLeadingAndDuplicateSlashes() {
    TemplateId id = TemplateId.normalize("///views//users///list.vm");
    assertThat(id.value()).isEqualTo("views/users/list.vm");
  }

  @Test
  void resolvesCurrentDirectoryDots() {
    TemplateId id = TemplateId.normalize("./views/./users/./list.vm");
    assertThat(id.value()).isEqualTo("views/users/list.vm");
  }

  @Test
  void resolvesSafeRelativeParentDots() {
    TemplateId id = TemplateId.normalize("views/common/../users/list.vm");
    assertThat(id.value()).isEqualTo("views/users/list.vm");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"../views/list.vm", "views/../../list.vm", "/../escape.vm", "a/b/../../../c.vm"})
  void rejectsEscapingRoot(String invalidTraversal) {
    assertThatThrownBy(() -> TemplateId.normalize(invalidTraversal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traversal above root is forbidden");
  }

  @Test
  void rejectsNullByte() {
    assertThatThrownBy(() -> TemplateId.normalize("views/list.vm\0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null bytes");
  }

  @Test
  void testsIsTraversalSafe() {
    assertThat(TemplateId.isTraversalSafe("users/list.vm")).isTrue();
    assertThat(TemplateId.isTraversalSafe("/users/./list.vm")).isTrue();
    assertThat(TemplateId.isTraversalSafe("views/common/../users/list.vm")).isTrue();

    assertThat(TemplateId.isTraversalSafe("../escape.vm")).isFalse();
    assertThat(TemplateId.isTraversalSafe("views/../../secret.vm")).isFalse();
    assertThat(TemplateId.isTraversalSafe(null)).isFalse();
    assertThat(TemplateId.isTraversalSafe("")).isFalse();
    assertThat(TemplateId.isTraversalSafe("bad\0name")).isFalse();
  }
}
