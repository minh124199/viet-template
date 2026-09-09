package io.github.minh124199.viettemplate.tck.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ContextCollisionException;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint;
import io.github.minh124199.viettemplate.api.SensitiveObjectClassifier;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.context.ContributingContextComposer;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Comprehensive security regression test corpus covering all 22 M13 categories. */
class SecurityRegressionCorpusTest {

  // Helper bean
  public static class SafeBean {
    public String getSecret() {
      return "safeValue";
    }

    public String compute(String arg) {
      return "result:" + arg;
    }
  }

  // --- Category 1: ClassLoader Access ---
  @Nested
  @DisplayName("Category 1: ClassLoader Access Denial")
  class Category1ClassLoaderAccess {

    @Test
    void deniesClassLoaderInspectionViaPolicy() throws Exception {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      Method getCl = SafeBean.class.getMethod("getClass");
      assertThat(policy.isMethodPermitted(SafeBean.class, "getClass", 0)).isFalse();
      assertThat(policy.isMethodPermitted(SafeBean.class, getCl)).isFalse();
      assertThat(policy.isMethodPermitted(ClassLoader.class, "loadClass", 1)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void suppressesClassLoaderNavigationAcrossAllTiers(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("cl.vm", "Output:[$!bean.class.classLoader]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .securityPolicy(VtlSecurityPolicy.standard())
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("cl.vm"), RenderContext.of("bean", new SafeBean()), out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 2: Reflection & Method Handles ---
  @Nested
  @DisplayName("Category 2: Reflection and Method Handles Denial")
  class Category2ReflectionDenial {

    @Test
    void deniesReflectionClasses() throws Exception {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      assertThat(policy.isMethodPermitted(Class.class, "forName", 1)).isFalse();
      assertThat(policy.isMethodPermitted(Class.class, "getMethod", 2)).isFalse();
      assertThat(policy.isMethodPermitted(Class.class, "getDeclaredMethod", 2)).isFalse();
      assertThat(policy.isMethodPermitted(Method.class, "invoke", 2)).isFalse();
      assertThat(policy.isMethodPermitted(java.lang.invoke.MethodHandles.class, "lookup", 0))
          .isFalse();
    }
  }

  // --- Category 3: Process Execution ---
  @Nested
  @DisplayName("Category 3: Process Execution Denial")
  class Category3ProcessExecution {

    @Test
    void deniesProcessBuilderAndRuntime() {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      assertThat(policy.isMethodPermitted(ProcessBuilder.class, "start", 0)).isFalse();
      assertThat(policy.isMethodPermitted(Runtime.class, "exec", 1)).isFalse();
      assertThat(policy.isMethodPermitted(Runtime.class, "getRuntime", 0)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void blocksProcessExecutionInTemplates(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("exec.vm", "Result:[$!pb.start()]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .securityPolicy(VtlSecurityPolicy.standard())
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("exec.vm"),
                        RenderContext.of("pb", new ProcessBuilder("echo", "pwned")),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 4: Threads and Concurrency ---
  @Nested
  @DisplayName("Category 4: Threads and Concurrency Denial")
  class Category4ThreadDenial {

    @Test
    void deniesThreadManipulation() throws Exception {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      assertThat(policy.isMethodPermitted(Thread.class, "stop", 0)).isFalse();
      assertThat(policy.isMethodPermitted(Thread.class, "interrupt", 0)).isFalse();
      assertThat(policy.isMethodPermitted(Thread.class, "currentThread", 0)).isFalse();
      assertThat(
              policy.isMethodPermitted(
                  java.util.concurrent.ExecutorService.class,
                  java.util.concurrent.ExecutorService.class.getMethod("shutdown")))
          .isFalse();
    }
  }

  // --- Category 5: System Manipulation ---
  @Nested
  @DisplayName("Category 5: System Manipulation Denial")
  class Category5SystemDenial {

    @Test
    void deniesSystemCalls() {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      assertThat(policy.isMethodPermitted(System.class, "exit", 1)).isFalse();
      assertThat(policy.isMethodPermitted(System.class, "setProperty", 2)).isFalse();
      assertThat(policy.isMethodPermitted(System.class, "setSecurityManager", 1)).isFalse();
      assertThat(policy.isMethodPermitted(System.class, "getenv", 1)).isFalse();
    }
  }

  // --- Category 6: Filesystem Escapes ---
  @Nested
  @DisplayName("Category 6: Filesystem Path Traversal Escapes")
  class Category6FilesystemEscapes {

    @Test
    void rejectsPathTraversalSequences() {
      assertThatThrownBy(() -> TemplateId.of("../secret.txt"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("foo/../../bar.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.normalize("../secret.txt"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEncodedTraversalsAndNullBytes() {
      assertThatThrownBy(() -> TemplateId.of("%2e%2e/evil.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("..%2fevil.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("template.vm\0.extra"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("template.vm%00"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.normalize("%2e%2e/evil.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.normalize("template.vm\0"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWindowsDriveLettersAndUNC() {
      assertThatThrownBy(() -> TemplateId.of("C:/boot.ini"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("D:\\data.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.of("\\\\server\\share\\template.vm"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> TemplateId.normalize("C:/boot.ini"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // --- Category 7: Container Objects ---
  @Nested
  @DisplayName("Category 7: Spring and Servlet Container Objects")
  class Category7ContainerObjects {

    @Test
    void identifiesContainerClassesWithoutDependencies() {
      assertThat(
              SensitiveObjectClassifier.isSensitiveClassName(
                  "org.springframework.context.ApplicationContext"))
          .isTrue();
      assertThat(
              SensitiveObjectClassifier.isSensitiveClassName(
                  "org.springframework.beans.factory.BeanFactory"))
          .isTrue();
      assertThat(
              SensitiveObjectClassifier.isSensitiveClassName(
                  "jakarta.servlet.http.HttpServletRequest"))
          .isTrue();
      assertThat(SensitiveObjectClassifier.isSensitiveClassName("javax.servlet.ServletRequest"))
          .isTrue();
      assertThat(SensitiveObjectClassifier.isSensitiveClassName("io.github.minh124199.SafeBean"))
          .isFalse();
    }

    @Test
    void permitsSafeObjectMethods() throws Exception {
      MemberAccessPolicy policy = MemberAccessPolicy.standard();
      Method m = SafeBean.class.getMethod("getSecret");
      assertThat(policy.isMethodPermitted(SafeBean.class, m)).isTrue();
    }
  }

  // --- Category 8: Protected Context Variables ---
  @Nested
  @DisplayName("Category 8: Protected Context Variables")
  class Category8ProtectedVariables {

    @Test
    void preventsOverwritingProtectedVariablesViaSet() {
      MutableRenderContext ctx =
          MutableRenderContext.of(Map.of("screen_content", "original"), Set.of("screen_content"));

      assertThatThrownBy(() -> ctx.put("screen_content", "pwned"))
          .isInstanceOf(TemplateSecurityException.class)
          .satisfies(
              ex ->
                  assertThat(((TemplateSecurityException) ex).code())
                      .contains(DiagnosticCode.of("SECURITY", "PROTECTED_VARIABLE")));

      assertThatThrownBy(() -> ctx.remove("screen_content"))
          .isInstanceOf(TemplateSecurityException.class);
    }

    @Test
    void protectsScreenContentDuringLayoutExecution() {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("screen.vm", "#set($screen_content = 'tampered')Screen Content");
      repo.put("layout.vm", "<html>$screen_content</html>");

      LayoutConfiguration layoutConfig =
          LayoutConfiguration.builder()
              .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("layout.vm")))
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build()) {

        LayoutRenderPlan plan =
            engine.prepareLayoutPlan(TemplateId.of("screen.vm"), RenderContext.empty());
        StringTemplateOutput out = new StringTemplateOutput();

        assertThatThrownBy(() -> plan.render(RenderContext.empty(), out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 9: Context Contributor Collision ---
  @Nested
  @DisplayName("Category 9: Context Contributor Collisions")
  class Category9ContributorCollision {

    @Test
    void failsOnContributorCollisionWhenConfigured() {
      RenderContextContributor c1 = (ctx, req) -> ctx.put("sharedKey", "val1");
      RenderContextContributor c2 = (ctx, req) -> ctx.put("sharedKey", "val2");

      RenderRequest req = RenderRequest.of(TemplateId.of("test.vm"), RenderContext.empty());

      assertThatThrownBy(
              () ->
                  ContributingContextComposer.compose(
                      req,
                      List.of(c1, c2),
                      ContextCollisionPolicy.ERROR_ON_COLLISION,
                      Set.of(),
                      Map.of()))
          .isInstanceOf(ContextCollisionException.class)
          .satisfies(
              ex -> {
                ContextCollisionException cce = (ContextCollisionException) ex;
                assertThat(cce.variableName()).isEqualTo("sharedKey");
              });
    }

    @Test
    void allowsOverrideWhenConfigured() {
      RenderContextContributor c1 = (ctx, req) -> ctx.put("sharedKey", "val1");
      RenderContextContributor c2 = (ctx, req) -> ctx.put("sharedKey", "val2");

      RenderRequest req = RenderRequest.of(TemplateId.of("test.vm"), RenderContext.empty());

      ContributingContextComposer.CompositionResult res =
          ContributingContextComposer.compose(
              req, List.of(c1, c2), ContextCollisionPolicy.CONTRIBUTOR_WINS, Set.of(), Map.of());

      assertThat(res.context().get("sharedKey")).isEqualTo("val2");
    }
  }

  // --- Category 10: #evaluate Directive Restriction ---
  @Nested
  @DisplayName("Category 10: #evaluate Directive Safety")
  class Category10EvaluateDirectiveSafety {

    @Test
    void evaluateIsDisabledByDefaultInStandardProfile() {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("eval.vm", "#evaluate('1 + 1')");

      TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatThrownBy(() -> engine.render(TemplateId.of("eval.vm"), RenderContext.empty(), out))
          .isInstanceOf(TemplateSecurityException.class);
    }

    @Test
    void evaluateWorksWhenExplicitlyEnabled() throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("eval.vm", "Result: #evaluate('#set($y = $x * 2)$y')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .profile(VtlProfile.VTL_DYNAMIC)
              .executionTier(ExecutionTier.AST)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("eval.vm"), RenderContext.of("x", 21), out);
        assertThat(out.toString().trim()).isEqualTo("Result: 42");
      }
    }
  }

  // --- Category 11: Unified Monotonic Render Execution Budget ---
  @Nested
  @DisplayName("Category 11: Monotonic Output Character Budget")
  class Category11OutputCharacterBudget {

    @Test
    void enforcesCharacterBudgetAcrossTopLevelAndParse() {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("top.vm", "Start#parse('sub.vm')End");
      repo.put("sub.vm", "012345678901234567890123456789");

      ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(20).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().limits(limits).executionTier(ExecutionTier.AST).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(() -> engine.render(TemplateId.of("top.vm"), RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class)
            .satisfies(
                ex ->
                    assertThat(((TemplateLimitException) ex).code())
                        .contains(DiagnosticCode.of("LIMIT", "LIMIT_EXCEEDED")));
      }
    }

    @Test
    void enforcesCharacterBudgetAcrossScreenAndLayout() {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("screen.vm", "ScreenContentOfSubstantialLength");
      repo.put("layout.vm", "Header-$screen_content-Footer");

      LayoutConfiguration layoutConfig =
          LayoutConfiguration.builder()
              .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("layout.vm")))
              .build();

      ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(30).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().limits(limits).executionTier(ExecutionTier.AST).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder()
              .repository(repo)
              .layoutConfiguration(layoutConfig)
              .interpreterOptions(opts)
              .build()) {
        LayoutRenderPlan plan =
            engine.prepareLayoutPlan(TemplateId.of("screen.vm"), RenderContext.empty());
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(() -> plan.render(RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class);
      }
    }
  }

  // --- Category 12: Loop Iteration Limits ---
  @Nested
  @DisplayName("Category 12: Monotonic Loop Iteration Limit")
  class Category12LoopLimits {

    @Test
    void enforcesMonotonicLoopLimitAcrossMultipleLoops() {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("loops.vm", "#foreach($i in [1..4])A#end#foreach($j in [1..4])B#end");

      ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(6).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().limits(limits).executionTier(ExecutionTier.AST).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () -> engine.render(TemplateId.of("loops.vm"), RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class)
            .hasMessageContaining("maximum foreach iterations");
      }
    }
  }

  // --- Category 13: Wall-clock Execution Timeout ---
  @Nested
  @DisplayName("Category 13: Wall-clock Execution Timeout")
  class Category13ExecutionTimeout {

    @Test
    void terminatesExecutionExceedingWallClockTimeout() {
      ExecutionLimits limits =
          ExecutionLimits.builder().maxExecutionTimeMillis(1).maxLoopIterations(100_000).build();

      RenderBudget budget = limits.createRenderBudget();
      assertThatThrownBy(
              () -> {
                try {
                  Thread.sleep(5);
                } catch (InterruptedException ignored) {
                }
                budget.checkDeadline(
                    TemplateId.of("slow.vm"),
                    io.github.minh124199.viettemplate.api.SourceSpan.UNKNOWN);
              })
          .isInstanceOf(TemplateLimitException.class)
          .hasMessageContaining("Exceeded maximum template execution time limit");
    }
  }

  // --- Category 14: Source Character Limits ---
  @Nested
  @DisplayName("Category 14: Source Character Limits")
  class Category14SourceLimits {

    @Test
    void rejectsSourceExceedingCharacterLimit() {
      String largeSource = "a".repeat(1001);
      VtlParserOptions options = VtlParserOptions.DEFAULT.withMaxSourceCharacters(1000);

      var result = VtlParser.parse(SourceText.of("large.vm", largeSource), options);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anySatisfy(
              d -> assertThat(d.code()).isEqualTo(DiagnosticCode.of("LIMIT", "SOURCE_TOO_LARGE")));
    }
  }

  // --- Category 15: AST Node Count Limits ---
  @Nested
  @DisplayName("Category 15: AST Node Count Limits")
  class Category15AstNodeLimits {

    @Test
    void rejectsTemplateExceedingAstNodeBudget() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 100; i++) {
        sb.append("$var").append(i).append(" ");
      }
      VtlParserOptions options = VtlParserOptions.DEFAULT.withMaxAstNodes(50);

      var result = VtlParser.parse(SourceText.of("nodes.vm", sb.toString()), options);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anySatisfy(
              d -> assertThat(d.code()).isEqualTo(DiagnosticCode.of("LIMIT", "AST_NODE_LIMIT")));
    }
  }

  // --- Category 16: Expression Depth Limits ---
  @Nested
  @DisplayName("Category 16: Expression Depth Limits")
  class Category16ExpressionDepth {

    @Test
    void rejectsDeeplyNestedExpressions() {
      StringBuilder expr = new StringBuilder("1");
      for (int i = 0; i < 40; i++) {
        expr.insert(0, "(").append(" + 1)");
      }
      String template = "#set($x = " + expr + ")";
      VtlParserOptions options = VtlParserOptions.DEFAULT.withMaxExpressionDepth(20);

      var result = VtlParser.parse(SourceText.of("deep_expr.vm", template), options);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anySatisfy(d -> assertThat(d.code().id()).isEqualTo("MAX_NESTING_EXCEEDED"));
    }
  }

  // --- Category 17: Directive Nesting Limits ---
  @Nested
  @DisplayName("Category 17: Directive Nesting Depth Limits")
  class Category17DirectiveNesting {

    @Test
    void rejectsDeeplyNestedDirectives() {
      StringBuilder template = new StringBuilder();
      for (int i = 0; i < 25; i++) {
        template.append("#if(true)");
      }
      template.append("Deep");
      for (int i = 0; i < 25; i++) {
        template.append("#end");
      }

      VtlParserOptions options = VtlParserOptions.DEFAULT.withMaxDirectiveNesting(10);

      var result = VtlParser.parse(SourceText.of("deep_dir.vm", template.toString()), options);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anySatisfy(d -> assertThat(d.code().id()).isEqualTo("MAX_NESTING_EXCEEDED"));
    }
  }

  // --- Category 18: Reference Navigation Depth Limits ---
  @Nested
  @DisplayName("Category 18: Reference Navigation Depth")
  class Category18ReferenceNavigationDepth {

    @Test
    void rejectsReferenceNavigationExceedingDepthLimit() {
      StringBuilder ref = new StringBuilder("$root");
      for (int i = 0; i < 30; i++) {
        ref.append(".child");
      }
      VtlParserOptions options = VtlParserOptions.DEFAULT.withMaxExpressionDepth(15);

      var result = VtlParser.parse(SourceText.of("deep_ref.vm", ref.toString()), options);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anySatisfy(d -> assertThat(d.code().id()).isEqualTo("MAX_NESTING_EXCEEDED"));
    }
  }

  // --- Category 19: Cross-Tier Parity ---
  @Nested
  @DisplayName("Category 19: Cross-Tier Execution Parity")
  class Category19CrossTierParity {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void enforcesIdenticalDenialAcrossTiers(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("tier.vm", "Value:[$!bean.secret]");

      MemberAccessPolicy allowNothing = MemberAccessPolicy.denyAll();

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .securityPolicy(VtlSecurityPolicy.of(allowNothing))
              .executionTier(tier)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder()
              .repository(repo)
              .memberAccessPolicy(allowNothing)
              .interpreterOptions(opts)
              .build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("tier.vm"), RenderContext.of("bean", new SafeBean()), out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 20: Policy Fingerprint Cache Partitioning ---
  @Nested
  @DisplayName("Category 20: Policy Fingerprint Cache Partitioning")
  class Category20CachePartitioning {

    @Test
    void distinctPoliciesProduceDistinctFingerprints() {
      MemberAccessPolicy standard = MemberAccessPolicy.standard();
      MemberAccessPolicy allowOnlySafe =
          MemberAccessPolicy.builder()
              .safeProfile(true)
              .allowClass(SafeBean.class)
              .allowMethod(SafeBean.class, "getSecret")
              .build();

      SecurityPolicyFingerprint fp1 = standard.fingerprint();
      SecurityPolicyFingerprint fp2 = allowOnlySafe.fingerprint();

      assertThat(fp1.value()).isNotEqualTo(fp2.value());
      assertThat(fp1.value()).hasSize(64);
      assertThat(fp2.value()).hasSize(64);
    }
  }

  // --- Category 21: Symlink Escape Rejection ---
  @Nested
  @DisplayName("Category 21: Symlink Escape Prevention")
  class Category21SymlinkEscapes {

    @Test
    void rejectsSymlinkEscapingRepositoryRoot(@TempDir Path tempDir) throws IOException {
      Path repoRoot = tempDir.resolve("templates");
      Path outside = tempDir.resolve("outside");
      Files.createDirectories(repoRoot);
      Files.createDirectories(outside);

      Path outsideFile = outside.resolve("secret.txt");
      Files.writeString(outsideFile, "confidential");

      Path symlink = repoRoot.resolve("symlink.vm");
      try {
        Files.createSymbolicLink(symlink, outsideFile);
      } catch (UnsupportedOperationException | IOException e) {
        return;
      }

      FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(repoRoot);

      assertThatThrownBy(() -> repo.find(TemplateId.of("symlink.vm")))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }

  // --- Category 22: Resource Root Confinement ---
  @Nested
  @DisplayName("Category 22: Resource Root Confinement")
  class Category22ResourceConfinement {

    @Test
    void rejectsUriSchemesInTemplateIds() {
      String[] maliciousIds = {
        "file:///etc/passwd",
        "http://attacker.com/payload.vm",
        "https://evil.org/malware.vm",
        "jar:file:/app.jar!/secret",
        "netdoc:///etc/hosts",
        "ftp://remote/script.vm"
      };

      for (String id : maliciousIds) {
        assertThatThrownBy(() -> TemplateId.of(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateId.normalize(id))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }
}
