package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
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

/** Immutable request configuration for migration analysis against Apache Velocity. */
public final class TemplateMigrationRequest {

  private final List<Path> sourceDirectories;
  private final List<String> includePatterns;
  private final List<String> excludePatterns;
  private final Charset encoding;
  private final VtlProfile profile;
  private final boolean strictReferences;
  private final TypeCheckingMode typeCheckingMode;
  private final String template;
  private final String format;
  private final Path outputFile;
  private final MigrationSeverity minimumSeverity;
  private final boolean failOnBlocker;
  private final boolean failOnWarning;
  private final Map<TemplateId, TemplateContract> contracts;
  private final List<TemplateId> globalMacroLibraries;
  private final TemplateId layoutId;
  private final ClassLoader classLoader;

  TemplateMigrationRequest(
      List<Path> sourceDirectories,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      VtlProfile profile,
      boolean strictReferences,
      TypeCheckingMode typeCheckingMode,
      String template,
      String format,
      Path outputFile,
      MigrationSeverity minimumSeverity,
      boolean failOnBlocker,
      boolean failOnWarning,
      Map<TemplateId, TemplateContract> contracts,
      List<TemplateId> globalMacroLibraries,
      TemplateId layoutId,
      ClassLoader classLoader) {
    this.sourceDirectories =
        List.copyOf(
            Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null"));
    this.includePatterns =
        List.copyOf(Objects.requireNonNull(includePatterns, "includePatterns must not be null"));
    this.excludePatterns =
        List.copyOf(Objects.requireNonNull(excludePatterns, "excludePatterns must not be null"));
    this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    this.profile = profile;
    this.strictReferences = strictReferences;
    this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
    this.template = template;
    this.format = format != null && !format.isBlank() ? format.trim() : "text";
    this.outputFile = outputFile;
    this.minimumSeverity = minimumSeverity != null ? minimumSeverity : MigrationSeverity.INFO;
    this.failOnBlocker = failOnBlocker;
    this.failOnWarning = failOnWarning;
    this.contracts = contracts != null ? Map.copyOf(contracts) : Map.of();
    this.globalMacroLibraries =
        globalMacroLibraries != null ? List.copyOf(globalMacroLibraries) : List.of();
    this.layoutId = layoutId;
    this.classLoader = classLoader;
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

  public List<String> includes() {
    return includePatterns;
  }

  public List<String> excludePatterns() {
    return excludePatterns;
  }

  public List<String> excludes() {
    return excludePatterns;
  }

  public Charset encoding() {
    return encoding;
  }

  public Optional<VtlProfile> profile() {
    return Optional.ofNullable(profile);
  }

  public boolean strictReferences() {
    return strictReferences;
  }

  public TypeCheckingMode typeCheckingMode() {
    return typeCheckingMode;
  }

  public Optional<String> template() {
    return Optional.ofNullable(template);
  }

  public String format() {
    return format;
  }

  public Optional<Path> outputFile() {
    return Optional.ofNullable(outputFile);
  }

  public MigrationSeverity minimumSeverity() {
    return minimumSeverity;
  }

  public boolean failOnBlocker() {
    return failOnBlocker;
  }

  public boolean failOnWarning() {
    return failOnWarning;
  }

  public Map<TemplateId, TemplateContract> contracts() {
    return contracts;
  }

  public List<TemplateId> globalMacroLibraries() {
    return globalMacroLibraries;
  }

  public Optional<TemplateId> layoutId() {
    return Optional.ofNullable(layoutId);
  }

  public Optional<ClassLoader> classLoader() {
    return Optional.ofNullable(classLoader);
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
    b.strictReferences = this.strictReferences;
    b.typeCheckingMode = this.typeCheckingMode;
    b.template = this.template;
    b.format = this.format;
    b.outputFile = this.outputFile;
    b.minimumSeverity = this.minimumSeverity;
    b.failOnBlocker = this.failOnBlocker;
    b.failOnWarning = this.failOnWarning;
    b.contracts.putAll(this.contracts);
    b.globalMacroLibraries.addAll(this.globalMacroLibraries);
    b.layoutId = this.layoutId;
    b.classLoader = this.classLoader;
    return b;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TemplateMigrationRequest that)) {
      return false;
    }
    return strictReferences == that.strictReferences
        && failOnBlocker == that.failOnBlocker
        && failOnWarning == that.failOnWarning
        && Objects.equals(sourceDirectories, that.sourceDirectories)
        && Objects.equals(includePatterns, that.includePatterns)
        && Objects.equals(excludePatterns, that.excludePatterns)
        && Objects.equals(encoding, that.encoding)
        && profile == that.profile
        && typeCheckingMode == that.typeCheckingMode
        && Objects.equals(template, that.template)
        && Objects.equals(format, that.format)
        && Objects.equals(outputFile, that.outputFile)
        && minimumSeverity == that.minimumSeverity
        && Objects.equals(contracts, that.contracts)
        && Objects.equals(globalMacroLibraries, that.globalMacroLibraries)
        && Objects.equals(layoutId, that.layoutId)
        && Objects.equals(classLoader, that.classLoader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sourceDirectories,
        includePatterns,
        excludePatterns,
        encoding,
        profile,
        strictReferences,
        typeCheckingMode,
        template,
        format,
        outputFile,
        minimumSeverity,
        failOnBlocker,
        failOnWarning,
        contracts,
        globalMacroLibraries,
        layoutId,
        classLoader);
  }

  @Override
  public String toString() {
    return "TemplateMigrationRequest{"
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
        + ", strictReferences="
        + strictReferences
        + ", typeCheckingMode="
        + typeCheckingMode
        + ", template="
        + template
        + ", format='"
        + format
        + '\''
        + ", outputFile="
        + outputFile
        + ", minimumSeverity="
        + minimumSeverity
        + ", failOnBlocker="
        + failOnBlocker
        + ", failOnWarning="
        + failOnWarning
        + ", contracts="
        + contracts
        + ", globalMacroLibraries="
        + globalMacroLibraries
        + ", layoutId="
        + layoutId
        + ", classLoader="
        + classLoader
        + '}';
  }

  public static final class Builder {

    private final List<Path> sourceDirectories = new ArrayList<>();
    private final List<String> includePatterns = new ArrayList<>(List.of("**/*.vtl", "**/*.vm"));
    private final List<String> excludePatterns = new ArrayList<>();
    private Charset encoding = StandardCharsets.UTF_8;
    private VtlProfile profile;
    private boolean strictReferences = false;
    private TypeCheckingMode typeCheckingMode = TypeCheckingMode.OFF;
    private String template;
    private String format = "text";
    private Path outputFile;
    private MigrationSeverity minimumSeverity = MigrationSeverity.INFO;
    private boolean failOnBlocker = true;
    private boolean failOnWarning = false;
    private final Map<TemplateId, TemplateContract> contracts = new LinkedHashMap<>();
    private final List<TemplateId> globalMacroLibraries = new ArrayList<>();
    private TemplateId layoutId;
    private ClassLoader classLoader;

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
        for (Path p : sourceDirectories) {
          if (p != null) {
            this.sourceDirectories.add(p);
          }
        }
      }
      return this;
    }

    public Builder include(String includePattern) {
      if (includePattern != null && !includePattern.isBlank()) {
        this.includePatterns.add(includePattern);
      }
      return this;
    }

    public Builder includes(String... includePatterns) {
      if (includePatterns != null) {
        for (String p : includePatterns) {
          if (p != null && !p.isBlank()) {
            this.includePatterns.add(p);
          }
        }
      }
      return this;
    }

    public Builder includes(List<String> includePatterns) {
      if (includePatterns != null) {
        this.includePatterns.clear();
        for (String p : includePatterns) {
          if (p != null && !p.isBlank()) {
            this.includePatterns.add(p);
          }
        }
      }
      return this;
    }

    public Builder includePattern(String includePattern) {
      return include(includePattern);
    }

    public Builder includePatterns(String... includePatterns) {
      return includes(includePatterns);
    }

    public Builder includePatterns(List<String> includePatterns) {
      return includes(includePatterns);
    }

    public Builder exclude(String excludePattern) {
      if (excludePattern != null && !excludePattern.isBlank()) {
        this.excludePatterns.add(excludePattern);
      }
      return this;
    }

    public Builder excludes(String... excludePatterns) {
      if (excludePatterns != null) {
        for (String p : excludePatterns) {
          if (p != null && !p.isBlank()) {
            this.excludePatterns.add(p);
          }
        }
      }
      return this;
    }

    public Builder excludes(List<String> excludePatterns) {
      if (excludePatterns != null) {
        this.excludePatterns.clear();
        for (String p : excludePatterns) {
          if (p != null && !p.isBlank()) {
            this.excludePatterns.add(p);
          }
        }
      }
      return this;
    }

    public Builder excludePattern(String excludePattern) {
      return exclude(excludePattern);
    }

    public Builder excludePatterns(String... excludePatterns) {
      return excludes(excludePatterns);
    }

    public Builder excludePatterns(List<String> excludePatterns) {
      return excludes(excludePatterns);
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

    public Builder strictReferences(boolean strictReferences) {
      this.strictReferences = strictReferences;
      return this;
    }

    public Builder typeCheckingMode(TypeCheckingMode typeCheckingMode) {
      if (typeCheckingMode != null) {
        this.typeCheckingMode = typeCheckingMode;
      }
      return this;
    }

    public Builder template(String template) {
      this.template = template != null && !template.isBlank() ? template.trim() : null;
      return this;
    }

    public Builder format(String format) {
      if (format != null && !format.isBlank()) {
        this.format = format.trim();
      }
      return this;
    }

    public Builder outputFile(Path outputFile) {
      this.outputFile = outputFile;
      return this;
    }

    public Builder minimumSeverity(MigrationSeverity minimumSeverity) {
      if (minimumSeverity != null) {
        this.minimumSeverity = minimumSeverity;
      }
      return this;
    }

    public Builder failOnBlocker(boolean failOnBlocker) {
      this.failOnBlocker = failOnBlocker;
      return this;
    }

    public Builder failOnWarning(boolean failOnWarning) {
      this.failOnWarning = failOnWarning;
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
        for (TemplateId id : libraryIds) {
          if (id != null) {
            this.globalMacroLibraries.add(id);
          }
        }
      }
      return this;
    }

    public Builder layoutId(TemplateId layoutId) {
      this.layoutId = layoutId;
      return this;
    }

    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public TemplateMigrationRequest build() {
      return new TemplateMigrationRequest(
          sourceDirectories,
          includePatterns,
          excludePatterns,
          encoding,
          profile,
          strictReferences,
          typeCheckingMode,
          template,
          format,
          outputFile,
          minimumSeverity,
          failOnBlocker,
          failOnWarning,
          contracts,
          globalMacroLibraries,
          layoutId,
          classLoader);
    }
  }
}
