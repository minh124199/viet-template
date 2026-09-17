package io.github.minh124199.viettemplate.spring.boot.autoconfigure.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

class VietTemplateRuntimeHintsTest {

  @Test
  void registersResourcePatternsAndProperties() {
    RuntimeHints hints = new RuntimeHints();
    VietTemplateRuntimeHints registrar = new VietTemplateRuntimeHints();

    registrar.registerHints(hints, getClass().getClassLoader());

    assertThat(hints.resources().resourcePatternHints())
        .anyMatch(
            hint ->
                hint.getIncludes().stream()
                    .anyMatch(
                        entry ->
                            entry.getPattern().equals("META-INF/viet-template/templates.idx")));
    assertThat(hints.resources().resourcePatternHints())
        .anyMatch(
            hint ->
                hint.getIncludes().stream()
                    .anyMatch(entry -> entry.getPattern().equals("META-INF/viet-template/*")));

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType().equals(TypeReference.of(VietTemplateProperties.class))
                    && hint.getMemberCategories()
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    && hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));
  }

  @Test
  void parsesSingleAndMultipleTemplateIndexEntries(@TempDir File tempDir) throws Exception {
    File metaInf = new File(tempDir, "META-INF/viet-template");
    assertThat(metaInf.mkdirs()).isTrue();
    File indexFile = new File(metaInf, "templates.idx");

    String indexContent =
        """
        # Header comment
        hello.vtl=io.github.minh124199.viettemplate.generated.T_hello_vtl_123
        user/card.vtl=io.github.minh124199.viettemplate.generated.T_user_card_vtl_456

        # Trailing comment
        """;
    try (FileOutputStream fos = new FileOutputStream(indexFile)) {
      fos.write(indexContent.getBytes(StandardCharsets.UTF_8));
    }

    URLClassLoader cl = new URLClassLoader(new URL[] {tempDir.toURI().toURL()}, null);
    Set<String> classNames = VietTemplateRuntimeHints.discoverGeneratedClassNames(cl);

    assertThat(classNames)
        .containsExactly(
            "io.github.minh124199.viettemplate.generated.T_hello_vtl_123",
            "io.github.minh124199.viettemplate.generated.T_user_card_vtl_456");

    RuntimeHints hints = new RuntimeHints();
    VietTemplateRuntimeHints registrar = new VietTemplateRuntimeHints();
    registrar.registerHints(hints, cl);

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType()
                    .equals(
                        TypeReference.of(
                            "io.github.minh124199.viettemplate.generated.T_hello_vtl_123")));
    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType()
                    .equals(
                        TypeReference.of(
                            "io.github.minh124199.viettemplate.generated.T_user_card_vtl_456")));
  }

  @Test
  void handlesEmptyAndMalformedIndexGracefully(@TempDir File tempDir) throws Exception {
    File metaInf = new File(tempDir, "META-INF/viet-template");
    assertThat(metaInf.mkdirs()).isTrue();
    File indexFile = new File(metaInf, "templates.idx");

    String malformedContent =
        """
        # Comment only
        invalid_line_without_equals
        =missing_key
        missing_value=

        valid.vtl=io.github.minh124199.viettemplate.generated.T_valid_vtl_789
        """;
    try (FileOutputStream fos = new FileOutputStream(indexFile)) {
      fos.write(malformedContent.getBytes(StandardCharsets.UTF_8));
    }

    URLClassLoader cl = new URLClassLoader(new URL[] {tempDir.toURI().toURL()}, null);
    Set<String> classNames = VietTemplateRuntimeHints.discoverGeneratedClassNames(cl);

    assertThat(classNames)
        .containsExactly("io.github.minh124199.viettemplate.generated.T_valid_vtl_789");
  }
}
