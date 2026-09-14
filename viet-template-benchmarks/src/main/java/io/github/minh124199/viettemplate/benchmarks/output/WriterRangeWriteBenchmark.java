package io.github.minh124199.viettemplate.benchmarks.output;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark suite for Milestone M19.3b.2.1 (Writer Range Write Optimization).
 *
 * <p>Compares three range-write implementations for {@code WriterTemplateOutput}:
 *
 * <ul>
 *   <li>Strategy A (current chunk array): per-invocation {@code new char[Math.min(len, 1024)]}
 *   <li>Strategy B (direct char loop): {@code for (int i = start; i < end; i++)
 *       writer.write(value.charAt(i))}
 *   <li>Strategy C (output-owned buffer): reusable instance {@code char[1024]}
 * </ul>
 *
 * Evaluates performance across diverse input representations ({@code String}, {@code
 * StringBuilder}, {@code StringBuffer}, custom {@code CharSequence}), range sizes (0 to 16,384
 * chars), and realistic HTML segment distributions.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class WriterRangeWriteBenchmark {

  @Param({"CHUNK_ARRAY", "DIRECT_CHAR_LOOP", "OWNED_BUFFER"})
  private String strategy;

  @Param({"STRING", "STRING_BUILDER", "STRING_BUFFER", "CUSTOM"})
  private String inputType;

  @Param({"0", "1", "8", "32", "128", "1024", "4096", "16384"})
  private int rangeSize;

  @Param({"FIXED_SIZE", "REALISTIC_HTML_SMALL", "REALISTIC_HTML_MEDIUM", "REALISTIC_HTML_LARGE"})
  private String distribution;

  @Param({"NOOP", "STRING_WRITER"})
  private String writerType;

  private CharSequence testInput;
  private int sliceStart;
  private int sliceEnd;

  // Reusable writer instances
  private NoOpCountingWriter noOpWriter;
  private ResettableStringWriter stringWriter;

  // Reusable owned buffer for Strategy C
  private final char[] ownedBuffer = new char[1024];

  @Setup(Level.Trial)
  public void setUpTrial() {
    noOpWriter = new NoOpCountingWriter();
    stringWriter = new ResettableStringWriter();

    // Prepare large corpus text (>= 35,000 characters)
    String baseBlock =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Benchmark</title></head>"
            + "<body><div id=\"content\" class=\"main-panel\"><p>Viet Template high-performance"
            + " streaming template engine with zero allocation. Testing range write throughput"
            + " and memory efficiency across large buffer slices.</p><span>Category: Performance"
            + " Optimization</span><ul><li>Item 1: Speed</li><li>Item 2: Low Latency</li>"
            + "<li>Item 3: Predictable GC Pause</li></ul></div></body></html>\n";

    StringBuilder sb = new StringBuilder(40_000);
    while (sb.length() < 35_000) {
      sb.append(baseBlock);
    }
    String fullText = sb.toString();

    switch (inputType) {
      case "STRING" -> testInput = fullText;
      case "STRING_BUILDER" -> testInput = new StringBuilder(fullText);
      case "STRING_BUFFER" -> testInput = new StringBuffer(fullText);
      case "CUSTOM" -> testInput = new CustomCharSequence(fullText);
      default -> throw new IllegalArgumentException("Unknown inputType: " + inputType);
    }

    int baseOffset = 128;
    int len;
    switch (distribution) {
      case "FIXED_SIZE" -> len = rangeSize;
      case "REALISTIC_HTML_SMALL" -> len = 24; // Representative slice in [8, 32]
      case "REALISTIC_HTML_MEDIUM" -> len = 80; // Representative slice in [32, 128]
      case "REALISTIC_HTML_LARGE" -> len = 320; // Representative slice in [128, 512]
      default -> throw new IllegalArgumentException("Unknown distribution: " + distribution);
    }

    sliceStart = baseOffset;
    sliceEnd = Math.min(sliceStart + len, testInput.length());
  }

  @Setup(Level.Invocation)
  public void setUpInvocation() {
    noOpWriter.reset();
    stringWriter.reset();
  }

  private Writer getActiveWriter() {
    return "NOOP".equals(writerType) ? noOpWriter : stringWriter;
  }

  private void consumeOutput(Writer writer, Blackhole bh) {
    if (writer == noOpWriter) {
      bh.consume(noOpWriter.checksum());
    } else {
      bh.consume(stringWriter.getBuffer().length());
    }
  }

  /**
   * Strategy A: Current chunk array transfer allocating {@code new char[Math.min(len, 1024)]} per
   * invocation for non-String inputs.
   */
  public void strategyA_currentChunkArray(Writer writer, CharSequence value, int start, int end)
      throws IOException {
    if (value != null) {
      Objects.checkFromToIndex(start, end, value.length());
      if (value instanceof String s) {
        writer.write(s, start, end - start);
      } else {
        int len = end - start;
        char[] buf = new char[Math.min(len, 1024)];
        int srcIdx = start;
        while (srcIdx < end) {
          int chunk = Math.min(buf.length, end - srcIdx);
          for (int i = 0; i < chunk; i++) {
            buf[i] = value.charAt(srcIdx + i);
          }
          writer.write(buf, 0, chunk);
          srcIdx += chunk;
        }
      }
    }
  }

  /**
   * Strategy B: Direct character iteration delegating each char to {@code writer.write(int)} for
   * non-String inputs without chunk array allocation.
   */
  public void strategyB_directCharLoop(Writer writer, CharSequence value, int start, int end)
      throws IOException {
    if (value != null) {
      Objects.checkFromToIndex(start, end, value.length());
      if (value instanceof String s) {
        writer.write(s, start, end - start);
      } else {
        for (int i = start; i < end; i++) {
          writer.write(value.charAt(i));
        }
      }
    }
  }

  /**
   * Strategy C: Instance output-owned buffer (reusable {@code char[1024]}) for chunked transfers
   * without per-invocation allocations.
   */
  public void strategyC_outputOwnedBuffer(Writer writer, CharSequence value, int start, int end)
      throws IOException {
    if (value != null) {
      Objects.checkFromToIndex(start, end, value.length());
      if (value instanceof String s) {
        writer.write(s, start, end - start);
      } else {
        int srcIdx = start;
        while (srcIdx < end) {
          int chunk = Math.min(ownedBuffer.length, end - srcIdx);
          for (int i = 0; i < chunk; i++) {
            ownedBuffer[i] = value.charAt(srcIdx + i);
          }
          writer.write(ownedBuffer, 0, chunk);
          srcIdx += chunk;
        }
      }
    }
  }

  @Benchmark
  public void writeRange(Blackhole bh) throws IOException {
    Writer writer = getActiveWriter();
    switch (strategy) {
      case "CHUNK_ARRAY" -> strategyA_currentChunkArray(writer, testInput, sliceStart, sliceEnd);
      case "DIRECT_CHAR_LOOP" -> strategyB_directCharLoop(writer, testInput, sliceStart, sliceEnd);
      case "OWNED_BUFFER" -> strategyC_outputOwnedBuffer(writer, testInput, sliceStart, sliceEnd);
      default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
    }
    consumeOutput(writer, bh);
  }

  public void strategyA_currentChunkArray(Blackhole bh) throws IOException {
    Writer writer = getActiveWriter();
    strategyA_currentChunkArray(writer, testInput, sliceStart, sliceEnd);
    consumeOutput(writer, bh);
  }

  public void strategyB_directCharLoop(Blackhole bh) throws IOException {
    Writer writer = getActiveWriter();
    strategyB_directCharLoop(writer, testInput, sliceStart, sliceEnd);
    consumeOutput(writer, bh);
  }

  public void strategyC_outputOwnedBuffer(Blackhole bh) throws IOException {
    Writer writer = getActiveWriter();
    strategyC_outputOwnedBuffer(writer, testInput, sliceStart, sliceEnd);
    consumeOutput(writer, bh);
  }

  /** Non-allocating, counting Writer accumulating written character counts and checksum. */
  public static final class NoOpCountingWriter extends Writer {
    private long charsWritten;
    private long checksum;

    @Override
    public void write(int c) {
      charsWritten++;
      checksum = checksum * 31 + c;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
      charsWritten += len;
      long cs = checksum;
      for (int i = 0; i < len; i++) {
        cs = cs * 31 + cbuf[off + i];
      }
      checksum = cs;
    }

    @Override
    public void write(String str, int off, int len) {
      charsWritten += len;
      long cs = checksum;
      for (int i = 0; i < len; i++) {
        cs = cs * 31 + str.charAt(off + i);
      }
      checksum = cs;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    public void reset() {
      charsWritten = 0;
      checksum = 0;
    }

    public long charsWritten() {
      return charsWritten;
    }

    public long checksum() {
      return checksum;
    }
  }

  /** Resettable wrapper around {@link StringWriter}. */
  public static final class ResettableStringWriter extends StringWriter {
    public void reset() {
      getBuffer().setLength(0);
    }
  }

  /** Custom CharSequence implementation that does not implement special JDK-internal methods. */
  public static final class CustomCharSequence implements CharSequence {
    private final String data;

    public CustomCharSequence(String data) {
      this.data = data;
    }

    @Override
    public int length() {
      return data.length();
    }

    @Override
    public char charAt(int index) {
      return data.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return data.subSequence(start, end);
    }

    @Override
    public String toString() {
      return data;
    }
  }
}
