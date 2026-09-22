package io.github.minh124199.viettemplate.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("removal")
public class VietTemplateExtensionTest {

  @RegisterExtension
  static final QuarkusUnitTest unitTest =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addAsResource(
                          new StringAsset("Hello $name! Welcome to $framework."),
                          "templates/hello.vtl"));

  @Inject TemplateEngine engine;

  @Inject VietTemplateRenderer renderer;

  @Test
  public void testCdiInjection() {
    assertThat(engine).isNotNull();
    assertThat(renderer).isNotNull();
  }

  @Test
  public void testTemplateAotCompilationAndRendering() {
    Map<String, Object> model = Map.of("name", "Quarkus User", "framework", "Viet Template");

    // Verify template was precompiled and can be retrieved from engine
    Template template = engine.get("hello.vtl");
    assertThat(template).isNotNull();

    // Verify rendering via VietTemplateRenderer
    String outputWithSuffix = renderer.render("hello.vtl", model);
    assertThat(outputWithSuffix).isEqualTo("Hello Quarkus User! Welcome to Viet Template.");

    String outputWithoutSuffix = renderer.render("hello", model);
    assertThat(outputWithoutSuffix).isEqualTo("Hello Quarkus User! Welcome to Viet Template.");

    String outputById = renderer.render(TemplateId.of("hello.vtl"), model);
    assertThat(outputById).isEqualTo("Hello Quarkus User! Welcome to Viet Template.");

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    renderer.render("hello", model, outputStream);
    assertThat(outputStream.toString()).isEqualTo("Hello Quarkus User! Welcome to Viet Template.");
  }
}
