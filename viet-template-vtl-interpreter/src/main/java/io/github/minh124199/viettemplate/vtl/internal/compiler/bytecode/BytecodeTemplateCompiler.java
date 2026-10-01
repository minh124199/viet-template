package io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateCompilationException;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIndexGet;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.ir.verifier.IrVerifier;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.MemberOperation;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendCapabilities;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendId;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.CompilationStatus;
import io.github.minh124199.viettemplate.vtl.internal.compiler.CompiledArtifact;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateBackend;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateSidecarIndex;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Milestone M11 Ahead-Of-Time (AOT) bytecode template compiler.
 *
 * <p>Translates verified, optimized Intermediate Representation ({@link IrTemplate}) directly into
 * Java 17-compatible JVM bytecode without source code generation or intermediate disk artifacts.
 */
public final class BytecodeTemplateCompiler implements TemplateBackend {

  /**
   * Slot offset separating method invocation arguments (slots 0..3) from compiler-managed semantic
   * user locals and temporary registers.
   *
   * <p>Slot layout partitioning:
   *
   * <ul>
   *   <li>Slots 0..2: {@code this}, {@code RenderContext context}, {@code TemplateOutput out}
   *   <li>Slot 3: Unused in {@code render()}, {@code Object[] args} in chunk helper methods
   *   <li>Slots 4 .. {@code baseTempSlot - 1}: Semantic template locals and parameters ({@code slot
   *       + SLOT_OFFSET})
   *   <li>Slots {@code baseTempSlot} .. {@code scratchSlot - 1}: Depth-scoped loop iteration and
   *       counter registers
   *   <li>Slot {@code scratchSlot}: Dedicated scratch register for short-circuit and null-guard
   *       expressions
   *   <li>{@code totalLocals}: Upper bound {@code scratchSlot + 1}
   * </ul>
   */
  private static final int SLOT_OFFSET = 4;

  private static final BackendId ID = BackendId.AOT_BYTECODE;
  private static final BackendCapabilities CAPABILITIES = BackendCapabilities.AOT_DEFAULT;

  @Override
  public BackendId id() {
    return ID;
  }

  @Override
  public BackendCapabilities capabilities() {
    return CAPABILITIES;
  }

  @Override
  public BackendResult compile(IrTemplate template, BackendOptions options) {
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(options, "options must not be null");

    // 1. Check evaluate and parse capabilities
    if (template.capabilities().requiresRuntimeEvaluation()) {
      Diagnostic diag =
          Diagnostic.warning(
              DiagnosticCode.of("VTLAOT", "1101"),
              "#evaluate directive requires dynamic interpreter execution tier",
              template.span());
      return BackendResult.failure(CompilationStatus.INTERPRETER_REQUIRED_EVALUATE, List.of(diag));
    }
    if (template.capabilities().requiresDynamicIncludeParse() || containsCallTemplate(template)) {
      Diagnostic diag =
          Diagnostic.warning(
              DiagnosticCode.of("VTLAOT", "1102"),
              "#parse directive requires dynamic interpreter execution tier",
              template.span());
      return BackendResult.failure(CompilationStatus.INTERPRETER_REQUIRED_EVALUATE, List.of(diag));
    }

    // 2. Optimize template according to configured options
    MemberAccessPolicy secPolicy =
        options.securityPolicy() != null
            ? toMemberAccessPolicy(options.securityPolicy())
            : MemberAccessPolicy.standard();
    IrTemplate optimized = IrOptimizer.optimize(template, options.optimizationOptions(), secPolicy);
    IrVerifier.verify(optimized);

    // 3. Determine compilation capability status
    CompilationStatus status =
        optimized.capabilities().eligibleForStaticAot()
            ? CompilationStatus.AOT_OK
            : CompilationStatus.AOT_OK_WITH_DYNAMIC_SITES;

    if (options.failOnDynamicFallback() && status == CompilationStatus.AOT_OK_WITH_DYNAMIC_SITES) {
      Diagnostic diag =
          Diagnostic.error(
              DiagnosticCode.of("VTLAOT", "1102"),
              "Template requires dynamic sites but failOnDynamicFallback is enabled",
              template.span());
      return BackendResult.failure(CompilationStatus.AOT_OK_WITH_DYNAMIC_SITES, List.of(diag));
    }

    // 4. Generate deterministic class identity
    String fingerprint =
        BytecodeNaming.sha256Hex(
            optimized.id().value()
                + ":"
                + (options.securityPolicy() != null ? options.securityPolicy().policyId() : "none")
                + ":"
                + options.optimizationOptions().level().name());
    String className = BytecodeNaming.className(optimized.id(), fingerprint);
    String fqcn = options.packagePrefix() + "." + className;
    String internalName = fqcn.replace('.', '/');

    // 5. Code generation context
    int totalLocals = calculateTotalLocals(optimized);
    int maxScratchDepth = calculateMaxScratchDepth(optimized);
    int maxStack = maxScratchDepth <= 2 ? 16 : Math.max(32, 16 + maxScratchDepth * 2);
    IrSlotLayout.SlotLayout layout = IrSlotLayout.layout(optimized);
    int maxSemanticSlot = -1;
    for (IrParameter p : optimized.parameters()) {
      maxSemanticSlot = Math.max(maxSemanticSlot, p.slot());
    }
    maxSemanticSlot = Math.max(maxSemanticSlot, scanMaxSlot(optimized.root()));
    int maxLoopDepth = calculateMaxLoopDepth(optimized.root());
    for (IrFunction f : optimized.functions()) {
      maxSemanticSlot = Math.max(maxSemanticSlot, scanMaxSlot(f.body()));
      for (IrParameter p : f.parameters()) {
        maxSemanticSlot = Math.max(maxSemanticSlot, p.slot());
      }
      for (IrLocal l : f.locals()) {
        maxSemanticSlot = Math.max(maxSemanticSlot, l.slot());
      }
      maxLoopDepth = Math.max(maxLoopDepth, calculateMaxLoopDepth(f.body()));
    }
    int baseTempSlot =
        Math.max(SLOT_OFFSET, Math.max(layout.frameSize(), maxSemanticSlot + 1) + SLOT_OFFSET);
    int scratchSlot = baseTempSlot + (2 * maxLoopDepth);

    CompilerContext context =
        new CompilerContext(
            optimized,
            options,
            internalName,
            fqcn,
            fingerprint,
            baseTempSlot,
            scratchSlot,
            totalLocals);

    // 6. Build bytecode
    ClassFileWriter cf = new ClassFileWriter(internalName, "java/lang/Object");
    cf.addInterface("io/github/minh124199/viettemplate/api/CompiledTemplate");
    cf.setSourceFile(optimized.id().value());

    // Static fields
    cf.addField(
        ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC | ClassFileWriter.ACC_FINAL,
        "TEMPLATE_ID",
        "Ljava/lang/String;");
    cf.addField(
        ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    cf.addField(ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC, "UTF8_CHUNKS", "[[B");
    cf.addField(
        ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC,
        "SECURITY_POLICY",
        "Lio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;");

    // 1. Default constructor: <init>()
    ClassFileWriter.MethodWriter init = cf.addMethod(ClassFileWriter.ACC_PUBLIC, "<init>", "()V");
    init.aload(0);
    init.invokespecial("java/lang/Object", "<init>", "()V");
    init.returnOp();

    // 2. id() implementation: public TemplateId id()
    ClassFileWriter.MethodWriter idMethod =
        cf.addMethod(
            ClassFileWriter.ACC_PUBLIC,
            "id",
            "()Lio/github/minh124199/viettemplate/api/TemplateId;");
    idMethod.getstatic(internalName, "TEMPLATE_ID", "Ljava/lang/String;");
    idMethod.invokestatic(
        "io/github/minh124199/viettemplate/api/TemplateId",
        "of",
        "(Ljava/lang/String;)Lio/github/minh124199/viettemplate/api/TemplateId;");
    idMethod.areturn();

    // 3. render(RenderContext, TemplateOutput)
    ClassFileWriter.MethodWriter render =
        cf.addMethod(
            ClassFileWriter.ACC_PUBLIC,
            "render",
            "(Lio/github/minh124199/viettemplate/api/RenderContext;Lio/github/minh124199/viettemplate/api/TemplateOutput;)V");

    // Initialize local slots and parameters in render method
    render.setMaxLocals(totalLocals);
    render.setMaxStack(maxStack);

    for (int i = 3; i < totalLocals; i++) {
      render.aconst_null();
      render.astore(i);
    }

    // Seed parameters and template locals from context into slots (irSlot + SLOT_OFFSET)
    List<IrSlotLayout.SlotMetadata> seededSlots = context.layout.seededSlots();
    if (!seededSlots.isEmpty()) {
      // Emit optimized slotted seeding path: if context is SlottedRenderContext,
      // use positional getBySlot() instead of name-based get() to eliminate
      // string comparison overhead during render prologue.
      ClassFileWriter.Label nameBasedLabel = render.newLabel();
      ClassFileWriter.Label seedDoneLabel = render.newLabel();

      render.aload(1); // context
      render.instanceofOp("io/github/minh124199/viettemplate/api/SlottedRenderContext");
      render.ifeq(nameBasedLabel);

      // --- Slotted fast path: getBySlot(int) ---
      render.aload(1); // context
      render.checkcast("io/github/minh124199/viettemplate/api/SlottedRenderContext");
      int slottedCtxSlot = context.scratchSlot;
      render.astore(slottedCtxSlot);
      for (IrSlotLayout.SlotMetadata meta : seededSlots) {
        if (meta.kind() == IrSlotLayout.BindingKind.TEMPLATE_PARAMETER) {
          render.aload(slottedCtxSlot);
          render.iconst(meta.slot()); // use the IR slot as the positional index
          render.invokeinterface(
              "io/github/minh124199/viettemplate/api/SlottedRenderContext",
              "getBySlot",
              "(I)Ljava/lang/Object;",
              2);
          render.astore(meta.slot() + SLOT_OFFSET);
        } else {
          render.aload(1); // context
          render.ldc(meta.name());
          render.invokeinterface(
              "io/github/minh124199/viettemplate/api/RenderContext",
              "get",
              "(Ljava/lang/String;)Ljava/lang/Object;",
              2);
          render.astore(meta.slot() + SLOT_OFFSET);
        }
      }
      render.gotoOp(seedDoneLabel);

      // --- Name-based fallback path ---
      render.bindLabel(nameBasedLabel);
      for (IrSlotLayout.SlotMetadata meta : seededSlots) {
        render.aload(1); // context
        render.ldc(meta.name());
        render.invokeinterface(
            "io/github/minh124199/viettemplate/api/RenderContext",
            "get",
            "(Ljava/lang/String;)Ljava/lang/Object;",
            2);
        render.astore(meta.slot() + SLOT_OFFSET);
      }

      render.bindLabel(seedDoneLabel);
    }

    // Compile root block statements
    compileBlock(optimized.root(), render, context, 0);
    if (!blockAlwaysTerminates(optimized.root())) {
      render.returnOp();
    }

    // Compile helper functions (from MethodSizePlanningPass or macros)
    Set<String> compiledMethods = new HashSet<>();
    for (IrFunction function : optimized.functions()) {
      String methodName = BytecodeNaming.chunkMethodName(function.name());
      if (!compiledMethods.add(methodName)) {
        throw new IllegalStateException(
            "Duplicate generated helper method name '"
                + methodName
                + "' detected for function '"
                + function.name()
                + "'. The optimizer must ensure unique helper function names.");
      }
      ClassFileWriter.MethodWriter funcMw =
          cf.addMethod(
              ClassFileWriter.ACC_PUBLIC,
              methodName,
              "(Lio/github/minh124199/viettemplate/api/RenderContext;Lio/github/minh124199/viettemplate/api/TemplateOutput;[Ljava/lang/Object;)V");
      funcMw.setMaxLocals(totalLocals);
      funcMw.setMaxStack(maxStack);

      for (int i = 4; i < totalLocals; i++) {
        funcMw.aconst_null();
        funcMw.astore(i);
      }

      for (int i = 0; i < function.parameters().size(); i++) {
        IrParameter param = function.parameters().get(i);
        funcMw.aload(3); // args
        funcMw.iconst(i);
        funcMw.aaload();
        funcMw.astore(param.slot() + SLOT_OFFSET);
      }

      compileBlock(function.body(), funcMw, context, 0);
      if (!blockAlwaysTerminates(function.body())) {
        funcMw.returnOp();
      }
    }

    // Static initializer: <clinit>()
    ClassFileWriter.MethodWriter clinit =
        cf.addMethod(ClassFileWriter.ACC_STATIC, "<clinit>", "()V");
    clinit.setMaxStack(10);
    clinit.setMaxLocals(4);

    // 1. Initialize UTF8_CHUNKS array
    int utf8Size = context.utf8Chunks.size();
    clinit.iconst(utf8Size);
    clinit.anewarray("[B");
    for (int i = 0; i < utf8Size; i++) {
      clinit.dup();
      clinit.iconst(i);
      byte[] chunkBytes = context.utf8Chunks.get(i);
      String chunkStr = new String(chunkBytes, java.nio.charset.StandardCharsets.UTF_8);
      clinit.ldc(chunkStr);
      clinit.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "toUtf8Bytes",
          "(Ljava/lang/String;)[B");
      clinit.aastore();
    }
    clinit.putstatic(internalName, "UTF8_CHUNKS", "[[B");

    // 2. Initialize SITES array
    int sitesSize = context.dynamicSites.size();
    clinit.iconst(sitesSize);
    clinit.anewarray("io/github/minh124199/viettemplate/runtime/linker/DynamicCallSite");
    for (int i = 0; i < sitesSize; i++) {
      DynamicSiteSpec spec = context.dynamicSites.get(i);
      clinit.dup();
      clinit.iconst(i);
      clinit.iconst(spec.siteId);
      clinit.ldc(spec.memberName);
      clinit.iconst(spec.operation.ordinal());
      clinit.iconst(spec.arity);
      clinit.aconst_null();
      clinit.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "createCallSite",
          "(ILjava/lang/String;IILio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;)Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      clinit.aastore();
    }
    clinit.putstatic(
        internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");

    // 3. Initialize TEMPLATE_ID static field
    clinit.ldc(optimized.id().value());
    clinit.putstatic(internalName, "TEMPLATE_ID", "Ljava/lang/String;");

    // 4. Return
    clinit.returnOp();

    // 7. Binary class bytes emission
    byte[] classBytes = cf.toByteArray();

    // 8. Artifact and Sidecar creation
    TemplateSidecarIndex sidecar =
        new TemplateSidecarIndex(optimized.id(), fqcn, fingerprint, context.sourceMappings);
    CompiledArtifact artifact =
        new CompiledArtifact(optimized.id(), classBytes, fqcn, fingerprint, sidecar, List.of());

    TemplateClassLoader loader =
        options
            .classLoader()
            .orElseGet(
                () -> new TemplateClassLoader(BytecodeTemplateCompiler.class.getClassLoader()));
    Class<? extends CompiledTemplate> clazz = loader.defineTemplateClass(fqcn, classBytes);

    // Initialize static fields (SITES, UTF8_CHUNKS)
    initializeClassFields(clazz, context, options);

    CompiledTemplate instance;
    try {
      instance = clazz.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new TemplateCompilationException(
          "Failed to instantiate compiled template: " + fqcn,
          TemplateId.of(context.templateId),
          SourceSpan.UNKNOWN,
          null,
          e);
    }

    return BackendResult.success(status, artifact, clazz, instance);
  }

  private static void initializeClassFields(
      Class<?> clazz, CompilerContext context, BackendOptions options) {
    try {
      // 1. SITES
      Field sitesField = clazz.getDeclaredField("SITES");
      DynamicCallSite[] sites = new DynamicCallSite[context.dynamicSites.size()];
      for (int i = 0; i < sites.length; i++) {
        DynamicSiteSpec spec = context.dynamicSites.get(i);
        sites[i] =
            BytecodeRuntimeBridge.createCallSite(
                spec.siteId,
                spec.memberName,
                spec.operation.ordinal(),
                spec.arity,
                options.securityPolicy());
      }
      sitesField.set(null, sites);

      // 2. UTF8_CHUNKS
      Field utf8Field = clazz.getDeclaredField("UTF8_CHUNKS");
      byte[][] utf8Chunks = new byte[context.utf8Chunks.size()][];
      for (int i = 0; i < utf8Chunks.length; i++) {
        utf8Chunks[i] = context.utf8Chunks.get(i);
      }
      utf8Field.set(null, utf8Chunks);

      // 3. SECURITY_POLICY
      Field secField = clazz.getDeclaredField("SECURITY_POLICY");
      secField.set(null, options.securityPolicy());
    } catch (ReflectiveOperationException e) {
      throw new TemplateCompilationException(
          "Failed to initialize static fields on compiled class: " + clazz.getName(),
          TemplateId.of(context.templateId),
          SourceSpan.UNKNOWN,
          null,
          e);
    }
  }

  private static int calculateMaxLoopDepth(IrBlock block) {
    if (block == null) {
      return 0;
    }
    int max = 0;
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrLoop loop) {
        int inner =
            1
                + Math.max(
                    calculateMaxLoopDepth(loop.body()),
                    loop.elseBody().map(BytecodeTemplateCompiler::calculateMaxLoopDepth).orElse(0));
        max = Math.max(max, inner);
      } else if (stmt instanceof IrIf ifStmt) {
        int inner =
            Math.max(
                calculateMaxLoopDepth(ifStmt.thenBlock()),
                ifStmt.elseBlock().map(BytecodeTemplateCompiler::calculateMaxLoopDepth).orElse(0));
        max = Math.max(max, inner);
      }
    }
    return max;
  }

  private static int calculateTotalLocals(IrTemplate template) {
    int maxSemanticSlot = -1;
    for (IrParameter p : template.parameters()) {
      maxSemanticSlot = Math.max(maxSemanticSlot, p.slot());
    }
    maxSemanticSlot = Math.max(maxSemanticSlot, scanMaxSlot(template.root()));
    int maxLoopDepth = calculateMaxLoopDepth(template.root());

    for (IrFunction f : template.functions()) {
      maxSemanticSlot = Math.max(maxSemanticSlot, scanMaxSlot(f.body()));
      for (IrParameter p : f.parameters()) {
        maxSemanticSlot = Math.max(maxSemanticSlot, p.slot());
      }
      for (IrLocal l : f.locals()) {
        maxSemanticSlot = Math.max(maxSemanticSlot, l.slot());
      }
      maxLoopDepth = Math.max(maxLoopDepth, calculateMaxLoopDepth(f.body()));
    }

    // Invariant: Compiler temporary locals represented by this allocator are Object/reference
    // slots for their entire lifetime. They are pre-initialized with aconst_null/astore in the
    // method
    // prologue and strictly manipulated via aload/astore to maintain complete compatibility
    // with ClassFileWriter's full_frame StackMapTable generator.
    IrSlotLayout.SlotLayout layout = IrSlotLayout.layout(template);
    int baseTempSlot =
        Math.max(SLOT_OFFSET, Math.max(layout.frameSize(), maxSemanticSlot + 1) + SLOT_OFFSET);
    int scratchSlot = baseTempSlot + (2 * maxLoopDepth);
    int maxScratchDepth = calculateMaxScratchDepth(template);
    int scratchSlotsCount = Math.max(1, maxScratchDepth);
    int totalLocals = scratchSlot + scratchSlotsCount;
    if (scratchSlot >= totalLocals) {
      throw new IllegalStateException("totalLocals must be strictly greater than scratchSlot");
    }
    return totalLocals;
  }

  private static int calculateExprScratchDepth(IrExpression expr) {
    if (expr == null) return 0;
    if (expr instanceof IrGetProperty prop) {
      return Math.max(1, calculateExprScratchDepth(prop.receiver()));
    }
    if (expr instanceof IrDynamicDispatch dyn) {
      if (dyn.receiver().isEmpty()) return 0;
      int depth = calculateExprScratchDepth(dyn.receiver().get());
      int numArgs = dyn.arguments().size();
      for (int i = 0; i < numArgs; i++) {
        depth = Math.max(depth, 1 + i + calculateExprScratchDepth(dyn.arguments().get(i)));
      }
      return Math.max(depth, 1 + numArgs);
    }
    if (expr instanceof IrInvokeAllowedMethod inv) {
      int depth = calculateExprScratchDepth(inv.receiver());
      int numArgs = inv.arguments().size();
      for (int i = 0; i < numArgs; i++) {
        depth = Math.max(depth, 1 + i + calculateExprScratchDepth(inv.arguments().get(i)));
      }
      return Math.max(depth, 1 + numArgs);
    }
    if (expr instanceof IrBinaryOp bin) {
      return Math.max(
          calculateExprScratchDepth(bin.left()), 1 + calculateExprScratchDepth(bin.right()));
    }
    if (expr instanceof IrUnaryOp un) {
      return calculateExprScratchDepth(un.operand());
    }
    if (expr instanceof IrIndexGet idx) {
      return Math.max(
          calculateExprScratchDepth(idx.receiver()), 1 + calculateExprScratchDepth(idx.index()));
    }
    if (expr instanceof IrTruthiness tr) {
      return calculateExprScratchDepth(tr.expression());
    }
    if (expr instanceof IrIsNull isNull) {
      return Math.max(1, calculateExprScratchDepth(isNull.expression()));
    }
    if (expr instanceof IrAlternateValue alt) {
      return Math.max(
          calculateExprScratchDepth(alt.primary()), 1 + calculateExprScratchDepth(alt.fallback()));
    }
    if (expr instanceof IrConvert conv) {
      return calculateExprScratchDepth(conv.expression());
    }
    return 0;
  }

  private static int calculateStmtScratchDepth(IrStatement stmt) {
    if (stmt instanceof IrWriteValue wv) {
      return calculateExprScratchDepth(wv.value());
    }
    if (stmt instanceof IrStoreLocal sl) {
      return calculateExprScratchDepth(sl.value());
    }
    if (stmt instanceof IrSetProperty sp) {
      return Math.max(
          calculateExprScratchDepth(sp.target()), 1 + calculateExprScratchDepth(sp.value()));
    }
    if (stmt instanceof IrSetIndex si) {
      return Math.max(
          calculateExprScratchDepth(si.target()),
          Math.max(
              1 + calculateExprScratchDepth(si.index()),
              2 + calculateExprScratchDepth(si.value())));
    }
    if (stmt instanceof IrEvaluate ev) {
      return calculateExprScratchDepth(ev.expression());
    }
    if (stmt instanceof IrIf ifStmt) {
      int d = calculateExprScratchDepth(ifStmt.condition());
      d = Math.max(d, calculateBlockScratchDepth(ifStmt.thenBlock()));
      if (ifStmt.elseBlock().isPresent()) {
        d = Math.max(d, calculateBlockScratchDepth(ifStmt.elseBlock().get()));
      }
      return d;
    }
    if (stmt instanceof IrLoop loop) {
      int d = calculateExprScratchDepth(loop.iterable());
      d = Math.max(d, calculateBlockScratchDepth(loop.body()));
      if (loop.elseBody().isPresent()) {
        d = Math.max(d, calculateBlockScratchDepth(loop.elseBody().get()));
      }
      return d;
    }
    return 0;
  }

  private static int calculateBlockScratchDepth(IrBlock block) {
    if (block == null) return 0;
    int max = 0;
    for (IrStatement stmt : block.statements()) {
      max = Math.max(max, calculateStmtScratchDepth(stmt));
    }
    return max;
  }

  private static int calculateMaxScratchDepth(IrTemplate template) {
    int max = calculateBlockScratchDepth(template.root());
    for (IrFunction fn : template.functions()) {
      max = Math.max(max, calculateBlockScratchDepth(fn.body()));
    }
    return max;
  }

  private static int scanMaxSlot(IrBlock block) {
    int max = -1;
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal sl) {
        max = Math.max(max, sl.local().slot());
      } else if (stmt instanceof IrLoop loop) {
        max = Math.max(max, loop.elementLocal().slot());
        if (loop.loopStateLocal().isPresent()) {
          max = Math.max(max, loop.loopStateLocal().get().slot());
        }
        max = Math.max(max, scanMaxSlot(loop.body()));
        if (loop.elseBody().isPresent()) {
          max = Math.max(max, scanMaxSlot(loop.elseBody().get()));
        }
      } else if (stmt instanceof IrIf ifStmt) {
        max = Math.max(max, scanMaxSlot(ifStmt.thenBlock()));
        if (ifStmt.elseBlock().isPresent()) {
          max = Math.max(max, scanMaxSlot(ifStmt.elseBlock().get()));
        }
      }
    }
    return max;
  }

  private static void compileBlock(
      IrBlock block, ClassFileWriter.MethodWriter mw, CompilerContext context, int loopDepth) {
    for (IrStatement stmt : block.statements()) {
      compileStatement(stmt, mw, context, loopDepth);
      if (stmt instanceof IrStop
          || stmt instanceof IrReturn
          || (stmt instanceof IrBreak && context.currentLoopExit() == null)) {
        break;
      }
    }
  }

  private static void compileStatement(
      IrStatement stmt, ClassFileWriter.MethodWriter mw, CompilerContext context, int loopDepth) {
    SourceSpan span = stmt.span();
    if (span != null && span.isKnown()) {
      mw.addLineNumber(span.startLine());
      context.recordSourceMapping(mw.currentPc(), span.startLine(), span);
    }

    if (stmt instanceof IrWriteConst wc) {
      compileWriteConst(wc, mw, context);
    } else if (stmt instanceof IrWriteValue wv) {
      compileWriteValue(wv, mw, context);
    } else if (stmt instanceof IrStoreLocal sl) {
      compileStoreLocal(sl, mw, context);
    } else if (stmt instanceof IrIf ifStmt) {
      compileIf(ifStmt, mw, context, loopDepth);
    } else if (stmt instanceof IrLoop loop) {
      compileLoop(loop, mw, context, loopDepth);
    } else if (stmt instanceof IrBreak) {
      ClassFileWriter.Label exitLabel = context.currentLoopExit();
      if (exitLabel != null) {
        mw.gotoOp(exitLabel);
      } else {
        mw.returnOp();
      }
    } else if (stmt instanceof IrStop || stmt instanceof IrReturn) {
      mw.returnOp();
    } else if (stmt instanceof IrCallMacro callM) {
      compileCallMacro(callM, mw, context);
    } else if (stmt instanceof IrSetProperty sp) {
      compileSetProperty(sp, mw, context);
    } else if (stmt instanceof IrSetIndex si) {
      compileSetIndex(si, mw, context);
    } else if (stmt instanceof IrBudgetCheck) {
      mw.aload(2); // output
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "countLoopIteration",
          "(Lio/github/minh124199/viettemplate/api/TemplateOutput;)V");
    } else if (stmt instanceof IrNoOp) {
      // no-op
    }
  }

  private static void compileWriteConst(
      IrWriteConst wc, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    Optional<IrTextConstant> opt = context.template.constants().getTextConstant(wc.constantId());
    if (opt.isEmpty()) {
      return;
    }
    IrTextConstant constant = opt.get();
    String text = constant.text();
    byte[] utf8Bytes = constant.utf8Bytes().orElse(null);

    mw.aload(2); // output
    mw.ldc(text);

    if (utf8Bytes != null) {
      int chunkIdx = context.registerUtf8Chunk(utf8Bytes);
      mw.getstatic(context.internalName, "UTF8_CHUNKS", "[[B");
      mw.iconst(chunkIdx);
      mw.aaload();
    } else {
      mw.aconst_null();
    }

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "writeConst",
        "(Lio/github/minh124199/viettemplate/api/TemplateOutput;Ljava/lang/String;[B)V");
  }

  private static void compileWriteValue(
      IrWriteValue wv, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    compileExpression(wv.value(), mw, context);
    mw.aload(2); // output
    mw.iconst(wv.escapeMode().ordinal());
    mw.iconst(wv.nullMode().ordinal());

    String literal = null;
    if (wv.span() != null && wv.span().isKnown()) {
      // In non-strict mode, unescaped literal representation
      literal = "$" + extractRootName(wv.value());
    }

    if (literal != null) {
      mw.ldc(literal);
    } else {
      mw.aconst_null();
    }

    boolean isStrict =
        context.options.strictReferences()
            || wv.nullMode()
                == io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode
                    .THROW_ERROR;
    mw.iconst(isStrict ? 1 : 0);
    mw.ldc(context.template.id().value());
    SourceSpan span = wv.span();
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);
    mw.getstatic(
        context.internalName,
        "SECURITY_POLICY",
        "Lio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;");

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "writeValue",
        "(Ljava/lang/Object;Lio/github/minh124199/viettemplate/api/TemplateOutput;IILjava/lang/String;ZLjava/lang/String;IIIILio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;)V");
  }

  private static void compileIf(
      IrIf ifStmt, ClassFileWriter.MethodWriter mw, CompilerContext context, int loopDepth) {
    ClassFileWriter.Label elseLabel = mw.newLabel();
    ClassFileWriter.Label endLabel = mw.newLabel();

    compileExpression(ifStmt.condition(), mw, context);
    mw.iconst(1); // emptyCheck = true
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "isTruthy",
        "(Ljava/lang/Object;Z)Z");
    mw.ifeq(elseLabel);

    compileBlock(ifStmt.thenBlock(), mw, context, loopDepth);
    if (!blockAlwaysTerminates(ifStmt.thenBlock())) {
      mw.gotoOp(endLabel);
    }

    mw.bindLabel(elseLabel);
    if (ifStmt.elseBlock().isPresent()) {
      compileBlock(ifStmt.elseBlock().get(), mw, context, loopDepth);
    }
    mw.bindLabel(endLabel);
  }

  private static boolean blockAlwaysTerminates(IrBlock block) {
    if (block == null || block.isEmpty()) {
      return false;
    }
    for (IrStatement s : block.statements()) {
      if (s instanceof IrStop || s instanceof IrReturn || s instanceof IrBreak) {
        return true;
      }
    }
    return false;
  }

  private static void compileLoop(
      IrLoop loop, ClassFileWriter.MethodWriter mw, CompilerContext context, int loopDepth) {
    ClassFileWriter.Label loopHeader = mw.newLabel();
    ClassFileWriter.Label loopExit = mw.newLabel();

    int iterSlot = context.baseTempSlot + (2 * loopDepth);
    int counterSlot = context.baseTempSlot + (2 * loopDepth) + 1;
    if (counterSlot >= context.scratchSlot) {
      throw new IllegalStateException(
          String.format(
              "Loop slot allocation overflow: loopDepth=%d, counterSlot=%d exceeds scratchSlot=%d"
                  + " (baseTempSlot=%d)",
              loopDepth, counterSlot, context.scratchSlot, context.baseTempSlot));
    }
    int itemSlot = loop.elementLocal().slot() + SLOT_OFFSET;

    SourceSpan loopSpan = loop.span();
    if (loop.plan() == LoopPlan.RANGE && loop.iterable() instanceof IrBinaryOp range) {
      compileExpression(range.left(), mw, context);
      compileExpression(range.right(), mw, context);
      mw.iconst(10000); // maxRangeSize
      emitLoopLocation(mw, context, loopSpan);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "rangeIterator",
          "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;IIII)Ljava/util/Iterator;");
    } else {
      compileExpression(loop.iterable(), mw, context);
      mw.getstatic(
          context.internalName,
          "SECURITY_POLICY",
          "Lio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;");
      emitLoopLocation(mw, context, loopSpan);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          loop.plan() == LoopPlan.ARRAY ? "arrayIterator" : "toIterator",
          "(Ljava/lang/Object;Lio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;Ljava/lang/String;IIII)Ljava/util/Iterator;");
    }
    mw.astore(iterSlot);

    Integer parentMetaSlot = context.currentForeachMetaSlot();
    int metaSlot = -1;
    if (loop.loopStateLocal().isPresent()) {
      metaSlot = loop.loopStateLocal().get().slot() + SLOT_OFFSET;
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "createLoopState",
          "()Ljava/lang/Object;");
      mw.astore(counterSlot);
      context.pushForeachMetaSlot(metaSlot);
    }

    mw.bindLabel(loopHeader);
    mw.aload(iterSlot);
    mw.invokeinterface("java/util/Iterator", "hasNext", "()Z", 1);
    mw.ifeq(loopExit);

    mw.aload(2);
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "countLoopIteration",
        "(Lio/github/minh124199/viettemplate/api/TemplateOutput;)V");

    mw.aload(iterSlot);
    mw.invokeinterface("java/util/Iterator", "next", "()Ljava/lang/Object;", 1);
    mw.astore(itemSlot);

    List<Integer> loopOwnedSlots =
        context.layout != null && context.layout.loopLocals() != null
            ? context.layout.loopLocals().getOrDefault(loop, List.of())
            : List.of();

    if (loop.loopStateLocal().isPresent()) {
      mw.aload(counterSlot);
      mw.aload(iterSlot);
      mw.invokeinterface("java/util/Iterator", "hasNext", "()Z", 1);
      if (parentMetaSlot != null) {
        mw.aload(parentMetaSlot);
      } else {
        mw.aconst_null();
      }
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "createForeachMetadata",
          "(Ljava/lang/Object;ZLjava/lang/Object;)Lio/github/minh124199/viettemplate/language/vtl/semantics/scope/ForeachMetadata;");
      mw.astore(metaSlot);
    }

    for (int slot : loopOwnedSlots) {
      mw.aconst_null();
      mw.astore(slot + SLOT_OFFSET);
    }

    context.pushLoop(loopExit);
    compileBlock(loop.body(), mw, context, loopDepth + 1);
    context.popLoop();
    if (loop.loopStateLocal().isPresent()) {
      context.popForeachMetaSlot();
    }

    mw.gotoOp(loopHeader);
    mw.bindLabel(loopExit);
    mw.aconst_null();
    mw.astore(itemSlot);
    if (metaSlot != -1) {
      mw.aconst_null();
      mw.astore(metaSlot);
    }
    for (int slot : loopOwnedSlots) {
      mw.aconst_null();
      mw.astore(slot + SLOT_OFFSET);
    }
  }

  private static void emitLoopLocation(
      ClassFileWriter.MethodWriter mw, CompilerContext context, SourceSpan span) {
    mw.ldc(context.template.id().value());
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);
  }

  private static void compileStoreLocal(
      IrStoreLocal sl, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    compileExpression(sl.value(), mw, context);
    if (!context.options.setNullAllowed()) {
      int tempValSlot = context.scratchSlot;
      mw.astore(tempValSlot);
      mw.aload(tempValSlot);
      ClassFileWriter.Label skipStore = mw.newLabel();
      mw.ifnull(skipStore);
      mw.aload(tempValSlot);
      mw.astore(sl.local().slot() + SLOT_OFFSET);
      mw.aload(1);
      mw.ldc(sl.local().name());
      mw.aload(sl.local().slot() + SLOT_OFFSET);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "recordContextVariable",
          "(Lio/github/minh124199/viettemplate/api/RenderContext;Ljava/lang/String;Ljava/lang/Object;)V");
      mw.bindLabel(skipStore);
    } else {
      mw.astore(sl.local().slot() + SLOT_OFFSET);
      mw.aload(1);
      mw.ldc(sl.local().name());
      mw.aload(sl.local().slot() + SLOT_OFFSET);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "recordContextVariable",
          "(Lio/github/minh124199/viettemplate/api/RenderContext;Ljava/lang/String;Ljava/lang/Object;)V");
    }
  }

  private static void compileCallMacro(
      IrCallMacro callM, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    mw.aload(2); // output
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "countLoopIteration",
        "(Lio/github/minh124199/viettemplate/api/TemplateOutput;)V");

    String methodName = BytecodeNaming.chunkMethodName(callM.macroName());
    mw.aload(0); // this
    mw.aload(1); // context
    mw.aload(2); // output

    List<IrExpression> args = callM.arguments();
    mw.iconst(args.size());
    mw.anewarray("java/lang/Object");
    for (int i = 0; i < args.size(); i++) {
      mw.dup();
      mw.iconst(i);
      compileExpression(args.get(i), mw, context);
      mw.aastore();
    }

    mw.invokevirtual(
        context.internalName,
        methodName,
        "(Lio/github/minh124199/viettemplate/api/RenderContext;Lio/github/minh124199/viettemplate/api/TemplateOutput;[Ljava/lang/Object;)V");
  }

  private static void compileSetProperty(
      IrSetProperty sp, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    int siteIdx = context.registerDynamicSite(sp.propertyName(), MemberOperation.PROPERTY_SET, 1);
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();

    compileExpression(sp.target(), mw, context, 0);
    compileExpression(sp.value(), mw, context, 1);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicSetProperty",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;Ljava/lang/Object;)V");
  }

  private static void compileSetIndex(
      IrSetIndex si, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    int siteIdx = context.registerDynamicSite("setIndex", MemberOperation.INDEX_SET, 2);
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();

    compileExpression(si.target(), mw, context, 0);
    compileExpression(si.index(), mw, context, 1);
    compileExpression(si.value(), mw, context, 2);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicSetIndex",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
  }

  private static void compileExpression(
      IrExpression expr, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    compileExpression(expr, mw, context, 0);
  }

  private static void compileExpression(
      IrExpression expr,
      ClassFileWriter.MethodWriter mw,
      CompilerContext context,
      int scratchOffset) {
    if (expr instanceof IrConst c) {
      compileConst(c, mw);
    } else if (expr instanceof IrLoadLocal load) {
      mw.aload(load.slot() + SLOT_OFFSET);
    } else if (expr instanceof IrLoadParam param) {
      mw.aload(param.slot() + SLOT_OFFSET);
    } else if (expr instanceof IrGetProperty prop) {
      compileGetProperty(prop, mw, context, scratchOffset);
    } else if (expr instanceof IrDynamicDispatch dyn) {
      compileDynamicDispatch(dyn, mw, context, scratchOffset);
    } else if (expr instanceof IrIndexGet idx) {
      compileIndexGet(idx, mw, context, scratchOffset);
    } else if (expr instanceof IrBinaryOp bin) {
      compileBinaryOp(bin, mw, context, scratchOffset);
    } else if (expr instanceof IrUnaryOp un) {
      compileUnaryOp(un, mw, context, scratchOffset);
    } else if (expr instanceof IrTruthiness tr) {
      compileExpression(tr.expression(), mw, context, scratchOffset);
      mw.iconst(tr.emptyCheck() ? 1 : 0);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
    } else if (expr instanceof IrIsNull isNull) {
      compileIsNull(isNull, mw, context, scratchOffset);
    } else if (expr instanceof IrAlternateValue alt) {
      compileExpression(alt.primary(), mw, context, scratchOffset);
      compileExpression(alt.fallback(), mw, context, scratchOffset + 1);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "alternateValue",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    } else if (expr instanceof IrInvokeAllowedMethod inv) {
      compileInvokeAllowedMethod(inv, mw, context, scratchOffset);
    } else if (expr instanceof IrConvert conv) {
      compileExpression(conv.expression(), mw, context, scratchOffset);
    } else {
      mw.aconst_null();
    }
  }

  private static void compileConst(IrConst c, ClassFileWriter.MethodWriter mw) {
    Object val = c.value();
    if (val == null) {
      mw.aconst_null();
    } else if (val instanceof String s) {
      mw.ldc(s);
    } else if (val instanceof Integer i) {
      mw.iconst(i);
      mw.invokestatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
    } else if (val instanceof Long l) {
      mw.lconst(l);
      mw.invokestatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
    } else if (val instanceof Double d) {
      mw.dconst(d);
      mw.invokestatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
    } else if (val instanceof Boolean b) {
      mw.iconst(b ? 1 : 0);
      mw.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
    } else {
      mw.ldc(val.toString());
    }
  }

  private static void compileGetProperty(
      IrGetProperty prop,
      ClassFileWriter.MethodWriter mw,
      CompilerContext context,
      int scratchOffset) {
    AccessPlan plan = prop.accessPlan();
    int scratchSlot = context.scratchSlotBase + scratchOffset;

    if (plan instanceof AccessPlan.DirectRecord rec) {
      ClassFileWriter.Label nullLabel = mw.newLabel();
      ClassFileWriter.Label dynamicLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();
      compileExpression(prop.receiver(), mw, context, scratchOffset);
      mw.astore(scratchSlot);
      mw.aload(scratchSlot);
      mw.ifnull(nullLabel);
      mw.aload(scratchSlot);
      String owner = rec.owner().getName().replace('.', '/');
      mw.instanceofOp(owner);
      mw.ifeq(dynamicLabel);
      mw.aload(scratchSlot);
      mw.checkcast(owner);
      String desc = "()" + rec.returnType().descriptorString();
      mw.invokevirtual(owner, rec.componentName(), desc);
      boxIfPrimitive(rec.returnType(), mw);
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(dynamicLabel);
      int siteIdx =
          context.registerDynamicSite(prop.propertyName(), MemberOperation.PROPERTY_GET, 0);
      mw.getstatic(
          context.internalName,
          "SITES",
          "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      mw.iconst(siteIdx);
      mw.aaload();
      mw.aload(scratchSlot);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "dynamicGetProperty",
          "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;)Ljava/lang/Object;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(nullLabel);
      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
    } else if (plan instanceof AccessPlan.DirectGetter getter) {
      ClassFileWriter.Label nullLabel = mw.newLabel();
      ClassFileWriter.Label dynamicLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();
      compileExpression(prop.receiver(), mw, context, scratchOffset);
      mw.astore(scratchSlot);
      mw.aload(scratchSlot);
      mw.ifnull(nullLabel);
      mw.aload(scratchSlot);
      String owner = getter.owner().getName().replace('.', '/');
      mw.instanceofOp(owner);
      mw.ifeq(dynamicLabel);
      mw.aload(scratchSlot);
      mw.checkcast(owner);
      String desc = "()" + getter.returnType().descriptorString();
      if (getter.owner().isInterface()) {
        mw.invokeinterface(owner, getter.methodName(), desc, 1);
      } else {
        mw.invokevirtual(owner, getter.methodName(), desc);
      }
      boxIfPrimitive(getter.returnType(), mw);
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(dynamicLabel);
      int siteIdx =
          context.registerDynamicSite(prop.propertyName(), MemberOperation.PROPERTY_GET, 0);
      mw.getstatic(
          context.internalName,
          "SITES",
          "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      mw.iconst(siteIdx);
      mw.aaload();
      mw.aload(scratchSlot);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "dynamicGetProperty",
          "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;)Ljava/lang/Object;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(nullLabel);
      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
    } else if (plan instanceof AccessPlan.DirectField field) {
      ClassFileWriter.Label nullLabel = mw.newLabel();
      ClassFileWriter.Label dynamicLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();
      compileExpression(prop.receiver(), mw, context, scratchOffset);
      mw.astore(scratchSlot);
      mw.aload(scratchSlot);
      mw.ifnull(nullLabel);
      mw.aload(scratchSlot);
      String owner = field.owner().getName().replace('.', '/');
      mw.instanceofOp(owner);
      mw.ifeq(dynamicLabel);
      mw.aload(scratchSlot);
      mw.checkcast(owner);
      String desc = field.fieldType().descriptorString();
      mw.getfield(owner, field.fieldName(), desc);
      boxIfPrimitive(field.fieldType(), mw);
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(dynamicLabel);
      int siteIdx =
          context.registerDynamicSite(prop.propertyName(), MemberOperation.PROPERTY_GET, 0);
      mw.getstatic(
          context.internalName,
          "SITES",
          "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      mw.iconst(siteIdx);
      mw.aaload();
      mw.aload(scratchSlot);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "dynamicGetProperty",
          "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;)Ljava/lang/Object;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(nullLabel);
      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
    } else if (plan instanceof AccessPlan.MapLookup mapLookup) {
      ClassFileWriter.Label nullLabel = mw.newLabel();
      ClassFileWriter.Label dynamicLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();
      compileExpression(prop.receiver(), mw, context, scratchOffset);
      mw.astore(scratchSlot);
      mw.aload(scratchSlot);
      mw.ifnull(nullLabel);
      mw.aload(scratchSlot);
      mw.instanceofOp("java/util/Map");
      mw.ifeq(dynamicLabel);
      mw.aload(scratchSlot);
      mw.checkcast("java/util/Map");
      mw.ldc(mapLookup.keyConstant());
      mw.invokeinterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", 2);
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(dynamicLabel);
      int siteIdx =
          context.registerDynamicSite(prop.propertyName(), MemberOperation.PROPERTY_GET, 0);
      mw.getstatic(
          context.internalName,
          "SITES",
          "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      mw.iconst(siteIdx);
      mw.aaload();
      mw.aload(scratchSlot);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "dynamicGetProperty",
          "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;)Ljava/lang/Object;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(nullLabel);
      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
    } else {
      // Dynamic call site dispatch
      int siteIdx =
          context.registerDynamicSite(prop.propertyName(), MemberOperation.PROPERTY_GET, 0);
      mw.getstatic(
          context.internalName,
          "SITES",
          "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
      mw.iconst(siteIdx);
      mw.aaload();
      compileExpression(prop.receiver(), mw, context, scratchOffset);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "dynamicGetProperty",
          "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;)Ljava/lang/Object;");
    }
  }

  private static void boxIfPrimitive(Class<?> clazz, ClassFileWriter.MethodWriter mw) {
    if (!clazz.isPrimitive()) {
      return;
    }
    if (clazz == boolean.class) {
      mw.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
    } else if (clazz == byte.class) {
      mw.invokestatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
    } else if (clazz == short.class) {
      mw.invokestatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
    } else if (clazz == char.class) {
      mw.invokestatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
    } else if (clazz == int.class) {
      mw.invokestatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
    } else if (clazz == long.class) {
      mw.invokestatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
    } else if (clazz == float.class) {
      mw.invokestatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
    } else if (clazz == double.class) {
      mw.invokestatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
    }
  }

  private static void compileInvokeAllowedMethod(
      IrInvokeAllowedMethod inv,
      ClassFileWriter.MethodWriter mw,
      CompilerContext context,
      int scratchOffset) {
    Method m = inv.targetMethod();
    Class<?> ownerClass = m.getDeclaringClass();
    String owner = ownerClass.getName().replace('.', '/');
    Class<?>[] ptypes = m.getParameterTypes();
    int numArgs = inv.arguments().size();

    int slotRecv = context.scratchSlotBase + scratchOffset;
    int[] slotArgs = new int[numArgs];
    for (int i = 0; i < numArgs; i++) {
      slotArgs[i] = context.scratchSlotBase + scratchOffset + 1 + i;
    }

    ClassFileWriter.Label nullLabel = mw.newLabel();
    ClassFileWriter.Label dynamicLabel = mw.newLabel();
    ClassFileWriter.Label endLabel = mw.newLabel();

    // 1. Evaluate receiver expression
    compileExpression(inv.receiver(), mw, context, scratchOffset);
    mw.astore(slotRecv);

    // 2. Evaluate each argument expression exactly once left-to-right into dedicated scratch slots
    for (int i = 0; i < numArgs; i++) {
      compileExpression(inv.arguments().get(i), mw, context, scratchOffset + 1 + i);
      mw.astore(slotArgs[i]);
    }

    // 3. Receiver null check: return null when receiver is null (all arguments were evaluated once)
    mw.aload(slotRecv);
    mw.ifnull(nullLabel);

    // 4. Runtime guards
    // Guard 4a: Receiver type check
    mw.aload(slotRecv);
    mw.instanceofOp(owner);
    mw.ifeq(dynamicLabel);

    // Guard 4b: Argument type checks (widening-only, no narrowing!)
    for (int i = 0; i < numArgs; i++) {
      Class<?> ptype = ptypes[i];
      int argSlot = slotArgs[i];
      if (ptype.isPrimitive()) {
        mw.aload(argSlot);
        mw.ifnull(dynamicLabel);
        if (ptype == boolean.class) {
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Boolean");
          mw.ifeq(dynamicLabel);
        } else if (ptype == char.class) {
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Character");
          mw.ifeq(dynamicLabel);
        } else if (ptype == byte.class) {
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Byte");
          mw.ifeq(dynamicLabel);
        } else if (ptype == short.class) {
          ClassFileWriter.Label shortOk = mw.newLabel();
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Short");
          mw.ifne(shortOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Byte");
          mw.ifeq(dynamicLabel);
          mw.bindLabel(shortOk);
        } else if (ptype == int.class) {
          ClassFileWriter.Label intOk = mw.newLabel();
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Integer");
          mw.ifne(intOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Short");
          mw.ifne(intOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Byte");
          mw.ifeq(dynamicLabel);
          mw.bindLabel(intOk);
        } else if (ptype == long.class) {
          ClassFileWriter.Label longOk = mw.newLabel();
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Long");
          mw.ifne(longOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Integer");
          mw.ifne(longOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Short");
          mw.ifne(longOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Byte");
          mw.ifeq(dynamicLabel);
          mw.bindLabel(longOk);
        } else if (ptype == float.class) {
          ClassFileWriter.Label floatOk = mw.newLabel();
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Float");
          mw.ifne(floatOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Long");
          mw.ifne(floatOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Integer");
          mw.ifne(floatOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Short");
          mw.ifne(floatOk);
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Byte");
          mw.ifeq(dynamicLabel);
          mw.bindLabel(floatOk);
        } else if (ptype == double.class) {
          mw.aload(argSlot);
          mw.instanceofOp("java/lang/Number");
          mw.ifeq(dynamicLabel);
        }
      } else if (ptype != Object.class) {
        ClassFileWriter.Label argOk = mw.newLabel();
        mw.aload(argSlot);
        mw.ifnull(argOk);
        mw.aload(argSlot);
        mw.instanceofOp(ptype.getName().replace('.', '/'));
        mw.ifeq(dynamicLabel);
        mw.bindLabel(argOk);
      }
    }

    // 5. Direct invocation path (zero heap allocations)
    mw.aload(slotRecv);
    mw.checkcast(owner);
    for (int i = 0; i < numArgs; i++) {
      mw.aload(slotArgs[i]);
      unboxIfPrimitive(ptypes[i], mw);
    }
    StringBuilder descBuilder = new StringBuilder("(");
    for (Class<?> p : ptypes) {
      descBuilder.append(p.descriptorString());
    }
    descBuilder.append(")").append(m.getReturnType().descriptorString());
    String desc = descBuilder.toString();
    if (ownerClass.isInterface()) {
      int count = 1; // receiver
      for (Class<?> ptype : ptypes) {
        count += (ptype == long.class || ptype == double.class) ? 2 : 1;
      }
      mw.invokeinterface(owner, m.getName(), desc, count);
    } else {
      mw.invokevirtual(owner, m.getName(), desc);
    }
    if (m.getReturnType() == void.class) {
      mw.aconst_null();
    } else {
      boxIfPrimitive(m.getReturnType(), mw);
    }
    mw.astore(slotRecv);
    mw.gotoOp(endLabel);

    // 6. Dynamic fallback path (single-evaluation preserved, pack slots into Object[])
    mw.bindLabel(dynamicLabel);
    int siteIdx = context.registerDynamicSite(m.getName(), MemberOperation.METHOD_CALL, numArgs);
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();
    mw.aload(slotRecv);
    mw.iconst(numArgs);
    mw.anewarray("java/lang/Object");
    for (int i = 0; i < numArgs; i++) {
      mw.dup();
      mw.iconst(i);
      mw.aload(slotArgs[i]);
      mw.aastore();
    }
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicInvokeMethod",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    mw.astore(slotRecv);
    mw.gotoOp(endLabel);

    // 7. Null path
    mw.bindLabel(nullLabel);
    mw.aconst_null();
    mw.astore(slotRecv);

    // 8. End path
    mw.bindLabel(endLabel);
    mw.aload(slotRecv);
  }

  private static void unboxIfPrimitive(Class<?> clazz, ClassFileWriter.MethodWriter mw) {
    if (!clazz.isPrimitive()) {
      mw.checkcast(clazz.getName().replace('.', '/'));
      return;
    }
    if (clazz == boolean.class) {
      mw.checkcast("java/lang/Boolean");
      mw.invokevirtual("java/lang/Boolean", "booleanValue", "()Z");
    } else if (clazz == byte.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "byteValue", "()B");
    } else if (clazz == short.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "shortValue", "()S");
    } else if (clazz == char.class) {
      mw.checkcast("java/lang/Character");
      mw.invokevirtual("java/lang/Character", "charValue", "()C");
    } else if (clazz == int.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "intValue", "()I");
    } else if (clazz == long.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "longValue", "()J");
    } else if (clazz == float.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "floatValue", "()F");
    } else if (clazz == double.class) {
      mw.checkcast("java/lang/Number");
      mw.invokevirtual("java/lang/Number", "doubleValue", "()D");
    }
  }

  private static void compileDynamicDispatch(
      IrDynamicDispatch dyn,
      ClassFileWriter.MethodWriter mw,
      CompilerContext context,
      int scratchOffset) {
    if (dyn.receiver().isEmpty()) {
      // Root context parameter lookup
      mw.aload(1); // context
      mw.ldc(dyn.targetName());
      mw.invokeinterface(
          "io/github/minh124199/viettemplate/api/RenderContext",
          "get",
          "(Ljava/lang/String;)Ljava/lang/Object;",
          2);
      return;
    }

    int siteIdx =
        context.registerDynamicSite(
            dyn.targetName(), MemberOperation.METHOD_CALL, dyn.arguments().size());
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();

    compileExpression(dyn.receiver().get(), mw, context, scratchOffset);

    int numArgs = dyn.arguments().size();
    mw.iconst(numArgs);
    mw.anewarray("java/lang/Object");

    for (int i = 0; i < numArgs; i++) {
      mw.dup();
      mw.iconst(i);
      compileExpression(dyn.arguments().get(i), mw, context, scratchOffset + 1 + i);
      mw.aastore();
    }

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicInvokeMethod",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
  }

  private static void compileIndexGet(
      IrIndexGet idx, ClassFileWriter.MethodWriter mw, CompilerContext context, int scratchOffset) {
    int siteIdx = context.registerDynamicSite("getIndex", MemberOperation.INDEX_GET, 1);
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();

    compileExpression(idx.receiver(), mw, context, scratchOffset);
    compileExpression(idx.index(), mw, context, scratchOffset + 1);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicGetIndex",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  }

  private static void compileBinaryOp(
      IrBinaryOp bin, ClassFileWriter.MethodWriter mw, CompilerContext context, int scratchOffset) {
    if (bin.type()
        instanceof io.github.minh124199.viettemplate.language.vtl.semantics.type.VType.ArrayType) {
      compileExpression(bin.left(), mw, context, scratchOffset);
      compileExpression(bin.right(), mw, context, scratchOffset + 1);
      mw.iconst(10000); // maxRangeSize
      mw.ldc(context.template.id().value());
      SourceSpan span = bin.span();
      mw.iconst(span != null ? span.startLine() : 1);
      mw.iconst(span != null ? span.startColumn() : 1);
      mw.iconst(span != null ? span.endLine() : 1);
      mw.iconst(span != null ? span.endColumn() : 1);

      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "range",
          "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;IIII)Ljava/util/List;");
      return;
    }

    if (bin.op() == BinaryOpKind.AND) {
      int scratchSlot = context.scratchSlotBase + scratchOffset;
      ClassFileWriter.Label falseLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();

      compileExpression(bin.left(), mw, context, scratchOffset);
      mw.iconst(1); // emptyCheck = true
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.ifeq(falseLabel);

      compileExpression(bin.right(), mw, context, scratchOffset);
      mw.iconst(1); // emptyCheck = true
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.ifeq(falseLabel);

      mw.getstatic("java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(falseLabel);
      mw.getstatic("java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
      mw.astore(scratchSlot);

      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
      return;
    }

    if (bin.op() == BinaryOpKind.OR) {
      int scratchSlot = context.scratchSlotBase + scratchOffset;
      ClassFileWriter.Label trueLabel = mw.newLabel();
      ClassFileWriter.Label endLabel = mw.newLabel();

      compileExpression(bin.left(), mw, context, scratchOffset);
      mw.iconst(1); // emptyCheck = true
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.ifne(trueLabel);

      compileExpression(bin.right(), mw, context, scratchOffset);
      mw.iconst(1); // emptyCheck = true
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.ifne(trueLabel);

      mw.getstatic("java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
      mw.astore(scratchSlot);
      mw.gotoOp(endLabel);

      mw.bindLabel(trueLabel);
      mw.getstatic("java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
      mw.astore(scratchSlot);

      mw.bindLabel(endLabel);
      mw.aload(scratchSlot);
      return;
    }

    compileExpression(bin.left(), mw, context, scratchOffset);
    compileExpression(bin.right(), mw, context, scratchOffset + 1);
    mw.iconst(bin.op().ordinal());
    mw.ldc(context.template.id().value());
    SourceSpan span = bin.span();
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);
    mw.getstatic(
        context.internalName,
        "SECURITY_POLICY",
        "Lio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;");

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "binaryOp",
        "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;IIIILio/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy;)Ljava/lang/Object;");
  }

  private static void compileUnaryOp(
      IrUnaryOp un, ClassFileWriter.MethodWriter mw, CompilerContext context, int scratchOffset) {
    compileExpression(un.operand(), mw, context, scratchOffset);
    mw.iconst(un.op().ordinal());
    mw.ldc(context.template.id().value());
    SourceSpan span = un.span();
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "unaryOp",
        "(Ljava/lang/Object;ILjava/lang/String;IIII)Ljava/lang/Object;");
  }

  private static void compileIsNull(
      IrIsNull isNull,
      ClassFileWriter.MethodWriter mw,
      CompilerContext context,
      int scratchOffset) {
    int scratchSlot = context.scratchSlotBase + scratchOffset;
    ClassFileWriter.Label isNullLabel = mw.newLabel();
    ClassFileWriter.Label endLabel = mw.newLabel();

    compileExpression(isNull.expression(), mw, context, scratchOffset);
    mw.ifnull(isNullLabel);
    mw.getstatic("java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
    mw.astore(scratchSlot);
    mw.gotoOp(endLabel);

    mw.bindLabel(isNullLabel);
    mw.getstatic("java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
    mw.astore(scratchSlot);

    mw.bindLabel(endLabel);
    mw.aload(scratchSlot);
  }

  private static String extractRootName(IrExpression expr) {
    if (expr instanceof IrDynamicDispatch dyn && dyn.receiver().isEmpty()) {
      return dyn.targetName();
    }
    if (expr instanceof IrLoadLocal load) {
      return load.name();
    }
    if (expr instanceof IrLoadParam param) {
      return param.name();
    }
    if (expr instanceof IrGetProperty prop) {
      return extractRootName(prop.receiver()) + "." + prop.propertyName();
    }
    return "ref";
  }

  private record DynamicSiteSpec(
      int siteId, String memberName, MemberOperation operation, int arity) {}

  private static final class CompilerContext {
    final IrTemplate template;
    final String templateId;
    final BackendOptions options;
    final String internalName;
    final String fqcn;
    final String fingerprint;
    final List<DynamicSiteSpec> dynamicSites = new ArrayList<>();
    final List<byte[]> utf8Chunks = new ArrayList<>();
    final List<TemplateSidecarIndex.SourceMapping> sourceMappings = new ArrayList<>();
    final Deque<ClassFileWriter.Label> loopStack = new ArrayDeque<>();
    final Deque<Integer> foreachMetaSlotStack = new ArrayDeque<>();
    final IrSlotLayout.SlotLayout layout;
    final int baseTempSlot;
    final int scratchSlot;
    final int scratchSlotBase;
    final int totalLocals;

    CompilerContext(
        IrTemplate template,
        BackendOptions options,
        String internalName,
        String fqcn,
        String fingerprint,
        int baseTempSlot,
        int scratchSlot) {
      this(
          template,
          options,
          internalName,
          fqcn,
          fingerprint,
          baseTempSlot,
          scratchSlot,
          scratchSlot + 1);
    }

    CompilerContext(
        IrTemplate template,
        BackendOptions options,
        String internalName,
        String fqcn,
        String fingerprint,
        int baseTempSlot,
        int scratchSlot,
        int totalLocals) {
      this.template = template;
      this.templateId = template.id().value();
      this.options = options;
      this.internalName = internalName;
      this.fqcn = fqcn;
      this.fingerprint = fingerprint;
      this.layout = IrSlotLayout.layout(template);
      if (baseTempSlot < SLOT_OFFSET) {
        throw new IllegalStateException(
            "baseTempSlot " + baseTempSlot + " must be >= " + SLOT_OFFSET);
      }
      if (scratchSlot < baseTempSlot) {
        throw new IllegalStateException(
            "scratchSlot " + scratchSlot + " must be >= baseTempSlot " + baseTempSlot);
      }
      this.baseTempSlot = baseTempSlot;
      this.scratchSlot = scratchSlot;
      this.scratchSlotBase = scratchSlot;
      this.totalLocals = totalLocals;
    }

    int registerDynamicSite(String memberName, MemberOperation operation, int arity) {
      int siteId = dynamicSites.size();
      dynamicSites.add(new DynamicSiteSpec(siteId, memberName, operation, arity));
      return siteId;
    }

    int registerUtf8Chunk(byte[] chunk) {
      int idx = utf8Chunks.size();
      utf8Chunks.add(chunk);
      return idx;
    }

    void recordSourceMapping(int bytecodeOffset, int line, SourceSpan span) {
      sourceMappings.add(new TemplateSidecarIndex.SourceMapping(bytecodeOffset, line, span));
    }

    void pushLoop(ClassFileWriter.Label exitLabel) {
      loopStack.push(exitLabel);
    }

    void popLoop() {
      loopStack.pop();
    }

    ClassFileWriter.Label currentLoopExit() {
      return loopStack.peek();
    }

    void pushForeachMetaSlot(int slot) {
      foreachMetaSlotStack.push(slot);
    }

    void popForeachMetaSlot() {
      foreachMetaSlotStack.pop();
    }

    Integer currentForeachMetaSlot() {
      return foreachMetaSlotStack.peek();
    }
  }

  private static boolean containsCallTemplate(IrTemplate template) {
    if (hasCallTemplate(template.root())) {
      return true;
    }
    for (IrFunction fn : template.functions()) {
      if (hasCallTemplate(fn.body())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasCallTemplate(IrBlock block) {
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrCallTemplate) {
        return true;
      }
      if (stmt instanceof IrIf ifStmt) {
        if (hasCallTemplate(ifStmt.thenBlock())) {
          return true;
        }
        if (ifStmt.elseBlock().isPresent() && hasCallTemplate(ifStmt.elseBlock().get())) {
          return true;
        }
      } else if (stmt instanceof IrLoop loop) {
        if (hasCallTemplate(loop.body())) {
          return true;
        }
        if (loop.elseBody().isPresent() && hasCallTemplate(loop.elseBody().get())) {
          return true;
        }
      }
    }
    return false;
  }

  private static MemberAccessPolicy toMemberAccessPolicy(LinkerAccessPolicy linkerPolicy) {
    if (linkerPolicy == null) {
      return MemberAccessPolicy.standard();
    }
    return new MemberAccessPolicy() {
      @Override
      public boolean isClassPermitted(Class<?> clazz) {
        return linkerPolicy.isClassPermitted(clazz);
      }

      @Override
      public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
        return linkerPolicy.isClassPermitted(receiverClass);
      }

      @Override
      public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
        return method != null
            && linkerPolicy.isClassPermitted(method.getDeclaringClass())
            && linkerPolicy.isMethodPermitted(receiverClass, method);
      }

      @Override
      public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
        return linkerPolicy.isPropertyPermitted(receiverClass, propertyName);
      }

      @Override
      public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
        return linkerPolicy.isClassPermitted(receiverClass);
      }

      @Override
      public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
        return field != null
            && linkerPolicy.isClassPermitted(field.getDeclaringClass())
            && linkerPolicy.isFieldPermitted(receiverClass, field);
      }

      @Override
      public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
        return linkerPolicy.isPropertyMutationPermitted(receiverClass, propertyName);
      }

      @Override
      public boolean isIndexMutationPermitted(Class<?> receiverClass) {
        return linkerPolicy.isIndexMutationPermitted(receiverClass);
      }

      @Override
      public SecurityPolicyFingerprint fingerprint() {
        String id = linkerPolicy.policyId();
        return SecurityPolicyFingerprint.of(
            id != null && !id.isBlank() ? id : "custom-linker-policy");
      }
    };
  }
}
