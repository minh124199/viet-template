package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
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
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
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
import io.github.minh124199.viettemplate.runtime.linker.MemberOperation;
import io.github.minh124199.viettemplate.vtl.compiler.BackendCapabilities;
import io.github.minh124199.viettemplate.vtl.compiler.BackendId;
import io.github.minh124199.viettemplate.vtl.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.compiler.CompilationStatus;
import io.github.minh124199.viettemplate.vtl.compiler.CompiledArtifact;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateBackend;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateSidecarIndex;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Milestone M11 Ahead-Of-Time (AOT) bytecode template compiler.
 *
 * <p>Translates verified, optimized Intermediate Representation ({@link IrTemplate}) directly into
 * Java 17-compatible JVM bytecode without source code generation or intermediate disk artifacts.
 */
public final class BytecodeTemplateCompiler implements TemplateBackend {

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
    IrTemplate optimized = IrOptimizer.optimize(template, options.optimizationOptions());
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
                + options.securityPolicy().hashCode()
                + ":"
                + options.optimizationOptions().level().name());
    String className = BytecodeNaming.className(optimized.id(), fingerprint);
    String fqcn = options.packagePrefix() + "." + className;
    String internalName = fqcn.replace('.', '/');

    // 5. Code generation context
    CompilerContext context =
        new CompilerContext(optimized, options, internalName, fqcn, fingerprint);

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
    int maxLocals = calculateMaxLocals(optimized);
    render.setMaxLocals(maxLocals + 16);
    render.setMaxStack(16);

    for (int i = 3; i <= maxLocals + SLOT_OFFSET + 4; i++) {
      render.aconst_null();
      render.astore(i);
    }

    // Seed parameters from context into slots (irSlot + SLOT_OFFSET)
    for (IrParameter param : optimized.parameters()) {
      render.aload(1); // context
      render.ldc(param.name());
      render.invokeinterface(
          "io/github/minh124199/viettemplate/api/RenderContext",
          "get",
          "(Ljava/lang/String;)Ljava/lang/Object;",
          2);
      render.astore(param.slot() + SLOT_OFFSET);
    }

    // Compile root block statements
    compileBlock(optimized.root(), render, context);
    render.returnOp();

    // Compile helper functions (from MethodSizePlanningPass or macros)
    for (IrFunction function : optimized.functions()) {
      String methodName = BytecodeNaming.chunkMethodName(function.name());
      ClassFileWriter.MethodWriter funcMw =
          cf.addMethod(
              ClassFileWriter.ACC_PUBLIC,
              methodName,
              "(Lio/github/minh124199/viettemplate/api/RenderContext;Lio/github/minh124199/viettemplate/api/TemplateOutput;[Ljava/lang/Object;)V");
      funcMw.setMaxLocals(maxLocals + 16);
      funcMw.setMaxStack(16);

      for (int i = 4; i <= maxLocals + SLOT_OFFSET + 4; i++) {
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

      compileBlock(function.body(), funcMw, context);
      funcMw.returnOp();
    }

    // Static initializer: <clinit>()
    ClassFileWriter.MethodWriter clinit =
        cf.addMethod(ClassFileWriter.ACC_STATIC, "<clinit>", "()V");
    clinit.ldc(optimized.id().value());
    clinit.putstatic(internalName, "TEMPLATE_ID", "Ljava/lang/String;");
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
    } catch (Exception e) {
      throw new IllegalStateException("Failed to instantiate compiled template: " + fqcn, e);
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
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize static fields on compiled class", e);
    }
  }

  private static int calculateMaxLocals(IrTemplate template) {
    int max = 0;
    for (IrParameter p : template.parameters()) {
      max = Math.max(max, p.slot());
    }
    max = Math.max(max, scanMaxSlot(template.root()));
    for (IrFunction f : template.functions()) {
      max = Math.max(max, scanMaxSlot(f.body()));
      for (IrParameter p : f.parameters()) {
        max = Math.max(max, p.slot());
      }
      for (IrLocal l : f.locals()) {
        max = Math.max(max, l.slot());
      }
    }
    return max + SLOT_OFFSET + 4;
  }

  private static int scanMaxSlot(IrBlock block) {
    int max = 0;
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal sl) {
        max = Math.max(max, sl.local().slot());
      } else if (stmt instanceof IrLoop loop) {
        max = Math.max(max, loop.elementLocal().slot());
        if (loop.loopStateLocal().isPresent()) {
          max = Math.max(max, loop.loopStateLocal().get().slot());
        }
        max = Math.max(max, scanMaxSlot(loop.body()));
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
      IrBlock block, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    for (IrStatement stmt : block.statements()) {
      compileStatement(stmt, mw, context);
    }
  }

  private static void compileStatement(
      IrStatement stmt, ClassFileWriter.MethodWriter mw, CompilerContext context) {
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
      compileExpression(sl.value(), mw, context);
      mw.astore(sl.local().slot() + SLOT_OFFSET);
      mw.aload(1);
      mw.ldc(sl.local().name());
      mw.aload(sl.local().slot() + SLOT_OFFSET);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "recordContextVariable",
          "(Lio/github/minh124199/viettemplate/api/RenderContext;Ljava/lang/String;Ljava/lang/Object;)V");
    } else if (stmt instanceof IrIf ifStmt) {
      compileIf(ifStmt, mw, context);
    } else if (stmt instanceof IrLoop loop) {
      compileLoop(loop, mw, context);
    } else if (stmt instanceof IrBreak) {
      ClassFileWriter.Label exitLabel = context.currentLoopExit();
      if (exitLabel != null) {
        mw.gotoOp(exitLabel);
      }
    } else if (stmt instanceof IrStop || stmt instanceof IrReturn) {
      mw.returnOp();
    } else if (stmt instanceof IrCallMacro callM) {
      compileCallMacro(callM, mw, context);
    } else if (stmt instanceof IrSetProperty sp) {
      compileSetProperty(sp, mw, context);
    } else if (stmt instanceof IrSetIndex si) {
      compileSetIndex(si, mw, context);
    } else if (stmt instanceof IrBudgetCheck || stmt instanceof IrNoOp) {
      // Checked dynamically or no-op
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

    mw.iconst(0); // strict = false (handled gracefully)
    mw.ldc(context.template.id().value());
    SourceSpan span = wv.span();
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "writeValue",
        "(Ljava/lang/Object;Lio/github/minh124199/viettemplate/api/TemplateOutput;IILjava/lang/String;ZLjava/lang/String;IIII)V");
  }

  private static void compileIf(
      IrIf ifStmt, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    ClassFileWriter.Label elseLabel = mw.newLabel();
    ClassFileWriter.Label endLabel = mw.newLabel();

    compileExpression(ifStmt.condition(), mw, context);
    mw.iconst(1); // emptyCheck = true
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "isTruthy",
        "(Ljava/lang/Object;Z)Z");
    mw.ifeq(elseLabel);

    compileBlock(ifStmt.thenBlock(), mw, context);
    if (!blockAlwaysTerminates(ifStmt.thenBlock())) {
      mw.gotoOp(endLabel);
    }

    mw.bindLabel(elseLabel);
    if (ifStmt.elseBlock().isPresent()) {
      compileBlock(ifStmt.elseBlock().get(), mw, context);
    }
    mw.bindLabel(endLabel);
  }

  private static boolean blockAlwaysTerminates(IrBlock block) {
    if (block.isEmpty()) {
      return false;
    }
    IrStatement last = block.statements().get(block.statements().size() - 1);
    return last instanceof IrBreak || last instanceof IrStop || last instanceof IrReturn;
  }

  private static void compileLoop(
      IrLoop loop, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    ClassFileWriter.Label loopHeader = mw.newLabel();
    ClassFileWriter.Label loopExit = mw.newLabel();

    int iterSlot = context.nextTempSlot();
    int itemSlot = loop.elementLocal().slot() + SLOT_OFFSET;

    compileExpression(loop.iterable(), mw, context);
    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "toIterator",
        "(Ljava/lang/Object;)Ljava/util/Iterator;");
    mw.astore(iterSlot);

    int counterSlot = -1;
    Integer parentMetaSlot = context.currentForeachMetaSlot();
    int metaSlot = -1;
    if (loop.loopStateLocal().isPresent()) {
      metaSlot = loop.loopStateLocal().get().slot() + SLOT_OFFSET;
      counterSlot = context.nextTempSlot();
      mw.iconst(0);
      mw.istore(counterSlot);
      context.pushForeachMetaSlot(metaSlot);
    }

    mw.bindLabel(loopHeader);
    mw.aload(iterSlot);
    mw.invokeinterface("java/util/Iterator", "hasNext", "()Z", 1);
    mw.ifeq(loopExit);

    mw.aload(iterSlot);
    mw.invokeinterface("java/util/Iterator", "next", "()Ljava/lang/Object;", 1);
    mw.astore(itemSlot);

    if (counterSlot != -1) {
      mw.iload(counterSlot);
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
          "(IZLjava/lang/Object;)Lio/github/minh124199/viettemplate/language/vtl/semantics/scope/ForeachMetadata;");
      mw.astore(metaSlot);

      mw.iload(counterSlot);
      mw.iconst(1);
      // increment counter
      mw.invokestatic("java/lang/Integer", "sum", "(II)I");
      mw.istore(counterSlot);
    }

    context.pushLoop(loopExit);
    compileBlock(loop.body(), mw, context);
    context.popLoop();
    if (loop.loopStateLocal().isPresent()) {
      context.popForeachMetaSlot();
    }

    mw.gotoOp(loopHeader);
    mw.bindLabel(loopExit);
  }

  private static void compileCallMacro(
      IrCallMacro callM, ClassFileWriter.MethodWriter mw, CompilerContext context) {
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

    compileExpression(sp.target(), mw, context);
    compileExpression(sp.value(), mw, context);

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

    compileExpression(si.target(), mw, context);
    compileExpression(si.index(), mw, context);
    compileExpression(si.value(), mw, context);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicSetIndex",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
  }

  private static void compileExpression(
      IrExpression expr, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    if (expr instanceof IrConst c) {
      compileConst(c, mw);
    } else if (expr instanceof IrLoadLocal load) {
      mw.aload(load.slot() + SLOT_OFFSET);
    } else if (expr instanceof IrLoadParam param) {
      mw.aload(param.slot() + SLOT_OFFSET);
    } else if (expr instanceof IrGetProperty prop) {
      compileGetProperty(prop, mw, context);
    } else if (expr instanceof IrDynamicDispatch dyn) {
      compileDynamicDispatch(dyn, mw, context);
    } else if (expr instanceof IrIndexGet idx) {
      compileIndexGet(idx, mw, context);
    } else if (expr instanceof IrBinaryOp bin) {
      compileBinaryOp(bin, mw, context);
    } else if (expr instanceof IrUnaryOp un) {
      compileUnaryOp(un, mw, context);
    } else if (expr instanceof IrTruthiness tr) {
      compileExpression(tr.expression(), mw, context);
      mw.iconst(tr.emptyCheck() ? 1 : 0);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "isTruthy",
          "(Ljava/lang/Object;Z)Z");
      mw.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
    } else if (expr instanceof IrIsNull isNull) {
      compileIsNull(isNull, mw, context);
    } else if (expr instanceof IrAlternateValue alt) {
      compileExpression(alt.primary(), mw, context);
      compileExpression(alt.fallback(), mw, context);
      mw.invokestatic(
          "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
          "alternateValue",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    } else if (expr instanceof IrConvert conv) {
      compileExpression(conv.expression(), mw, context);
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
      IrGetProperty prop, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    AccessPlan plan = prop.accessPlan();

    if (plan instanceof AccessPlan.DirectRecord rec) {
      compileExpression(prop.receiver(), mw, context);
      String owner = rec.owner().getName().replace('.', '/');
      mw.checkcast(owner);
      String desc = "()" + rec.returnType().descriptorString();
      mw.invokevirtual(owner, rec.componentName(), desc);
      boxIfPrimitive(rec.returnType(), mw);
    } else if (plan instanceof AccessPlan.DirectGetter getter) {
      compileExpression(prop.receiver(), mw, context);
      String owner = getter.owner().getName().replace('.', '/');
      mw.checkcast(owner);
      String desc = "()" + getter.returnType().descriptorString();
      if (getter.owner().isInterface()) {
        mw.invokeinterface(owner, getter.methodName(), desc, 1);
      } else {
        mw.invokevirtual(owner, getter.methodName(), desc);
      }
      boxIfPrimitive(getter.returnType(), mw);
    } else if (plan instanceof AccessPlan.DirectField field) {
      compileExpression(prop.receiver(), mw, context);
      String owner = field.owner().getName().replace('.', '/');
      mw.checkcast(owner);
      String desc = field.fieldType().descriptorString();
      mw.getfield(owner, field.fieldName(), desc);
      boxIfPrimitive(field.fieldType(), mw);
    } else if (plan instanceof AccessPlan.MapLookup mapLookup) {
      compileExpression(prop.receiver(), mw, context);
      mw.checkcast("java/util/Map");
      mw.ldc(mapLookup.keyConstant());
      mw.invokeinterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", 2);
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
      compileExpression(prop.receiver(), mw, context);
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

  private static void compileDynamicDispatch(
      IrDynamicDispatch dyn, ClassFileWriter.MethodWriter mw, CompilerContext context) {
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

    compileExpression(dyn.receiver().get(), mw, context);

    int numArgs = dyn.arguments().size();
    mw.iconst(numArgs);
    mw.anewarray("java/lang/Object");

    for (int i = 0; i < numArgs; i++) {
      mw.dup();
      mw.iconst(i);
      compileExpression(dyn.arguments().get(i), mw, context);
      mw.aastore();
    }

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicInvokeMethod",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
  }

  private static void compileIndexGet(
      IrIndexGet idx, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    int siteIdx = context.registerDynamicSite("getIndex", MemberOperation.INDEX_GET, 1);
    mw.getstatic(
        context.internalName,
        "SITES",
        "[Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;");
    mw.iconst(siteIdx);
    mw.aaload();

    compileExpression(idx.receiver(), mw, context);
    compileExpression(idx.index(), mw, context);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "dynamicGetIndex",
        "(Lio/github/minh124199/viettemplate/runtime/linker/DynamicCallSite;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  }

  private static void compileBinaryOp(
      IrBinaryOp bin, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    if (bin.type()
        instanceof io.github.minh124199.viettemplate.language.vtl.semantics.type.VType.ArrayType) {
      compileExpression(bin.left(), mw, context);
      compileExpression(bin.right(), mw, context);
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

    compileExpression(bin.left(), mw, context);
    compileExpression(bin.right(), mw, context);
    mw.iconst(bin.op().ordinal());
    mw.ldc(context.template.id().value());
    SourceSpan span = bin.span();
    mw.iconst(span != null ? span.startLine() : 1);
    mw.iconst(span != null ? span.startColumn() : 1);
    mw.iconst(span != null ? span.endLine() : 1);
    mw.iconst(span != null ? span.endColumn() : 1);

    mw.invokestatic(
        "io/github/minh124199/viettemplate/vtl/compiler/bytecode/BytecodeRuntimeBridge",
        "binaryOp",
        "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;IIII)Ljava/lang/Object;");
  }

  private static void compileUnaryOp(
      IrUnaryOp un, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    compileExpression(un.operand(), mw, context);
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
      IrIsNull isNull, ClassFileWriter.MethodWriter mw, CompilerContext context) {
    ClassFileWriter.Label isNullLabel = mw.newLabel();
    ClassFileWriter.Label endLabel = mw.newLabel();

    compileExpression(isNull.expression(), mw, context);
    mw.ifnull(isNullLabel);
    mw.iconst(0);
    mw.gotoOp(endLabel);

    mw.bindLabel(isNullLabel);
    mw.iconst(1);

    mw.bindLabel(endLabel);
    mw.invokestatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
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
    final BackendOptions options;
    final String internalName;
    final String fqcn;
    final String fingerprint;
    final List<DynamicSiteSpec> dynamicSites = new ArrayList<>();
    final List<byte[]> utf8Chunks = new ArrayList<>();
    final List<TemplateSidecarIndex.SourceMapping> sourceMappings = new ArrayList<>();
    final Deque<ClassFileWriter.Label> loopStack = new ArrayDeque<>();
    final Deque<Integer> foreachMetaSlotStack = new ArrayDeque<>();
    int tempSlotOffset;

    CompilerContext(
        IrTemplate template,
        BackendOptions options,
        String internalName,
        String fqcn,
        String fingerprint) {
      this.template = template;
      this.options = options;
      this.internalName = internalName;
      this.fqcn = fqcn;
      this.fingerprint = fingerprint;
      this.tempSlotOffset = calculateMaxLocals(template) + 4;
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

    int nextTempSlot() {
      return tempSlotOffset++;
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
}
