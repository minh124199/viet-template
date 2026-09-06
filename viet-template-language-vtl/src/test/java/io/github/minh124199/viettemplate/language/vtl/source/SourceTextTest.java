package io.github.minh124199.viettemplate.language.vtl.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import org.junit.jupiter.api.Test;

class SourceTextTest {

  @Test
  void handlesEmptySource() {
    SourceText src = SourceText.of("empty.vm", "");
    assertThat(src.length()).isEqualTo(0);
    assertThat(src.lineCount()).isEqualTo(1);
    assertThat(src.lineStartOffset(1)).isEqualTo(0);

    SourceSpan span = src.spanAt(0, 0);
    assertThat(span.startOffset()).isEqualTo(0);
    assertThat(span.endOffset()).isEqualTo(0);
    assertThat(span.startLine()).isEqualTo(1);
    assertThat(span.startColumn()).isEqualTo(1);
    assertThat(span.endLine()).isEqualTo(1);
    assertThat(span.endColumn()).isEqualTo(1);
  }

  @Test
  void handlesSingleLineWithoutNewline() {
    SourceText src = SourceText.of("single.vm", "Hello world");
    assertThat(src.length()).isEqualTo(11);
    assertThat(src.lineCount()).isEqualTo(1);
    assertThat(src.charAt(0)).isEqualTo('H');
    assertThat(src.slice(0, 5)).isEqualTo("Hello");
    assertThat(src.substring(6, 11)).isEqualTo("world");

    SourceSpan span = src.spanAt(6, 11);
    assertThat(span.startLine()).isEqualTo(1);
    assertThat(span.startColumn()).isEqualTo(7);
    assertThat(span.endLine()).isEqualTo(1);
    assertThat(span.endColumn()).isEqualTo(12);
  }

  @Test
  void handlesLfLineEndings() {
    // Line 1: "Line 1\n" (0..7)
    // Line 2: "Line 2\n" (7..14)
    // Line 3: "Line 3"   (14..20)
    SourceText src = SourceText.of("lf.vm", "Line 1\nLine 2\nLine 3");
    assertThat(src.lineCount()).isEqualTo(3);
    assertThat(src.lineStartOffset(1)).isEqualTo(0);
    assertThat(src.lineStartOffset(2)).isEqualTo(7);
    assertThat(src.lineStartOffset(3)).isEqualTo(14);

    SourceSpan span = src.spanAt(8, 12); // "ine " in Line 2
    assertThat(span.startLine()).isEqualTo(2);
    assertThat(span.startColumn()).isEqualTo(2); // offset 8 - start 7 + 1 = 2
    assertThat(span.endLine()).isEqualTo(2);
    assertThat(span.endColumn()).isEqualTo(6);
  }

  @Test
  void handlesCrlfLineEndingsAsSingleLogicalBreak() {
    // Line 1: "Line 1\r\n" (0..8)
    // Line 2: "Line 2"     (8..14)
    SourceText src = SourceText.of("crlf.vm", "Line 1\r\nLine 2");
    assertThat(src.lineCount()).isEqualTo(2);
    assertThat(src.lineStartOffset(1)).isEqualTo(0);
    assertThat(src.lineStartOffset(2)).isEqualTo(8);

    SourceSpan span = src.spanAt(8, 14); // "Line 2"
    assertThat(span.startLine()).isEqualTo(2);
    assertThat(span.startColumn()).isEqualTo(1);
    assertThat(span.endLine()).isEqualTo(2);
    assertThat(span.endColumn()).isEqualTo(7);
  }

  @Test
  void handlesCrOnlyLineEndings() {
    // Line 1: "Line 1\r" (0..7)
    // Line 2: "Line 2"   (7..13)
    SourceText src = SourceText.of("cr.vm", "Line 1\rLine 2");
    assertThat(src.lineCount()).isEqualTo(2);
    assertThat(src.lineStartOffset(1)).isEqualTo(0);
    assertThat(src.lineStartOffset(2)).isEqualTo(7);

    SourceSpan span = src.spanAt(7, 13);
    assertThat(span.startLine()).isEqualTo(2);
    assertThat(span.startColumn()).isEqualTo(1);
  }

  @Test
  void handlesTrailingNewlines() {
    SourceText src = SourceText.of("trailing.vm", "Line 1\n");
    assertThat(src.lineCount()).isEqualTo(2);
    assertThat(src.lineStartOffset(1)).isEqualTo(0);
    assertThat(src.lineStartOffset(2)).isEqualTo(7);

    SourceSpan eofSpan = src.spanAt(7, 7);
    assertThat(eofSpan.startLine()).isEqualTo(2);
    assertThat(eofSpan.startColumn()).isEqualTo(1);
  }

  @Test
  void handlesUnicodeAndSurrogatePairs() {
    // "Xin chào 日本語 😀 $name"
    // '😀' is a surrogate pair (2 UTF-16 code units: \uD83D\uDE00)
    String content = "Xin chào 日本語 😀 $name";
    SourceText src = SourceText.of("unicode.vm", content);

    int emojiIdx = content.indexOf("😀");
    SourceSpan emojiSpan = src.spanAt(emojiIdx, emojiIdx + 2);
    assertThat(emojiSpan.length()).isEqualTo(2);
    assertThat(src.substring(emojiSpan.startOffset(), emojiSpan.endOffset())).isEqualTo("😀");

    int dollarIdx = content.indexOf("$");
    SourceSpan dollarSpan = src.spanAt(dollarIdx, dollarIdx + 5); // "$name"
    assertThat(src.substring(dollarSpan.startOffset(), dollarSpan.endOffset())).isEqualTo("$name");
    assertThat(dollarSpan.startLine()).isEqualTo(1);
    assertThat(dollarSpan.startColumn()).isEqualTo(dollarIdx + 1);
  }

  @Test
  void rejectsInvalidBounds() {
    SourceText src = SourceText.of("test.vm", "content");
    assertThatThrownBy(() -> src.spanAt(-1, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> src.spanAt(5, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> src.spanAt(0, 10)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> src.lineStartOffset(0)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> src.lineStartOffset(2)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void supportsFullSpan() {
    SourceText src = SourceText.of("full.vm", "abc\ndef");
    SourceSpan full = src.fullSpan();
    assertThat(full.startOffset()).isEqualTo(0);
    assertThat(full.endOffset()).isEqualTo(7);
    assertThat(full.startLine()).isEqualTo(1);
    assertThat(full.startColumn()).isEqualTo(1);
    assertThat(full.endLine()).isEqualTo(2);
    assertThat(full.endColumn()).isEqualTo(4);
  }
}
