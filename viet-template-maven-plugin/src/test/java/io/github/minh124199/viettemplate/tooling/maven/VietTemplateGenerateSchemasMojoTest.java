package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateGenerateSchemasMojoTest {

  @Test
  @DisplayName("Generates canonical contract schemas (*.vt-schema.json) from companion contracts")
  void testGenerateSchemas(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path schemaOutputDir = tempDir.resolve("target/generated-resources/viet-template/schemas");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("order-view.vtl"), "Order #$orderId for $customer", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("order-view.vtl.contract"),
        "orderId=long\ncustomer=String\n",
        StandardCharsets.UTF_8);

    VietTemplateGenerateSchemasMojo mojo = new VietTemplateGenerateSchemasMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setSchemaOutputDirectory(schemaOutputDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    Path schemaFile = schemaOutputDir.resolve("order-view.vt-schema.json");
    assertThat(schemaFile).isRegularFile();
    String json = Files.readString(schemaFile, StandardCharsets.UTF_8);
    assertThat(json).contains("\"format\": \"viet-template-contract-schema/1\"");
    assertThat(json).contains("\"schemaVersion\": 1");
    assertThat(json).contains("\"orderId\"");
    assertThat(json).contains("\"customer\"");
  }

  @Test
  @DisplayName("Skips execution when skip flag is set")
  void testSkipFlag(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path schemaOutputDir = tempDir.resolve("target/generated-resources/viet-template/schemas");

    VietTemplateGenerateSchemasMojo mojo = new VietTemplateGenerateSchemasMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setSchemaOutputDirectory(schemaOutputDir.toFile());
    mojo.setSkip(true);

    assertThatCode(mojo::execute).doesNotThrowAnyException();
    assertThat(schemaOutputDir).doesNotExist();
  }
}
