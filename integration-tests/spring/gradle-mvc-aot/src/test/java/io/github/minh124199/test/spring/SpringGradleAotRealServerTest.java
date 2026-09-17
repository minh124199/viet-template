package io.github.minh124199.test.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    classes = SpringTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class SpringGradleAotRealServerTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private TemplateEngine engine;

  @Test
  @DisplayName("Real server renders /hello with HTTP 200, Content-Type, and expected HTML")
  void testHelloEndpoint() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/hello?name=Minh&location=Danang", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isNotNull();
    assertThat(response.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();
    assertThat(response.getBody())
        .contains("<h1>Hello, Minh!</h1>")
        .contains("<p>Welcome to Danang.</p>");
  }

  @Test
  @DisplayName("Real server renders /users with HTTP 200 and iterable loop HTML")
  void testUsersEndpoint() {
    ResponseEntity<String> response = restTemplate.getForEntity("/users", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>Users List</h1>")
        .contains("<li>Alice - Admin</li>")
        .contains("<li>Bob - Engineer</li>");
  }

  @Test
  @DisplayName(
      "Verifies pure AOT execution: rejectRuntimeCompilation is true and source .vtl files are not"
          + " on classpath")
  void testAotPureExecutionInvariants() {
    assertThat(engine).isNotNull();
    assertThat(engine.rejectRuntimeCompilation()).isTrue();

    // Verify original .vtl source files are NOT on the runtime classpath
    ClassLoader cl = getClass().getClassLoader();
    assertThat(cl.getResource("hello.vtl")).isNull();
    assertThat(cl.getResource("users.vtl")).isNull();
    assertThat(cl.getResource("viet-template/hello.vtl")).isNull();
    assertThat(cl.getResource("templates/hello.vtl")).isNull();

    // Verify templates.idx exists and templates use AOT_BYTECODE execution tier
    assertThat(cl.getResource("META-INF/viet-template/templates.idx")).isNotNull();

    Template helloTemplate = engine.get(TemplateId.of("hello.vtl"));
    assertThat(helloTemplate).isNotNull();
    assertThat(helloTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

    Template usersTemplate = engine.get(TemplateId.of("users.vtl"));
    assertThat(usersTemplate).isNotNull();
    assertThat(usersTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");
  }

  @Test
  @DisplayName(
      "Stream ownership invariant: servlet container handles multiple sequential requests without"
          + " stream closure issues")
  void testStreamOwnershipMultipleRequests() {
    for (int i = 0; i < 10; i++) {
      ResponseEntity<String> response =
          restTemplate.getForEntity("/hello?name=Iter" + i + "&location=Place" + i, String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody())
          .contains("<h1>Hello, Iter" + i + "!</h1>")
          .contains("<p>Welcome to Place" + i + ".</p>");
    }
  }

  @Test
  @DisplayName(
      "Concurrency stress test: 200 concurrent HTTP requests across threads with different model"
          + " params without crosstalk")
  void testConcurrentRequestsWithoutCrosstalk() throws Exception {
    int requestCount = 200;
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>(requestCount);
      for (int i = 0; i < requestCount; i++) {
        final int id = i;
        tasks.add(
            () ->
                restTemplate.getForEntity(
                    "/hello?name=User" + id + "&location=City" + id, String.class));
      }

      List<Future<ResponseEntity<String>>> futures = executor.invokeAll(tasks);
      assertThat(futures).hasSize(requestCount);

      for (int i = 0; i < requestCount; i++) {
        ResponseEntity<String> response = futures.get(i).get(15, TimeUnit.SECONDS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .contains("<h1>Hello, User" + i + "!</h1>")
            .contains("<p>Welcome to City" + i + ".</p>");
      }
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }
}
