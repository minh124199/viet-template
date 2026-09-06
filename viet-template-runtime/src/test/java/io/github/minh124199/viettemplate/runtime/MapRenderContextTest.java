package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MapRenderContextTest {

  @Test
  void providesVariablesViaBuilder() {
    MapRenderContext ctx =
        MapRenderContext.builder().put("title", "Viet Template").put("count", 10).build();

    assertThat(ctx.contains("title")).isTrue();
    assertThat(ctx.get("title")).isEqualTo("Viet Template");
    assertThat(ctx.contains("count")).isTrue();
    assertThat(ctx.get("count")).isEqualTo(10);
    assertThat(ctx.contains("nonExistent")).isFalse();
    assertThat(ctx.get("nonExistent")).isNull();
    assertThat(ctx.find("title")).contains("Viet Template");
    assertThat(ctx.find("nonExistent")).isEmpty();
  }

  @Test
  void isImmutable() {
    MapRenderContext ctx = MapRenderContext.of("key", "value");
    assertThatThrownBy(() -> ctx.asMap().put("key2", "value2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
