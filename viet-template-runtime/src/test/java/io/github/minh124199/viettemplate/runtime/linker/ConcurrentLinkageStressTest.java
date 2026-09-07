package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConcurrentLinkageStressTest {

  public static class ItemA {
    public String getVal() {
      return "A";
    }
  }

  public static class ItemB {
    public String getVal() {
      return "B";
    }
  }

  public static class ItemC {
    public String getVal() {
      return "C";
    }
  }

  public static class ItemD {
    public String getVal() {
      return "D";
    }
  }

  public static class ItemE {
    public String getVal() {
      return "E";
    }
  }

  @Test
  void testConcurrentCallSiteLinkageAndInvocation() throws Exception {
    int threadCount = 16;
    int iterationsPerThread = 500;
    DynamicLinker linker = new DynamicLinker();
    LinkerStatistics stats = new LinkerStatistics();
    DynamicCallSite callSite =
        new DynamicCallSite(
            1, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard(), linker, stats);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                  int mod = (threadId + i) % 5;
                  Object target =
                      switch (mod) {
                        case 0 -> new ItemA();
                        case 1 -> new ItemB();
                        case 2 -> new ItemC();
                        case 3 -> new ItemD();
                        default -> new ItemE();
                      };
                  String expected =
                      switch (mod) {
                        case 0 -> "A";
                        case 1 -> "B";
                        case 2 -> "C";
                        case 3 -> "D";
                        default -> "E";
                      };
                  Object result;
                  try {
                    result = callSite.invoke(target);
                  } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                  }
                  if (!expected.equals(result)) {
                    return false;
                  }
                }
                return true;
              }));
    }

    startLatch.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

    for (Future<Boolean> future : futures) {
      assertTrue(future.get(), "Thread execution encountered unexpected result");
    }

    // CallSite state should be megamorphic given 5 distinct shapes
    assertEquals(DynamicCallSite.State.MEGAMORPHIC, callSite.state());
    assertTrue(stats.megamorphicHits() > 0);
  }

  @Test
  void testConcurrentCallSiteRegistryAccess() throws Exception {
    int threadCount = 12;
    int iterations = 300;
    DynamicLinker linker = new DynamicLinker();
    CallSiteRegistry registry = new CallSiteRegistry(50, linker);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      futures.add(
          executor.submit(
              () -> {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                  int siteId = (threadId + i) % 10;
                  DynamicCallSite site =
                      registry.getOrCreate(
                          siteId, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard());
                  if (site == null) {
                    return false;
                  }
                }
                return true;
              }));
    }

    startLatch.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

    for (Future<Boolean> future : futures) {
      assertTrue(future.get());
    }

    assertTrue(registry.size() <= registry.maxCapacity());
  }
}
