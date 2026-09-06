package io.github.minh124199.viettemplate.language.vtl.source;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable source text abstraction with pre-computed line-start index for efficient,
 * allocation-free coordinate and span resolution.
 *
 * <p>Source offsets represent 0-indexed UTF-16 code units matching Java {@link String} indexing.
 * Line and column numbers are 1-based. Line terminators LF ('\n'), CRLF ("\r\n"), and CR ('\r') are
 * recognized without altering original source text.
 */
public final class SourceText implements CharSequence {

  private final TemplateId templateId;
  private final String content;
  private final int[] lineStarts;

  private SourceText(TemplateId templateId, String content) {
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.lineStarts = computeLineStarts(content);
  }

  public static SourceText of(TemplateId templateId, String content) {
    return new SourceText(templateId, content);
  }

  public static SourceText of(String templateName, String content) {
    return new SourceText(TemplateId.of(templateName), content);
  }

  public static SourceText of(String content) {
    return new SourceText(TemplateId.of("anonymous"), content);
  }

  public static SourceText from(String content, TemplateId templateId) {
    return new SourceText(templateId, content);
  }

  private static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0); // Line 1 always starts at index 0

    int len = text.length();
    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      if (c == '\r') {
        if (i + 1 < len && text.charAt(i + 1) == '\n') {
          // CRLF: Advance past the '\n'
          i++;
        }
        starts.add(i + 1);
      } else if (c == '\n') {
        starts.add(i + 1);
      }
    }

    int[] array = new int[starts.size()];
    for (int i = 0; i < starts.size(); i++) {
      array[i] = starts.get(i);
    }
    return array;
  }

  public TemplateId templateId() {
    return templateId;
  }

  public String content() {
    return content;
  }

  @Override
  public int length() {
    return content.length();
  }

  @Override
  public char charAt(int index) {
    return content.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return content.subSequence(start, end);
  }

  public CharSequence slice(int start, int end) {
    return content.subSequence(start, end);
  }

  public String substring(int start, int end) {
    return content.substring(start, end);
  }

  public int lineCount() {
    return lineStarts.length;
  }

  public int lineStartOffset(int line) {
    if (line < 1 || line > lineStarts.length) {
      throw new IndexOutOfBoundsException(
          String.format("Line %d out of bounds (1..%d)", line, lineStarts.length));
    }
    return lineStarts[line - 1];
  }

  public SourceSpan spanAt(int startOffset, int endOffset) {
    if (startOffset < 0 || endOffset < startOffset || endOffset > content.length()) {
      throw new IndexOutOfBoundsException(
          String.format(
              "Span [%d..%d] out of bounds for source length %d",
              startOffset, endOffset, content.length()));
    }

    int startLineIdx = findLineIndex(startOffset);
    int startLine = startLineIdx + 1;
    int startCol = (startOffset - lineStarts[startLineIdx]) + 1;

    int endLineIdx = findLineIndex(endOffset);
    int endLine = endLineIdx + 1;
    int endCol = (endOffset - lineStarts[endLineIdx]) + 1;

    return SourceSpan.of(startOffset, endOffset, startLine, startCol, endLine, endCol);
  }

  public SourceSpan fullSpan() {
    return spanAt(0, content.length());
  }

  private int findLineIndex(int offset) {
    int idx = Arrays.binarySearch(lineStarts, offset);
    if (idx >= 0) {
      return idx;
    }
    // binarySearch returns -(insertionPoint) - 1 when not found.
    // insertionPoint is the index of the first element greater than key.
    // The line containing offset is (insertionPoint - 1).
    int insertionPoint = -idx - 1;
    return Math.max(0, insertionPoint - 1);
  }

  @Override
  public String toString() {
    return content;
  }
}
