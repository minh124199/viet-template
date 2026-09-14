package io.github.minh124199.viettemplate.runtime;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Package-private bounded buffer pool for standard 8 KiB streaming UTF-8 output buffers. Lock-free,
 * strictly bounded capacity, zero per-operation allocation, non-blocking fallback.
 */
final class Utf8BufferPool {
  static final int BUFFER_SIZE = 8192;
  static final int DEFAULT_CAPACITY = 16;

  private static final AtomicReferenceArray<byte[]> POOL =
      new AtomicReferenceArray<>(DEFAULT_CAPACITY);

  private Utf8BufferPool() {}

  static byte[] tryAcquire() {
    int len = POOL.length();
    for (int i = 0; i < len; i++) {
      byte[] buf = POOL.getAndSet(i, null);
      if (buf != null) {
        return buf;
      }
    }
    return null;
  }

  static boolean release(byte[] buf) {
    if (buf == null || buf.length != BUFFER_SIZE) {
      return false;
    }
    int len = POOL.length();
    for (int i = 0; i < len; i++) {
      if (POOL.compareAndSet(i, null, buf)) {
        return true;
      }
    }
    return false;
  }

  static int capacity() {
    return POOL.length();
  }

  static int size() {
    int count = 0;
    int len = POOL.length();
    for (int i = 0; i < len; i++) {
      if (POOL.get(i) != null) {
        count++;
      }
    }
    return count;
  }

  static void resetForTesting() {
    for (int i = 0; i < POOL.length(); i++) {
      POOL.set(i, null);
    }
  }
}
