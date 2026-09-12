package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency stress test verifying thread-safety of {@link DynamicCallSite} transitions (UNLINKED
 * -> MONOMORPHIC -> POLYMORPHIC -> MEGAMORPHIC) and strict isolation between security policies
 * (standard vs denyAll) under concurrent multi-threaded invocation.
 */
class DynamicCallSiteConcurrencyStressTest {

  @Test
  @DisplayName("Stress test concurrent polymorphic and megamorphic transitions")
  void testConcurrentCallSiteStateTransitions() throws Exception {
    DynamicLinker linker = new DynamicLinker(LinkerAccessPolicy.standard());
    MemberKey key = MemberKey.propertyGet("value");
    DynamicCallSite callSite =
        new DynamicCallSite(
            101, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());

    int threadCount = 32;
    int iterationsPerThread = 500;
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Boolean>> tasks = new ArrayList<>(threadCount);
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        tasks.add(
            () -> {
              for (int i = 0; i < iterationsPerThread; i++) {
                int shape = (threadId + i) % 6; // 6 distinct shapes -> triggers MEGAMORPHIC
                Object receiver =
                    switch (shape) {
                      case 0 -> new Shape0("s0");
                      case 1 -> new Shape1("s1");
                      case 2 -> new Shape2("s2");
                      case 3 -> new Shape3("s3");
                      case 4 -> new Shape4("s4");
                      default -> new Shape5("s5");
                    };
                try {
                  Object val = callSite.invoke(receiver);
                  if (!val.toString().startsWith("s")) {
                    return false;
                  }
                } catch (Throwable th) {
                  throw new RuntimeException(th);
                }
              }
              return true;
            });
      }

      List<Future<Boolean>> futures = executor.invokeAll(tasks);
      for (Future<Boolean> f : futures) {
        assertThat(f.get()).isTrue();
      }

      // After 6 distinct shapes, callsite must be in MEGAMORPHIC state
      assertThat(callSite.state()).isEqualTo(DynamicCallSite.State.MEGAMORPHIC);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @DisplayName(
      "Verify security partitioning between standard and denyAll policies under concurrency")
  void testSecurityPartitioningConcurrency() throws Exception {
    DynamicLinker standardLinker = new DynamicLinker(LinkerAccessPolicy.standard());
    DynamicLinker denyLinker = new DynamicLinker(LinkerAccessPolicy.denyAll());
    MemberKey key = MemberKey.propertyGet("secret");

    DynamicCallSite standardSite =
        new DynamicCallSite(
            201, key, LinkerAccessPolicy.standard(), standardLinker, new LinkerStatistics());
    DynamicCallSite denySite =
        new DynamicCallSite(
            202, key, LinkerAccessPolicy.denyAll(), denyLinker, new LinkerStatistics());

    int tasksCount = 500;
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(tasksCount * 2);
      for (int i = 0; i < tasksCount; i++) {
        final int id = i;
        // Standard site should succeed
        tasks.add(
            () -> {
              SecureData data = new SecureData("sec-" + id);
              try {
                Object result = standardSite.invoke(data);
                assertThat(result).isEqualTo("sec-" + id);
              } catch (Throwable th) {
                throw new RuntimeException(th);
              }
              return null;
            });

        // Deny site must throw TemplateSecurityException
        tasks.add(
            () -> {
              SecureData data = new SecureData("sec-" + id);
              assertThatThrownBy(
                      () -> {
                        try {
                          denySite.invoke(data);
                        } catch (Throwable th) {
                          if (th instanceof RuntimeException re) throw re;
                          throw new RuntimeException(th);
                        }
                      })
                  .isInstanceOf(TemplateSecurityException.class);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @DisplayName(
      "Verify 3-way security policy isolation under concurrency (standard, safe allowlist,"
          + " denyAll)")
  void testThreeWaySecurityPolicyConcurrencyIsolation() throws Exception {
    LinkerAccessPolicy permissivePolicy = LinkerAccessPolicy.standard();
    LinkerAccessPolicy safePolicy = LinkerAccessPolicy.of(MemberAccessPolicy.safe());
    LinkerAccessPolicy denyPolicy = LinkerAccessPolicy.denyAll();

    DynamicLinker permissiveLinker = new DynamicLinker(permissivePolicy);
    DynamicLinker safeLinker = new DynamicLinker(safePolicy);
    DynamicLinker denyLinker = new DynamicLinker(denyPolicy);

    MemberKey propKey = MemberKey.propertyGet("secret");
    MemberKey methodKey = MemberKey.methodCall("revealSecret", 0);

    DynamicCallSite permissivePropSite =
        new DynamicCallSite(
            301, propKey, permissivePolicy, permissiveLinker, new LinkerStatistics());
    DynamicCallSite permissiveMethodSite =
        new DynamicCallSite(
            302, methodKey, permissivePolicy, permissiveLinker, new LinkerStatistics());

    DynamicCallSite safePropSite =
        new DynamicCallSite(303, propKey, safePolicy, safeLinker, new LinkerStatistics());
    DynamicCallSite safeMethodSite =
        new DynamicCallSite(304, methodKey, safePolicy, safeLinker, new LinkerStatistics());

    DynamicCallSite denyPropSite =
        new DynamicCallSite(305, propKey, denyPolicy, denyLinker, new LinkerStatistics());
    DynamicCallSite denyMethodSite =
        new DynamicCallSite(306, methodKey, denyPolicy, denyLinker, new LinkerStatistics());

    int tasksCount = 600;
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(tasksCount * 3);
      for (int i = 0; i < tasksCount; i++) {
        final int id = i;
        final boolean alternateShape = (i % 2 == 0);

        // 1. Permissive tasks: must succeed on property get and method call
        tasks.add(
            () -> {
              Object target =
                  alternateShape
                      ? new NonRecordTargetA("perm-a-" + id)
                      : new NonRecordTargetB("perm-b-" + id);
              String expected = alternateShape ? "perm-a-" + id : "perm-b-" + id;
              try {
                Object propVal = permissivePropSite.invoke(target);
                assertThat(propVal).isEqualTo(expected);

                Object methodVal = permissiveMethodSite.invokeWithArgs(target, new Object[0]);
                assertThat(methodVal).isEqualTo(expected);
              } catch (Throwable th) {
                throw new RuntimeException(th);
              }
              return null;
            });

        // 2. Safe allowlist tasks: unapproved non-record target must be denied
        tasks.add(
            () -> {
              Object target =
                  alternateShape
                      ? new NonRecordTargetA("safe-a-" + id)
                      : new NonRecordTargetB("safe-b-" + id);
              assertThatThrownBy(
                      () -> {
                        try {
                          safePropSite.invoke(target);
                        } catch (Throwable th) {
                          if (th instanceof RuntimeException re) throw re;
                          throw new RuntimeException(th);
                        }
                      })
                  .isInstanceOf(TemplateSecurityException.class);

              assertThatThrownBy(
                      () -> {
                        try {
                          safeMethodSite.invokeWithArgs(target, new Object[0]);
                        } catch (Throwable th) {
                          if (th instanceof RuntimeException re) throw re;
                          throw new RuntimeException(th);
                        }
                      })
                  .isInstanceOf(TemplateSecurityException.class);
              return null;
            });

        // 3. DenyAll tasks: all target accesses must be denied
        tasks.add(
            () -> {
              Object target =
                  alternateShape
                      ? new NonRecordTargetA("deny-a-" + id)
                      : new NonRecordTargetB("deny-b-" + id);
              assertThatThrownBy(
                      () -> {
                        try {
                          denyPropSite.invoke(target);
                        } catch (Throwable th) {
                          if (th instanceof RuntimeException re) throw re;
                          throw new RuntimeException(th);
                        }
                      })
                  .isInstanceOf(TemplateSecurityException.class);

              assertThatThrownBy(
                      () -> {
                        try {
                          denyMethodSite.invokeWithArgs(target, new Object[0]);
                        } catch (Throwable th) {
                          if (th instanceof RuntimeException re) throw re;
                          throw new RuntimeException(th);
                        }
                      })
                  .isInstanceOf(TemplateSecurityException.class);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }

      // Verify that permissive sites never recorded any denials
      assertThat(permissivePropSite.statistics().denied()).isZero();
      assertThat(permissiveMethodSite.statistics().denied()).isZero();

      // Verify that safe allowlist and deny sites recorded denials and never leaked access
      assertThat(safePropSite.statistics().denied()).isGreaterThanOrEqualTo(1);
      assertThat(safeMethodSite.statistics().denied()).isGreaterThanOrEqualTo(1);
      assertThat(denyPropSite.statistics().denied()).isGreaterThanOrEqualTo(1);
      assertThat(denyMethodSite.statistics().denied()).isGreaterThanOrEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  public record Shape0(String value) {}

  public record Shape1(String value) {}

  public record Shape2(String value) {}

  public record Shape3(String value) {}

  public record Shape4(String value) {}

  public record Shape5(String value) {}

  public record SecureData(String secret) {}

  public static class NonRecordTargetA {
    private final String secret;

    public NonRecordTargetA(String secret) {
      this.secret = secret;
    }

    public String getSecret() {
      return secret;
    }

    public String revealSecret() {
      return secret;
    }
  }

  public static class NonRecordTargetB {
    private final String secret;

    public NonRecordTargetB(String secret) {
      this.secret = secret;
    }

    public String getSecret() {
      return secret;
    }

    public String revealSecret() {
      return secret;
    }
  }
}
