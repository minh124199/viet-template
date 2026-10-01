package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
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

/**
 * Phases 11 & 12: Benchmark evaluating Direct Accessor optimization vs Dynamic PIC across: - Direct
 * Getter - Direct Record - Subtype of expected type - Unexpected type triggering fallback - Null
 * receiver - Dynamic monomorphic - Dynamic polymorphic (2-class, 4-class) - Dynamic megamorphic
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class DirectAccessorBenchmark {

  public static class BaseUser {
    public String getName() {
      return "BaseUser";
    }
  }

  public static class SubUser extends BaseUser {
    @Override
    public String getName() {
      return "SubUser";
    }
  }

  public record UserRecord(String name) {
    public String getName() {
      return name;
    }
  }

  public static class UnexpectedType {
    public String getName() {
      return "Unexpected";
    }
  }

  // Classes for PIC testing
  public static class ClassA {
    public String getName() {
      return "A";
    }
  }

  public static class ClassB {
    public String getName() {
      return "B";
    }
  }

  public static class ClassC {
    public String getName() {
      return "C";
    }
  }

  public static class ClassD {
    public String getName() {
      return "D";
    }
  }

  public static class ClassE {
    public String getName() {
      return "E";
    }
  }

  public static class ClassF {
    public String getName() {
      return "F";
    }
  }

  @Param({
    "DIRECT_GETTER",
    "DIRECT_RECORD",
    "DIRECT_SUBTYPE",
    "DIRECT_UNEXPECTED_FALLBACK",
    "DIRECT_NULL",
    "DYNAMIC_MONOMORPHIC",
    "DYNAMIC_POLYMORPHIC_2",
    "DYNAMIC_POLYMORPHIC_4",
    "DYNAMIC_MEGAMORPHIC"
  })
  private String scenario;

  private CompiledTemplate template;
  private RenderContext context;
  private RenderContext[] polyContexts;
  private long invocationCounter;
  private SlottedSeedingBenchmark.BlackholeOutput output;

  @Setup(Level.Trial)
  public void setUp() {
    output = new SlottedSeedingBenchmark.BlackholeOutput();
    invocationCounter = 0;

    String templateSrc = "$user.name";

    switch (scenario) {
      case "DIRECT_GETTER" -> {
        TemplateContract contract =
            TemplateContract.builder(TemplateId.of("dg.vtl"))
                .parameter("user", BaseUser.class)
                .build();
        template = compile("dg.vtl", templateSrc, contract);
        context = RenderContext.of("user", new BaseUser());
      }
      case "DIRECT_RECORD" -> {
        TemplateContract contract =
            TemplateContract.builder(TemplateId.of("dr.vtl"))
                .parameter("user", UserRecord.class)
                .build();
        template = compile("dr.vtl", templateSrc, contract);
        context = RenderContext.of("user", new UserRecord("RecordUser"));
      }
      case "DIRECT_SUBTYPE" -> {
        TemplateContract contract =
            TemplateContract.builder(TemplateId.of("dst.vtl"))
                .parameter("user", BaseUser.class)
                .build();
        template = compile("dst.vtl", templateSrc, contract);
        context = RenderContext.of("user", new SubUser());
      }
      case "DIRECT_UNEXPECTED_FALLBACK" -> {
        TemplateContract contract =
            TemplateContract.builder(TemplateId.of("duf.vtl"))
                .parameter("user", BaseUser.class)
                .build();
        template = compile("duf.vtl", templateSrc, contract);
        context = RenderContext.of("user", new UnexpectedType());
      }
      case "DIRECT_NULL" -> {
        TemplateContract contract =
            TemplateContract.builder(TemplateId.of("dnull.vtl"))
                .parameter("user", BaseUser.class)
                .build();
        template = compile("dnull.vtl", "$!user.name", contract);
        context = RenderContext.of("user", null);
      }
      case "DYNAMIC_MONOMORPHIC" -> {
        // No contract -> fully dynamic
        template = compile("dyn_mono.vtl", templateSrc, null);
        context = RenderContext.of("user", new ClassA());
      }
      case "DYNAMIC_POLYMORPHIC_2" -> {
        template = compile("dyn_poly2.vtl", templateSrc, null);
        polyContexts =
            new RenderContext[] {
              RenderContext.of("user", new ClassA()), RenderContext.of("user", new ClassB())
            };
      }
      case "DYNAMIC_POLYMORPHIC_4" -> {
        template = compile("dyn_poly4.vtl", templateSrc, null);
        polyContexts =
            new RenderContext[] {
              RenderContext.of("user", new ClassA()),
              RenderContext.of("user", new ClassB()),
              RenderContext.of("user", new ClassC()),
              RenderContext.of("user", new ClassD())
            };
      }
      case "DYNAMIC_MEGAMORPHIC" -> {
        template = compile("dyn_mega.vtl", templateSrc, null);
        polyContexts =
            new RenderContext[] {
              RenderContext.of("user", new ClassA()),
              RenderContext.of("user", new ClassB()),
              RenderContext.of("user", new ClassC()),
              RenderContext.of("user", new ClassD()),
              RenderContext.of("user", new ClassE()),
              RenderContext.of("user", new ClassF()),
              RenderContext.of("user", new UnexpectedType()),
              RenderContext.of("user", new BaseUser())
            };
      }
      default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
    }
  }

  @Benchmark
  public void render(Blackhole bh) throws IOException {
    RenderContext ctx = context;
    if (polyContexts != null) {
      int idx = (int) (invocationCounter++ % polyContexts.length);
      ctx = polyContexts[idx];
    }
    template.render(ctx, output);
    bh.consume(output.count);
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
