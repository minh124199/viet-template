package io.github.minh124199.test.frontend.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = FrontendE2eApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "viet-template.assets.enabled=true"
    })
@AutoConfigureTestRestTemplate
class FrontendE2eServerTest {

  @Autowired private TestRestTemplate restTemplate;

  @AfterEach
  void resetState() {
    restTemplate.postForEntity("/api/test/reset", null, Map.class);
  }

  @Test
  @DisplayName("Health endpoint returns UP")
  void testHealthEndpoint() {
    ResponseEntity<Map> response = restTemplate.getForEntity("/health", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
  }

  @Test
  @DisplayName("Employee page renders SSR HTML semantic content and client data bridge")
  void testEmployeePageRendering() {
    ResponseEntity<String> response = restTemplate.getForEntity("/employees/42", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isNotNull();
    assertThat(response.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();

    String html = response.getBody();
    assertThat(html).isNotNull();
    assertThat(html).contains("data-testid=\"ssr-heading\">Staff Directory</h1>");
    assertThat(html).contains("data-testid=\"ssr-employee-name\">Employee: Jane Doe</h2>");
    assertThat(html).contains("data-testid=\"ssr-department\">Department: Engineering</p>");
    assertThat(html).contains("Jane Doe (Staff Engineer)");
    assertThat(html).contains("Followers: <span data-testid=\"ssr-followers\">3</span>");
    assertThat(html).contains("data-testid=\"ssr-fallback-form\"");
    // Check assets and client data were rendered
    assertThat(html).contains("<link rel=\"stylesheet\" href=\"/assets/employees-");
    assertThat(html).contains("<script type=\"application/json\" data-vt-client-data=\"employees-data\">");
    assertThat(html).contains("<script type=\"module\" src=\"/assets/employees-");
  }

  @Test
  @DisplayName("Follow REST endpoint increments counter deterministically")
  void testFollowRestEndpoint() {
    ResponseEntity<FollowResponse> response =
        restTemplate.postForEntity("/api/employees/42/follow", null, FollowResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().id()).isEqualTo("42");
    assertThat(response.getBody().followers()).isEqualTo(4);

    // Follow second time
    ResponseEntity<FollowResponse> response2 =
        restTemplate.postForEntity("/api/employees/42/follow", null, FollowResponse.class);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response2.getBody()).isNotNull();
    assertThat(response2.getBody().followers()).isEqualTo(5);
  }

  @Test
  @DisplayName("Fallback form POST endpoint redirects and increments follower counter")
  void testFallbackFormPostEndpoint() {
    ResponseEntity<String> response =
        restTemplate.postForEntity("/employees/42/follow", null, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("Followers: <span data-testid=\"ssr-followers\">4</span>");
  }
}
