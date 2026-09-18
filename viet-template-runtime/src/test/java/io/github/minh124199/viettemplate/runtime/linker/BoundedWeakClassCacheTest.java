package io.github.minh124199.viettemplate.runtime.linker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoundedWeakClassCacheTest {

  public static class TargetSample {
    public String value() {
      return "sample";
    }
  }

  private static final class TestByteArrayClassLoader extends ClassLoader {
    private final byte[] classBytes;
    private final String className;

    TestByteArrayClassLoader(String className, byte[] classBytes) {
      super(ClassLoader.getPlatformClassLoader());
      this.className = className;
      this.classBytes = classBytes;
    }

    Class<?> loadTargetClass() {
      return defineClass(className, classBytes, 0, classBytes.length);
    }
  }

  private static byte[] loadClassBytes(Class<?> clazz) throws Exception {
    String resourceName = clazz.getName().replace('.', '/') + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(resourceName)) {
      if (is == null) {
        throw new IllegalStateException("Class resource not found: " + resourceName);
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int read;
      while ((read = is.read(buf)) != -1) {
        baos.write(buf, 0, read);
      }
      return baos.toByteArray();
    }
  }

  @Test
  @DisplayName("Enforces capacity bounding under insertions exceeding max capacity")
  void testCapacityBounding() {
    int capacity = 5;
    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(capacity);
    assertThat(cache.maxCapacity()).isEqualTo(capacity);

    Class<?>[] classes =
        new Class<?>[] {
          String.class,
          Integer.class,
          Long.class,
          Double.class,
          Boolean.class,
          Byte.class,
          Short.class,
          Float.class
        };

    for (int i = 0; i < classes.length; i++) {
      cache.put(classes[i], "val-" + i);
      assertThat(cache.size()).isLessThanOrEqualTo(capacity);
    }

    assertThat(cache.size()).isEqualTo(capacity);
  }

  @Test
  @DisplayName("Invalid capacity values throw IllegalArgumentException")
  void testInvalidCapacity() {
    assertThatThrownBy(() -> new BoundedWeakClassCache<>(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BoundedWeakClassCache<>(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Weak reference cleanup reclaims ephemeral classes without pinning ClassLoaders")
  void testWeakReferenceCleanupWithoutPinning() throws Exception {
    byte[] bytes = loadClassBytes(TargetSample.class);
    String className = TargetSample.class.getName();

    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(50);
    List<WeakReference<ClassLoader>> clRefs = new ArrayList<>();
    List<WeakReference<Class<?>>> classRefs = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      TestByteArrayClassLoader cl = new TestByteArrayClassLoader(className, bytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      clRefs.add(new WeakReference<>(cl));
      classRefs.add(new WeakReference<>(dynamicClass));

      cache.put(dynamicClass, "entry-" + i);
      assertThat(cache.get(dynamicClass)).isEqualTo("entry-" + i);
    }

    assertThat(cache.size()).isEqualTo(10);

    // Drop references and trigger GC loop
    boolean allCollected = false;
    for (int i = 0; i < 50; i++) {
      System.gc();
      boolean anyClAlive = clRefs.stream().anyMatch(ref -> ref.get() != null);
      boolean anyClassAlive = classRefs.stream().anyMatch(ref -> ref.get() != null);
      if (!anyClAlive && !anyClassAlive && cache.size() == 0) {
        allCollected = true;
        break;
      }
      Thread.sleep(20L);
    }

    assertThat(allCollected).isTrue();
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  @DisplayName("Cleared keys disappear from observable size while preserving live keys")
  void testClearedKeysDisappearFromObservableSize() throws Exception {
    byte[] bytes = loadClassBytes(TargetSample.class);
    String className = TargetSample.class.getName();

    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(20);

    // Add persistent/live entries
    cache.put(String.class, "live-string");
    cache.put(Integer.class, "live-int");
    assertThat(cache.size()).isEqualTo(2);

    List<WeakReference<Class<?>>> ephemeralClassRefs = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      TestByteArrayClassLoader cl = new TestByteArrayClassLoader(className, bytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      ephemeralClassRefs.add(new WeakReference<>(dynamicClass));
      cache.put(dynamicClass, "ephemeral-" + i);
    }

    assertThat(cache.size()).isEqualTo(7);

    // GC cycle
    for (int i = 0; i < 50; i++) {
      System.gc();
      if (ephemeralClassRefs.stream().noneMatch(ref -> ref.get() != null)) {
        break;
      }
      Thread.sleep(20L);
    }

    // Ephemeral entries disappear; live entries remain
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get(String.class)).isEqualTo("live-string");
    assertThat(cache.get(Integer.class)).isEqualTo("live-int");
  }

  @Test
  @DisplayName("Evicts dead entries prior to evicting live entries when capacity is reached")
  void testDeadEntryEvictionPriority() throws Exception {
    byte[] bytes = loadClassBytes(TargetSample.class);
    String className = TargetSample.class.getName();

    int capacity = 3;
    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(capacity);

    // Insert 1 permanent class
    cache.put(String.class, "permanent");

    // Insert 2 ephemeral classes to reach full capacity
    List<WeakReference<Class<?>>> ephemeralRefs = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      TestByteArrayClassLoader cl = new TestByteArrayClassLoader(className, bytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      ephemeralRefs.add(new WeakReference<>(dynamicClass));
      cache.put(dynamicClass, "ephemeral-" + i);
    }

    assertThat(cache.size()).isEqualTo(3);

    // Drop strong references to ephemeral classes and trigger GC
    for (int i = 0; i < 50; i++) {
      System.gc();
      if (ephemeralRefs.stream().noneMatch(ref -> ref.get() != null)) {
        break;
      }
      Thread.sleep(20L);
    }

    // Now insert another live class. The cache must evict the dead ephemeral entries
    // before evicting the permanent entry.
    cache.put(Integer.class, "new-live");

    assertThat(cache.get(String.class))
        .as("Permanent live entry must not be evicted when dead entries can be reclaimed")
        .isEqualTo("permanent");
    assertThat(cache.get(Integer.class)).isEqualTo("new-live");
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("computeIfAbsent computes value once and handles capacity bounding")
  void testComputeIfAbsent() {
    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(2);
    int[] counter = new int[1];

    String v1 =
        cache.computeIfAbsent(
            String.class,
            k -> {
              counter[0]++;
              return "computed";
            });
    assertThat(v1).isEqualTo("computed");
    assertThat(counter[0]).isEqualTo(1);

    // Second call should return cached value without invoking mapping function
    String v2 =
        cache.computeIfAbsent(
            String.class,
            k -> {
              counter[0]++;
              return "recomputed";
            });
    assertThat(v2).isEqualTo("computed");
    assertThat(counter[0]).isEqualTo(1);

    // Add another entry
    cache.computeIfAbsent(Integer.class, k -> "int");
    assertThat(cache.size()).isEqualTo(2);

    // Add third entry to trigger eviction
    cache.computeIfAbsent(Long.class, k -> "long");
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("Null arguments and clear method work as expected")
  void testNullHandlingAndClear() {
    BoundedWeakClassCache<String> cache = new BoundedWeakClassCache<>(5);

    assertThat(cache.get(null)).isNull();
    assertThatThrownBy(() -> cache.put(null, "val")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> cache.put(String.class, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> cache.computeIfAbsent(null, k -> "val"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> cache.computeIfAbsent(String.class, null))
        .isInstanceOf(NullPointerException.class);

    cache.put(String.class, "val");
    assertThat(cache.size()).isEqualTo(1);
    cache.clear();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache.get(String.class)).isNull();
  }
}
