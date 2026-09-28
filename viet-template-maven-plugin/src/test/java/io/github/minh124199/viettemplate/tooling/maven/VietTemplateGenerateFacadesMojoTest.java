package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateGenerateFacadesMojoTest {

  @Test
  @DisplayName("Generates typed Java facades from companion contracts during generate-sources")
  void testGenerateTypedFacades(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path genSourcesDir = tempDir.resolve("target/generated-sources/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("order-view.vtl"), "Order #$orderId for $customer", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("order-view.vtl.contract"),
        "orderId=long\ncustomer=String\n",
        StandardCharsets.UTF_8);

    VietTemplateGenerateFacadesMojo mojo = new VietTemplateGenerateFacadesMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setGeneratedSourcesDirectory(genSourcesDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    Path viewFile =
        genSourcesDir.resolve("io/github/minh124199/viettemplate/generated/OrderViewView.java");
    assertThat(viewFile).isRegularFile();
    String viewContent = Files.readString(viewFile, StandardCharsets.UTF_8);
    assertThat(viewContent).contains("public final class OrderViewView");
    assertThat(viewContent)
        .contains(
            "public static void render(TemplateOutput output, long orderId, java.lang.String"
                + " customer)");
    assertThat(viewContent)
        .contains("public static String render(long orderId, java.lang.String customer)");
  }

  @Test
  @DisplayName("Skips execution when skip flag is set")
  void testSkipFlag(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path genSourcesDir = tempDir.resolve("target/generated-sources/viet-template");

    VietTemplateGenerateFacadesMojo mojo = new VietTemplateGenerateFacadesMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setGeneratedSourcesDirectory(genSourcesDir.toFile());
    mojo.setSkip(true);

    assertThatCode(mojo::execute).doesNotThrowAnyException();
    assertThat(genSourcesDir).doesNotExist();
  }
}
