package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class IntegerRenderingBenchmark {

  public static class UserBean {
    private final String name;
    private final int age;
    private final String department;
    private final int salary;
    private final int rank;

    public UserBean(String name, int age, String department, int salary, int rank) {
      this.name = name;
      this.age = age;
      this.department = department;
      this.salary = salary;
      this.rank = rank;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    public String getDepartment() {
      return department;
    }

    public int getSalary() {
      return salary;
    }

    public int getRank() {
      return rank;
    }
  }

  public record UserRecord(String name, int age, String department, int salary, int rank) {}

  @Param({"STRING", "UTF8_STREAM"})
  private String outputSink;

  private CompiledTemplate tplDirectGetter;
  private CompiledTemplate tplDirectRecord;
  private CompiledTemplate tplMixed;
  private RenderContext ctxBean;
  private RenderContext ctxRecord;

  private ByteArrayOutputStream sharedBaos;
  private Utf8OutputStreamTemplateOutput sharedUtf8Output;
  private StringTemplateOutput sharedStringOutput;

  @Setup(Level.Trial)
  public void setUp() {
    UserBean bean = new UserBean("NguyenVanA", 35, "Engineering", 125000, 4);
    UserRecord record = new UserRecord("NguyenVanA", 35, "Engineering", 125000, 4);

    ctxBean = RenderContext.of("user", bean);
    ctxRecord = RenderContext.of("user", record);

    TemplateContract beanContract =
        TemplateContract.builder(TemplateId.of("direct_age.vtl"))
            .parameter("user", UserBean.class)
            .build();
    tplDirectGetter = compile("direct_age.vtl", "$user.age", beanContract);

    TemplateContract recordContract =
        TemplateContract.builder(TemplateId.of("record_age.vtl"))
            .parameter("user", UserRecord.class)
            .build();
    tplDirectRecord = compile("record_age.vtl", "$user.age", recordContract);

    TemplateContract mixedContract =
        TemplateContract.builder(TemplateId.of("mixed.vtl"))
            .parameter("user", UserRecord.class)
            .build();
    tplMixed =
        compile(
            "mixed.vtl",
            "<div><h1>$user.name</h1><p>Age: $user.age</p><p>Dept: $user.department</p><p>Salary:"
                + " $user.salary</p><p>Rank: $user.rank</p></div>",
            mixedContract);

    sharedBaos = new ByteArrayOutputStream(8192);
    sharedUtf8Output = new Utf8OutputStreamTemplateOutput(sharedBaos);
    sharedStringOutput = new StringTemplateOutput(8192);
  }

  @Benchmark
  public void render_direct_getter(Blackhole bh) throws IOException {
    if ("STRING".equals(outputSink)) {
      sharedStringOutput.reset();
      tplDirectGetter.render(ctxBean, sharedStringOutput);
      bh.consume(sharedStringOutput.length());
    } else {
      sharedBaos.reset();
      tplDirectGetter.render(ctxBean, sharedUtf8Output);
      bh.consume(sharedBaos.size());
    }
  }

  @Benchmark
  public void render_direct_record(Blackhole bh) throws IOException {
    if ("STRING".equals(outputSink)) {
      sharedStringOutput.reset();
      tplDirectRecord.render(ctxRecord, sharedStringOutput);
      bh.consume(sharedStringOutput.length());
    } else {
      sharedBaos.reset();
      tplDirectRecord.render(ctxRecord, sharedUtf8Output);
      bh.consume(sharedBaos.size());
    }
  }

  @Benchmark
  public void render_mixed_profile(Blackhole bh) throws IOException {
    if ("STRING".equals(outputSink)) {
      sharedStringOutput.reset();
      tplMixed.render(ctxRecord, sharedStringOutput);
      bh.consume(sharedStringOutput.length());
    } else {
      sharedBaos.reset();
      tplMixed.render(ctxRecord, sharedUtf8Output);
      bh.consume(sharedBaos.size());
    }
  }

  private static CompiledTemplate compile(
      String idStr, String templateText, TemplateContract contract) {
    SourceText source = SourceText.of(idStr, templateText);
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions.Builder semOptionsBuilder =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE);
    if (contract != null) {
      semOptionsBuilder.modelSchema(ModelSchema.fromContract(contract));
    }
    VtlSemanticOptions semOptions = semOptionsBuilder.build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());
    if (!result.isSuccess() || result.templateInstance() == null) {
      throw new IllegalStateException("Compilation failed: " + result.diagnostics());
    }
    return result.templateInstance();
  }
}
