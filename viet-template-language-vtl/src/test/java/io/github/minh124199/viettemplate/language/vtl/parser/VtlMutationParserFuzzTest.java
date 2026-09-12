package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlMutationParserFuzzTest {

  private static final long SEED = 0x807A7101L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  private static final String[] BASE_VALID_TEMPLATES = {
    "Hello $name, welcome to $place!",
    "#set($total = $price * $quantity) Total: $total",
    "#if($user.active) Active: $user.name #elseif($user.pending) Pending #else Inactive #end",
    "#foreach($item in $items) Item: $item ($foreach.index) #end",
    "#macro(renderCard $title $body) <div class=\"card\"><h3>$title</h3><p>$body</p></div> #end"
        + " #renderCard('T', 'B')",
    "#define($block) Content with $val and #( 1 + 2 ) #end $block",
    "Escaped: \\$foo and \\\\$bar and \\#if",
    "#[[ raw content with unparsed $var and #if ]]#",
    "$list[0] and $map['key'] and $map.get('key')",
    "#foreach($i in [1..5]) #if($i == 3) #break #end $i #end",
    "Truthiness: #if($items && !$items.isEmpty()) Has items #end"
  };

  @Test
  @DisplayName("P1: Mutation fuzzing over valid templates fails safely and deterministically")
  void mutationFuzzingNeverCrashes() {
    SplittableRandom rng = new SplittableRandom(SEED);
    int iterations = isDeepMode() ? 4000 : 600;

    VtlParserOptions options = VtlParserOptions.DEFAULT;

    for (int i = 0; i < iterations; i++) {
      String base = BASE_VALID_TEMPLATES[rng.nextInt(BASE_VALID_TEMPLATES.length)];
      String mutated = applyMutations(base, rng);

      SourceText source = SourceText.of("mutated_" + i + ".vtl", mutated);

      try {
        VtlParseResult result = VtlParser.parse(source, options);
        assertThat(result).isNotNull();
      } catch (TemplateException expected) {
        assertThat(expected.getMessage()).isNotNull();
      } catch (Throwable unexpected) {
        fail(
            String.format(
                "Parser crashed on mutation fuzz iteration %d!%n"
                    + "Seed: 0x%X%n"
                    + "Base:%n%s%n"
                    + "Mutated:%n%s",
                i, SEED, base, mutated),
            unexpected);
      }
    }
  }

  private static String applyMutations(String base, SplittableRandom rng) {
    StringBuilder sb = new StringBuilder(base);
    int mutationCount = 1 + rng.nextInt(4);

    for (int m = 0; m < mutationCount; m++) {
      if (sb.isEmpty()) {
        sb.append("$x");
        continue;
      }
      int op = rng.nextInt(12);
      int idx = rng.nextInt(sb.length());

      switch (op) {
        case 0 -> sb.deleteCharAt(idx); // delete char
        case 1 -> sb.insert(idx, sb.charAt(idx)); // duplicate char
        case 2 -> sb.setCharAt(idx, (char) ('a' + rng.nextInt(26))); // replace char
        case 3 -> sb.insert(idx, '#'); // insert #
        case 4 -> sb.insert(idx, '$'); // insert $
        case 5 -> sb.insert(idx, rng.nextBoolean() ? '"' : '\''); // insert quote
        case 6 -> sb.insert(idx, '\\'); // insert backslash
        case 7 -> sb.setLength(idx); // truncate source
        case 8 -> sb.insert(idx, "\0"); // insert NUL
        case 9 -> sb.insert(idx, "\u0300"); // insert combining grave accent
        case 10 -> sb.insert(idx, "\uD83D\uDE00"); // insert emoji
        case 11 -> {
          // insert surrogate or delimiter swap
          if (rng.nextBoolean()) {
            sb.insert(idx, "\uD800"); // unpaired high surrogate
          } else {
            char swap = sb.charAt(idx) == '#' ? '$' : (sb.charAt(idx) == '$' ? '#' : '}');
            sb.setCharAt(idx, swap);
          }
        }
      }
    }
    return sb.toString();
  }
}
