package io.github.minh124199.test.security;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = SecurityTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.threads.virtual.enabled=true",
      "server.tomcat.threads.max=200"
    })
@AutoConfigureTestRestTemplate
class Spring7SecurityVirtualThreadServerTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("Verifies concurrent anonymous and authenticated requests on Tomcat 11 with virtual threads")
  void testConcurrentVirtualThreadRequestsWithSecurity() throws Exception {
    int requestCount = 100;
    try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      AtomicInteger virtualThreadCount = new AtomicInteger(0);
      List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>(requestCount * 2);

      for (int i = 0; i < requestCount; i++) {
        tasks.add(
            () -> {
              if (Thread.currentThread().isVirtual()) {
                virtualThreadCount.incrementAndGet();
              }
              return restTemplate.getForEntity("/public", String.class);
            });
        tasks.add(
            () -> {
              if (Thread.currentThread().isVirtual()) {
                virtualThreadCount.incrementAndGet();
              }
              return restTemplate
                  .withBasicAuth("alice", "password")
                  .getForEntity("/dashboard", String.class);
            });
      }

      List<Future<ResponseEntity<String>>> futures = virtualExecutor.invokeAll(tasks);
      assertThat(futures).hasSize(requestCount * 2);

      for (int i = 0; i < futures.size(); i++) {
        ResponseEntity<String> response = futures.get(i).get(20, TimeUnit.SECONDS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        if (i % 2 == 0) {
          assertThat(response.getBody())
              .contains("<h1>Public Page</h1>")
              .contains("Browsing anonymously");
        } else {
          assertThat(response.getBody())
              .contains("<h1>Welcome, alice!</h1>")
              .contains("Admin Access Granted")
              .contains("name=\"_csrf\"");
        }
      }

      assertThat(virtualThreadCount.get()).isEqualTo(requestCount * 2);
    }
  }
}
