package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import java.util.ArrayList;
import java.util.List;

public final class TestTemplateEngineBuilder implements TemplateEngine.Builder {

  private TemplateRepository repository = ClasspathTemplateRepository.of("");
  private boolean rejectRuntimeCompilation = false;
  private int maxCacheEntries = 500;
  private long negativeCacheTtlMillis = 5000L;
  private boolean hotReload = false;
  private long watchDebounceMillis = 50L;
  private List<TemplateId> globalMacroLibraries = List.of();
  private GlobalMacroPrecedence globalMacroPrecedence = GlobalMacroPrecedence.LAST_WINS;
  private final List<RenderContextContributor> contextContributors = new ArrayList<>();
  private ContextCollisionPolicy contextCollisionPolicy = ContextCollisionPolicy.MODEL_WINS;
  private LayoutConfiguration layoutConfiguration = LayoutConfiguration.builder().build();
  private MemberAccessPolicy memberAccessPolicy = null;
  private boolean customized = false;

  @Override
  public TemplateEngine.Builder repository(TemplateRepository repository) {
    this.repository = repository;
    return this;
  }

  @Override
  public TemplateEngine.Builder rejectRuntimeCompilation(boolean reject) {
    this.rejectRuntimeCompilation = reject;
    return this;
  }

  @Override
  public TemplateEngine.Builder maxCacheEntries(int maxEntries) {
    this.maxCacheEntries = maxEntries;
    return this;
  }

  @Override
  public TemplateEngine.Builder negativeCacheTtlMillis(long ttlMillis) {
    this.negativeCacheTtlMillis = ttlMillis;
    return this;
  }

  @Override
  public TemplateEngine.Builder hotReload(boolean enabled) {
    this.hotReload = enabled;
    return this;
  }

  @Override
  public TemplateEngine.Builder watchDebounceMillis(long millis) {
    this.watchDebounceMillis = millis;
    return this;
  }

  @Override
  public TemplateEngine.Builder globalMacroLibraries(List<TemplateId> libraries) {
    this.globalMacroLibraries = libraries;
    return this;
  }

  @Override
  public TemplateEngine.Builder globalMacroPrecedence(GlobalMacroPrecedence precedence) {
    this.globalMacroPrecedence = precedence;
    return this;
  }

  @Override
  public TemplateEngine.Builder addContextContributor(RenderContextContributor contributor) {
    this.contextContributors.add(contributor);
    return this;
  }

  @Override
  public TemplateEngine.Builder contextContributors(List<RenderContextContributor> contributors) {
    this.contextContributors.clear();
    if (contributors != null) {
      this.contextContributors.addAll(contributors);
    }
    return this;
  }

  @Override
  public TemplateEngine.Builder contextCollisionPolicy(ContextCollisionPolicy policy) {
    this.contextCollisionPolicy = policy;
    return this;
  }

  @Override
  public TemplateEngine.Builder layoutConfiguration(LayoutConfiguration configuration) {
    this.layoutConfiguration = configuration;
    return this;
  }

  @Override
  public TemplateEngine.Builder memberAccessPolicy(MemberAccessPolicy policy) {
    this.memberAccessPolicy = policy;
    return this;
  }

  public void markCustomized() {
    this.customized = true;
  }

  public boolean isCustomized() {
    return this.customized;
  }

  public TemplateRepository getRepository() {
    return this.repository;
  }

  public boolean isRejectRuntimeCompilation() {
    return this.rejectRuntimeCompilation;
  }

  public int getMaxCacheEntries() {
    return this.maxCacheEntries;
  }

  public long getNegativeCacheTtlMillis() {
    return this.negativeCacheTtlMillis;
  }

  public boolean isHotReload() {
    return this.hotReload;
  }

  public long getWatchDebounceMillis() {
    return this.watchDebounceMillis;
  }

  public List<TemplateId> getGlobalMacroLibraries() {
    return this.globalMacroLibraries;
  }

  public GlobalMacroPrecedence getGlobalMacroPrecedence() {
    return this.globalMacroPrecedence;
  }

  public List<RenderContextContributor> getContextContributors() {
    return this.contextContributors;
  }

  public ContextCollisionPolicy getContextCollisionPolicy() {
    return this.contextCollisionPolicy;
  }

  public LayoutConfiguration getLayoutConfiguration() {
    return this.layoutConfiguration;
  }

  public MemberAccessPolicy getMemberAccessPolicy() {
    return this.memberAccessPolicy;
  }

  @Override
  public TemplateEngine build() {
    return new TestTemplateEngine(this);
  }
}
