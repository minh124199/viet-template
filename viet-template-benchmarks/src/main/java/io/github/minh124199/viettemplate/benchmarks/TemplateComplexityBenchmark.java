package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SlottedRenderContext;
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
import java.util.List;
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
 * Phase 7: Benchmark suite measuring template rendering across distinct template complexity
 * profiles comparing NameBased vs Slotted contexts.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class TemplateComplexityBenchmark {

  public record Address(String street, String city, String zip) {
    public String getCity() {
      return city;
    }
  }

  public record Item(String id, String name, double price) {
    public String getName() {
      return name;
    }
  }

  public static class UserBean {
    private final String name;
    private final String email;
    private final int age;
    private final boolean active;
    private final Address address;

    public UserBean(String name, String email, int age, boolean active, Address address) {
      this.name = name;
      this.email = email;
      this.age = age;
      this.active = active;
      this.address = address;
    }

    public String getName() {
      return name;
    }

    public String getEmail() {
      return email;
    }

    public int getAge() {
      return age;
    }

    public boolean isActive() {
      return active;
    }

    public Address getAddress() {
      return address;
    }

    public String formattedName() {
      return name.toUpperCase();
    }
  }

  @Param({
    "SIMPLE_VARS",
    "PROPERTIES",
    "REPEATED_PROPERTIES",
    "CONDITIONALS",
    "LOOPS",
    "NESTED_EXPR",
    "METHOD_CALLS",
    "MIXED_STATIC_DYNAMIC",
    "REALISTIC_APP"
  })
  private String workload;

  private CompiledTemplate template;
  private RenderContext nameBasedCtx;
  private SlottedRenderContext slottedCtx;
  private SlottedSeedingBenchmark.BlackholeOutput output;

  @Setup(Level.Trial)
  public void setUp() {
    output = new SlottedSeedingBenchmark.BlackholeOutput();

    Address addr = new Address("123 Main St", "Hanoi", "10000");
    UserBean user = new UserBean("Nguyen Van A", "a@example.com", 25, true, addr);
    List<Item> items =
        List.of(
            new Item("1", "Laptop", 1200.0),
            new Item("2", "Mouse", 25.0),
            new Item("3", "Keyboard", 75.0));

    TemplateContract.Builder cb = TemplateContract.builder(TemplateId.of(workload + ".vtl"));
    String templateSrc;
    String[] keys;
    Object[] values;

    switch (workload) {
      case "SIMPLE_VARS" -> {
        templateSrc = "$user $name $age";
        cb.parameter("user", UserBean.class)
            .parameter("name", String.class)
            .parameter("age", Integer.class);
        keys = new String[] {"user", "name", "age"};
        values = new Object[] {user, "Nguyen Van A", 25};
      }
      case "PROPERTIES" -> {
        templateSrc = "$user.name $user.email $user.address.city";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user"};
        values = new Object[] {user};
      }
      case "REPEATED_PROPERTIES" -> {
        templateSrc = "$user.name $user.name $user.name $user.name $user.name";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user"};
        values = new Object[] {user};
      }
      case "CONDITIONALS" -> {
        templateSrc = "#if($user.active)Active User: $user.name#elseInactive#end";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user"};
        values = new Object[] {user};
      }
      case "LOOPS" -> {
        templateSrc = "#foreach($item in $items)$item.name ($item.price) #end";
        cb.parameter("items", List.class);
        keys = new String[] {"items"};
        values = new Object[] {items};
      }
      case "NESTED_EXPR" -> {
        templateSrc = "#if($user.age > 18 && $user.active)Adult: $user.name#end";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user"};
        values = new Object[] {user};
      }
      case "METHOD_CALLS" -> {
        templateSrc = "$user.formattedName() - $user.email";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user"};
        values = new Object[] {user};
      }
      case "MIXED_STATIC_DYNAMIC" -> {
        templateSrc = "$user.name | $dynamicVal | $unknown";
        cb.parameter("user", UserBean.class);
        keys = new String[] {"user", "dynamicVal", "unknown"};
        values = new Object[] {user, "Dynamic String", "Fallback Value"};
      }
      case "REALISTIC_APP" -> {
        templateSrc =
            "<div class=\"user-card\">"
                + "<h2>$user.name</h2>"
                + "<p>Email: $user.email</p>"
                + "<p>Location: $user.address.city, $user.address.zip</p>"
                + "#if($user.active)"
                + "  <span class=\"badge active\">Active</span>"
                + "#end"
                + "<h3>Recent Purchases</h3>"
                + "<ul>"
                + "#foreach($item in $items)"
                + "  <li>$item.name - $$$item.price</li>"
                + "#end"
                + "</ul>"
                + "</div>";
        cb.parameter("user", UserBean.class).parameter("items", List.class);
        keys = new String[] {"user", "items"};
        values = new Object[] {user, items};
      }
      default -> throw new IllegalArgumentException("Unknown workload: " + workload);
    }

    TemplateContract contract = cb.build();
    template = compile(workload + ".vtl", templateSrc, contract);
    nameBasedCtx = RenderContext.of(keys, values);
    slottedCtx = RenderContext.slotted(keys, values);
  }

  @Benchmark
  public void renderNameBased(Blackhole bh) throws IOException {
    template.render(nameBasedCtx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void renderSlotted(Blackhole bh) throws IOException {
    template.render(slottedCtx, output);
    bh.consume(output.count);
  }

  private static CompiledTemplate compile(
      String idStr, String templateText, TemplateContract contract) {
    SourceText source = SourceText.of(idStr, templateText);
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(ModelSchema.fromContract(contract))
            .build();
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
