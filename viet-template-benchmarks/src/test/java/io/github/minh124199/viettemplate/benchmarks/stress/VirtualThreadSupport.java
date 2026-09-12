package io.github.minh124199.viettemplate.benchmarks.stress;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

/**
 * Utility providing dynamic resolution of Java 21+ Virtual Threads while maintaining strict {@code
 * --release 17} binary compatibility across all compilation backends.
 */
public final class VirtualThreadSupport {

  private static final MethodHandle NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR;
  private static final MethodHandle IS_VIRTUAL_THREAD;

  static {
    MethodHandle newVirtual = null;
    MethodHandle isVirtual = null;
    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      newVirtual =
          lookup.findStatic(
              Executors.class,
              "newVirtualThreadPerTaskExecutor",
              MethodType.methodType(ExecutorService.class));
      isVirtual =
          lookup.findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // Running on Java 17 runtime baseline where virtual threads are not present.
    }
    NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR = newVirtual;
    IS_VIRTUAL_THREAD = isVirtual;
  }

  private VirtualThreadSupport() {}

  /** Returns true if the current JVM runtime natively supports Java 21+ virtual threads. */
  public static boolean isVirtualThreadSupported() {
    return NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null;
  }

  /**
   * Creates an {@link ExecutorService} using virtual threads on Java 21+, falling back to an
   * unbounded cached platform thread pool on Java 17.
   */
  public static ExecutorService createVirtualThreadExecutor() {
    if (NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null) {
      try {
        ExecutorService executor =
            (ExecutorService) NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.invokeExact();
        boolean virtual =
            executor.submit(() -> isVirtual(Thread.currentThread())).get(10, TimeUnit.SECONDS);
        if (!virtual) {
          executor.shutdownNow();
          throw new IllegalStateException(
              "Java 21+ executor probe did not execute on a virtual thread");
        }
        return executor;
      } catch (Throwable t) {
        if (t instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Failed to instantiate virtual thread executor", t);
      }
    }
    return Executors.newCachedThreadPool();
  }

  /** Skips a virtual-thread-specific test explicitly when running on the Java 17 baseline. */
  public static void assumeVirtualThreads() {
    Assumptions.assumeTrue(
        isVirtualThreadSupported(), "Virtual-thread coverage requires a Java 21+ runtime");
  }

  /** Creates a virtual-thread executor and proves that submitted work uses a virtual thread. */
  public static ExecutorService createVerifiedVirtualThreadExecutor() {
    if (!isVirtualThreadSupported()) {
      throw new IllegalStateException("Virtual threads require a Java 21+ runtime");
    }
    return createVirtualThreadExecutor();
  }

  /** Creates a standard fixed platform thread pool for apples-to-apples comparison. */
  public static ExecutorService createPlatformThreadExecutor(int threads) {
    return Executors.newFixedThreadPool(Math.max(1, threads));
  }

  /** Checks whether the specified thread is a virtual thread. Returns false on Java 17. */
  public static boolean isVirtual(Thread thread) {
    if (thread == null || IS_VIRTUAL_THREAD == null) {
      return false;
    }
    try {
      return (boolean) IS_VIRTUAL_THREAD.invokeExact(thread);
    } catch (Throwable t) {
      return false;
    }
  }

  /** Returns an informative description of the active execution model. */
  public static String executionModelName() {
    return isVirtualThreadSupported()
        ? "VirtualThreads (Java 21+)"
        : "PlatformThreadsFallback (Java 17)";
  }
}
