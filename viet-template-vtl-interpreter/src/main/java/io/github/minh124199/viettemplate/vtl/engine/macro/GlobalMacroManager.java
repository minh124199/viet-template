package io.github.minh124199.viettemplate.vtl.engine.macro;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.engine.dependency.StaticDependencyExtractor;
import io.github.minh124199.viettemplate.vtl.interpreter.SpaceGobbler;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages global Velocimacro library templates (`velocimacro.library`), parsing them once into
 * reusable IR functions, caching them by source fingerprint, and merging them with local template
 * macros according to configured precedence.
 */
public final class GlobalMacroManager {

  private record ParsedLibrary(
      String fingerprint,
      List<IrFunction> functions,
      Set<TemplateDependency> dependencies,
      IrConstantPool constants) {}

  private final TemplateRepository repository;
  private final List<TemplateId> libraryIds;
  private final GlobalMacroPrecedence precedence;
  private final VtlSemanticOptions semanticOptions;
  private final VtlInterpreterOptions interpreterOptions;
  private final IrOptimizationOptions optimizationOptions;

  private final Map<TemplateId, ParsedLibrary> libraryCache = new ConcurrentHashMap<>();

  public GlobalMacroManager(
      TemplateRepository repository,
      List<TemplateId> libraryIds,
      GlobalMacroPrecedence precedence,
      VtlSemanticOptions semanticOptions,
      VtlInterpreterOptions interpreterOptions,
      IrOptimizationOptions optimizationOptions) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.libraryIds =
        List.copyOf(Objects.requireNonNull(libraryIds, "libraryIds must not be null"));
    this.precedence = Objects.requireNonNull(precedence, "precedence must not be null");
    this.semanticOptions =
        Objects.requireNonNull(semanticOptions, "semanticOptions must not be null");
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");
    this.optimizationOptions =
        Objects.requireNonNull(optimizationOptions, "optimizationOptions must not be null");
  }

  public List<TemplateId> libraryIds() {
    return libraryIds;
  }

  public GlobalMacroPrecedence precedence() {
    return precedence;
  }

  public void invalidate(TemplateId id) {
    libraryCache.remove(id);
  }

  public void invalidateAll() {
    libraryCache.clear();
  }

  /** Returns all dependencies extracted from the configured global macro libraries. */
  public List<TemplateDependency> allLibraryDependencies() {
    List<TemplateDependency> all = new ArrayList<>();
    for (TemplateId id : libraryIds) {
      ParsedLibrary lib = ensureLibrary(id);
      all.addAll(lib.dependencies());
    }
    return all;
  }

  /**
   * Returns a map of all global macros provided by the configured libraries, resolved according to
   * {@link GlobalMacroPrecedence}.
   */
  public Map<String, IrFunction> globalFunctions() {
    if (libraryIds.isEmpty()) {
      return Map.of();
    }
    Map<String, IrFunction> result = new LinkedHashMap<>();
    for (TemplateId libId : libraryIds) {
      ParsedLibrary lib = ensureLibrary(libId);
      for (IrFunction fn : lib.functions()) {
        if (precedence == GlobalMacroPrecedence.FIRST_WINS) {
          result.putIfAbsent(fn.name(), fn);
        } else {
          result.put(fn.name(), fn);
        }
      }
    }
    return result;
  }

  /**
   * Merges global macros into the given template, giving local template macros precedence over
   * global macros of the same name.
   */
  public IrTemplate mergeWithTemplate(IrTemplate template) {
    Objects.requireNonNull(template, "template must not be null");
    if (libraryIds.isEmpty()) {
      return template;
    }

    IrConstantPool combinedPool = new IrConstantPool(template.constants().allTextConstants());
    Map<TemplateId, Map<Integer, Integer>> libraryRemaps = new LinkedHashMap<>();
    for (TemplateId libId : libraryIds) {
      ParsedLibrary lib = ensureLibrary(libId);
      Map<Integer, Integer> remap = new HashMap<>();
      for (IrTextConstant tc : lib.constants().allTextConstants()) {
        int newId = combinedPool.registerText(tc.text(), tc.span());
        remap.put(tc.id(), newId);
      }
      libraryRemaps.put(libId, remap);
    }

    Map<String, IrFunction> merged = new LinkedHashMap<>();
    for (TemplateId libId : libraryIds) {
      ParsedLibrary lib = ensureLibrary(libId);
      Map<Integer, Integer> remap = libraryRemaps.get(libId);
      for (IrFunction fn : lib.functions()) {
        IrBlock remappedBody = remapBlock(fn.body(), remap);
        IrFunction remappedFn =
            new IrFunction(fn.name(), fn.parameters(), fn.locals(), remappedBody, fn.span());
        if (precedence == GlobalMacroPrecedence.FIRST_WINS) {
          merged.putIfAbsent(remappedFn.name(), remappedFn);
        } else {
          merged.put(remappedFn.name(), remappedFn);
        }
      }
    }

    for (IrFunction localFn : template.functions()) {
      // Local macro definitions take precedence over global macros
      merged.put(localFn.name(), localFn);
    }

    return new IrTemplate(
        template.id(),
        template.parameters(),
        template.root(),
        combinedPool,
        template.capabilities(),
        List.copyOf(merged.values()),
        template.span());
  }

  private static IrBlock remapBlock(IrBlock block, Map<Integer, Integer> remap) {
    List<IrStatement> newStmts = new ArrayList<>(block.statements().size());
    for (IrStatement stmt : block.statements()) {
      newStmts.add(remapStatement(stmt, remap));
    }
    return new IrBlock(newStmts, block.span());
  }

  private static IrStatement remapStatement(IrStatement stmt, Map<Integer, Integer> remap) {
    if (stmt instanceof IrWriteConst wc) {
      Integer newId = remap.get(wc.constantId());
      if (newId != null) {
        return new IrWriteConst(newId, wc.span());
      }
      return wc;
    } else if (stmt instanceof IrIf ifStmt) {
      IrBlock thenBlock = remapBlock(ifStmt.thenBlock(), remap);
      Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(b -> remapBlock(b, remap));
      return new IrIf(ifStmt.condition(), thenBlock, elseBlock, ifStmt.span());
    } else if (stmt instanceof IrLoop loop) {
      IrBlock body = remapBlock(loop.body(), remap);
      Optional<IrBlock> elseBody = loop.elseBody().map(b -> remapBlock(b, remap));
      return new IrLoop(
          loop.plan(),
          loop.iterable(),
          loop.elementLocal(),
          loop.loopStateLocal(),
          body,
          elseBody,
          loop.span());
    }
    return stmt;
  }

  /**
   * Computes a unified cryptographic fingerprint for all configured macro libraries and precedence.
   */
  public String computeFingerprint() {
    if (libraryIds.isEmpty()) {
      return "";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (TemplateId id : libraryIds) {
        ParsedLibrary lib = ensureLibrary(id);
        digest.update(id.value().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(lib.fingerprint().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ';');
      }
      digest.update(precedence.name().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private ParsedLibrary ensureLibrary(TemplateId libId) {
    Optional<TemplateSource> sourceOpt = repository.find(libId);
    if (sourceOpt.isEmpty()) {
      throw new TemplateResourceException(
          "Configured global macro library not found: " + libId.value(),
          libId,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
    }

    TemplateSource source = sourceOpt.get();
    ParsedLibrary cached = libraryCache.get(libId);
    if (cached != null && cached.fingerprint().equals(source.fingerprint())) {
      return cached;
    }

    SourceText sourceText = SourceText.of(libId, source.content());
    VtlParseResult parseResult = VtlParser.parse(sourceText);
    BitSet gobbled =
        SpaceGobbler.computeGobbledIndices(sourceText, interpreterOptions.spaceGobbling());
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate irTemplate =
        AstToIrLowerer.lower(
            parseResult.template(), sourceText, analysis, semanticOptions, gobbled);
    IrTemplate optimizedIr = IrOptimizer.optimize(irTemplate, optimizationOptions);

    Set<TemplateDependency> deps =
        StaticDependencyExtractor.extract(optimizedIr, Collections.emptyList(), Optional.empty());
    ParsedLibrary parsed =
        new ParsedLibrary(
            source.fingerprint(), optimizedIr.functions(), deps, optimizedIr.constants());
    libraryCache.put(libId, parsed);
    return parsed;
  }
}
