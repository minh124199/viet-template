package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class HelperObjectTest {

  public static final class EscapeHelper {
    public String html(String input) {
      if (input == null) return "";
      return input
          .replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")
          .replace("\"", "&quot;");
    }

    public String xml(String input) {
      return html(input);
    }

    public String javascript(String input) {
      if (input == null) return "";
      return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }

    public String url(String input) {
      if (input == null) return "";
      return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }
  }

  public static final class MathHelper {
    public int add(int a, int b) {
      return a + b;
    }

    public int sub(int a, int b) {
      return a - b;
    }

    public int mul(int a, int b) {
      return a * b;
    }

    public double div(double a, double b) {
      return a / b;
    }

    public long round(double a) {
      return Math.round(a);
    }
  }

  public static final class NumberHelper {
    public String currency(double amount) {
      return String.format(Locale.US, "$%.2f", amount);
    }
  }

  public static final class DateHelper {
    public String format(LocalDate date, String pattern) {
      if (date == null || pattern == null) return "";
      return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    public int year(LocalDate date) {
      return date != null ? date.getYear() : 0;
    }
  }

  public static final class SortHelper {
    public <T extends Comparable<? super T>> List<T> sort(List<T> list) {
      if (list == null) return List.of();
      List<T> copy = new ArrayList<>(list);
      Collections.sort(copy);
      return copy;
    }
  }

  public static final class DisplayHelper {
    public String capitalize(String input) {
      if (input == null || input.isEmpty()) return "";
      return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    public String truncate(String input, int maxLen) {
      if (input == null) return "";
      if (input.length() <= maxLen) return input;
      return input.substring(0, maxLen) + "...";
    }
  }

  @Test
  void contributesAndExecutesVelocityToolLikeHelpers() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "tools.vm",
        "Esc: $esc.html($rawHtml)\n"
            + "Math: $math.add(7, 3), $math.mul(4, 5)\n"
            + "Number: $number.currency($price)\n"
            + "Date: $date.format($today, 'yyyy/MM/dd'), Year=$date.year($today)\n"
            + "Display: $display.capitalize('vietnam') | $display.truncate('Very Long Description"
            + " Here', 9)\n"
            + "Sort: #foreach($item in $sort.sort($tags))$item #end");

    RenderContextContributor toolsContributor =
        (target, request) -> {
          target.put("esc", new EscapeHelper());
          target.put("math", new MathHelper());
          target.put("number", new NumberHelper());
          target.put("date", new DateHelper());
          target.put("sort", new SortHelper());
          target.put("display", new DisplayHelper());
        };

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(toolsContributor)
            .build();

    RenderContext userModel =
        RenderContext.builder()
            .put("rawHtml", "<div class=\"alert\">Warning</div>")
            .put("price", 1234.50)
            .put("today", LocalDate.of(2026, 9, 7))
            .put("tags", List.of("zebra", "apple", "mango"))
            .build();

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("tools.vm"), userModel, out);

    String expected =
        "Esc: &lt;div class=&quot;alert&quot;&gt;Warning&lt;/div&gt;\n"
            + "Math: 10, 20\n"
            + "Number: $1234.50\n"
            + "Date: 2026/09/07, Year=2026\n"
            + "Display: Vietnam | Very Long...\n"
            + "Sort: apple mango zebra ";

    assertThat(out.toString()).isEqualTo(expected);
    engine.close();
  }
}
