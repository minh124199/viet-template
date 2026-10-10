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
    classes = FrontendSecurityE2eApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"viet-template.assets.enabled=true"})
@AutoConfigureTestRestTemplate
class FrontendSecurityE2eServerTest {

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
  @DisplayName("Anonymous access to secure employee page redirects to login")
  void testAnonymousRedirect() {
    // Disable automatic redirect following to inspect 302 Found
    TestRestTemplate noRedirectTemplate =
        restTemplate.withBasicAuth("unused", "unused"); // Or default restTemplate without follow
    ResponseEntity<String> response = restTemplate.getForEntity("/secure/employees/42", String.class);
    // TestRestTemplate follows redirects by default, so it lands on /login
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("data-testid=\"login-form\"");
  }

  @Test
  @DisplayName("Login page renders CSRF token and credentials form")
  void testLoginPageRendering() {
    ResponseEntity<String> response = restTemplate.getForEntity("/login", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String html = response.getBody();
    assertThat(html).isNotNull();
    assertThat(html).contains("data-testid=\"login-form\"");
    assertThat(html).contains("data-testid=\"login-username\"");
    assertThat(html).contains("data-testid=\"login-password\"");
    assertThat(html).contains("data-testid=\"login-csrf\"");
  }
}
