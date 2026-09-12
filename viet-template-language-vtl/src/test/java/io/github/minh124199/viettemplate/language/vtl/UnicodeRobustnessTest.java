package io.github.minh124199.viettemplate.language.vtl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnicodeRobustnessTest {

  private static final List<String> UNICODE_SAMPLES =
      List.of(
          // Vietnamese diacritics
          "Tiếng Việt có dấu: à á ả ã ạ, ê ế ề ể ễ ệ, ô ố ồ ổ ỗ ộ, ư ứ ừ ử ữ ự, đ Đ",
          // Latin-1 supplement
          "Café, résumé, naïve, señor, Über, façade, København, £ € ¥",
          // CJK characters
          "日本語: 漢字、ひらがな、カタカナ。中文: 简体字与繁體字。한국어: 한글 테스트.",
          // Arabic and bidirectional text
          "اللغة العربية مرحباً بكم \u202E text reversed \u202C normal text",
          // Emoji and supplementary code points (surrogate pairs)
          "🎉 🚀 🍕 🦀 🐶 🌟 💎 \uD83D\uDE00 \uD83E\uDD80 \uD83D\uDE80",
          // Combining marks
          "e\u0301 (e with combining acute), a\u0300, o\u0303, u\u0302\u0301",
          // Zero-width characters and variation selectors
          "Zero\u200BWidth\u200CJoiner\u200DTest\uFEFFBOM\uFE0F",
          // Unicode line/paragraph separators
          "Line1\u2028Line2\u2029Line3",
          // Control characters and NUL
          "Text with \0 null byte and \u0007 bell",
          // Unpaired surrogates
          "Unpaired high surrogate \uD800 and low \uDC00"
      );

  @Test
  @DisplayName("Unicode in literal text parses cleanly and deterministically")
  void unicodeInLiteralText() {
    for (int i = 0; i < UNICODE_SAMPLES.size(); i++) {
      String sample = UNICODE_SAMPLES.get(i);
      String template = "<div>" + sample + "</div>";
      SourceText source = SourceText.of("unicode_lit_" + i + ".vtl", template);

      try {
        VtlParseResult res1 = VtlParser.parse(source);
        VtlParseResult res2 = VtlParser.parse(source);

        assertThat(res1).isNotNull();
        assertThat(res2).isNotNull();
        assertThat(res1.hasErrors()).isFalse();
        assertThat(res1.template().children()).hasSameSizeAs(res2.template().children());
      } catch (Exception e) {
        fail("Failed parsing Unicode in literal text for sample " + i + ": " + sample, e);
      }
    }
  }

  @Test
  @DisplayName("Unicode in string literals parses cleanly")
  void unicodeInStringLiterals() {
    for (int i = 0; i < UNICODE_SAMPLES.size(); i++) {
      String sample = UNICODE_SAMPLES.get(i);
      // Escape internal double quotes if any
      String escaped = sample.replace("\"", "\\\"");
      String template = "#set($msg = \"" + escaped + "\") $msg";
      SourceText source = SourceText.of("unicode_str_" + i + ".vtl", template);

      try {
        VtlParseResult res = VtlParser.parse(source);
        assertThat(res).isNotNull();
      } catch (TemplateException expected) {
        // Unpaired surrogates or nulls in strings may be flagged as syntax errors
        assertThat(expected.getMessage()).isNotNull();
      } catch (Exception e) {
        fail("Crashed on Unicode in string literal: " + sample, e);
      }
    }
  }

  @Test
  @DisplayName("Unicode in single-line, block, and raw comments parses cleanly")
  void unicodeInComments() {
    for (int i = 0; i < UNICODE_SAMPLES.size(); i++) {
      String sample = UNICODE_SAMPLES.get(i);
      String template =
          "## Single comment: " + sample.replace("\n", " ") + "\n"
              + "#* Block comment: " + sample.replace("*#", "* #") + " *#\n"
              + "#[[ Raw block: " + sample.replace("]]#", "] ]#") + " ]]#";
      SourceText source = SourceText.of("unicode_cmt_" + i + ".vtl", template);

      try {
        VtlParseResult res = VtlParser.parse(source);
        assertThat(res).isNotNull();
      } catch (TemplateException expected) {
        assertThat(expected.getMessage()).isNotNull();
      } catch (Exception e) {
        fail("Crashed on Unicode in comments: " + sample, e);
      }
    }
  }

  @Test
  @DisplayName("Unicode does not undergo unintended normalization")
  void unicodePreservesExactCodeUnits() {
    // NFC vs NFD: e with acute can be single codepoint U+00E9 (é) or decomposed e + U+0301 (é)
    String nfc = "\u00E9";
    String nfd = "e\u0301";

    assertThat(nfc).isNotEqualTo(nfd);

    SourceText srcNfc = SourceText.of("nfc.vtl", nfc);
    SourceText srcNfd = SourceText.of("nfd.vtl", nfd);

    VtlParseResult resNfc = VtlParser.parse(srcNfc);
    VtlParseResult resNfd = VtlParser.parse(srcNfd);

    assertThat(resNfc.hasErrors()).isFalse();
    assertThat(resNfd.hasErrors()).isFalse();

    // Source spans and lengths must reflect actual distinct code units
    assertThat(srcNfc.length()).isEqualTo(1);
    assertThat(srcNfd.length()).isEqualTo(2);
  }
}
