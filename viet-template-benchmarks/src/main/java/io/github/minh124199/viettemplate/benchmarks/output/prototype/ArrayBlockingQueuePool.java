package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import java.util.concurrent.ArrayBlockingQueue;

/** ArrayBlockingQueue-backed bounded buffer pool prototype. */
public final class ArrayBlockingQueuePool implements BufferPool {

  public static final int BUFFER_SIZE = 8192;

  private final ArrayBlockingQueue<byte[]> queue;
  private final int capacity;

  public ArrayBlockingQueuePool(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  public ArrayBlockingQueuePool(int capacity, boolean prefill) {
    this(capacity);
    if (prefill) {
      for (int i = 0; i < capacity; i++) {
        this.queue.offer(new byte[BUFFER_SIZE]);
      }
    }
  }

  @Override
  public byte[] tryAcquire() {
    return queue.poll();
  }

  @Override
  public boolean release(byte[] buf) {
    if (buf != null && buf.length == BUFFER_SIZE) {
      return queue.offer(buf);
    }
    return false;
  }

  @Override
  public int capacity() {
    return capacity;
  }

  @Override
  public int size() {
    return queue.size();
  }
}
