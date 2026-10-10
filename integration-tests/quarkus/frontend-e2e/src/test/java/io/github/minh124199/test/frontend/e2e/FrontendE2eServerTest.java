package io.github.minh124199.test.frontend.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FrontendE2eServerTest {

  @AfterEach
  void resetState() {
    given().when().post("/api/test/reset").then().statusCode(200);
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
  @DisplayName("Employee page renders SSR HTML semantic content and client data bridge")
  void testEmployeePageRendering() {
    given()
        .when()
        .get("/employees/42")
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("data-testid=\"ssr-heading\">Staff Directory</h1>"))
        .body(containsString("data-testid=\"ssr-employee-name\">Employee: Jane Doe</h2>"))
        .body(containsString("data-testid=\"ssr-department\">Department: Engineering</p>"))
        .body(containsString("Jane Doe (Staff Engineer)"))
        .body(containsString("Followers: <span data-testid=\"ssr-followers\">3</span>"))
        .body(containsString("data-testid=\"ssr-fallback-form\""))
        .body(containsString("<link rel=\"stylesheet\" href=\"/assets/employees-"))
        .body(containsString("<script type=\"application/json\" data-vt-client-data=\"employees-data\">"))
        .body(containsString("<script type=\"module\" src=\"/assets/employees-"));
  }

  @Test
  @DisplayName("Follow REST endpoint increments counter deterministically")
  void testFollowRestEndpoint() {
    given()
        .when()
        .post("/api/employees/42/follow")
        .then()
        .statusCode(200)
        .body("id", equalTo("42"))
        .body("followers", equalTo(4));

    given()
        .when()
        .post("/api/employees/42/follow")
        .then()
        .statusCode(200)
        .body("id", equalTo("42"))
        .body("followers", equalTo(5));
  }

  @Test
  @DisplayName("Fallback form POST endpoint redirects and increments follower counter")
  void testFallbackFormPostEndpoint() {
    given()
        .redirects()
        .follow(false)
        .when()
        .post("/employees/42/follow")
        .then()
        .statusCode(302)
        .header("Location", containsString("/employees/42"));

    given()
        .when()
        .get("/employees/42")
        .then()
        .statusCode(200)
        .body(containsString("Followers: <span data-testid=\"ssr-followers\">4</span>"));
  }

  @Test
  @DisplayName("Packaged Vite static assets are served by Quarkus with HTTP 200")
  void testStaticAssetServing() {
    String html =
        given()
            .when()
            .get("/employees/42")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    java.util.regex.Matcher jsMatcher =
        java.util.regex.Pattern.compile("/assets/employees-[a-zA-Z0-9_-]+\\.js").matcher(html);
    org.junit.jupiter.api.Assertions.assertTrue(
        jsMatcher.find(), "Asset script should be present in rendered HTML");
    given().when().get(jsMatcher.group()).then().statusCode(200);

    java.util.regex.Matcher cssMatcher =
        java.util.regex.Pattern.compile("/assets/employees-[a-zA-Z0-9_-]+\\.css").matcher(html);
    org.junit.jupiter.api.Assertions.assertTrue(
        cssMatcher.find(), "Asset stylesheet should be present in rendered HTML");
    given().when().get(cssMatcher.group()).then().statusCode(200);
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
