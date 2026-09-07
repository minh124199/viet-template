package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassLoaderChurnStressTest {

  public static class ChurnTarget {
    public String getVal() {
      return "churn-value";
    }
  }

  private static final class ByteArrayClassLoader extends ClassLoader {
    private final byte[] classBytes;
    private final String className;

    ByteArrayClassLoader(String className, byte[] classBytes) {
      super(ClassLoaderChurnStressTest.class.getClassLoader());
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
      assertNotNull(is, "Could not find class resource: " + resourceName);
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
  void testClassLoaderChurnAndGarbageCollection() throws Exception {
    byte[] classBytes = loadClassBytes(ChurnTarget.class);
    String targetClassName = ChurnTarget.class.getName();

    BoundedWeakClassCache<AccessLink> cache = new BoundedWeakClassCache<>(100);
    DynamicLinker linker = new DynamicLinker();
    MemberKey key = MemberKey.propertyGet("val");

    List<WeakReference<Class<?>>> weakClassRefs = new ArrayList<>();

    // Create multiple ephemeral classloaders and link classes
    for (int i = 0; i < 20; i++) {
      ByteArrayClassLoader cl = new ByteArrayClassLoader(targetClassName, classBytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      weakClassRefs.add(new WeakReference<>(dynamicClass));

      AccessLink link = linker.link(dynamicClass, key);
      assertTrue(link.isOk());

      cache.put(dynamicClass, link);
      assertEquals(link, cache.get(dynamicClass));
    }

    assertEquals(20, cache.size());

    // Trigger GC to exercise weak reference polling
    for (int i = 0; i < 3; i++) {
      System.gc();
      Thread.sleep(20);
    }

    int remaining = cache.size();
    assertTrue(remaining <= 20);
  }

  @Test
  void testBoundedCapacityUnderExtremeChurn() throws Exception {
    byte[] classBytes = loadClassBytes(ChurnTarget.class);
    String targetClassName = ChurnTarget.class.getName();
    int capacity = 15;
    BoundedWeakClassCache<AccessLink> boundedCache = new BoundedWeakClassCache<>(capacity);
    DynamicLinker linker = new DynamicLinker();
    MemberKey key = MemberKey.propertyGet("val");

    for (int i = 0; i < 60; i++) {
      ByteArrayClassLoader cl = new ByteArrayClassLoader(targetClassName, classBytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      AccessLink link = linker.link(dynamicClass, key);
      boundedCache.put(dynamicClass, link);
      assertTrue(
          boundedCache.size() <= capacity,
          "Cache size exceeded max capacity: " + boundedCache.size());
    }

    assertEquals(capacity, boundedCache.size());
  }

  @Test
  void testCallSiteWithClassLoaderChurn() throws Throwable {
    byte[] classBytes = loadClassBytes(ChurnTarget.class);
    String targetClassName = ChurnTarget.class.getName();

    DynamicLinker linker = new DynamicLinker();
    DynamicCallSite callSite =
        new DynamicCallSite(
            999,
            MemberKey.propertyGet("val"),
            LinkerAccessPolicy.standard(),
            linker,
            new LinkerStatistics());

    // Invoke 10 instances from distinct classloaders
    for (int i = 0; i < 10; i++) {
      ByteArrayClassLoader cl = new ByteArrayClassLoader(targetClassName, classBytes);
      Class<?> dynamicClass = cl.loadTargetClass();
      Object instance = dynamicClass.getDeclaredConstructor().newInstance();
      Object result = callSite.invoke(instance);
      assertEquals("churn-value", result);
    }

    assertEquals(DynamicCallSite.State.MEGAMORPHIC, callSite.state());
  }
}
