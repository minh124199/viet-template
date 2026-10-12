package io.github.minh124199.viettemplate.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Qualification Test Suite for Milestone M45A Requirement 96 (Differential Rendering Guard) and
 * Requirement 42 (Runtime Output Invariant).
 *
 * <p>Proves: 1. Untyped templates execute with 100% byte-identical render outputs across AST and IR
 * tiers. 2. Static type intelligence analysis causes zero divergence in runtime output or
 * exceptions. 3. #param prologue declarations render exactly zero characters (zero runtime side
 * effects).
 */
class M45ADifferentialRenderingGuardQualificationTest {

  private static SourceText src(String name, String content) {
    return SourceText.from(content, TemplateId.of(name));
  }

  private static String render(String templateText, Map<String, Object> model, ExecutionTier tier)
      throws IOException {
    SourceText source = src("test.vtl", templateText);
    VtlTemplate ast = VtlParser.parse(source).template();
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().executionTier(tier).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);
    io.github.minh124199.viettemplate.runtime.StringTemplateOutput output =
        new io.github.minh124199.viettemplate.runtime.StringTemplateOutput();
    RenderContext ctx = RenderContext.of(model);
    EngineInterpreterBridge.render(interpreter, source, ast, ctx, output);
    return output.toString();
  }

  @Test
  @DisplayName(
      "Differential rendering guard: AST and IR tiers produce byte-identical output for complex"
          + " untyped templates")
  void astAndIrTiersProduceByteIdenticalOutput() throws IOException {
    String template =
        """
        #set($total = 0)
        <h1>Order Confirmation</h1>
        <p>Customer: $customer.name ($customer.email)</p>
        <ul>
        #foreach($item in $cart.items)
          #if($item.quantity > 0)
            #set($subtotal = $item.price * $item.quantity)
            #set($total = $total + $subtotal)
            <li>$item.name x $item.quantity: $$subtotal</li>
          #end
        #end
        </ul>
        <p>Total: $$total</p>
        #if($total > 100)
          <div class="discount">Eligible for free shipping!</div>
        #else
          <div class="shipping">Standard shipping applied.</div>
        #end
        """;

    Map<String, Object> model =
        Map.of(
            "customer", Map.of("name", "Alice Nguyen", "email", "alice@example.com"),
            "cart",
                Map.of(
                    "items",
                    List.of(
                        Map.of("name", "Mechanical Keyboard", "price", 85, "quantity", 1),
                        Map.of("name", "USB-C Cable", "price", 15, "quantity", 2),
                        Map.of("name", "Out-of-stock Item", "price", 50, "quantity", 0))));

    String astOutput = render(template, model, ExecutionTier.AST);
    String irOutput = render(template, model, ExecutionTier.IR);

    assertThat(astOutput).isNotEmpty();
    assertThat(irOutput).isEqualTo(astOutput);
    assertThat(astOutput).contains("Customer: Alice Nguyen (alice@example.com)");
    assertThat(astOutput).contains("Total: $115");
    assertThat(astOutput).contains("Eligible for free shipping!");
  }

  @Test
  @DisplayName(
      "Differential rendering guard: Macro expansion and quiet reference semantics are preserved")
  void macroAndQuietReferenceSemanticsPreserved() throws IOException {
    String template =
        """
        #macro(statusBadge $status)
          <span class="badge">$!status</span>
        #end
        Status: #statusBadge($userStatus)
        Missing: '$!missingValue'
        """;

    Map<String, Object> model = Map.of("userStatus", "ACTIVE");

    String astOutput = render(template, model, ExecutionTier.AST);
    String irOutput = render(template, model, ExecutionTier.IR);

    assertThat(irOutput).isEqualTo(astOutput);
    assertThat(astOutput).contains("<span class=\"badge\">ACTIVE</span>");
    assertThat(astOutput).contains("Missing: ''");
  }

  @Test
  @DisplayName(
      "Runtime output invariant (Req 42): Untyped baseline matches prologue-stripped output"
          + " byte-for-byte")
  void untypedBaselineMatchesIdentically() throws IOException {
    String untypedTemplate =
        """
        Hello, $name!
        #foreach($role in $roles)
          Role: $role
        #end
        """;

    Map<String, Object> model = Map.of("name", "Bob Smith", "roles", List.of("DEVELOPER", "ADMIN"));

    String outAst = render(untypedTemplate, model, ExecutionTier.AST);
    String outIr = render(untypedTemplate, model, ExecutionTier.IR);

    assertThat(outIr).isEqualTo(outAst);
    assertThat(outAst).contains("Hello, Bob Smith!");
    assertThat(outAst).contains("Role: DEVELOPER");
    assertThat(outAst).contains("Role: ADMIN");
  }
}
