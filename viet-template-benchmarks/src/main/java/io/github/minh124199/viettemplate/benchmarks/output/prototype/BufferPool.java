package io.github.minh124199.viettemplate.benchmarks.output.prototype;

/** Benchmark prototype interface for bounded buffer pools. */
public interface BufferPool {

  /**
   * Attempts to acquire an 8192-byte buffer from the pool without blocking or allocating.
   *
   * @return an 8192-byte buffer, or {@code null} if the pool is empty
   */
  byte[] tryAcquire();

  /**
   * Releases an 8192-byte buffer back to the pool if capacity permits.
   *
   * @param buf the buffer to return
   * @return {@code true} if the buffer was accepted back into the pool, {@code false} otherwise
   */
  boolean release(byte[] buf);

  /**
   * Returns the maximum buffer capacity of this pool.
   *
   * @return pool capacity
   */
  int capacity();

  /**
   * Returns the current number of available buffers retained in the pool.
   *
   * @return current pool size
   */
  int size();
}
