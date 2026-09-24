package io.github.minh124199.test.quarkus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HelloResourceTest {

  @Test
  @DisplayName("Standard AOT template rendering via GET /hello")
  public void testHelloEndpoint() {
    given()
        .when()
        .get("/hello?name=QuarkusUser")
        .then()
        .statusCode(200)
        .body(containsString("<h1>Hello, QuarkusUser!</h1>"));
  }

  @Test
  @DisplayName("Multiple suffix template resolution via GET /hello/page (.html.vtl)")
  public void testPageEndpoint() {
    given()
        .when()
        .get("/hello/page?name=QuarkusPage")
        .then()
        .statusCode(200)
        .body(containsString("<p>Page Content: QuarkusPage</p>"));
  }

  @Test
  @DisplayName("Streaming output without container stream closure via GET /hello/stream")
  public void testStreamEndpoint() {
    given()
        .when()
        .get("/hello/stream?name=QuarkusStream")
        .then()
        .statusCode(200)
        .body(containsString("<h1>Hello, QuarkusStream!</h1>"))
        .body(containsString("<!-- streamed footer -->"));
  }

  @Test
  @DisplayName("Undefined reference handling under SILENT policy via GET /hello/undefined")
  public void testUndefinedEndpoint() {
    given()
        .when()
        .get("/hello/undefined")
        .then()
        .statusCode(200)
        .body(containsString("<span>Normal: </span>"));
  }
}
