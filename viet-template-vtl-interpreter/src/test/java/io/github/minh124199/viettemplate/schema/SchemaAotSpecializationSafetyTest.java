package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceOutputNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolution;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.internal.CanonicalModelSchemaConverter;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationContext;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationDecider;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchDecision;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaAotSpecializationSafetyTest {

  public record BoundOrder(String name, int count) {}

  private static OutputSpecializationContext createContext(
      boolean strict, boolean safeProfile, boolean typed) {
    return new OutputSpecializationContext() {
      @Override
      public boolean isStrict() {
        return strict;
      }

      @Override
      public boolean isSafeProfile() {
        return safeProfile;
      }

      @Override
      public boolean isTyped() {
        return typed;
      }

      @Override
      public boolean isNonIntLocal(int slot) {
        return false;
      }
    };
  }

  @Test
  @DisplayName(
      "Shape-only TypeScript schema: validation works, semantic types known, JVM specialization NOT"
          + " enabled")
  void testShapeOnlyTypeScriptSchema() {
    String tsCode =
        """
        export interface Order {
          name: string;
          count: number;
        }
        export interface TemplateParams {
          order: Order;
        }
        """;

    TypeScriptSchemaImporter importer = new TypeScriptSchemaImporter();
    SchemaImportResult importResult = importer.importString(tsCode, "order.vtl");
    assertThat(importResult.hasErrors()).isFalse();

    CanonicalSchema canonicalSchema = importResult.schemas().get("order.vtl");
    ModelSchema modelSchema = CanonicalModelSchemaConverter.toModelSchema(canonicalSchema, null);

    // 1. Property validation works
    // 1a. Valid template
    String validTemplate = "$order.name $order.count";
    SourceText source = SourceText.of(TemplateId.of("order.vtl"), validTemplate);
    VtlParseResult parseResult = VtlParser.parse(source);
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_MIGRATION)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .modelSchema(modelSchema)
            .build();
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(analysis.hasErrors()).isFalse();

    // 1b. Invalid property fails validation
    String invalidTemplate = "$order.unknownField";
    SourceText invalidSource = SourceText.of(TemplateId.of("order.vtl"), invalidTemplate);
    VtlParseResult invalidParse = VtlParser.parse(invalidSource);
    SemanticAnalysisResult invalidAnalysis =
        VtlSemanticAnalyzer.analyze(invalidParse.template(), options);
    assertThat(invalidAnalysis.hasErrors()).isTrue();
    VtlAccessStep.PropertyAccess invalidProp =
        findPropertyAccess(invalidParse.template(), "unknownField");
    MemberResolution invalidRes = invalidAnalysis.memberResolutionOf(invalidProp).orElseThrow();
    assertThat(invalidRes.kind()).isEqualTo(MemberResolution.Kind.NOT_FOUND);

    // 2. Semantic types are known
    VtlAccessStep.PropertyAccess nameProp = findPropertyAccess(parseResult.template(), "name");
    MemberResolution nameRes = analysis.memberResolutionOf(nameProp).orElseThrow();
    assertThat(nameRes.isFound()).isTrue();
    assertThat(nameRes.resultType().typeName()).isEqualTo("java.lang.String");
    assertThat(nameRes.targetMember()).isEmpty(); // No Java Member binding

    VtlAccessStep.PropertyAccess countProp = findPropertyAccess(parseResult.template(), "count");
    MemberResolution countRes = analysis.memberResolutionOf(countProp).orElseThrow();
    assertThat(countRes.isFound()).isTrue();
    assertThat(countRes.resultType().typeName()).isEqualTo("java.lang.Number");
    assertThat(countRes.targetMember()).isEmpty(); // No Java Member binding

    // 3. Direct JVM accessor specialization is NOT enabled solely from schema shape
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, options);
    IrGetProperty nameIrProp = findGetProperty(ir, "name");
    assertThat(nameIrProp.accessPlan()).isInstanceOf(AccessPlan.DynamicCallSite.class);
    assertThat(nameIrProp.accessPlan()).isNotInstanceOf(AccessPlan.DirectRecord.class);
    assertThat(nameIrProp.accessPlan()).isNotInstanceOf(AccessPlan.DirectGetter.class);
    assertThat(nameIrProp.accessPlan()).isNotInstanceOf(AccessPlan.DirectField.class);

    // After optimization pass, still dynamic (DynamicCallSite)
    IrTemplate optIr = IrOptimizer.optimize(ir, IrOptimizationOptions.defaultOptions());
    IrGetProperty optNameProp = findGetProperty(optIr, "name");
    assertThat(optNameProp.accessPlan()).isInstanceOf(AccessPlan.DynamicCallSite.class);

    // 4. writeString / writeInteger specialization is NOT enabled solely from schema shape
    OutputSpecializationContext specContext = createContext(false, false, true);
    WriteDispatchDecision decName =
        OutputSpecializationDecider.decide(optNameProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decName.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(decName.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_VALUE);

    IrGetProperty optCountProp = findGetProperty(optIr, "count");
    WriteDispatchDecision decCount =
        OutputSpecializationDecider.decide(optCountProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decCount.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(decCount.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_VALUE);
  }

  @Test
  @DisplayName(
      "Shape-only JSON Schema: validation works, semantic types known, JVM specialization NOT"
          + " enabled")
  void testShapeOnlyJsonSchema() {
    String jsonSchema =
        """
        {
          "title": "Order",
          "type": "object",
          "properties": {
            "order": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "count": { "type": "integer" }
              }
            }
          }
        }
        """;

    JsonSchemaImporter importer = new JsonSchemaImporter();
    SchemaImportResult importResult = importer.importString(jsonSchema, "order.vtl");
    assertThat(importResult.hasErrors()).isFalse();

    CanonicalSchema canonicalSchema = importResult.schemas().get("order.vtl");
    ModelSchema modelSchema = CanonicalModelSchemaConverter.toModelSchema(canonicalSchema, null);

    // 1. Property validation works
    String validTemplate = "$order.name $order.count";
    SourceText source = SourceText.of(TemplateId.of("order.vtl"), validTemplate);
    VtlParseResult parseResult = VtlParser.parse(source);
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_MIGRATION)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .modelSchema(modelSchema)
            .build();
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(analysis.hasErrors()).isFalse();

    // Invalid property fails validation
    String invalidTemplate = "$order.nonExistent";
    SourceText invalidSource = SourceText.of(TemplateId.of("order.vtl"), invalidTemplate);
    VtlParseResult invalidParse = VtlParser.parse(invalidSource);
    SemanticAnalysisResult invalidAnalysis =
        VtlSemanticAnalyzer.analyze(invalidParse.template(), options);
    assertThat(invalidAnalysis.hasErrors()).isTrue();

    // 2. Semantic types are known
    VtlAccessStep.PropertyAccess nameProp = findPropertyAccess(parseResult.template(), "name");
    MemberResolution nameRes = analysis.memberResolutionOf(nameProp).orElseThrow();
    assertThat(nameRes.isFound()).isTrue();
    assertThat(nameRes.resultType().typeName()).isEqualTo("java.lang.String");
    assertThat(nameRes.targetMember()).isEmpty();

    VtlAccessStep.PropertyAccess countProp = findPropertyAccess(parseResult.template(), "count");
    MemberResolution countRes = analysis.memberResolutionOf(countProp).orElseThrow();
    assertThat(countRes.isFound()).isTrue();
    assertThat(countRes.resultType().typeName()).isEqualTo("int");
    assertThat(countRes.targetMember()).isEmpty();

    // 3. Direct JVM accessor specialization is NOT enabled
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, options);
    IrGetProperty nameIrProp = findGetProperty(ir, "name");
    assertThat(nameIrProp.accessPlan()).isInstanceOf(AccessPlan.DynamicCallSite.class);

    IrTemplate optIr = IrOptimizer.optimize(ir, IrOptimizationOptions.defaultOptions());
    IrGetProperty optNameProp = findGetProperty(optIr, "name");
    assertThat(optNameProp.accessPlan()).isInstanceOf(AccessPlan.DynamicCallSite.class);

    // 4. writeString / writeInteger specialization is NOT enabled
    OutputSpecializationContext specContext = createContext(false, false, true);
    WriteDispatchDecision decName =
        OutputSpecializationDecider.decide(optNameProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decName.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(decName.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_VALUE);

    IrGetProperty optCountProp = findGetProperty(optIr, "count");
    WriteDispatchDecision decCount =
        OutputSpecializationDecider.decide(optCountProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decCount.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(decCount.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_VALUE);
  }

  @Test
  @DisplayName(
      "Java-bound contract: JVM direct accessor and writeString/writeInteger specialization remain"
          + " permitted")
  void testJavaBoundContract() {
    TemplateContract contract =
        TemplateContract.of(
            TemplateId.of("order.vtl"), TemplateParameter.of("order", BoundOrder.class, false));

    ModelSchema modelSchema = ModelSchema.fromContract(contract);

    String templateText = "$order.name $order.count";
    SourceText source = SourceText.of(TemplateId.of("order.vtl"), templateText);
    VtlParseResult parseResult = VtlParser.parse(source);
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_MIGRATION)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .modelSchema(modelSchema)
            .build();
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(analysis.hasErrors()).isFalse();

    // 1. Property validation works & target member is proven
    VtlAccessStep.PropertyAccess nameProp = findPropertyAccess(parseResult.template(), "name");
    MemberResolution nameRes = analysis.memberResolutionOf(nameProp).orElseThrow();
    assertThat(nameRes.isFound()).isTrue();
    assertThat(nameRes.targetMember()).isPresent();
    assertThat(nameRes.kind()).isEqualTo(MemberResolution.Kind.RECORD_COMPONENT);

    // 2. Direct JVM accessor specialization IS enabled
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, options);
    IrGetProperty nameIrProp = findGetProperty(ir, "name");
    assertThat(nameIrProp.accessPlan()).isInstanceOf(AccessPlan.DirectRecord.class);

    AccessPlan.DirectRecord directRecord = (AccessPlan.DirectRecord) nameIrProp.accessPlan();
    assertThat(directRecord.owner()).isEqualTo(BoundOrder.class);
    assertThat(directRecord.componentName()).isEqualTo("name");
    assertThat(directRecord.returnType()).isEqualTo(String.class);

    // 3. writeString specialization IS enabled
    OutputSpecializationContext specContext = createContext(false, false, true);
    WriteDispatchDecision decName =
        OutputSpecializationDecider.decide(nameIrProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decName.kind()).isEqualTo(WriteDispatchKind.WRITE_STRING_SPECIALIZED);
    assertThat(decName.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_STRING);

    // 4. writeInteger specialization IS enabled
    IrGetProperty countIrProp = findGetProperty(ir, "count");
    assertThat(countIrProp.accessPlan()).isInstanceOf(AccessPlan.DirectRecord.class);
    WriteDispatchDecision decCount =
        OutputSpecializationDecider.decide(countIrProp, NullRenderMode.EMPTY_STRING, specContext);
    assertThat(decCount.kind()).isEqualTo(WriteDispatchKind.WRITE_INTEGER_SPECIALIZED);
    assertThat(decCount.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_INTEGER);
  }

  // --- Helper Methods ---

  private static IrGetProperty findGetProperty(IrTemplate ir, String propertyName) {
    for (IrStatement stmt : ir.root().statements()) {
      if (stmt instanceof IrWriteValue wv) {
        IrGetProperty gp = findInExpr(wv.value(), propertyName);
        if (gp != null) return gp;
      }
    }
    throw new AssertionError("Property " + propertyName + " not found in IR");
  }

  private static IrGetProperty findInExpr(IrExpression expr, String propertyName) {
    if (expr instanceof IrGetProperty gp) {
      if (gp.propertyName().equals(propertyName)) {
        return gp;
      }
      return findInExpr(gp.receiver(), propertyName);
    }
    return null;
  }

  private static VtlAccessStep.PropertyAccess findPropertyAccess(
      VtlTemplate template, String propertyName) {
    for (VtlNode child : template.children()) {
      if (child instanceof VtlReferenceOutputNode ro) {
        for (VtlAccessStep step : ro.reference().steps()) {
          if (step instanceof VtlAccessStep.PropertyAccess pa
              && pa.propertyName().equals(propertyName)) {
            return pa;
          }
        }
      }
    }
    throw new AssertionError("PropertyAccess " + propertyName + " not found in AST");
  }
}
