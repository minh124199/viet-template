package io.github.minh124199.viettemplate.tck.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Deterministic adversarial fuzz test ensuring parser and execution engine stability. */
class SecurityFuzzTest {

  private static final long SEED = 0x5EC0717EL;

  private static final String[] ADVERSARIAL_SNIPPETS = {
    "$",
    "$$",
    "$!",
    "$!{",
    "${}",
    "#",
    "## comment",
    "#* unclosed comment",
    "#* block *#",
    "#[[ unparsed ]]#",
    "#[[ unclosed",
    "#if",
    "#if()",
    "#if(true)",
    "#if($x.y.z.w)",
    "#else",
    "#elseif",
    "#end",
    "#foreach",
    "#foreach($item in $list)",
    "#foreach($item in [])",
    "#set",
    "#set($ = 1)",
    "#set($x = )",
    "#set($x = 1 + )",
    "#set($x = 1 / 0)",
    "#macro",
    "#macro(fn)",
    "#define",
    "#evaluate('$malicious')",
    "#parse('nested.vm')",
    "#include('static.txt')",
    "#stop",
    "#break",
    "\\",
    "\\\\",
    "\\$",
    "\"unclosed string",
    "'unclosed single quote",
    "$obj.class.classLoader",
    "$obj.getClass().getMethods()",
    "$obj.wait()",
    "$obj.notify()",
    "$map['evil\0key']",
    "(((1 + 2) * 3)",
    "\"hello $x.y world\"",
    "'single $x no interpolate'",
    "$null",
    "null",
    "true",
    "false"
  };

  @Test
  @DisplayName("Adversarial parser fuzzing fails cleanly with controlled diagnostics")
  void adversarialParserFuzzingDoesNotCrash() {
    Random random = new Random(SEED);

    VtlParserOptions options =
        VtlParserOptions.DEFAULT
            .withMaxSourceCharacters(10_000)
            .withMaxAstNodes(500)
            .withMaxExpressionDepth(25)
            .withMaxDirectiveNesting(25);

    for (int i = 0; i < 300; i++) {
      int parts = 1 + random.nextInt(8);
      StringBuilder sb = new StringBuilder();
      for (int p = 0; p < parts; p++) {
        sb.append(ADVERSARIAL_SNIPPETS[random.nextInt(ADVERSARIAL_SNIPPETS.length)]);
        if (random.nextBoolean()) {
          sb.append(" ");
        }
      }

      SourceText source = SourceText.of("fuzz_" + i + ".vm", sb.toString());
      try {
        VtlParser.parse(source, options);
      } catch (TemplateException expected) {
        assertThat(expected.getMessage()).isNotNull();
      } catch (Throwable unexpected) {
        throw new AssertionError(
            "Fuzzing crashed on iteration " + i + " input: [" + sb + "]", unexpected);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Adversarial template execution fails safely across all tiers")
  void adversarialExecutionFailsSafelyAcrossTiers(ExecutionTier tier) {
    Random random = new Random(SEED + tier.ordinal());
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    Map<String, Object> testContext = new HashMap<>();
    testContext.put("x", 42);
    testContext.put("str", "hello");
    testContext.put("list", List.of("a", "b"));
    testContext.put("map", Map.of("k", "v"));
    testContext.put("obj", new Object());

    ExecutionLimits limits =
        ExecutionLimits.builder()
            .maxOutputCharacters(5_000)
            .maxLoopIterations(50)
            .maxExecutionTimeMillis(500)
            .build();

    VtlInterpreterOptions opts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .limits(limits)
            .securityPolicy(VtlSecurityPolicy.standard())
            .executionTier(tier)
            .build();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .interpreterOptions(opts)
            .build()) {

      for (int i = 0; i < 150; i++) {
        String templateName = "exec_fuzz_" + tier.name() + "_" + i + ".vm";
        int parts = 1 + random.nextInt(5);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < parts; p++) {
          sb.append(ADVERSARIAL_SNIPPETS[random.nextInt(ADVERSARIAL_SNIPPETS.length)]).append("\n");
        }

        repo.put(templateName, sb.toString());

        try {
          StringTemplateOutput out = new StringTemplateOutput();
          engine.render(TemplateId.of(templateName), RenderContext.of(testContext), out);
          assertThat(out.toString()).isNotNull();
        } catch (TemplateException expected) {
          assertThat(expected.getMessage()).isNotNull();
        } catch (Throwable unexpected) {
          throw new AssertionError(
              "Execution tier " + tier + " crashed on iteration " + i + " input: [" + sb + "]",
              unexpected);
        }
      }
    }
  }
}
