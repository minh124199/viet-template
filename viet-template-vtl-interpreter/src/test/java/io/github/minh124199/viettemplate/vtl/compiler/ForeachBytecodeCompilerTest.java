package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForeachBytecodeCompilerTest {

  @Test
  @DisplayName("Verifies nested foreach parent metadata parity between IR and AOT backends")
  void testNestedForeachParentMetadataAotAndIrParity() throws IOException {
    String templateSource =
        "#foreach($o in [1..2])#foreach($i in"
            + " [1..3])[$foreach.parent.count:$o-$foreach.count:$i]#end#end";
    String expected = "[1:1-1:1][1:1-2:2][1:1-3:3][2:2-1:1][2:2-2:2][2:2-3:3]";

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("nested_foreach.vm", templateSource);

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .build()) {

      Template irTemplate = irEngine.get("nested_foreach.vm");
      Template aotTemplate = aotEngine.get("nested_foreach.vm");

      StringTemplateOutput irOut = new StringTemplateOutput();
      irTemplate.render(RenderContext.empty(), irOut);

      StringTemplateOutput aotOut = new StringTemplateOutput();
      aotTemplate.render(RenderContext.empty(), aotOut);

      assertThat(irOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(irOut.toString());
    }
  }

  @Test
  @DisplayName("Verifies loop-owned local variables do not leak across iterations in IR and AOT")
  void testLoopOwnedLocalsDoNotLeakAcrossIterationsInIrAndAot() throws IOException {
    String templateSource = "#foreach($item in $items)#set($local = $item.val)[$!local]#end";
    String expected = "[first][]";

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("loop_locals.vm", templateSource);

    VtlInterpreterOptions options = VtlInterpreterOptions.builder().setNullAllowed(false).build();

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.IR)
                .interpreterOptions(options)
                .build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .interpreterOptions(options)
                .build()) {

      Template irTemplate = irEngine.get("loop_locals.vm");
      Template aotTemplate = aotEngine.get("loop_locals.vm");

      RenderContext ctx =
          RenderContext.of(Map.of("items", List.of(Map.of("val", "first"), Map.of())));

      StringTemplateOutput irOut = new StringTemplateOutput();
      irTemplate.render(ctx, irOut);

      StringTemplateOutput aotOut = new StringTemplateOutput();
      aotTemplate.render(ctx, aotOut);

      assertThat(irOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(irOut.toString());
    }
  }

  @Test
  @DisplayName("Verifies loop-owned local variables reset on loop exit in IR and AOT")
  void testLoopOwnedLocalsResetOnLoopExitInIrAndAot() throws IOException {
    String templateSource = "#foreach($i in [1..2])#set($local = $i)#end[$!local]";
    String expected = "[]";

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("loop_exit.vm", templateSource);

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .build()) {

      Template irTemplate = irEngine.get("loop_exit.vm");
      Template aotTemplate = aotEngine.get("loop_exit.vm");

      StringTemplateOutput irOut = new StringTemplateOutput();
      irTemplate.render(RenderContext.empty(), irOut);

      StringTemplateOutput aotOut = new StringTemplateOutput();
      aotTemplate.render(RenderContext.empty(), aotOut);

      assertThat(irOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(irOut.toString());
    }
  }

  @Test
  @DisplayName("Verifies outer locals persist across iterations and after loop in IR and AOT")
  void testOuterLocalsPersistAcrossIterationsAndAfterLoopInIrAndAot() throws IOException {
    String templateSource =
        "#set($outer = 'init')#foreach($i in [1..2])#if($i == 1)#set($outer ="
            + " 'changed')#end[$outer]#end[$outer]";
    String expected = "[changed][changed][changed]";

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("outer_locals.vm", templateSource);

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .build()) {

      Template irTemplate = irEngine.get("outer_locals.vm");
      Template aotTemplate = aotEngine.get("outer_locals.vm");

      StringTemplateOutput irOut = new StringTemplateOutput();
      irTemplate.render(RenderContext.empty(), irOut);

      StringTemplateOutput aotOut = new StringTemplateOutput();
      aotTemplate.render(RenderContext.empty(), aotOut);

      assertThat(irOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(expected);
      assertThat(aotOut.toString()).isEqualTo(irOut.toString());
    }
  }
}
