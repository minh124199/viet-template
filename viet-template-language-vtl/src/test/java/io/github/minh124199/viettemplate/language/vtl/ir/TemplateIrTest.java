package io.github.minh124199.viettemplate.language.vtl.ir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BudgetKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBranch;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBranchIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopEnd;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopNext;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopSetup;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.ir.verifier.IrVerifier;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateIrTest {

  public static class Customer {
    private final String name;
    private final boolean premium;

    public Customer(String name, boolean premium) {
      this.name = name;
      this.premium = premium;
    }

    public String getName() {
      return name;
    }

    public boolean isPremium() {
      return premium;
    }
  }

  @Test
  @DisplayName("end-to-end typed template lowers to verifiable IR with AOT eligibility")
  void endToEndTypedTemplate() {
    String sourceStr =
        """
        Dear $customer.name,
        #if($customer.premium)
        Thank you for being a premium customer!
        #else
        Upgrade to premium today!
        #end
        """;

    SourceText source = SourceText.of("welcome.vtl", sourceStr);
    VtlParseResult parsed = VtlParser.parse(source);

    ModelSchema schema =
        ModelSchema.builder()
            .add(
                ModelParameter.of(
                    "customer", VTypes.fromJavaType(Customer.class, Nullability.NON_NULL)))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(analysis.hasErrors()).isFalse();
    assertThat(analysis.capabilities().eligibleForStaticAot()).isTrue();

    IrTemplate ir = AstToIrLowerer.lower(parsed.template(), source, analysis, options);

    // Invariant check passes cleanly
    IrVerifier.verify(ir);

    // Constant pool has static text chunks
    assertThat(ir.constants().size()).isGreaterThan(0);

    // Parameters mapped
    assertThat(ir.parameters()).hasSize(1);
    assertThat(ir.parameters().get(0).name()).isEqualTo("customer");

    // All statements have source spans
    for (IrStatement stmt : ir.root().statements()) {
      assertThat(stmt.span()).isNotNull();
      if (stmt instanceof IrWriteValue wv) {
        assertThat(wv.value().span()).isNotNull();
      }
    }
  }

  @Test
  @DisplayName("all effectful statements and expressions preserve source mapping")
  void preservesSourceMapping() {
    String sourceStr =
        """
        #set($val = 42)
        Output: $val
        """;

    SourceText source = SourceText.of("test.vtl", sourceStr);
    VtlParseResult parsed = VtlParser.parse(source);
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parsed.template(), VtlSemanticOptions.defaults());
    IrTemplate ir =
        AstToIrLowerer.lower(parsed.template(), source, analysis, VtlSemanticOptions.defaults());

    for (IrStatement stmt : ir.root().statements()) {
      assertThat(stmt.span().isKnown()).isTrue();
      if (stmt instanceof IrWriteValue wv) {
        assertThat(wv.value().span().isKnown()).isTrue();
      }
    }
  }

  @Test
  @DisplayName("supports linear control flow, budget checks, and conversions")
  void supportsLinearControlFlowAndMiscOps() {
    SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
    IrLocal iterLocal = new IrLocal("iter", VTypes.DYNAMIC, 0, span);
    IrLocal elemLocal = new IrLocal("item", VTypes.STRING, 1, span);
    IrLocal stateLocal = new IrLocal("foreach", VTypes.DYNAMIC, 2, span);

    IrBlock root =
        IrBlock.of(
            span,
            new IrBudgetCheck(BudgetKind.OUTPUT_CHARS, span),
            new IrNoOp(span),
            new IrLoopSetup(
                LoopPlan.LIST_INDEXED, IrConst.ofString("items", span), iterLocal, span),
            new IrLoopNext(iterLocal, elemLocal, Optional.of(stateLocal), "loop_exit", span),
            new IrStoreLocal(
                elemLocal,
                new IrConvert(
                    new IrUnaryOp(
                        UnaryOpKind.NOT,
                        new IrIsNull(IrConst.ofNull(span), span),
                        VTypes.BOOLEAN,
                        span),
                    VTypes.STRING,
                    span),
                span),
            IrBranchIf.of(
                new IrIsNull(new IrLoadLocal("item", 1, VTypes.STRING, span), span),
                "target_label",
                span),
            new IrBranch("loop_start", span),
            new IrLoopEnd(iterLocal, span),
            new IrReturn(Optional.of(IrConst.ofInt(0, span)), span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("linear.vtl"),
            List.of(),
            root,
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    // Verifier checks and succeeds
    assertThat(IrVerifier.check(template)).isEmpty();
    IrVerifier.verify(template);
  }
}
