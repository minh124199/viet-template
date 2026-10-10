package io.github.minh124199.test.frontend.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FrontendSecurityE2eServerTest {

  @AfterEach
  void resetState() {
    given().contentType("application/json").when().post("/api/test/reset").then().statusCode(200);
  }

  @Test
  @DisplayName("Health endpoint returns UP")
  void testHealthEndpoint() {
    given()
        .when()
        .get("/health")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"));
  }

  @Test
  @DisplayName("Anonymous access to secure employee page redirects to login")
  void testAnonymousRedirect() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/secure/employees/42")
        .then()
        .statusCode(302)
        .header("Location", containsString("/login"));
  }

  @Test
  @DisplayName("Login page renders CSRF token and credentials form")
  void testLoginPageRendering() {
    given()
        .when()
        .get("/login")
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("data-testid=\"login-form\""))
        .body(containsString("data-testid=\"login-username\""))
        .body(containsString("data-testid=\"login-password\""))
        .body(containsString("data-testid=\"login-csrf\""));
  }

  @Test
  @DisplayName("Packaged Vite manifest is present on classpath")
  void testManifestResourceExists() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    org.junit.jupiter.api.Assertions.assertNotNull(
        cl.getResource("META-INF/resources/.vite/manifest.json"),
        "Packaged Vite manifest must exist on classpath under META-INF/resources/.vite/manifest.json");
  }
}
