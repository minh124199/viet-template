package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class DevToolsContainmentTest {

  private static final List<String> PRODUCTION_MODULES =
      List.of(
          "viet-template-api",
          "viet-template-runtime",
          "viet-template-language-vtl",
          "viet-template-vtl-interpreter",
          "viet-template-spring",
          "viet-template-spring-security",
          "viet-template-spring-boot-autoconfigure",
          "viet-template-spring-boot-starter");

  private Path findRepoRoot() {
    Path current = Paths.get("").toAbsolutePath();
    while (current != null && !Files.exists(current.resolve("pom.xml"))) {
      current = current.getParent();
    }
    if (current != null && Files.exists(current.resolve("viet-template-api"))) {
      return current;
    }
    // If current is inside a submodule, walk up to parent
    while (current != null) {
      if (Files.exists(current.resolve("viet-template-spring-boot-starter"))
          && Files.exists(current.resolve("viet-template-vtl-interpreter"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not find repository root containing production modules");
  }

  @Test
  void springBootDevToolsNeverPresentInProductionModuleCompileOrRuntimeDependencies()
      throws Exception {
    Path root = findRepoRoot();
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();

    for (String moduleName : PRODUCTION_MODULES) {
      Path pomPath = root.resolve(moduleName).resolve("pom.xml");
      assertThat(pomPath).as("Module pom.xml must exist for %s", moduleName).exists();

      Document doc = db.parse(pomPath.toFile());
      doc.getDocumentElement().normalize();

      NodeList dependencyNodes = doc.getElementsByTagName("dependency");
      for (int i = 0; i < dependencyNodes.getLength(); i++) {
        Element dep = (Element) dependencyNodes.item(i);
        String groupId = getElementText(dep, "groupId");
        String artifactId = getElementText(dep, "artifactId");
        String scope = getElementText(dep, "scope");
        String optional = getElementText(dep, "optional");

        if ("org.springframework.boot".equals(groupId)
            && "spring-boot-devtools".equals(artifactId)) {
          // DevTools must NEVER be compile or runtime in any published module
          assertThat(scope)
              .as(
                  "spring-boot-devtools in %s must have test scope if present, but was: %s",
                  moduleName, scope)
              .isEqualTo("test");
          assertThat("true".equalsIgnoreCase(optional) || "test".equalsIgnoreCase(scope))
              .as(
                  "spring-boot-devtools in %s must never be an unconditional non-test dependency",
                  moduleName)
              .isTrue();
        }
      }

      // Also verify build.gradle.kts
      Path gradlePath = root.resolve(moduleName).resolve("build.gradle.kts");
      if (Files.exists(gradlePath)) {
        String gradleContent = Files.readString(gradlePath);
        assertThat(gradleContent)
            .as(
                "Module %s build.gradle.kts must not leak spring-boot-devtools into implementation"
                    + " or api",
                moduleName)
            .doesNotContain("implementation(\"org.springframework.boot:spring-boot-devtools")
            .doesNotContain("api(\"org.springframework.boot:spring-boot-devtools")
            .doesNotContain("implementation(\"org.springframework.boot:spring-boot-devtools-")
            .doesNotContain("api(\"org.springframework.boot:spring-boot-devtools-");
      }
    }
  }

  @Test
  void starterModuleContainsZeroDevtoolsTransitiveDependencies() throws Exception {
    Path root = findRepoRoot();
    Path starterPom = root.resolve("viet-template-spring-boot-starter").resolve("pom.xml");
    String content = Files.readString(starterPom);
    assertThat(content)
        .as(
            "viet-template-spring-boot-starter must not mention spring-boot-devtools anywhere in"
                + " its POM")
        .doesNotContain("spring-boot-devtools");
  }

  @Test
  void autoconfigurationClassLoadsWithoutDevToolsOnClasspath() {
    ClassLoader cl = VietTemplateAutoConfiguration.class.getClassLoader();
    assertThat(VietTemplateAutoConfiguration.class.getName())
        .isEqualTo(
            "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateAutoConfiguration");
    assertThat(VietTemplateProperties.class.getName())
        .isEqualTo(
            "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties");

    // Assert that RestartClassLoader is not required or present on this module's standard runtime
    // classpath
    boolean restartClassLoaderPresent = false;
    try {
      Class.forName(
          "org.springframework.boot.devtools.restart.classloader.RestartClassLoader", false, cl);
      restartClassLoaderPresent = true;
    } catch (ClassNotFoundException ignored) {
      // Expected: autoconfigure module does not pull devtools onto compile/runtime classpath
    }
    assertThat(restartClassLoaderPresent)
        .as("RestartClassLoader must not be on autoconfigure module compile/test runtime classpath")
        .isFalse();
  }

  private static String getElementText(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() > 0 && nodes.item(0).getFirstChild() != null) {
      return nodes.item(0).getFirstChild().getNodeValue().trim();
    }
    return "";
  }
}
