package io.github.minh124199.viettemplate.benchmarks.output.prototype;

/** Synchronized array-backed LIFO stack bounded buffer pool prototype. */
public final class SynchronizedArrayStackPool implements BufferPool {

  public static final int BUFFER_SIZE = 8192;

  private final byte[][] slots;
  private int count;

  public SynchronizedArrayStackPool(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.slots = new byte[capacity][];
    this.count = 0;
  }

  public SynchronizedArrayStackPool(int capacity, boolean prefill) {
    this(capacity);
    if (prefill) {
      for (int i = 0; i < capacity; i++) {
        this.slots[i] = new byte[BUFFER_SIZE];
      }
      this.count = capacity;
    }
  }

  @Override
  public synchronized byte[] tryAcquire() {
    if (count > 0) {
      byte[] buf = slots[--count];
      slots[count] = null;
      return buf;
    }
    return null;
  }

  @Override
  public synchronized boolean release(byte[] buf) {
    if (buf != null && buf.length == BUFFER_SIZE && count < slots.length) {
      slots[count++] = buf;
      return true;
    }
    return false;
  }

  @Override
  public int capacity() {
    return slots.length;
  }

  @Override
  public synchronized int size() {
    return count;
  }
}
