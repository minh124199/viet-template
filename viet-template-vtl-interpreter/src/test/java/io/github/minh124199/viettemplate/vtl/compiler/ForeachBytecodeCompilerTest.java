package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
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
}
