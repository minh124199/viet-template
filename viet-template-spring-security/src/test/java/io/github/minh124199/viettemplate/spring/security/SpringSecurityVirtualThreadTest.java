package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

class SpringSecurityVirtualThreadTest {

  private static final MethodHandle NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR;

  static {
    MethodHandle mh = null;
    try {
      mh =
          MethodHandles.publicLookup()
              .findStatic(
                  Executors.class,
                  "newVirtualThreadPerTaskExecutor",
                  MethodType.methodType(ExecutorService.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
    }
    NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR = mh;
  }

  private static ExecutorService createExecutor() {
    if (NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null) {
      try {
        return (ExecutorService) NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.invokeExact();
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }
    return Executors.newFixedThreadPool(40);
  }

  private InMemoryTemplateRepository repository;
  private SpringSecurityRenderContextContributor contributor;
  private TemplateEngine engine;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    this.repository = InMemoryTemplateRepository.create();
    this.contributor = new SpringSecurityRenderContextContributor();
    this.engine =
        TemplateEngine.builder()
            .repository(this.repository)
            .contextCollisionPolicy(ContextCollisionPolicy.MODEL_WINS)
            .addContextContributor(this.contributor)
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "Virtual-thread stress: 1,000 concurrent renders on virtual threads with zero security state"
          + " leakage")
  void testConcurrentVirtualThreadRenders() throws Exception {
    TemplateId templateId = TemplateId.of("vthread-user.vtl");
    this.repository.put(
        templateId,
        "name:$security.name|auth:$security.authenticated|role:$security.hasAuthority('ROLE_' +"
            + " $security.name)");

    int taskCount = 1_000;
    ExecutorService executor = createExecutor();
    AtomicInteger failureCount = new AtomicInteger(0);
    List<Callable<Void>> tasks = new ArrayList<>(taskCount);

    for (int i = 0; i < taskCount; i++) {
      final int taskId = i;
      final String userName = "vuser" + taskId;
      final String roleName = "ROLE_" + userName;

      tasks.add(
          () -> {
            try {
              Authentication auth =
                  new UsernamePasswordAuthenticationToken(
                      userName, "n/a", AuthorityUtils.createAuthorityList(roleName));
              SecurityContext sc = new SecurityContextImpl(auth);

              Map<String, Object> attributes =
                  Map.of(SpringSecurityRenderContextContributor.SECURITY_CONTEXT_ATTRIBUTE, sc);

              RenderRequest request =
                  RenderRequest.of(templateId, RenderContext.empty(), attributes);

              StringTemplateOutput output = new StringTemplateOutput();
              engine.render(request, output);

              String expected = "name:" + userName + "|auth:true|role:true";
              if (!output.toString().equals(expected)) {
                failureCount.incrementAndGet();
              }
            } catch (Exception e) {
              failureCount.incrementAndGet();
            }
            return null;
          });
    }

    List<Future<Void>> futures = executor.invokeAll(tasks);
    executor.shutdown();
    boolean completed = executor.awaitTermination(20, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    for (Future<Void> f : futures) {
      f.get();
    }
    assertThat(failureCount.get()).isZero();
  }
}
