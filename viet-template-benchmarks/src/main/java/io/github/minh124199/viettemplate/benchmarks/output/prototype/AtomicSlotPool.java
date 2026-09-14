package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** Lock-free atomic reference array bounded buffer pool prototype. */
public final class AtomicSlotPool implements BufferPool {

  public static final int BUFFER_SIZE = 8192;

  private final AtomicReferenceArray<byte[]> slots;

  public AtomicSlotPool(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.slots = new AtomicReferenceArray<>(capacity);
  }

  public AtomicSlotPool(int capacity, boolean prefill) {
    this(capacity);
    if (prefill) {
      for (int i = 0; i < capacity; i++) {
        this.slots.set(i, new byte[BUFFER_SIZE]);
      }
    }
  }

  @Override
  public byte[] tryAcquire() {
    int len = slots.length();
    for (int i = 0; i < len; i++) {
      byte[] buf = slots.getAndSet(i, null);
      if (buf != null) {
        return buf;
      }
    }
    return null;
  }

  @Override
  public boolean release(byte[] buf) {
    if (buf == null || buf.length != BUFFER_SIZE) {
      return false;
    }
    int len = slots.length();
    for (int i = 0; i < len; i++) {
      if (slots.compareAndSet(i, null, buf)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int capacity() {
    return slots.length();
  }

  @Override
  public int size() {
    int count = 0;
    int len = slots.length();
    for (int i = 0; i < len; i++) {
      if (slots.get(i) != null) {
        count++;
      }
    }
    return count;
  }
}
