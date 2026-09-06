package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.math.BigDecimal;
import java.util.Objects;

/** Syntactic floating-point/decimal literal expression (e.g. {@code 3.14159}). */
public record VtlDecimalLiteralExpression(BigDecimal value, String rawText, SourceSpan span)
    implements VtlExpression {

  public VtlDecimalLiteralExpression {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(rawText, "rawText must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlDecimalLiteralExpression of(double value, String rawText, SourceSpan span) {
    return new VtlDecimalLiteralExpression(BigDecimal.valueOf(value), rawText, span);
  }

  public static VtlDecimalLiteralExpression of(String rawText, SourceSpan span) {
    BigDecimal bd;
    try {
      bd = new BigDecimal(rawText);
    } catch (NumberFormatException e) {
      bd = BigDecimal.ZERO;
    }
    return new VtlDecimalLiteralExpression(bd, rawText, span);
  }

  public double doubleValue() {
    return value.doubleValue();
  }
}
