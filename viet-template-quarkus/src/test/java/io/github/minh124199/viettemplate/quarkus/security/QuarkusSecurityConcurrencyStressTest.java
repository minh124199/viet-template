package io.github.minh124199.viettemplate.quarkus.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class QuarkusSecurityConcurrencyStressTest {

  private ExecutorService executor;
  private TemplateEngine engine;

  @BeforeEach
  public void setUp() {
    this.executor = Executors.newFixedThreadPool(50);
    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "stress.vtl",
                "Name: $security.name | Auth: $security.authenticated | IsAdmin:"
                    + " $security.hasRole('ADMIN') | IsUser: $security.hasRole('USER') |"
                    + " IsViewer: $security.hasRole('VIEWER') | Csrf: $csrf.token | CsrfAvail:"
                    + " $csrf.available");

    this.engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(new QuarkusSecurityRenderContextContributor())
            .build();
  }

  @AfterEach
  public void tearDown() throws Exception {
    this.executor.shutdown();
    if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
      this.executor.shutdownNow();
    }
  }

  enum UserType {
    ALICE,
    BOB,
    CHARLIE,
    ANONYMOUS
  }

  @Test
  public void testHighConcurrencyCrossContaminationIsolation() throws Exception {
    int threadCount = 50;
    int tasksPerThread = 20;
    int totalTasks = threadCount * tasksPerThread;

    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<Callable<Void>> tasks = new ArrayList<>();
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < totalTasks; i++) {
      final UserType userType = UserType.values()[i % UserType.values().length];
      final String taskCsrfToken = "token-" + userType.name().toLowerCase() + "-" + i;

      tasks.add(
          () -> {
            readyLatch.countDown();
            if (!startLatch.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Timeout waiting for start latch");
            }

            Map<String, Object> attrs;
            if (userType == UserType.ANONYMOUS) {
              attrs = Map.of();
            } else {
              Set<String> roles =
                  switch (userType) {
                    case ALICE -> Set.of("ADMIN", "MANAGER");
                    case BOB -> Set.of("USER");
                    case CHARLIE -> Set.of("VIEWER");
                    default -> Set.of();
                  };
              String name = userType.name().toLowerCase();
              QuarkusSecurityViewTest.TestSecurityIdentity identity =
                  new QuarkusSecurityViewTest.TestSecurityIdentity(name, false, roles);
              attrs =
                  Map.of(
                      QuarkusSecurityRenderContextContributor.SECURITY_IDENTITY_ATTRIBUTE,
                      identity,
                      QuarkusSecurityRenderContextContributor.CSRF_TOKEN_ATTRIBUTE,
                      taskCsrfToken);
            }

            RenderRequest req =
                new RenderRequest(TemplateId.of("stress.vtl"), RenderContext.empty(), attrs);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
              engine.render(req, out);
            }

            String output = baos.toString(StandardCharsets.UTF_8);

            // Assert isolation according to user type
            switch (userType) {
              case ALICE -> {
                assertThat(output)
                    .contains(
                        "Name: alice | Auth: true | IsAdmin: true | IsUser: false | IsViewer: false"
                            + " | Csrf: "
                            + taskCsrfToken
                            + " | CsrfAvail: true");
                assertThat(output).doesNotContain("bob").doesNotContain("charlie");
              }
              case BOB -> {
                assertThat(output)
                    .contains(
                        "Name: bob | Auth: true | IsAdmin: false | IsUser: true | IsViewer: false |"
                            + " Csrf: "
                            + taskCsrfToken
                            + " | CsrfAvail: true");
                assertThat(output).doesNotContain("alice").doesNotContain("charlie");
              }
              case CHARLIE -> {
                assertThat(output)
                    .contains(
                        "Name: charlie | Auth: true | IsAdmin: false | IsUser: false | IsViewer:"
                            + " true | Csrf: "
                            + taskCsrfToken
                            + " | CsrfAvail: true");
                assertThat(output).doesNotContain("alice").doesNotContain("bob");
              }
              case ANONYMOUS -> {
                assertThat(output)
                    .contains(
                        "Name:  | Auth: false | IsAdmin: false | IsUser: false | IsViewer: false |"
                            + " Csrf:  | CsrfAvail: false");
                assertThat(output)
                    .doesNotContain("alice")
                    .doesNotContain("bob")
                    .doesNotContain("charlie");
              }
            }

            return null;
          });
    }

    List<Future<Void>> futures = new ArrayList<>();
    for (Callable<Void> task : tasks) {
      futures.add(executor.submit(task));
    }

    readyLatch.await(10, TimeUnit.SECONDS);
    startLatch.countDown();

    for (Future<Void> future : futures) {
      try {
        future.get(15, TimeUnit.SECONDS);
      } catch (Throwable t) {
        errors.add(t);
      }
    }

    assertThat(errors).isEmpty();
  }
}
