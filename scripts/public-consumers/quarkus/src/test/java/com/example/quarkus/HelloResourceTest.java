package com.example.quarkus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HelloResourceTest {

    @Test
    @DisplayName("Quarkus REST endpoint renders Viet Template returning HTTP 200 and expected HTML")
    public void testHelloEndpoint() {
        given()
            .when().get("/hello?name=CentralUser")
            .then()
            .statusCode(200)
            .body(containsString("<h1>Hello from Quarkus, CentralUser!</h1>"));
    }
}
