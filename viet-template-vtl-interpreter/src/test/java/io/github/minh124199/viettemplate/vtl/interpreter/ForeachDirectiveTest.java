package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForeachDirectiveTest extends AbstractInterpreterTest {

  @Test
  void iteratesListAndExposesMetadata() {
    List<String> items = List.of("A", "B", "C");
    Map<String, Object> ctx = Map.of("items", items);

    String template =
        "#foreach($item in"
            + " $items)[$foreach.index:$foreach.count:$item:first=$foreach.first:last=$foreach.last]#end";
    assertThat(render(template, ctx))
        .isEqualTo(
            "[0:1:A:first=true:last=false][1:2:B:first=false:last=false][2:3:C:first=false:last=true]");
  }

  @Test
  void iteratesMapValues() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    Map<String, Object> ctx = Map.of("map", map);

    String template = "#foreach($val in $map)$val #end";
    assertThat(render(template, ctx)).isEqualTo("v1 v2 ");
  }

  @Test
  void iteratesRangesAscendingAndDescending() {
    assertThat(render("#foreach($i in [1..4])$i #end")).isEqualTo("1 2 3 4 ");
    assertThat(render("#foreach($i in [4..1])$i #end")).isEqualTo("4 3 2 1 ");
  }

  @Test
  void executesElseWhenCollectionEmptyOrNull() {
    Map<String, Object> ctx = Map.of("emptyList", List.of());
    String template = "#foreach($item in $emptyList)item#{else}empty#end";
    assertThat(render(template, ctx)).isEqualTo("empty");

    String templateNull = "#foreach($item in $nullList)item#{else}wasNull#end";
    assertThat(render(templateNull)).isEqualTo("wasNull");
  }

  @Test
  void breaksOutOfLoopWithBreakDirective() {
    List<Integer> list = List.of(1, 2, 3, 4, 5);
    Map<String, Object> ctx = Map.of("list", list);

    String template = "#foreach($i in $list)#if($i == 3)#break#end$i #end";
    assertThat(render(template, ctx)).isEqualTo("1 2 ");
  }

  @Test
  void breaksOutOfLoopWithForeachStopMethod() {
    List<Integer> list = List.of(1, 2, 3, 4, 5);
    Map<String, Object> ctx = Map.of("list", list);

    String template = "#foreach($i in $list)#if($i == 3)$foreach.stop()#end$i #end";
    assertThat(render(template, ctx)).isEqualTo("1 2 ");
  }

  @Test
  void supportsNestedLoopsAndParentMetadata() {
    List<String> outer = List.of("X", "Y");
    List<Integer> inner = List.of(1, 2);
    Map<String, Object> ctx = Map.of("outer", outer, "inner", inner);

    String template =
        "#foreach($o in $outer)#foreach($i in $inner)${o}${i}(outerCount=$foreach.parent.count)"
            + " #end#end";
    assertThat(render(template, ctx))
        .isEqualTo("X1(outerCount=1) X2(outerCount=1) Y1(outerCount=2) Y2(outerCount=2) ");
  }

  @Test
  void restoresLoopVariableAfterForeach() {
    Map<String, Object> ctx = Map.of("item", "ORIGINAL", "list", List.of("A", "B"));
    String template = "before:$item;#foreach($item in $list)$item;#end after:$item";
    assertThat(render(template, ctx)).isEqualTo("before:ORIGINAL;A;B; after:ORIGINAL");
  }
}
