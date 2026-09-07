package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeNaming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BytecodeNamingTest {

  @Test
  @DisplayName("Generates deterministic class name from TemplateId and fingerprint")
  void testClassNameDeterministic() {
    TemplateId id = TemplateId.of("user/profile.vtl");
    String fingerprint = "a1b2c3d4e5f6";

    String name1 = BytecodeNaming.className(id, fingerprint);
    String name2 = BytecodeNaming.className(id, fingerprint);

    assertThat(name1).isEqualTo(name2);
    assertThat(name1).startsWith("T_user_profile_vtl_");
    assertThat(name1).contains("a1b2c3d4e5f6");
  }

  @Test
  @DisplayName("Sanitizes invalid Java identifier characters in template ID")
  void testSanitizesSpecialCharacters() {
    TemplateId id = TemplateId.of("templates-2026/hello@world#1.vtl");
    String name = BytecodeNaming.className(id, "1234567890ab");

    assertThat(name).matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    assertThat(name).doesNotContain("-", "@", "#", "/");
  }

  @Test
  @DisplayName("Prefixes class name when sanitized ID starts with digit")
  void testNumericPrefix() {
    TemplateId id = TemplateId.of("123template");
    String name = BytecodeNaming.className(id, "abcdef123456");

    assertThat(name).matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    assertThat(name).startsWith("T_");
  }

  @Test
  @DisplayName("Derives fully qualified class name with default or custom package")
  void testFullyQualifiedClassName() {
    TemplateId id = TemplateId.of("order.vtl");
    String fqcnDefault = BytecodeNaming.fullyQualifiedClassName(null, id, "feedbeef1234");
    assertThat(fqcnDefault).startsWith("io.github.minh124199.viettemplate.generated.T_order_vtl_");

    String fqcnCustom =
        BytecodeNaming.fullyQualifiedClassName("com.example.views", id, "feedbeef1234");
    assertThat(fqcnCustom).startsWith("com.example.views.T_order_vtl_");
  }

  @Test
  @DisplayName("Derives valid chunk helper method names")
  void testChunkMethodName() {
    String chunk1 = BytecodeNaming.chunkMethodName("chunk_1");
    assertThat(chunk1).isEqualTo("renderChunk_chunk_1");

    String chunkSpecial = BytecodeNaming.chunkMethodName("chunk-macro.helper");
    assertThat(chunkSpecial).matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    assertThat(chunkSpecial).isEqualTo("renderChunk_chunk_macro_helper");
  }

  @Test
  @DisplayName("SHA-256 hex is deterministic and lowercase")
  void testSha256Hex() {
    String hash1 = BytecodeNaming.sha256Hex("hello world");
    String hash2 = BytecodeNaming.sha256Hex("hello world");
    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
  }

  @Test
  @DisplayName("Rejects null inputs")
  void testNullGuards() {
    assertThatThrownBy(() -> BytecodeNaming.className(null, "hash"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> BytecodeNaming.chunkMethodName(null))
        .isInstanceOf(NullPointerException.class);
  }
}
