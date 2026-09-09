package io.github.minh124199.viettemplate.vtl.engine.layout;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutContextScope;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLayoutException;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Executes a two-stage layout rendering plan: 1. Renders the screen template to an in-memory buffer
 * enforcing character budgets. 2. Resolves the layout template (supporting runtime overrides or
 * bypass). 3. Binds the captured screen content under the configured key and renders the layout.
 */
public final class DefaultLayoutRenderPlan implements LayoutRenderPlan {

  private final TemplateEngine engine;
  private final TemplateId screenId;
  private final LayoutConfiguration configuration;
  private final VtlInterpreterOptions interpreterOptions;
  private final Set<TemplateId> activeLayouts;
  private final int currentDepth;

  public DefaultLayoutRenderPlan(
      TemplateEngine engine,
      TemplateId screenId,
      LayoutConfiguration configuration,
      VtlInterpreterOptions interpreterOptions) {
    this(engine, screenId, configuration, interpreterOptions, Collections.emptySet(), 0);
  }

  private DefaultLayoutRenderPlan(
      TemplateEngine engine,
      TemplateId screenId,
      LayoutConfiguration configuration,
      VtlInterpreterOptions interpreterOptions,
      Set<TemplateId> activeLayouts,
      int currentDepth) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
    this.screenId = Objects.requireNonNull(screenId, "screenId must not be null");
    this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");
    this.activeLayouts = Set.copyOf(activeLayouts);
    this.currentDepth = currentDepth;
  }

  @Override
  public TemplateId screenId() {
    return screenId;
  }

  @Override
  public Optional<TemplateId> layoutId() {
    return configuration.resolver().resolveLayout(screenId, RenderContext.empty());
  }

  @Override
  public LayoutConfiguration configuration() {
    return configuration;
  }

  @Override
  public void render(RenderContext context, TemplateOutput output) throws IOException {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(output, "output must not be null");

    // Prepare mutable execution context for the screen template
    MutableRenderContext screenContext;
    Map<String, Object> initialSnapshot = new HashMap<>();
    for (String key : context.keys()) {
      initialSnapshot.put(key, context.get(key));
    }

    Set<String> protectedKeys = Set.of(configuration.screenContentKey());
    if (context instanceof MutableRenderContext mrc) {
      screenContext = MutableRenderContext.of(mrc.asMap(), protectedKeys);
    } else {
      screenContext = MutableRenderContext.of(initialSnapshot, protectedKeys);
    }

    // Stage 1: Render screen template to bounded in-memory buffer
    RenderBudget budget =
        (output
                instanceof
                io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput cto)
            ? cto.budget()
            : interpreterOptions.limits().createRenderBudget();

    StringTemplateOutput screenBuffer = new StringTemplateOutput();
    Template screenTemplate = engine.get(screenId);

    // Enforce output size limit during screen capture
    long maxChars = interpreterOptions.limits().maxOutputCharacters();
    BoundedScreenOutput boundedOutput =
        new BoundedScreenOutput(screenBuffer, maxChars, screenId, budget);
    screenTemplate.render(screenContext, boundedOutput);

    String renderedScreen = screenBuffer.toString();

    // Account for captured screen characters in overall render budget
    budget.consumeCharacters(renderedScreen.length(), screenId, SourceSpan.UNKNOWN);

    // Resolve layout template after screen rendering (allowing screen #set($layout = ...) to
    // override)
    Optional<TemplateId> layoutOpt =
        configuration.resolver().resolveLayout(screenId, screenContext);

    if (layoutOpt.isEmpty()) {
      // Layout bypassed: write screen content directly to target output
      output.write(renderedScreen);
      return;
    }

    TemplateId layoutId = layoutOpt.get();

    // Recursion / Cycle detection
    if (screenId.equals(layoutId) || activeLayouts.contains(layoutId)) {
      throw new TemplateLayoutException(
          "Recursive layout rendering cycle detected: layout "
              + layoutId.value()
              + " already active",
          layoutId,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("LAYOUT", "CYCLE_DETECTED"));
    }

    if (currentDepth >= configuration.maxLayoutDepth()) {
      throw new TemplateLayoutException(
          "Exceeded maximum layout depth limit (" + configuration.maxLayoutDepth() + ")",
          layoutId,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("LAYOUT", "DEPTH_EXCEEDED"));
    }

    // Stage 2: Prepare layout context based on scope policy
    Map<String, Object> layoutVars = new HashMap<>();
    if (configuration.contextScope() == LayoutContextScope.SHARED_COMPATIBILITY_SCOPE) {
      for (String key : screenContext.keys()) {
        layoutVars.put(key, screenContext.get(key));
      }
    } else {
      layoutVars.putAll(initialSnapshot);
    }
    layoutVars.put(configuration.screenContentKey(), renderedScreen);
    MutableRenderContext layoutContext = MutableRenderContext.of(layoutVars, protectedKeys);

    // Render layout template with shared budget
    Set<TemplateId> nextActive = new LinkedHashSet<>(activeLayouts);
    nextActive.add(layoutId);

    Template layoutTemplate = engine.get(layoutId);
    TemplateOutput layoutOutput =
        (output
                instanceof
                io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput cto)
            ? cto
            : new io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput(
                output, budget, layoutId);
    layoutTemplate.render(layoutContext, layoutOutput);
  }

  private static final class BoundedScreenOutput
      extends io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput {
    private final long maxChars;
    private long written = 0;

    BoundedScreenOutput(
        TemplateOutput delegate,
        long maxChars,
        TemplateId templateId,
        io.github.minh124199.viettemplate.runtime.RenderBudget budget) {
      super(delegate, budget, templateId);
      this.maxChars = maxChars;
    }

    @Override
    protected void checkLimit(int added) {
      written += added;
      if (budget() != null) {
        budget().checkDeadline(templateId(), SourceSpan.UNKNOWN);
      }
      if (written > maxChars) {
        throw new TemplateLimitException(
            "Screen output exceeded maximum character limit (" + maxChars + ")",
            templateId(),
            SourceSpan.UNKNOWN,
            DiagnosticCode.of("LIMIT", "EXCEEDED"));
      }
    }
  }
}
