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

    if (context instanceof MutableRenderContext mrc) {
      screenContext = mrc;
    } else {
      screenContext = MutableRenderContext.of(initialSnapshot);
    }

    // Stage 1: Render screen template to bounded in-memory buffer
    StringTemplateOutput screenBuffer = new StringTemplateOutput();
    Template screenTemplate = engine.get(screenId);

    // Enforce output size limit during screen capture
    long maxChars = interpreterOptions.limits().maxOutputCharacters();
    BoundedScreenOutput boundedOutput = new BoundedScreenOutput(screenBuffer, maxChars, screenId);
    screenTemplate.render(screenContext, boundedOutput);

    String renderedScreen = screenBuffer.toString();

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
    MutableRenderContext layoutContext;
    if (configuration.contextScope() == LayoutContextScope.SHARED_COMPATIBILITY_SCOPE) {
      layoutContext = screenContext;
    } else {
      // ISOLATED_SCREEN_SCOPE: discard screen mutations, restore initial variables
      layoutContext = MutableRenderContext.of(initialSnapshot);
    }

    // Expose screen content under configured key
    layoutContext.put(configuration.screenContentKey(), renderedScreen);

    // Render layout template
    Set<TemplateId> nextActive = new LinkedHashSet<>(activeLayouts);
    nextActive.add(layoutId);

    Template layoutTemplate = engine.get(layoutId);
    layoutTemplate.render(layoutContext, output);
  }

  private static final class BoundedScreenOutput implements TemplateOutput {
    private final TemplateOutput delegate;
    private final long maxChars;
    private final TemplateId templateId;
    private long written = 0;

    BoundedScreenOutput(TemplateOutput delegate, long maxChars, TemplateId templateId) {
      this.delegate = delegate;
      this.maxChars = maxChars;
      this.templateId = templateId;
    }

    private void checkBudget(int added) {
      written += added;
      if (written > maxChars) {
        throw new TemplateLimitException(
            "Screen output exceeded maximum character limit (" + maxChars + ")",
            templateId,
            SourceSpan.UNKNOWN,
            DiagnosticCode.of("LIMIT", "EXCEEDED"));
      }
    }

    @Override
    public void write(CharSequence value) throws IOException {
      if (value != null) {
        checkBudget(value.length());
        delegate.write(value);
      }
    }

    @Override
    public void write(char value) throws IOException {
      checkBudget(1);
      delegate.write(value);
    }

    @Override
    public void writeUtf8(byte[] bytes) throws IOException {
      if (bytes != null) {
        checkBudget(bytes.length);
        delegate.writeUtf8(bytes);
      }
    }

    @Override
    public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
      if (bytes != null) {
        checkBudget(length);
        delegate.writeUtf8(bytes, offset, length);
      }
    }

    @Override
    public void writeInt(int value) throws IOException {
      delegate.writeInt(value);
    }

    @Override
    public void writeLong(long value) throws IOException {
      delegate.writeLong(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
      delegate.writeDouble(value);
    }

    @Override
    public void writeFloat(float value) throws IOException {
      delegate.writeFloat(value);
    }

    @Override
    public void writeShort(short value) throws IOException {
      delegate.writeShort(value);
    }

    @Override
    public void writeByte(byte value) throws IOException {
      delegate.writeByte(value);
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
      delegate.writeBoolean(value);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }
  }
}
