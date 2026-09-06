package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.math.BigInteger;
import java.util.Objects;

/** Syntactic integer literal expression (e.g. {@code 42}, {@code -1}). */
public record VtlIntegerLiteralExpression(BigInteger value, String rawText, SourceSpan span)
    implements VtlExpression {

  public VtlIntegerLiteralExpression {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(rawText, "rawText must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlIntegerLiteralExpression of(long value, String rawText, SourceSpan span) {
    return new VtlIntegerLiteralExpression(BigInteger.valueOf(value), rawText, span);
  }

  public static VtlIntegerLiteralExpression of(String rawText, SourceSpan span) {
    BigInteger bi;
    try {
      bi = new BigInteger(rawText);
    } catch (NumberFormatException e) {
      bi = BigInteger.ZERO;
    }
    return new VtlIntegerLiteralExpression(bi, rawText, span);
  }

  public long longValue() {
    return value.longValue();
  }
}
