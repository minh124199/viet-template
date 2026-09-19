package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class PreparedIrExecutionTest {

  @Test
  void reusesOnePreparedExecutableAcrossCachedTemplateWrappers() throws IOException {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("prepared.vm");
    repository.put(id, "#macro(greet $name)Hello $name#end#greet($name)");

    try (VtlTemplateEngine engine = irEngine(repository)) {
      VtlTemplate first = (VtlTemplate) engine.get(id);
      VtlTemplate second = (VtlTemplate) engine.get(id);

      CompiledTemplate firstExecutable = first.handle().compiledTemplate().orElseThrow();
      CompiledTemplate secondExecutable = second.handle().compiledTemplate().orElseThrow();
      assertThat(secondExecutable).isSameAs(firstExecutable);
      assertThat(render(first, RenderContext.builder().put("name", "Viet").build()))
          .isEqualTo("Hello Viet");
    }
  }

  @Test
  void keepsPreparedExecutablesIsolatedByEngineAndMacroConfiguration() throws IOException {
    InMemoryTemplateRepository repository = macroRepository();
    TemplateId page = TemplateId.of("page.vm");
    try (VtlTemplateEngine firstWins =
            VtlTemplateEngine.builder()
                .repository(repository)
                .executionTier(ExecutionTier.IR)
                .globalMacroLibraries(List.of(TemplateId.of("first.vm"), TemplateId.of("last.vm")))
                .globalMacroPrecedence(GlobalMacroPrecedence.FIRST_WINS)
                .build();
        VtlTemplateEngine lastWins =
            VtlTemplateEngine.builder()
                .repository(repository)
                .executionTier(ExecutionTier.IR)
                .globalMacroLibraries(List.of(TemplateId.of("first.vm"), TemplateId.of("last.vm")))
                .globalMacroPrecedence(GlobalMacroPrecedence.LAST_WINS)
                .build()) {
      VtlTemplate first = (VtlTemplate) firstWins.get(page);
      VtlTemplate last = (VtlTemplate) lastWins.get(page);

      assertThat(first.handle().compiledTemplate().orElseThrow())
          .isNotSameAs(last.handle().compiledTemplate().orElseThrow());
      assertThat(render(first, RenderContext.empty())).isEqualTo("first");
      assertThat(render(last, RenderContext.empty())).isEqualTo("last");
    }
  }

  @Test
  void localMacroStillShadowsPreparedGlobalMacro() throws IOException {
    InMemoryTemplateRepository repository = macroRepository();
    repository.put("page.vm", "#macro(choice)local#end#choice()");

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .executionTier(ExecutionTier.IR)
            .globalMacroLibraries(List.of(TemplateId.of("first.vm")))
            .build()) {
      assertThat(render(engine.get("page.vm"), RenderContext.empty())).isEqualTo("local");
    }
  }

  @Test
  void preparedMacroMetadataIsSafeForConcurrentVirtualThreadCallers() throws Exception {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("concurrent-prepared.vm");
    repository.put(
        id,
        "#macro(outer $v)#macroText($v)#end" + "#macro(macroText $v)[$v]#end" + "#outer($value)");

    try (VtlTemplateEngine engine = irEngine(repository);
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Template template = engine.get(id);
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 1_000; i++) {
        int value = i;
        tasks.add(() -> render(template, RenderContext.builder().put("value", value).build()));
      }
      var results = executor.invokeAll(tasks);
      for (int i = 0; i < results.size(); i++) {
        assertThat(results.get(i).get()).isEqualTo("[" + i + "]");
      }
    }
  }

  private static VtlTemplateEngine irEngine(InMemoryTemplateRepository repository) {
    return VtlTemplateEngine.builder()
        .repository(repository)
        .executionTier(ExecutionTier.IR)
        .build();
  }

  private static InMemoryTemplateRepository macroRepository() {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put("page.vm", "#choice()");
    repository.put("first.vm", "#macro(choice)first#end");
    repository.put("last.vm", "#macro(choice)last#end");
    return repository;
  }

  private static String render(Template template, RenderContext context) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    template.render(context, output);
    return output.toString();
  }
}
