package io.github.minh124199.viettemplate.runtime.linker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CallSiteRegistryTest {

  @Test
  @DisplayName("Enforces positive max capacity")
  void testConstructorValidatesCapacity() {
    assertThatThrownBy(() -> new CallSiteRegistry(0, new DynamicLinker()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CallSiteRegistry(-5, new DynamicLinker()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Test capacity boundaries: capacity - 1, capacity, capacity + 1, and 2x capacity")
  void testCapacityBoundaries() {
    int maxCap = 10;
    CallSiteRegistry registry =
        new CallSiteRegistry(maxCap, new DynamicLinker(), new LinkerStatistics());

    assertThat(registry.maxCapacity()).isEqualTo(maxCap);
    assertThat(registry.size()).isZero();

    // 1. Insert capacity - 1 (9 keys)
    for (int i = 0; i < maxCap - 1; i++) {
      DynamicCallSite site =
          registry.getOrCreate(i, MemberKey.propertyGet("prop" + i), LinkerAccessPolicy.standard());
      assertThat(site).isNotNull();
    }
    assertThat(registry.size()).isEqualTo(9);

    // 2. Insert capacity (10th key)
    registry.getOrCreate(9, MemberKey.propertyGet("prop9"), LinkerAccessPolicy.standard());
    assertThat(registry.size()).isEqualTo(10);

    // 3. Insert capacity + 1 (11th key) -> triggers eviction
    registry.getOrCreate(10, MemberKey.propertyGet("prop10"), LinkerAccessPolicy.standard());
    assertThat(registry.size())
        .as("Size must not exceed maxCapacity after capacity + 1 insertions")
        .isLessThanOrEqualTo(maxCap);

    // 4. Insert 2x capacity (keys 11 through 19)
    for (int i = 11; i < maxCap * 2; i++) {
      registry.getOrCreate(i, MemberKey.propertyGet("prop" + i), LinkerAccessPolicy.standard());
      assertThat(registry.size())
          .as("Size must remain <= maxCapacity at step %d", i)
          .isLessThanOrEqualTo(maxCap);
    }
    assertThat(registry.size()).isLessThanOrEqualTo(maxCap);

    // 5. Verify clear() resets size to 0
    registry.clear();
    assertThat(registry.size()).isZero();
  }

  @Test
  @DisplayName("Reuses identical call site instances for same key")
  void testReusesCallSiteForIdenticalKey() {
    CallSiteRegistry registry = new CallSiteRegistry(10, new DynamicLinker());
    MemberKey key = MemberKey.propertyGet("name");
    LinkerAccessPolicy policy = LinkerAccessPolicy.standard();

    DynamicCallSite site1 = registry.getOrCreate(42, key, policy);
    DynamicCallSite site2 = registry.getOrCreate(42, key, policy);

    assertThat(site1).isSameAs(site2);
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  @DisplayName("Concurrent getOrCreate across 100 threads remains bounded and thread-safe")
  void testConcurrentGetOrCreateRemainsBoundedAndThreadSafe() throws Exception {
    int maxCap = 10;
    CallSiteRegistry registry =
        new CallSiteRegistry(maxCap, new DynamicLinker(), new LinkerStatistics());

    int threadCount = 100;
    int opsPerThread = 50;
    List<Callable<Void>> tasks = new ArrayList<>(threadCount);

    for (int t = 0; t < threadCount; t++) {
      tasks.add(
          () -> {
            for (int i = 0; i < opsPerThread; i++) {
              int siteId = i % maxCap;
              DynamicCallSite site =
                  registry.getOrCreate(
                      siteId,
                      MemberKey.propertyGet("prop" + siteId),
                      LinkerAccessPolicy.standard());
              assertThat(site).isNotNull();
              assertThat(site.siteId()).isEqualTo(siteId);
            }
            return null;
          });
    }

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }
    }

    assertThat(registry.size())
        .as("Registry size must remain <= maxCapacity")
        .isLessThanOrEqualTo(maxCap);

    registry.clear();
    assertThat(registry.size()).isZero();
  }
}
