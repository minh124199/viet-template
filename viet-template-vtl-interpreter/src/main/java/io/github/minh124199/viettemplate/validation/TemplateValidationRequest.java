package io.github.minh124199.viettemplate.validation;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable request configuration for build-time template validation. */
public final class TemplateValidationRequest {

  private final List<Path> sourceDirectories;
  private final List<String> includePatterns;
  private final List<String> excludePatterns;
  private final Charset encoding;
  private final VtlProfile profile;
  private final TypeCheckingMode typeCheckingMode;
  private final Map<TemplateId, TemplateContract> contracts;
  private final ClassLoader classLoader;
  private final VtlSemanticOptions semanticOptions;
  private final boolean failOnWarning;
  private final boolean validateDependencies;
  private final List<TemplateId> globalMacroLibraries;
  private final TemplateId layoutId;

  TemplateValidationRequest(
      List<Path> sourceDirectories,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      VtlProfile profile,
      TypeCheckingMode typeCheckingMode,
      Map<TemplateId, TemplateContract> contracts,
      ClassLoader classLoader,
      VtlSemanticOptions semanticOptions,
      boolean failOnWarning,
      boolean validateDependencies,
      List<TemplateId> globalMacroLibraries,
      TemplateId layoutId) {
    this.sourceDirectories =
        List.copyOf(
            Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null"));
    this.includePatterns =
        List.copyOf(Objects.requireNonNull(includePatterns, "includePatterns must not be null"));
    this.excludePatterns =
        List.copyOf(Objects.requireNonNull(excludePatterns, "excludePatterns must not be null"));
    this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    this.profile = profile;
    this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
    this.contracts = contracts != null ? Map.copyOf(contracts) : Map.of();
    this.classLoader = classLoader;
    this.semanticOptions = semanticOptions;
    this.failOnWarning = failOnWarning;
    this.validateDependencies = validateDependencies;
    this.globalMacroLibraries =
        globalMacroLibraries != null ? List.copyOf(globalMacroLibraries) : List.of();
    this.layoutId = layoutId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<Path> sourceDirectories() {
    return sourceDirectories;
  }

  public List<String> includePatterns() {
    return includePatterns;
  }

  public List<String> excludePatterns() {
    return excludePatterns;
  }

  public Charset encoding() {
    return encoding;
  }

  public Optional<VtlProfile> profile() {
    return Optional.ofNullable(profile);
  }

  public TypeCheckingMode typeCheckingMode() {
    return typeCheckingMode;
  }

  public Map<TemplateId, TemplateContract> contracts() {
    return contracts;
  }

  public Optional<ClassLoader> classLoader() {
    return Optional.ofNullable(classLoader);
  }

  Optional<VtlSemanticOptions> semanticOptions() {
    return Optional.ofNullable(semanticOptions);
  }

  public boolean failOnWarning() {
    return failOnWarning;
  }

  public boolean validateDependencies() {
    return validateDependencies;
  }

  public List<TemplateId> globalMacroLibraries() {
    return globalMacroLibraries;
  }

  public Optional<TemplateId> layoutId() {
    return Optional.ofNullable(layoutId);
  }

  public Builder toBuilder() {
    Builder b = new Builder();
    b.sourceDirectories.addAll(this.sourceDirectories);
    b.includePatterns.clear();
    b.includePatterns.addAll(this.includePatterns);
    b.excludePatterns.clear();
    b.excludePatterns.addAll(this.excludePatterns);
    b.encoding = this.encoding;
    b.profile = this.profile;
    b.typeCheckingMode = this.typeCheckingMode;
    b.contracts.putAll(this.contracts);
    b.classLoader = this.classLoader;
    b.semanticOptions = this.semanticOptions;
    b.failOnWarning = this.failOnWarning;
    b.validateDependencies = this.validateDependencies;
    b.globalMacroLibraries.addAll(this.globalMacroLibraries);
    b.layoutId = this.layoutId;
    return b;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TemplateValidationRequest that)) {
      return false;
    }
    return failOnWarning == that.failOnWarning
        && validateDependencies == that.validateDependencies
        && Objects.equals(sourceDirectories, that.sourceDirectories)
        && Objects.equals(includePatterns, that.includePatterns)
        && Objects.equals(excludePatterns, that.excludePatterns)
        && Objects.equals(encoding, that.encoding)
        && profile == that.profile
        && typeCheckingMode == that.typeCheckingMode
        && Objects.equals(contracts, that.contracts)
        && Objects.equals(classLoader, that.classLoader)
        && Objects.equals(semanticOptions, that.semanticOptions)
        && Objects.equals(globalMacroLibraries, that.globalMacroLibraries)
        && Objects.equals(layoutId, that.layoutId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sourceDirectories,
        includePatterns,
        excludePatterns,
        encoding,
        profile,
        typeCheckingMode,
        contracts,
        classLoader,
        semanticOptions,
        failOnWarning,
        validateDependencies,
        globalMacroLibraries,
        layoutId);
  }

  @Override
  public String toString() {
    return "TemplateValidationRequest{"
        + "sourceDirectories="
        + sourceDirectories
        + ", includePatterns="
        + includePatterns
        + ", excludePatterns="
        + excludePatterns
        + ", encoding="
        + encoding
        + ", profile="
        + profile
        + ", typeCheckingMode="
        + typeCheckingMode
        + ", contracts="
        + contracts
        + ", classLoader="
        + classLoader
        + ", semanticOptions="
        + semanticOptions
        + ", failOnWarning="
        + failOnWarning
        + ", validateDependencies="
        + validateDependencies
        + ", globalMacroLibraries="
        + globalMacroLibraries
        + ", layoutId="
        + layoutId
        + '}';
  }

  public static final class Builder {

    private final List<Path> sourceDirectories = new ArrayList<>();
    private final List<String> includePatterns = new ArrayList<>(List.of("**/*.vtl", "**/*.vm"));
    private final List<String> excludePatterns = new ArrayList<>();
    private Charset encoding = StandardCharsets.UTF_8;
    private VtlProfile profile;
    private TypeCheckingMode typeCheckingMode = TypeCheckingMode.OFF;
    private final Map<TemplateId, TemplateContract> contracts = new LinkedHashMap<>();
    private ClassLoader classLoader;
    private VtlSemanticOptions semanticOptions;
    private boolean failOnWarning = false;
    private boolean validateDependencies = true;
    private final List<TemplateId> globalMacroLibraries = new ArrayList<>();
    private TemplateId layoutId;

    public Builder sourceDirectory(Path sourceDirectory) {
      if (sourceDirectory != null) {
        this.sourceDirectories.add(sourceDirectory);
      }
      return this;
    }

    public Builder sourceDirectories(Path... sourceDirectories) {
      if (sourceDirectories != null) {
        this.sourceDirectories.addAll(Arrays.asList(sourceDirectories));
      }
      return this;
    }

    public Builder sourceDirectories(List<Path> sourceDirectories) {
      if (sourceDirectories != null) {
        this.sourceDirectories.addAll(sourceDirectories);
      }
      return this;
    }

    public Builder includePattern(String includePattern) {
      if (includePattern != null && !includePattern.isBlank()) {
        this.includePatterns.add(includePattern.trim());
      }
      return this;
    }

    public Builder includePatterns(String... includePatterns) {
      if (includePatterns != null) {
        this.includePatterns.clear();
        for (String p : includePatterns) {
          if (p != null && !p.isBlank()) {
            this.includePatterns.add(p.trim());
          }
        }
      }
      return this;
    }

    public Builder includePatterns(List<String> includePatterns) {
      if (includePatterns != null) {
        this.includePatterns.clear();
        for (String p : includePatterns) {
          if (p != null && !p.isBlank()) {
            this.includePatterns.add(p.trim());
          }
        }
      }
      return this;
    }

    public Builder excludePattern(String excludePattern) {
      if (excludePattern != null && !excludePattern.isBlank()) {
        this.excludePatterns.add(excludePattern.trim());
      }
      return this;
    }

    public Builder excludePatterns(String... excludePatterns) {
      if (excludePatterns != null) {
        this.excludePatterns.clear();
        for (String p : excludePatterns) {
          if (p != null && !p.isBlank()) {
            this.excludePatterns.add(p.trim());
          }
        }
      }
      return this;
    }

    public Builder excludePatterns(List<String> excludePatterns) {
      if (excludePatterns != null) {
        this.excludePatterns.clear();
        for (String p : excludePatterns) {
          if (p != null && !p.isBlank()) {
            this.excludePatterns.add(p.trim());
          }
        }
      }
      return this;
    }

    public Builder encoding(Charset encoding) {
      if (encoding != null) {
        this.encoding = encoding;
      }
      return this;
    }

    public Builder profile(VtlProfile profile) {
      this.profile = profile;
      return this;
    }

    public Builder profile(Optional<VtlProfile> profile) {
      this.profile = (profile != null) ? profile.orElse(null) : null;
      return this;
    }

    public Builder typeCheckingMode(TypeCheckingMode typeCheckingMode) {
      if (typeCheckingMode != null) {
        this.typeCheckingMode = typeCheckingMode;
      }
      return this;
    }

    public Builder contract(TemplateId templateId, TemplateContract contract) {
      if (templateId != null && contract != null) {
        this.contracts.put(templateId, contract);
      }
      return this;
    }

    public Builder contracts(Map<TemplateId, TemplateContract> contracts) {
      if (contracts != null) {
        this.contracts.putAll(contracts);
      }
      return this;
    }

    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public Builder classLoader(Optional<ClassLoader> classLoader) {
      this.classLoader = (classLoader != null) ? classLoader.orElse(null) : null;
      return this;
    }

    Builder semanticOptions(VtlSemanticOptions semanticOptions) {
      this.semanticOptions = semanticOptions;
      return this;
    }

    Builder semanticOptions(Optional<VtlSemanticOptions> semanticOptions) {
      this.semanticOptions = (semanticOptions != null) ? semanticOptions.orElse(null) : null;
      return this;
    }

    public Builder failOnWarning(boolean failOnWarning) {
      this.failOnWarning = failOnWarning;
      return this;
    }

    public Builder validateDependencies(boolean validateDependencies) {
      this.validateDependencies = validateDependencies;
      return this;
    }

    public Builder globalMacroLibrary(TemplateId libraryId) {
      if (libraryId != null) {
        this.globalMacroLibraries.add(libraryId);
      }
      return this;
    }

    public Builder globalMacroLibraries(TemplateId... libraryIds) {
      if (libraryIds != null) {
        this.globalMacroLibraries.addAll(Arrays.asList(libraryIds));
      }
      return this;
    }

    public Builder globalMacroLibraries(List<TemplateId> libraryIds) {
      if (libraryIds != null) {
        this.globalMacroLibraries.addAll(libraryIds);
      }
      return this;
    }

    public Builder layoutId(TemplateId layoutId) {
      this.layoutId = layoutId;
      return this;
    }

    public Builder layoutId(Optional<TemplateId> layoutId) {
      this.layoutId = (layoutId != null) ? layoutId.orElse(null) : null;
      return this;
    }

    public TemplateValidationRequest build() {
      return new TemplateValidationRequest(
          sourceDirectories,
          includePatterns,
          excludePatterns,
          encoding,
          profile,
          typeCheckingMode,
          contracts,
          classLoader,
          semanticOptions,
          failOnWarning,
          validateDependencies,
          globalMacroLibraries,
          layoutId);
    }
  }
}
