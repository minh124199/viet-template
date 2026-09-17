package io.github.minh124199.test.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = SecurityTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringMavenSecurityRealServerTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("Live HTTP server renders /public anonymously without credentials")
  void liveServerPublicEndpoint() {
    ResponseEntity<String> response = restTemplate.getForEntity("/public", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>Public Page</h1>")
        .contains("Browsing anonymously");
  }

  @Test
  @DisplayName("Live HTTP server intercepts unauthenticated access to protected endpoint")
  void liveServerUnauthenticatedAccess() {
    ResponseEntity<String> response = restTemplate.getForEntity("/admin", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Live HTTP server renders /dashboard for authenticated admin user with full authorities")
  void liveServerAuthenticatedAdmin() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("alice", "password")
            .getForEntity("/dashboard", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>Welcome, alice!</h1>")
        .contains("Admin Access Granted")
        .contains("User Access Granted")
        .contains("name=\"_csrf\"");
  }

  @Test
  @DisplayName("Live HTTP server renders /dashboard for authenticated user without admin privileges")
  void liveServerAuthenticatedUser() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("bob", "password")
            .getForEntity("/dashboard", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>Welcome, bob!</h1>")
        .doesNotContain("Admin Access Granted")
        .contains("User Access Granted")
        .contains("name=\"_csrf\"");
  }

  @Test
  @DisplayName("Live HTTP server enforces Spring Security HTTP authorization on /admin")
  void liveServerForbiddenAdminAccess() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("bob", "password")
            .getForEntity("/admin", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
