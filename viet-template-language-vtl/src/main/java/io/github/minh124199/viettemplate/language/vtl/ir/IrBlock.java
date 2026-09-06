package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** An ordered sequence of IR statements executed in sequence. */
public record IrBlock(List<IrStatement> statements, SourceSpan span)
    implements Iterable<IrStatement> {

  public IrBlock {
    statements = List.copyOf(Objects.requireNonNull(statements, "statements must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrBlock empty(SourceSpan span) {
    return new IrBlock(List.of(), span);
  }

  public static IrBlock of(SourceSpan span, IrStatement... statements) {
    return new IrBlock(List.of(statements), span);
  }

  @Override
  public Iterator<IrStatement> iterator() {
    return statements.iterator();
  }

  public int size() {
    return statements.size();
  }

  public boolean isEmpty() {
    return statements.isEmpty();
  }
}
