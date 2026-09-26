package com.example.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("/public endpoint renders public template returning HTTP 200")
    void testPublicEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/public", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .contains("<h1>Public Content</h1>")
            .contains("Viewing anonymously");
    }

    @Test
    @DisplayName("/protected endpoint returns 401 Unauthorized when unauthenticated")
    void testProtectedUnauthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity("/protected", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("/protected endpoint returns HTTP 200 with authenticated username when credentials provided")
    void testProtectedAuthenticated() {
        ResponseEntity<String> response = restTemplate
            .withBasicAuth("alice", "secret123")
            .getForEntity("/protected", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .contains("<h1>Protected Content</h1>")
            .contains("Hello, alice!");
    }
}
