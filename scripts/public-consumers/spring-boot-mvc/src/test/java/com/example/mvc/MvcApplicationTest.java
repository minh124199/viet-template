package com.example.mvc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Spring Boot MVC renders Viet Template view with HTTP 200 OK and expected HTML body")
    void testHelloEndpoint() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/hello?name=CentralUser", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody())
                .contains("<h1>Hello, CentralUser!</h1>")
                .contains("<p>Welcome to Viet Template on Spring Boot MVC.</p>");
    }
}
