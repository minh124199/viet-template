package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.SourceSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspPositionEncodingTest {

  @Test
  @DisplayName("ASCII coordinate mapping across multiple lines")
  void testAsciiCoordinateMapping() {
    String text = "alpha\nbravo\ncharlie";
    TemplateDocument doc = new TemplateDocument("file:///test-ascii.vt", 1, text);

    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(5, doc.positionToOffset(Position.of(0, 5)));
    assertEquals(6, doc.positionToOffset(Position.of(1, 0)));
    assertEquals(11, doc.positionToOffset(Position.of(1, 5)));
    assertEquals(12, doc.positionToOffset(Position.of(2, 0)));
    assertEquals(19, doc.positionToOffset(Position.of(2, 7)));

    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(0, 5), doc.offsetToPosition(5));
    assertEquals(Position.of(1, 0), doc.offsetToPosition(6));
    assertEquals(Position.of(1, 5), doc.offsetToPosition(11));
    assertEquals(Position.of(2, 0), doc.offsetToPosition(12));
    assertEquals(Position.of(2, 7), doc.offsetToPosition(19));
  }

  @Test
  @DisplayName("BMP non-ASCII Vietnamese diacritics coordinate mapping")
  void testVietnameseDiacriticsMapping() {
    // "Tiếng Việt" -> 'T'(0), 'i'(1), 'ế'(2), 'n'(3), 'g'(4), ' '(5), 'V'(6), 'i'(7), 'ệ'(8),
    // 't'(9)
    String text = "Tiếng Việt\nĐất nước hình chữ S";
    TemplateDocument doc = new TemplateDocument("file:///test-vn.vt", 1, text);

    int idxE = text.indexOf("ế");
    assertEquals(2, idxE);
    Position posE = doc.offsetToPosition(idxE);
    assertEquals(0, posE.line());
    assertEquals(2, posE.character());
    assertEquals(idxE, doc.positionToOffset(posE));

    int idxViet = text.indexOf("Việt");
    Position posViet = doc.offsetToPosition(idxViet);
    assertEquals(0, posViet.line());
    assertEquals(6, posViet.character());
    assertEquals(idxViet, doc.positionToOffset(posViet));

    int line2Start = text.indexOf("Đất nước");
    assertEquals(11, line2Start);
    Position posLine2 = doc.offsetToPosition(line2Start);
    assertEquals(1, posLine2.line());
    assertEquals(0, posLine2.character());
    assertEquals(line2Start, doc.positionToOffset(posLine2));

    assertEquals("Tiếng Việt", doc.lineContent(0));
    assertEquals("Đất nước hình chữ S", doc.lineContent(1));
  }

  @Test
  @DisplayName("Supplementary Unicode characters / surrogate pairs (emojis)")
  void testSurrogatePairsAndEmojis() {
    // 😀 is \uD83D\uDE00 (length 2 chars)
    // 🚀 is \uD83D\uDE80 (length 2 chars)
    // 🇻🇳 is \uD83C\uDDFB\uD83C\uDDF3 (length 4 chars)
    String line0 = "Hello \uD83D\uDE00 World \uD83D\uDE80!";
    String line1 = "Vietnam \uD83C\uDDFB\uD83C\uDDF3 template";
    String text = line0 + "\n" + line1;
    TemplateDocument doc = new TemplateDocument("file:///test-surrogate.vt", 1, text);

    int emoji1Start = text.indexOf("\uD83D\uDE00");
    assertEquals(6, emoji1Start);
    assertEquals(Position.of(0, 6), doc.offsetToPosition(emoji1Start));
    assertEquals(Position.of(0, 8), doc.offsetToPosition(emoji1Start + 2));
    assertEquals(emoji1Start, doc.positionToOffset(Position.of(0, 6)));
    assertEquals(emoji1Start + 2, doc.positionToOffset(Position.of(0, 8)));

    int rocketStart = text.indexOf("\uD83D\uDE80");
    assertEquals(15, rocketStart);
    assertEquals(Position.of(0, 15), doc.offsetToPosition(rocketStart));
    assertEquals(Position.of(0, 17), doc.offsetToPosition(rocketStart + 2));

    int flagStart = text.indexOf("\uD83C\uDDFB\uD83C\uDDF3");
    assertEquals(line0.length() + 1 + 8, flagStart);
    assertEquals(Position.of(1, 8), doc.offsetToPosition(flagStart));
    assertEquals(Position.of(1, 12), doc.offsetToPosition(flagStart + 4));
  }

  @Test
  @DisplayName("CRLF line endings and mixed line endings")
  void testCrlfAndMixedLineEndings() {
    String text = "first line\r\nsecond line\nthird line\r\n";
    TemplateDocument doc = new TemplateDocument("file:///test-mixed.vt", 1, text);

    assertEquals(4, doc.lineCount());
    assertEquals("first line", doc.lineContent(0));
    assertEquals("second line", doc.lineContent(1));
    assertEquals("third line", doc.lineContent(2));
    assertEquals("", doc.lineContent(3));

    // First line: starts at 0, content length 10
    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(10, doc.positionToOffset(Position.of(0, 10)));
    // Offset into CRLF or past line end clamps to 10
    assertEquals(10, doc.positionToOffset(Position.of(0, 11)));
    assertEquals(10, doc.positionToOffset(Position.of(0, 50)));

    // Second line starts at offset 12 (0 + 10 + 2)
    assertEquals(12, doc.positionToOffset(Position.of(1, 0)));
    assertEquals(23, doc.positionToOffset(Position.of(1, 11)));

    // Third line starts at offset 24 (12 + 11 + 1)
    assertEquals(24, doc.positionToOffset(Position.of(2, 0)));
    assertEquals(34, doc.positionToOffset(Position.of(2, 10)));
  }

  @Test
  @DisplayName("Boundary safety and out-of-range positions never throw exceptions")
  void testBoundarySafety() {
    String text = "hello\nworld";
    TemplateDocument doc = new TemplateDocument("file:///test-bounds.vt", 1, text);

    // Negative positions / out-of-bounds line numbers
    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(11, doc.positionToOffset(Position.of(99, 99)));
    assertEquals(11, doc.positionToOffset(Position.of(2, 0)));

    // Out-of-bounds offsets
    assertEquals(Position.of(0, 0), doc.offsetToPosition(-100));
    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(1, 5), doc.offsetToPosition(1000));

    // lineContent boundary safety
    assertEquals("", doc.lineContent(-1));
    assertEquals("", doc.lineContent(99));
    assertEquals("hello", doc.lineContent(0));
    assertEquals("world", doc.lineContent(1));

    // Unknown span
    Range unknownRange = doc.spanToRange(SourceSpan.UNKNOWN);
    assertEquals(Range.of(0, 0, 0, 0), unknownRange);

    // Range to span boundary safety
    SourceSpan spanBeyond = doc.rangeToSpan(Range.of(0, 0, 100, 100));
    assertTrue(spanBeyond.isKnown());
    assertEquals(0, spanBeyond.startOffset());
    assertEquals(text.length(), spanBeyond.endOffset());

    // Inverted span defensively clamped
    SourceSpan invertedSpan = SourceSpan.UNKNOWN;
    assertEquals(Range.of(0, 0, 0, 0), doc.spanToRange(invertedSpan));
  }

  @Test
  @DisplayName("Span to Range and Range to Span round-trip fidelity")
  void testSpanAndRangeRoundTrip() {
    String text = "name: $user.name\nage: $user.age";
    TemplateDocument doc = new TemplateDocument("file:///test-roundtrip.vt", 1, text);

    // Span for '$user.name' -> starts at 6, ends at 16, line 1 col 7 to col 17
    SourceSpan originalSpan = SourceSpan.of(6, 16, 1, 7, 1, 17);
    Range range = doc.spanToRange(originalSpan);
    assertEquals(Position.of(0, 6), range.start());
    assertEquals(Position.of(0, 16), range.end());

    SourceSpan roundTripSpan = doc.rangeToSpan(range);
    assertEquals(originalSpan.startOffset(), roundTripSpan.startOffset());
    assertEquals(originalSpan.endOffset(), roundTripSpan.endOffset());
    assertEquals(originalSpan.startLine(), roundTripSpan.startLine());
    assertEquals(originalSpan.startColumn(), roundTripSpan.startColumn());
    assertEquals(originalSpan.endLine(), roundTripSpan.endLine());
    assertEquals(originalSpan.endColumn(), roundTripSpan.endColumn());
  }
}
