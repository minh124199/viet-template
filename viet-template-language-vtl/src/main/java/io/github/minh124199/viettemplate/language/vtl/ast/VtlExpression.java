package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;

/** Common sealed interface for all syntactic expressions in the VTL language. */
public sealed interface VtlExpression
    permits VtlReferenceExpression,
        VtlIntegerLiteralExpression,
        VtlDecimalLiteralExpression,
        VtlBooleanLiteralExpression,
        VtlNullLiteralExpression,
        VtlStringLiteralExpression,
        VtlInterpolatedStringExpression,
        VtlListLiteralExpression,
        VtlMapLiteralExpression,
        VtlRangeExpression,
        VtlUnaryExpression,
        VtlBinaryExpression,
        VtlGroupedExpression,
        VtlErrorExpression {

  /** Returns the source span covering this expression. */
  SourceSpan span();
}
