package io.github.minh124199.test.quarkus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
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

  @Test
  @DisplayName("Unauthenticated request to secured endpoint returns HTTP 401")
  public void testSecuredUnauthenticated() {
    given()
        .when()
        .get("/hello/secured")
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("Authenticated user access to secured endpoint returns identity presentation")
  public void testSecuredAuthenticatedUser() {
    given()
        .header("X-Test-User", "user")
        .when()
        .get("/hello/secured")
        .then()
        .statusCode(200)
        .body(containsString("authenticated=true"))
        .body(containsString("name=user"))
        .body(containsString("admin=false"));
  }

  @Test
  @DisplayName("Authenticated admin access to secured endpoint returns admin presentation")
  public void testSecuredAuthenticatedAdmin() {
    given()
        .header("X-Test-User", "admin")
        .when()
        .get("/hello/secured")
        .then()
        .statusCode(200)
        .body(containsString("authenticated=true"))
        .body(containsString("name=admin"))
        .body(containsString("admin=true"));
  }

  @Test
  @DisplayName("User without ADMIN role is forbidden from accessing /hello/admin")
  public void testAdminForbiddenForUser() {
    given()
        .header("X-Test-User", "user")
        .when()
        .get("/hello/admin")
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("Admin user is allowed to access /hello/admin")
  public void testAdminAllowedForAdmin() {
    given()
        .header("X-Test-User", "admin")
        .when()
        .get("/hello/admin")
        .then()
        .statusCode(200)
        .body(containsString("admin=true"));
  }

  @Test
  @DisplayName("CSRF GET endpoint provides token and metadata")
  public void testCsrfGetEndpoint() {
    given()
        .when()
        .get("/hello/csrf")
        .then()
        .statusCode(200)
        .body(containsString("available=true"))
        .body(containsString("parameter=csrf-token"))
        .body(containsString("header=X-CSRF-TOKEN"))
        .body(containsString("hasToken=true"))
        .cookie("csrf-token");
  }

  @Test
  @DisplayName("CSRF POST submission succeeds with valid token and cookie")
  public void testCsrfPostSubmitSuccess() {
    Response getResponse =
        given()
            .when()
            .get("/hello/csrf")
            .then()
            .statusCode(200)
            .cookie("csrf-token")
            .extract()
            .response();

    String token = getResponse.getCookie("csrf-token");

    given()
        .contentType("application/x-www-form-urlencoded")
        .cookie("csrf-token", token)
        .formParam("csrf-token", token)
        .formParam("message", "valid submission")
        .when()
        .post("/hello/csrf-submit")
        .then()
        .statusCode(200)
        .body(containsString("Received: valid submission"));
  }

  @Test
  @DisplayName("CSRF POST submission rejected with HTTP 400 when token is omitted")
  public void testCsrfPostSubmitWithoutToken() {
    given()
        .contentType("application/x-www-form-urlencoded")
        .formParam("message", "unauthorized attempt")
        .when()
        .post("/hello/csrf-submit")
        .then()
        .statusCode(400);
  }

  @Test
  @DisplayName("Native client-data serialization with SimpleJsonSerializer for POJO and record")
  public void testClientDataSerialization() {
    given()
        .when()
        .get("/hello/client-data")
        .then()
        .statusCode(200)
        .body(containsString("\"username\":\"native-user\""))
        .body(containsString("\"roleLevel\":99"))
        .body(containsString("\"team\":\"core\""))
        .body(containsString("\"lead\":true"));
  }
}
