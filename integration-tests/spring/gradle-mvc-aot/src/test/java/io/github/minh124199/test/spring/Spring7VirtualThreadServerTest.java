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
import java.util.concurrent.atomic.AtomicInteger;
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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.threads.virtual.enabled=true",
      "server.tomcat.threads.max=200"
    })
@AutoConfigureTestRestTemplate
class Spring7VirtualThreadServerTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private TemplateEngine engine;

  @Test
  @DisplayName(
      "Verifies virtual threads execute concurrent template rendering on embedded Tomcat without crosstalk")
  void testConcurrentVirtualThreadTemplateRendering() throws Exception {
    int requestCount = 200;
    try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      AtomicInteger virtualThreadCount = new AtomicInteger(0);
      List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>(requestCount);

      for (int i = 0; i < requestCount; i++) {
        final int id = i;
        tasks.add(
            () -> {
              if (Thread.currentThread().isVirtual()) {
                virtualThreadCount.incrementAndGet();
              }
              return restTemplate.getForEntity(
                  "/hello?name=VUser" + id + "&location=VLoc" + id, String.class);
            });
      }

      List<Future<ResponseEntity<String>>> futures = virtualExecutor.invokeAll(tasks);
      assertThat(futures).hasSize(requestCount);

      for (int i = 0; i < requestCount; i++) {
        ResponseEntity<String> response = futures.get(i).get(20, TimeUnit.SECONDS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody())
            .contains("<h1>Hello, VUser" + i + "!</h1>")
            .contains("<p>Welcome to VLoc" + i + ".</p>");
      }

      assertThat(virtualThreadCount.get())
          .as("All client tasks must execute on Virtual Threads")
          .isEqualTo(requestCount);
    }
  }

  @Test
  @DisplayName("Verifies concurrent collection template rendering under virtual threads")
  void testConcurrentCollectionTemplateRenderingOnVirtualThreads() throws Exception {
    int requestCount = 100;
    try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>(requestCount);

      for (int i = 0; i < requestCount; i++) {
        tasks.add(() -> restTemplate.getForEntity("/users", String.class));
      }

      List<Future<ResponseEntity<String>>> futures = virtualExecutor.invokeAll(tasks);
      for (Future<ResponseEntity<String>> future : futures) {
        ResponseEntity<String> response = future.get(15, TimeUnit.SECONDS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .contains("<h1>Users List</h1>")
            .contains("<li>Alice - Admin</li>")
            .contains("<li>Bob - Engineer</li>");
      }
    }
  }

  @Test
  @DisplayName("Verifies AOT template engine execution under virtual thread dispatcher")
  void testAotEngineInvariantsUnderVirtualThreads() {
    assertThat(engine).isNotNull();
    assertThat(engine.rejectRuntimeCompilation()).isTrue();

    Template helloTemplate = engine.get(TemplateId.of("hello.vtl"));
    assertThat(helloTemplate).isNotNull();
    assertThat(helloTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

    Template usersTemplate = engine.get(TemplateId.of("users.vtl"));
    assertThat(usersTemplate).isNotNull();
    assertThat(usersTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");
  }
}
