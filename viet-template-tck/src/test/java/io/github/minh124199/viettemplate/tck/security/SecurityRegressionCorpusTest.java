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
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateCallable;
import io.github.minh124199.viettemplate.api.TemplateData;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.SafeUrl;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Comprehensive security regression test corpus covering all M13 categories. */
class SecurityRegressionCorpusTest {

  // Helper beans
  public static class SafeBean {
    public String getSecret() {
      return "safeValue";
    }

    public String compute(String arg) {
      return "result:" + arg;
    }
  }

  public static class UnannotatedService {
    private boolean cancelled = false;

    public void cancel() {
      this.cancelled = true;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    public String getSecret() {
      return "unannotatedSecret";
    }
  }

  @TemplateData
  public static class AnnotatedOrder {
    private final String id;
    private boolean cancelled = false;

    public AnnotatedOrder(String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }

    public void cancel() {
      this.cancelled = true;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    @TemplateCallable
    public String calculateTax(int percent) {
      return "tax:" + percent;
    }
  }

  @TemplateData
  public record AnnotatedItem(String sku, int price) {}

  public static class MutatingModel {
    private String name = "initial";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class EvilComparable implements Comparable<EvilComparable> {
    public static boolean invoked = false;

    @Override
    public int compareTo(EvilComparable o) {
      invoked = true;
      return 0;
    }
  }

  public static class EvilEquals {
    public static boolean invoked = false;

    @Override
    public boolean equals(Object obj) {
      invoked = true;
      return super.equals(obj);
    }

    @Override
    public int hashCode() {
      return 42;
    }
  }

  public static class EvilToString {
    public static boolean invoked = false;

    @Override
    public String toString() {
      invoked = true;
      return "evil";
    }
  }

  public static class EvilIterable implements Iterable<String> {
    public static boolean invoked = false;

    @Override
    public java.util.Iterator<String> iterator() {
      invoked = true;
      return java.util.Collections.emptyIterator();
    }
  }

  public static class EvilTruthiness {
    public static boolean invoked = false;

    public int size() {
      invoked = true;
      return 1;
    }

    public boolean isEmpty() {
      invoked = true;
      return false;
    }

    public boolean getAsBoolean() {
      invoked = true;
      return true;
    }
  }

  public static class ServiceContainer {
    public Object getBean(String name) {
      return "secretBean";
    }
  }

  public static class RequestLike {
    public String getPath() {
      return "/admin";
    }
  }

  public static class EnvironmentLike {
    public String getProperty(String name) {
      return "systemSecret";
    }
  }

  public static class FileService {
    public String readFile(String path) {
      return "fileContent";
    }
  }

  @TemplateData
  public static class BaseAnnotatedData {
    public String getBaseProp() {
      return "baseValue";
    }
  }

  public static class SubDataExtendsBase extends BaseAnnotatedData {
    public String getSubProp() {
      return "subValue";
    }
  }

  @TemplateData
  public static class SubDataWithOwnAnnotation extends BaseAnnotatedData {
    public String getSubProp() {
      return "subValue";
    }
  }

  @TemplateData
  public static class BaseView {
    public String getName() {
      return "baseName";
    }
  }

  public static class ExtendedView extends BaseView {
    public static boolean secretAccessed = false;

    public String getSecret() {
      secretAccessed = true;
      return "superSecret";
    }
  }

  @TemplateData
  public static class AnnotatedSubclass extends BaseView {
    public String getSecret() {
      return "subclassSecret";
    }
  }

  public record UserRecord(String name) {
    public String deleteAccount() {
      return "accountDeleted";
    }
  }

  public static class CallableService {
    @TemplateCallable
    public String format(String s) {
      return "formatted:" + s;
    }

    public String format(Object o) {
      return "objectFormatted:" + o;
    }

    public String format(String s, Object o) {
      return s + ":" + o;
    }
  }

  @TemplateData
  public interface AnnotatedDataInterface {
    String getInterfaceProp();
  }

  public static class SubDataImplementsInterface implements AnnotatedDataInterface {
    @Override
    public String getInterfaceProp() {
      return "interfaceValue";
    }
  }

  @TemplateData
  public interface CallableInterface {
    @TemplateCallable
    String interfaceAction();
  }

  public static class SubWorkerImplementsCallable implements CallableInterface {
    @Override
    public String interfaceAction() {
      return "actionExecuted";
    }
  }

  @TemplateData
  public abstract static class BaseCallableSuperclass {
    @TemplateCallable
    public abstract String superAction();
  }

  public static class SubWorkerExtendsCallable extends BaseCallableSuperclass {
    @Override
    public String superAction() {
      return "superActionExecuted";
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

    @ParameterizedTest
    @EnumSource(
        value = ExecutionTier.class,
        names = {"AST", "IR"})
    void sharedRenderBudgetAcrossParseParent60Child41Limit100(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      String parentPrefix =
          "012345678901234567890123456789012345678901234567890123456789"; // 60 chars
      repo.put("parent.vm", parentPrefix + "#parse('child.vm')");
      String childContent = "12345678901234567890123456789012345678901"; // 41 chars
      repo.put("child.vm", childContent);

      ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(100).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().limits(limits).executionTier(tier).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () -> engine.render(TemplateId.of("parent.vm"), RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class);
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

  // --- Category 23: Capability Model & Annotation-driven Access ---
  @Nested
  @DisplayName("Category 23: Capability Model & Annotations (@TemplateData, @TemplateCallable)")
  class Category23CapabilityModelAndAnnotations {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesAccessToUnannotatedClassesInSafeProfile(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("unannotated.vm", "Result:$service.secret");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("unannotated.vm"),
                        RenderContext.of("service", new UnannotatedService()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsTemplateDataGettersAndRecordsInSafeProfile(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("annotated.vm", "ID:[$order.id] Sku:[$item.sku] Price:[$item.price]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("annotated.vm"),
            RenderContext.of(
                Map.of(
                    "order",
                    new AnnotatedOrder("ord-99"),
                    "item",
                    new AnnotatedItem("item-1", 100))),
            out);
        assertThat(out.toString()).isEqualTo("ID:[ord-99] Sku:[item-1] Price:[100]");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsTemplateCallableMethodsInSafeProfile(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("callable.vm", "Tax:[$order.calculateTax(10)]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("callable.vm"),
            RenderContext.of("order", new AnnotatedOrder("ord-1")),
            out);
        assertThat(out.toString()).isEqualTo("Tax:[tax:10]");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesUnannotatedActionMethodsEvenOnTemplateDataClass(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("action.vm", "Result:$order.cancel()");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("action.vm"),
                        RenderContext.of("order", new AnnotatedOrder("ord-1")),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 24: Zero-Arg Property Escalation Denial ---
  @Nested
  @DisplayName("Category 24: Zero-Arg Property Escalation Denial")
  class Category24ZeroArgPropertyEscalation {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesZeroArgActionMethodInvocationViaPropertyRead(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("escalate.vm", "Value:[$order.cancel]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        AnnotatedOrder order = new AnnotatedOrder("ord-safe");
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("escalate.vm"), RenderContext.of("order", order), out))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(order.isCancelled()).isFalse();
      }
    }
  }

  // --- Category 25: Truthiness Reflection Confinement ---
  @Nested
  @DisplayName("Category 25: Truthiness Reflection Confinement")
  class Category25TruthinessReflectionConfinement {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesReflectiveTruthinessInspectionOnUnpermittedClassInSafeMode(ExecutionTier tier)
        throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("truth.vm", "#if($service)Truthy#elseFalsy#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        UnannotatedService service = new UnannotatedService();
        engine.render(TemplateId.of("truth.vm"), RenderContext.of("service", service), out);
        assertThat(service.isCancelled()).isFalse();
      }
    }
  }

  // --- Category 26: Cross-Tier Dynamic HTML Auto-Escaping & SafeContent ---
  @Nested
  @DisplayName("Category 26: Cross-Tier Dynamic HTML Auto-Escaping & SafeContent")
  class Category26CrossTierAutoEscapingAndSafeContent {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void autoEscapesDynamicHtmlAcrossAllTiersWhilePreservingLiterals(ExecutionTier tier)
        throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("escape.vm", "<h1>Header</h1>Dynamic:[$danger]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("escape.vm"),
            RenderContext.of("danger", "<script>alert('xss')</script>&\"test'"),
            out);
        assertThat(out.toString())
            .isEqualTo(
                "<h1>Header</h1>Dynamic:[&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;&amp;&quot;test&#39;]");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void safeContentBypassesEscapingAcrossAllTiers(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("safe.vm", "Html:[$safeHtml]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("safe.vm"),
            RenderContext.of("safeHtml", SafeHtml.of("<b>trusted markup</b>")),
            out);
        assertThat(out.toString()).isEqualTo("Html:[<b>trusted markup</b>]");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void safeUrlEscapedInHtmlTextContextAcrossTiers(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("safe_url.vm", "Url:[$url]");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("safe_url.vm"),
            RenderContext.of("url", SafeUrl.ofTrusted("<script>alert('xss')</script>")),
            out);
        assertThat(out.toString())
            .isEqualTo("Url:[&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;]");
      }
    }
  }

  // --- Category 27: Monotonic Loop Iteration Limits & ExecutionLimits ---
  @Nested
  @DisplayName("Category 27: Monotonic Loop Iteration Limits & ExecutionLimits")
  class Category27MonotonicLoopLimits {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void enforcesMonotonicLoopIterationLimitAcrossNestedLoops(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("nested_loops.vm", "#foreach($i in [1..10])#foreach($j in [1..10])($i,$j)#end#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(ExecutionLimits.builder().maxLoopIterations(15).build())
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () -> engine.render(TemplateId.of("nested_loops.vm"), RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void enforcesZeroLimitAsZeroAllowed(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("zero_loop.vm", "#foreach($i in [1..5])$i#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(ExecutionLimits.builder().maxLoopIterations(0).build())
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () -> engine.render(TemplateId.of("zero_loop.vm"), RenderContext.empty(), out))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @Test
    void rejectsNegativeExecutionLimitsInConstructorAndBuilder() {
      assertThatThrownBy(() -> ExecutionLimits.builder().maxLoopIterations(-1).build())
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> ExecutionLimits.builder().maxOutputCharacters(-5L).build())
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> ExecutionLimits.builder().maxExecutionTimeMillis(-10L).build())
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new ExecutionLimits(-1, 10, 10, 10, 10, 10, 100L, 0L))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // --- Category 28: Model Mutation Confinement ---
  @Nested
  @DisplayName("Category 28: Model Mutation Confinement")
  class Category28ModelMutationConfinement {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesPropertyMutationInSafeProfile(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("mutate_prop.vm", "#set($model.name = 'pwned')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        MutatingModel model = new MutatingModel();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("mutate_prop.vm"), RenderContext.of("model", model), out))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(model.getName()).isEqualTo("initial");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void deniesMapAndIndexMutationInSafeProfile(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("mutate_map.vm", "#set($map['key'] = 'modified')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        Map<String, Object> map = new HashMap<>();
        map.put("key", "original");
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("mutate_map.vm"), RenderContext.of("map", map), out))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(map.get("key")).isEqualTo("original");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsModelMutationInCoreProfileForVelocityCompatibility(ExecutionTier tier)
        throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("core_mutate.vm", "#set($model.name = 'updated')$model.name");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_CORE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        MutatingModel model = new MutatingModel();
        engine.render(TemplateId.of("core_mutate.vm"), RenderContext.of("model", model), out);
        assertThat(model.getName()).isEqualTo("updated");
        assertThat(out.toString()).isEqualTo("updated");
      }
    }
  }

  // --- Category 29: Intermediate Symlink Path Confinement ---
  @Nested
  @DisplayName("Category 29: Intermediate Symlink Path Confinement")
  class Category29IntermediateSymlinks {

    @Test
    void rejectsIntermediateDirectorySymlinkPointingOutsideRoot(@TempDir Path tempDir)
        throws IOException {
      Path repoRoot = tempDir.resolve("templates");
      Path outside = tempDir.resolve("outside_secret");
      Files.createDirectories(repoRoot);
      Files.createDirectories(outside);

      Path secretFile = outside.resolve("confidential.vm");
      Files.writeString(secretFile, "secret content");

      Path symlinkDir = repoRoot.resolve("linked_dir");
      try {
        Files.createSymbolicLink(symlinkDir, outside);
      } catch (UnsupportedOperationException | IOException e) {
        return;
      }

      FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(repoRoot);

      assertThatThrownBy(() -> repo.find(TemplateId.of("linked_dir/confidential.vm")))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }

  // --- Category 30: Automatic VTL_SAFE Sandbox Policy Activation ---
  @Nested
  @DisplayName("Category 30: Automatic VTL_SAFE Sandbox Invariants")
  class Category30VtlSafeAutomaticEnforcement {

    @Test
    void safeProfileEnforcesSafePolicyEvenWhenStandardPolicySupplied() {
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .profile(VtlProfile.VTL_SAFE)
              .securityPolicy(VtlSecurityPolicy.standard())
              .build();

      assertThat(opts.profile()).isEqualTo(VtlProfile.VTL_SAFE);
      assertThat(opts.securityPolicy().isClassPermitted(UnannotatedService.class)).isFalse();
    }

    @Test
    void safeProfileRefusesWeakeningByPermissivePolicy() {
      MemberAccessPolicy permissive =
          MemberAccessPolicy.builder()
              .allowClass(Runtime.class)
              .allowClass(ProcessBuilder.class)
              .allowAllPropertyMutations(true)
              .allowAllIndexMutations(true)
              .build();

      MemberAccessPolicy safeMap = permissive.toSafeProfile();
      assertThat(safeMap.isClassPermitted(Runtime.class)).isFalse();
      assertThat(safeMap.isClassPermitted(ProcessBuilder.class)).isFalse();
      assertThat(safeMap.isPropertyMutationPermitted(MutatingModel.class, "name")).isFalse();
      assertThat(safeMap.isIndexMutationPermitted(Map.class)).isFalse();

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .profile(VtlProfile.VTL_SAFE)
              .securityPolicy(VtlSecurityPolicy.of(permissive))
              .build();

      assertThat(opts.profile()).isEqualTo(VtlProfile.VTL_SAFE);
      assertThat(opts.securityPolicy().isClassPermitted(Runtime.class)).isFalse();
      assertThat(opts.securityPolicy().isClassPermitted(ProcessBuilder.class)).isFalse();
      assertThat(opts.securityPolicy().isPropertyMutationPermitted(MutatingModel.class, "name"))
          .isFalse();
      assertThat(opts.securityPolicy().isIndexMutationPermitted(Map.class)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void safeProfileBlocksRuntimeExecutionEvenWithPermissivePolicy(ExecutionTier tier) {
      MemberAccessPolicy permissive =
          MemberAccessPolicy.builder()
              .allowClass(Runtime.class)
              .allowMethod(Runtime.class, "getRuntime")
              .build();

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .profile(VtlProfile.VTL_SAFE)
              .securityPolicy(VtlSecurityPolicy.of(permissive))
              .build();

      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("pwn.vm", "$runtime.getRuntime()");

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder()
              .repository(repo)
              .memberAccessPolicy(permissive)
              .interpreterOptions(opts)
              .build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("pwn.vm"),
                        RenderContext.of("runtime", Runtime.getRuntime()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @Test
    void customMemberAccessPolicyIsWrappedWithMandatorySafePolicy() throws Exception {
      MemberAccessPolicy custom =
          new MemberAccessPolicy() {
            @Override
            public boolean isClassPermitted(Class<?> clazz) {
              return true;
            }

            @Override
            public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
              return true;
            }

            @Override
            public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
              return true;
            }

            @Override
            public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
              return true;
            }

            @Override
            public SecurityPolicyFingerprint fingerprint() {
              return SecurityPolicyFingerprint.of("custom");
            }
          };

      MemberAccessPolicy safeCustom = custom.toSafeProfile();
      assertThat(safeCustom.isClassPermitted(Runtime.class)).isFalse();
      assertThat(safeCustom.isClassPermitted(ProcessBuilder.class)).isFalse();
      assertThat(safeCustom.isClassPermitted(ClassLoader.class)).isFalse();
      assertThat(safeCustom.isPropertyMutationPermitted(MutatingModel.class, "name")).isFalse();
      assertThat(safeCustom.isIndexMutationPermitted(Map.class)).isFalse();
      Method getClassMethod = Object.class.getMethod("getClass");
      assertThat(safeCustom.isMethodPermitted(Object.class, getClassMethod)).isFalse();

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .profile(VtlProfile.VTL_SAFE)
              .securityPolicy(VtlSecurityPolicy.of(custom))
              .build();

      assertThat(opts.securityPolicy().isClassPermitted(Runtime.class)).isFalse();
      assertThat(opts.securityPolicy().isClassPermitted(ProcessBuilder.class)).isFalse();
      assertThat(opts.securityPolicy().isClassPermitted(ClassLoader.class)).isFalse();
      assertThat(opts.securityPolicy().isPropertyMutationPermitted(MutatingModel.class, "name"))
          .isFalse();
      assertThat(opts.securityPolicy().isIndexMutationPermitted(Map.class)).isFalse();
      assertThat(opts.securityPolicy().isMethodPermitted(Object.class, getClassMethod)).isFalse();
    }
  }

  // --- Category 31: Implicit Java Invocation Prevention ---
  @Nested
  @DisplayName("Category 31: Implicit Java Invocation Prevention")
  class Category31ImplicitJavaInvocationPrevention {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void evilComparableNotInvokedInComparisons(ExecutionTier tier) {
      EvilComparable.invoked = false;
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("compare.vm", "#if($left < $right)yes#{else}no#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("compare.vm"),
                        RenderContext.of(
                            Map.of("left", new EvilComparable(), "right", new EvilComparable())),
                        out))
            .isInstanceOf(TemplateException.class);
        assertThat(EvilComparable.invoked).isFalse();
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void evilEqualsNotInvokedInEquality(ExecutionTier tier) throws IOException {
      EvilEquals.invoked = false;
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("equals.vm", "#if($left == $right)equal#{else}not-equal#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("equals.vm"),
            RenderContext.of(Map.of("left", new EvilEquals(), "right", new EvilEquals())),
            out);
        assertThat(out.toString()).isEqualTo("not-equal");
        assertThat(EvilEquals.invoked).isFalse();
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void evilToStringNotInvokedInRendering(ExecutionTier tier) {
      EvilToString.invoked = false;
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("render.vm", "val: $evil");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("render.vm"),
                        RenderContext.of("evil", new EvilToString()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(EvilToString.invoked).isFalse();
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void evilIterableNotInvokedInForeach(ExecutionTier tier) {
      EvilIterable.invoked = false;
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("loop.vm", "#foreach($item in $evil)item:#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("loop.vm"),
                        RenderContext.of("evil", new EvilIterable()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(EvilIterable.invoked).isFalse();
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void evilTruthinessNotInvokedInConditionals(ExecutionTier tier) throws IOException {
      EvilTruthiness.invoked = false;
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("truthy.vm", "#if($evil)truthy#{else}falsy#end");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("truthy.vm"), RenderContext.of("evil", new EvilTruthiness()), out);
        assertThat(EvilTruthiness.invoked).isFalse();
      }
    }
  }

  // --- Category 32: Framework Object Pivots ---
  @Nested
  @DisplayName("Category 32: Framework Object Pivots")
  class Category32FrameworkObjectPivots {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void blocksServiceContainerPivotAcrossTiers(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("service.vm", "$services.getBean('secret')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("service.vm"),
                        RenderContext.of("services", new ServiceContainer()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void blocksRequestLikePivotAcrossTiers(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("request.vm", "$req.getPath()");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("request.vm"),
                        RenderContext.of("req", new RequestLike()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void blocksEnvironmentLikePivotAcrossTiers(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("env.vm", "$env.getProperty('secret')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("env.vm"),
                        RenderContext.of("env", new EnvironmentLike()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void blocksFileServicePivotAcrossTiers(ExecutionTier tier) {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("file.vm", "$files.readFile('/etc/passwd')");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("file.vm"),
                        RenderContext.of("files", new FileService()),
                        out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 33: Annotation Inheritance ---
  // --- Category 33: Annotation Inheritance ---
  @Nested
  @DisplayName("Category 33: Annotation Inheritance")
  class Category33AnnotationInheritance {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void enforcesNonWideningInheritanceOnSubclass(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("inherit_base_ok.vm", "$data.baseProp");
      repo.put("inherit_base_denied.vm", "$data.subProp");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("inherit_base_ok.vm"),
            RenderContext.of("data", new SubDataExtendsBase()),
            out);
        assertThat(out.toString()).isEqualTo("baseValue");

        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("inherit_base_denied.vm"),
                        RenderContext.of("data", new SubDataExtendsBase()),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsSubclassWithExplicitTemplateData(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("inherit_sub_annotated.vm", "$data.baseProp-$data.subProp");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("inherit_sub_annotated.vm"),
            RenderContext.of("data", new SubDataWithOwnAnnotation()),
            out);
        assertThat(out.toString()).isEqualTo("baseValue-subValue");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsSubclassImplementingAnnotatedInterface(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("inherit_iface.vm", "$data.interfaceProp");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("inherit_iface.vm"),
            RenderContext.of("data", new SubDataImplementsInterface()),
            out);
        assertThat(out.toString()).isEqualTo("interfaceValue");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsCallableOnImplementedInterface(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("callable_iface.vm", "$worker.interfaceAction()");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("callable_iface.vm"),
            RenderContext.of("worker", new SubWorkerImplementsCallable()),
            out);
        assertThat(out.toString()).isEqualTo("actionExecuted");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void permitsCallableOnSuperclass(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("callable_super.vm", "$worker.superAction()");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(
            TemplateId.of("callable_super.vm"),
            RenderContext.of("worker", new SubWorkerExtendsCallable()),
            out);
        assertThat(out.toString()).isEqualTo("superActionExecuted");
      }
    }
  }

  // --- Category 34: SafeUrl Validation & Scheme Security ---
  @Nested
  @DisplayName("Category 34: SafeUrl Validation & Scheme Security")
  class Category34SafeUrlValidationAndSchemeSecurity {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "javascript:",
          "JAVASCRIPT:",
          "JaVaScRiPt:",
          "\tjavascript:",
          "\njavascript:",
          "java\nscript:",
          "javascript%3A",
          "&#106;avascript:",
          "data:text/html,<script>alert(1)</script>",
          "vbscript:msgbox(1)",
          "file:///etc/passwd",
          "blob:https://example.com/uuid"
        })
    void rejectsDangerousAndObfuscatedSchemes(String dangerousUrl) {
      assertThatThrownBy(() -> SafeUrl.of(dangerousUrl))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> SafeUrl.ofValidated(dangerousUrl))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(SafeUrl.tryOf(dangerousUrl)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://example.com",
          "http://example.com",
          "mailto:test@example.com",
          "tel:+123456",
          "/path/to",
          "./path",
          "../path",
          "?query=x",
          "#fragment",
          "path/to"
        })
    void acceptsSafeAndRelativeUrls(String validUrl) {
      SafeUrl safe = SafeUrl.of(validUrl);
      assertThat(safe).isNotNull();
      assertThat(safe.content().toString()).isEqualTo(validUrl);
      assertThat(SafeUrl.ofValidated(validUrl)).isEqualTo(safe);
      assertThat(SafeUrl.tryOf(validUrl)).contains(safe);
    }

    @Test
    void allowsTrustedBypassExplicitly() {
      SafeUrl trusted = SafeUrl.ofTrusted("javascript:void(0)");
      assertThat(trusted.content().toString()).isEqualTo("javascript:void(0)");
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void safeUrlEscapingAcrossContextsAndTiers(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("safe_url_text.vm", "Text:[$url]");
      repo.put("safe_url_attr.vm", "<a href=\"$url\">link</a>");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        SafeUrl url = SafeUrl.of("https://example.com/search?q=test&lang=vi");

        // In HTML_TEXT context, SafeUrl is escaped
        StringTemplateOutput textOut = new StringTemplateOutput();
        engine.render(TemplateId.of("safe_url_text.vm"), RenderContext.of("url", url), textOut);
        assertThat(textOut.toString())
            .isEqualTo("Text:[https://example.com/search?q=test&amp;lang=vi]");

        // In HTML_ATTRIBUTE_QUOTED context, SafeUrl is escaped
        StringTemplateOutput attrOut = new StringTemplateOutput();
        engine.render(TemplateId.of("safe_url_attr.vm"), RenderContext.of("url", url), attrOut);
        assertThat(attrOut.toString()).contains("&amp;lang=vi");
      }
    }
  }

  // --- Category 35: Non-Widening TemplateData & Exact Callable Matching ---
  @Nested
  @DisplayName("Category 35: Non-Widening TemplateData & Exact Callable Matching")
  class Category35NonWideningTemplateDataAndExactCallable {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void nonWideningTemplateDataWithSideEffectProbe(ExecutionTier tier) throws IOException {
      ExtendedView.secretAccessed = false;
      ExtendedView extended = new ExtendedView();

      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("view_name.vm", "Name:$obj.name");
      repo.put("view_secret.vm", "Secret:$obj.secret");
      repo.put("subclass_secret.vm", "SubSecret:$obj.secret");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // Base property access on unannotated subclass succeeds
        StringTemplateOutput out1 = new StringTemplateOutput();
        engine.render(TemplateId.of("view_name.vm"), RenderContext.of("obj", extended), out1);
        assertThat(out1.toString()).isEqualTo("Name:baseName");

        // Newly declared property on unannotated subclass is denied without invoking getter
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("view_secret.vm"),
                        RenderContext.of("obj", extended),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);
        assertThat(ExtendedView.secretAccessed).isFalse();

        // Subclass explicitly marked @TemplateData exposes secret
        StringTemplateOutput out3 = new StringTemplateOutput();
        engine.render(
            TemplateId.of("subclass_secret.vm"),
            RenderContext.of("obj", new AnnotatedSubclass()),
            out3);
        assertThat(out3.toString()).isEqualTo("SubSecret:subclassSecret");
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void recordComponentsAllowedArbitraryMethodsDenied(ExecutionTier tier) throws IOException {
      UserRecord user = new UserRecord("Alice");

      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("user_name.vm", "$user.name");
      repo.put("user_delete_method.vm", "$user.deleteAccount()");
      repo.put("user_delete_prop.vm", "$user.deleteAccount");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // Record component is permitted
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("user_name.vm"), RenderContext.of("user", user), out);
        assertThat(out.toString()).isEqualTo("Alice");

        // Arbitrary method call on record is denied
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("user_delete_method.vm"),
                        RenderContext.of("user", user),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);

        // Property access to arbitrary method on record is denied
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("user_delete_prop.vm"),
                        RenderContext.of("user", user),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void templateCallableEnforcesExactSignature(ExecutionTier tier) throws IOException {
      CallableService service = new CallableService();

      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("call_string.vm", "$service.format('hello')");
      repo.put("call_object.vm", "$service.format($rawObj)");
      repo.put("call_two_args.vm", "$service.format('hello', $rawObj)");

      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_SAFE).build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // format(String) is annotated with @TemplateCallable -> allowed
        StringTemplateOutput out1 = new StringTemplateOutput();
        engine.render(TemplateId.of("call_string.vm"), RenderContext.of("service", service), out1);
        assertThat(out1.toString()).isEqualTo("formatted:hello");

        // format(Object) is not annotated with @TemplateCallable -> denied
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("call_object.vm"),
                        RenderContext.of(Map.of("service", service, "rawObj", new Object())),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);

        // format(String, Object) is not annotated with @TemplateCallable -> denied
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("call_two_args.vm"),
                        RenderContext.of(Map.of("service", service, "rawObj", new Object())),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }
  }

  // --- Category 36: Global Iteration Budget Propagation & Boundaries ---
  @Nested
  @DisplayName("Category 36: Global Iteration Budget Propagation & Boundaries")
  class Category36GlobalIterationBudgetPropagationAndBoundaries {

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void iterationLimitPropagatesAcrossParse(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("parent.vm", "#foreach($i in [1..60])p#end#parse('child.vm')");
      String childPass = "#foreach($j in [1..40])c#end";
      String childFail = "#foreach($j in [1..41])c#end";

      ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(100).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(limits)
              .profile(VtlProfile.VTL_SAFE)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // 60 parent + 40 child = 100 iterations -> passes
        repo.put("child.vm", childPass);
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("parent.vm"), RenderContext.empty(), out);
        assertThat(out.toString().length()).isEqualTo(100);

        // 60 parent + 41 child = 101 iterations -> throws TemplateLimitException
        repo.put("child.vm", childFail);
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("parent.vm"),
                        RenderContext.empty(),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void iterationLimitPropagatesAcrossEvaluate(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put(
          "eval_pass.vm", "#foreach($i in [1..60])p#end#evaluate('#foreach($j in [1..40])c#end')");
      repo.put(
          "eval_fail.vm", "#foreach($i in [1..60])p#end#evaluate('#foreach($j in [1..41])c#end')");

      ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(100).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(limits)
              .profile(VtlProfile.VTL_DYNAMIC)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // 60 + 40 = 100 -> passes
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("eval_pass.vm"), RenderContext.empty(), out);
        assertThat(out.toString().length()).isEqualTo(100);

        // 60 + 41 = 101 -> throws TemplateLimitException
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("eval_fail.vm"),
                        RenderContext.empty(),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void iterationLimitPropagatesAcrossMacros(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put(
          "macro_pass.vm",
          "#macro(myLoop)#foreach($j in [1..40])m#end#end#foreach($i in [1..60])p#end#myLoop()");
      repo.put(
          "macro_fail.vm",
          "#macro(myLoop)#foreach($j in [1..41])m#end#end#foreach($i in [1..60])p#end#myLoop()");

      ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(100).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(limits)
              .profile(VtlProfile.VTL_SAFE)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
        // 60 + 40 = 100 -> passes
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("macro_pass.vm"), RenderContext.empty(), out);
        assertThat(out.toString().length()).isEqualTo(100);

        // 60 + 41 = 101 -> throws TemplateLimitException
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("macro_fail.vm"),
                        RenderContext.empty(),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void iterationLimitPropagatesAcrossLayoutAndBody(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("screen.vm", "#foreach($i in [1..60])s#end");
      repo.put("layout_pass.vm", "$screen_content#foreach($j in [1..40])l#end");
      repo.put("layout_fail.vm", "$screen_content#foreach($j in [1..41])l#end");

      ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(100).build();
      VtlInterpreterOptions opts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(limits)
              .profile(VtlProfile.VTL_SAFE)
              .build();

      LayoutConfiguration passLayout =
          LayoutConfiguration.builder()
              .resolver(
                  LayoutResolver.fromContextVariable("layout", TemplateId.of("layout_pass.vm")))
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder()
              .repository(repo)
              .layoutConfiguration(passLayout)
              .interpreterOptions(opts)
              .build()) {
        // 60 body + 40 layout = 100 -> passes
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("screen.vm"), RenderContext.empty(), out);
        assertThat(out.toString().length()).isEqualTo(100);
      }

      LayoutConfiguration failLayout =
          LayoutConfiguration.builder()
              .resolver(
                  LayoutResolver.fromContextVariable("layout", TemplateId.of("layout_fail.vm")))
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder()
              .repository(repo)
              .layoutConfiguration(failLayout)
              .interpreterOptions(opts)
              .build()) {
        // 60 body + 41 layout = 101 -> throws TemplateLimitException
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("screen.vm"),
                        RenderContext.empty(),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateLimitException.class);
      }
    }

    @ParameterizedTest
    @EnumSource(ExecutionTier.class)
    void zeroOutputLoopsConsumeIterationBudget(ExecutionTier tier) throws IOException {
      InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
      repo.put("zero_output.vm", "#foreach($x in [1..60])#set($dummy = $x)#end");

      // Limit 50 -> throws even though 0 output characters produced
      ExecutionLimits strictLimits = ExecutionLimits.builder().maxLoopIterations(50).build();
      VtlInterpreterOptions strictOpts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(strictLimits)
              .profile(VtlProfile.VTL_SAFE)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(strictOpts).build()) {
        assertThatThrownBy(
                () ->
                    engine.render(
                        TemplateId.of("zero_output.vm"),
                        RenderContext.empty(),
                        new StringTemplateOutput()))
            .isInstanceOf(TemplateLimitException.class);
      }

      // Limit 100 -> passes
      ExecutionLimits generousLimits = ExecutionLimits.builder().maxLoopIterations(100).build();
      VtlInterpreterOptions generousOpts =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .limits(generousLimits)
              .profile(VtlProfile.VTL_SAFE)
              .build();

      try (VtlTemplateEngine engine =
          VtlTemplateEngine.builder().repository(repo).interpreterOptions(generousOpts).build()) {
        StringTemplateOutput out = new StringTemplateOutput();
        engine.render(TemplateId.of("zero_output.vm"), RenderContext.empty(), out);
        assertThat(out.toString()).isEmpty();
      }
    }

    @Test
    void budgetBoundarySemanticsAroundLimits() {
      // 0 iterations limit: rejects 1st iteration
      RenderBudget zeroBudget = new RenderBudget(1000, 0, 0);
      assertThatThrownBy(zeroBudget::countLoopIteration).isInstanceOf(TemplateLimitException.class);

      // 1 iteration limit: accepts 1st, rejects 2nd
      RenderBudget oneBudget = new RenderBudget(1000, 0, 1);
      oneBudget.countLoopIteration();
      assertThat(oneBudget.loopIterations()).isEqualTo(1);
      assertThatThrownBy(oneBudget::countLoopIteration).isInstanceOf(TemplateLimitException.class);

      // Integer.MAX_VALUE limit
      RenderBudget intMaxBudget = new RenderBudget(1000, 0, Integer.MAX_VALUE);
      intMaxBudget.countLoopIteration();
      assertThat(intMaxBudget.loopIterations()).isEqualTo(1);

      // Saturating arithmetic: accumulates without wrapping around
      RenderBudget saturatingBudget = new RenderBudget(1000, 0, Integer.MAX_VALUE);
      saturatingBudget.consumeCharacters(100, TemplateId.of("test"), SourceSpan.UNKNOWN);
      assertThat(saturatingBudget.charactersWritten()).isEqualTo(100);
    }
  }
}
