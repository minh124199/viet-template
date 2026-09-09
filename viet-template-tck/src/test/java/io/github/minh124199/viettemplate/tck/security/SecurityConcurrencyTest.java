package io.github.minh124199.viettemplate.tck.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Concurrency and thread-safety tests for secure multi-threaded template execution. */
class SecurityConcurrencyTest {

  public static class UserContext {
    private final String username;
    private final String role;

    public UserContext(String username, String role) {
      this.username = username;
      this.role = role;
    }

    public String getUsername() {
      return username;
    }

    public String getRole() {
      return role;
    }
  }

  @Test
  @DisplayName("Concurrent rendering of templates maintains context isolation across threads")
  void concurrentRenderingMaintainsContextIsolation() throws Exception {
    int threadCount = 16;
    int iterationsPerThread = 50;

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("user_card.vm", "User:[$user.username] Role:[$user.role] Count:[$count]");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Void>> futures = new ArrayList<>();

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                  String username = "user_" + threadId + "_" + i;
                  String role = "role_" + threadId;
                  UserContext user = new UserContext(username, role);

                  RenderContext ctx = RenderContext.of(Map.of("user", user, "count", i));
                  StringTemplateOutput out = new StringTemplateOutput();
                  engine.render(TemplateId.of("user_card.vm"), ctx, out);

                  String expected = "User:[" + username + "] Role:[" + role + "] Count:[" + i + "]";
                  assertThat(out.toString()).isEqualTo(expected);
                }
                return null;
              }));
    }

    startLatch.countDown();
    for (Future<Void> f : futures) {
      f.get(15, TimeUnit.SECONDS);
    }
    executor.shutdown();
  }

  @Test
  @DisplayName("Concurrent tampering of protected context variables is safely rejected")
  void concurrentTamperingOfProtectedVariablesIsRejected() throws Exception {
    int threadCount = 8;
    int iterations = 100;

    MutableRenderContext ctx =
        MutableRenderContext.of(Map.of("immutableKey", "lockedValue"), Set.of("immutableKey"));

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger blockedAttempts = new AtomicInteger(0);
    List<Future<Void>> futures = new ArrayList<>();

    for (int t = 0; t < threadCount; t++) {
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                  try {
                    ctx.put("immutableKey", "pwned_" + i);
                  } catch (TemplateSecurityException tse) {
                    blockedAttempts.incrementAndGet();
                  }
                }
                return null;
              }));
    }

    startLatch.countDown();
    for (Future<Void> f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(blockedAttempts.get()).isEqualTo(threadCount * iterations);
    assertThat(ctx.get("immutableKey")).isEqualTo("lockedValue");
  }

  @Test
  @DisplayName("Concurrent compilation and cache lookup with distinct policies is partitioned")
  void concurrentCompilationWithDistinctPolicies() throws Exception {
    int threadCount = 8;
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("shared.vm", "Value: $val");

    MemberAccessPolicy policy1 = MemberAccessPolicy.standard();
    MemberAccessPolicy policy2 = MemberAccessPolicy.denyAll();

    VtlInterpreterOptions opts1 =
        VtlInterpreterOptions.builder()
            .securityPolicy(VtlSecurityPolicy.of(policy1))
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    VtlInterpreterOptions opts2 =
        VtlInterpreterOptions.builder()
            .securityPolicy(VtlSecurityPolicy.of(policy2))
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    try (VtlTemplateEngine engine1 =
            VtlTemplateEngine.builder()
                .repository(repo)
                .memberAccessPolicy(policy1)
                .interpreterOptions(opts1)
                .build();
        VtlTemplateEngine engine2 =
            VtlTemplateEngine.builder()
                .repository(repo)
                .memberAccessPolicy(policy2)
                .interpreterOptions(opts2)
                .build()) {

      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      List<Callable<Void>> tasks = new ArrayList<>();

      for (int i = 0; i < threadCount; i++) {
        final boolean useEngine1 = (i % 2 == 0);
        tasks.add(
            () -> {
              VtlTemplateEngine engine = useEngine1 ? engine1 : engine2;
              StringTemplateOutput out = new StringTemplateOutput();
              engine.render(TemplateId.of("shared.vm"), RenderContext.of("val", "ok"), out);
              assertThat(out.toString()).isEqualTo("Value: ok");
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
      for (Future<Void> f : futures) {
        f.get();
      }
      executor.shutdown();
    }
  }
}
