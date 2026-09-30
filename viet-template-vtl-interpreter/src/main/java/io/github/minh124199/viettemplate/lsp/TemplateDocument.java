package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe, immutable representation of an open template document tracked by the language
 * service.
 *
 * <p>Accurately bridges LSP 0-based UTF-16 {@link Position}s with 0-based character offsets and
 * 1-based {@link SourceSpan} coordinates across LF, CRLF, empty files, and multi-byte / surrogate
 * Unicode characters.
 */
final class TemplateDocument {

  private final String uri;
  private final int version;
  private final String content;
  private final SourceText sourceText;
  private final int[] lineStarts;

  TemplateDocument(String uri, int version, String content) {
    this.uri = Objects.requireNonNull(uri, "uri must not be null");
    this.version = version;
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.sourceText = SourceText.from(content, toTemplateId(uri));
    this.lineStarts = computeLineStarts(content);
  }

  private static TemplateId toTemplateId(String uri) {
    if (uri == null || uri.isBlank()) {
      return TemplateId.of("document.vt");
    }
    try {
      return TemplateId.of(uri);
    } catch (IllegalArgumentException e) {
      String cleaned = uri;
      int schemeIdx = cleaned.indexOf("://");
      if (schemeIdx >= 0) {
        cleaned = cleaned.substring(schemeIdx + 3);
      }
      int colonIdx = cleaned.indexOf(':');
      if (colonIdx >= 0) {
        cleaned = cleaned.substring(colonIdx + 1);
      }
      cleaned = cleaned.replace('\\', '/');
      while (cleaned.startsWith("/")) {
        cleaned = cleaned.substring(1);
      }
      int lastSlash = cleaned.lastIndexOf('/');
      String baseName = lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
      if (baseName.isBlank() || baseName.contains(":") || baseName.contains("..")) {
        baseName = "document.vt";
      }
      try {
        return TemplateId.of(baseName);
      } catch (IllegalArgumentException ex) {
        return TemplateId.of("document.vt");
      }
    }
  }

  private static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0); // Line 0 always begins at index 0

    int len = text.length();
    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      if (c == '\r') {
        if (i + 1 < len && text.charAt(i + 1) == '\n') {
          i++; // Skip \n in CRLF
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

  public String uri() {
    return uri;
  }

  public int version() {
    return version;
  }

  public String content() {
    return content;
  }

  public String text() {
    return content;
  }

  public TemplateDocument withContent(int newVersion, String newContent) {
    return new TemplateDocument(this.uri, newVersion, newContent);
  }

  public SourceText sourceText() {
    return sourceText;
  }

  public int lineCount() {
    return lineStarts.length;
  }

  public int length() {
    return content.length();
  }

  public int lineStartOffset(int line) {
    if (line < 0 || line >= lineStarts.length) {
      throw new IndexOutOfBoundsException(
          String.format("Line %d out of bounds (0..%d)", line, lineStarts.length - 1));
    }
    return lineStarts[line];
  }

  public int lineEndOffset(int line) {
    if (line < 0 || line >= lineStarts.length) {
      throw new IndexOutOfBoundsException(
          String.format("Line %d out of bounds (0..%d)", line, lineStarts.length - 1));
    }
    int nextStart = (line + 1 < lineStarts.length) ? lineStarts[line + 1] : content.length();
    int end = nextStart;
    while (end > lineStarts[line]) {
      char prev = content.charAt(end - 1);
      if (prev == '\n' || prev == '\r') {
        end--;
      } else {
        break;
      }
    }
    return end;
  }

  public String lineContent(int line) {
    int start = lineStartOffset(line);
    int end = lineEndOffset(line);
    return content.substring(start, end);
  }

  public int positionToOffset(Position position) {
    Objects.requireNonNull(position, "position must not be null");
    if (position.line() >= lineStarts.length) {
      return content.length();
    }
    int start = lineStarts[position.line()];
    int end = lineEndOffset(position.line());
    int offset = start + position.character();
    if (offset > end) {
      return end;
    }
    return Math.max(start, offset);
  }

  public Position offsetToPosition(int offset) {
    if (offset <= 0) {
      return Position.of(0, 0);
    }
    if (offset >= content.length()) {
      int lastLine = lineStarts.length - 1;
      int charOffset = content.length() - lineStarts[lastLine];
      return Position.of(lastLine, charOffset);
    }

    int idx = Arrays.binarySearch(lineStarts, offset);
    if (idx >= 0) {
      return Position.of(idx, 0);
    }
    int line = -idx - 2;
    int charOffset = offset - lineStarts[line];
    return Position.of(line, charOffset);
  }

  public Range spanToRange(SourceSpan span) {
    Objects.requireNonNull(span, "span must not be null");
    if (!span.isKnown()) {
      return Range.of(0, 0, 0, 0);
    }
    int startLine = Math.max(0, span.startLine() - 1);
    int startCol = Math.max(0, span.startColumn() - 1);
    int endLine = Math.max(0, span.endLine() - 1);
    int endCol = Math.max(0, span.endColumn() - 1);
    return Range.of(startLine, startCol, endLine, endCol);
  }

  public SourceSpan rangeToSpan(Range range) {
    Objects.requireNonNull(range, "range must not be null");
    int startOffset = positionToOffset(range.start());
    int endOffset = positionToOffset(range.end());
    int startLine = range.start().line() + 1;
    int startCol = range.start().character() + 1;
    int endLine = range.end().line() + 1;
    int endCol = range.end().character() + 1;
    return SourceSpan.of(startOffset, endOffset, startLine, startCol, endLine, endCol);
  }
}
