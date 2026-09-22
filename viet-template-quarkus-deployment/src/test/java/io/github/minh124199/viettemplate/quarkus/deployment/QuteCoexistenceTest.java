package io.github.minh124199.viettemplate.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("removal")
public class QuteCoexistenceTest {

  @RegisterExtension
  static final QuarkusUnitTest unitTest =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addAsResource(
                          new StringAsset("VietTemplate: Hello $name from Viet Template!"),
                          "templates/viet.vtl")
                      .addAsResource(
                          new StringAsset("Qute: Hello {name} from Qute!"),
                          "templates/hello-qute.txt"));

  @Inject io.quarkus.qute.Engine quteEngine;

  @Inject io.github.minh124199.viettemplate.api.TemplateEngine vietEngine;

  @Inject VietTemplateRenderer vietRenderer;

  @Test
  public void testBothEnginesInjectedWithoutAmbiguity() {
    assertThat(quteEngine).isNotNull();
    assertThat(vietEngine).isNotNull();
    assertThat(vietRenderer).isNotNull();
  }

  @Test
  public void testConcurrentRenderingSideBySide() {
    // Render using Viet Template Renderer
    String vietOutput = vietRenderer.render("viet.vtl", Map.of("name", "Quarkus Developer"));
    assertThat(vietOutput).isEqualTo("VietTemplate: Hello Quarkus Developer from Viet Template!");

    // Render using Qute Engine
    String quteOutput =
        quteEngine.getTemplate("hello-qute.txt").data("name", "Quarkus Developer").render();
    assertThat(quteOutput).isEqualTo("Qute: Hello Quarkus Developer from Qute!");

    // Also verify Qute parsed template execution
    String dynamicQute = quteEngine.parse("Dynamic {msg}").data("msg", "Success").render();
    assertThat(dynamicQute).isEqualTo("Dynamic Success");
  }
}
