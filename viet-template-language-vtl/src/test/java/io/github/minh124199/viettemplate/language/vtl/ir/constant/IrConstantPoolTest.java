package io.github.minh124199.viettemplate.language.vtl.ir.constant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IrConstantPoolTest {

  @Test
  @DisplayName("registers text and returns stable ID")
  void registerText() {
    IrConstantPool pool = new IrConstantPool();
    SourceSpan span1 = new SourceSpan(0, 5, 1, 1, 1, 6);
    int id1 = pool.registerText("hello", span1);

    assertThat(id1).isEqualTo(0);
    assertThat(pool.size()).isEqualTo(1);
    assertThat(pool.getTextConstant(0)).isPresent();
    assertThat(pool.getTextConstant(0).get().text()).isEqualTo("hello");
    assertThat(pool.getTextConstant(0).get().utf8Bytes()).isPresent();
    assertThat(pool.getTextConstant(0).get().utf8Bytes().get())
        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("deduplicates identical text chunks")
  void deduplicatesText() {
    IrConstantPool pool = new IrConstantPool();
    SourceSpan span1 = new SourceSpan(0, 5, 1, 1, 1, 6);
    SourceSpan span2 = new SourceSpan(10, 15, 2, 1, 2, 6);

    int id1 = pool.registerText("hello", span1);
    int id2 = pool.registerText("hello", span2);
    int id3 = pool.registerText("world", span2);

    assertThat(id1).isEqualTo(0);
    assertThat(id2).isEqualTo(0);
    assertThat(id3).isEqualTo(1);
    assertThat(pool.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("returns empty for invalid IDs")
  void invalidIdReturnsEmpty() {
    IrConstantPool pool = new IrConstantPool();
    assertThat(pool.getTextConstant(-1)).isEmpty();
    assertThat(pool.getTextConstant(0)).isEmpty();
    assertThat(pool.getTextConstant(99)).isEmpty();
  }
}
