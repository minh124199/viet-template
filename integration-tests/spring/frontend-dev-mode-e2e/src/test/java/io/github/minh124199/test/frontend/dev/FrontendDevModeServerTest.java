package io.github.minh124199.test.frontend.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = FrontendDevModeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "viet-template.assets.enabled=true",
      "viet-template.assets.mode=development",
      "viet-template.assets.dev-server=http://127.0.0.1:5173"
    })
@AutoConfigureTestRestTemplate
class FrontendDevModeServerTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void healthEndpointReturnsUp() {
    ResponseEntity<Map> response = restTemplate.getForEntity("/health", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
  }

  @Test
  void restartGenerationReturnsMeta() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity("/__test/restart-generation", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsKey("classLoaderId");
    assertThat(response.getBody()).containsEntry("javaVersion", "JAVA-A");
  }

  @Test
  void employeePageRendersDevelopmentAssetTags() {
    ResponseEntity<String> response = restTemplate.getForEntity("/employees/42", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String html = response.getBody();
    assertThat(html).isNotNull();
    assertThat(html).contains("data-testid=\"ssr-heading\">Staff Directory</h1>");
    assertThat(html).contains("data-testid=\"vtl-version\">VTL-A</span>");
    assertThat(html).contains("data-testid=\"java-version\">JAVA-A</span>");
    // Assert development mode emits @vite/client and direct entry script
    assertThat(html).contains("<script type=\"module\" src=\"http://127.0.0.1:5173/@vite/client\"></script>");
    assertThat(html).contains("<script type=\"module\" src=\"http://127.0.0.1:5173/src/pages/employees/index.ts\"></script>");
  }
}
