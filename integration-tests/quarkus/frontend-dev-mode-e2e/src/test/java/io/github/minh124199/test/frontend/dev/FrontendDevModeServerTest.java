package io.github.minh124199.test.frontend.dev;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FrontendDevModeServerTest {

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
  @DisplayName("Restart generation endpoint returns metadata and Java version")
  void testRestartGenerationEndpoint() {
    given()
        .when()
        .get("/__test/restart-generation")
        .then()
        .statusCode(200)
        .body("javaVersion", equalTo("JAVA-A"))
        .body(containsString("classLoaderId"));
  }

  @Test
  @DisplayName("Employee page renders SSR HTML with development mode assets")
  void testEmployeePageRendering() {
    String html =
        given()
            .when()
            .get("/employees/42")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("data-testid=\"ssr-heading\">Staff Directory</h1>"))
            .body(containsString("data-testid=\"vtl-version\">VTL-A</span>"))
            .body(containsString("data-testid=\"java-version\">JAVA-A</span>"))
            .body(containsString("<script type=\"module\" src=\"http://127.0.0.1:5173/@vite/client\"></script>"))
            .body(containsString("<script type=\"module\" src=\"http://127.0.0.1:5173/src/pages/employees/index.ts\"></script>"))
            .extract()
            .asString();

    int firstIdx = html.indexOf("/@vite/client");
    int lastIdx = html.lastIndexOf("/@vite/client");
    org.junit.jupiter.api.Assertions.assertTrue(firstIdx >= 0);
    org.junit.jupiter.api.Assertions.assertEquals(firstIdx, lastIdx, "Expected @vite/client to be emitted exactly once");
  }
}
