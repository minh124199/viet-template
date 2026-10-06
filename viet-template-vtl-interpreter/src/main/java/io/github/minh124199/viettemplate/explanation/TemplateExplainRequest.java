package io.github.minh124199.viettemplate.explanation;

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
import java.util.OptionalInt;

/** Immutable request configuration for structured compiler explanation. */
public final class TemplateExplainRequest {

  private final List<Path> sourceDirectories;
  private final List<String> includePatterns;
  private final List<String> excludePatterns;
  private final Charset encoding;
  private final TypeCheckingMode typeCheckingMode;
  private final VtlProfile profile;
  private final String template;
  private final Integer line;
  private final Integer column;
  private final String format;
  private final Map<TemplateId, TemplateContract> contracts;
  private final List<TemplateId> globalMacroLibraries;
  private final TemplateId layoutId;
  private final ClassLoader classLoader;
  private final Path outputFile;
  private final boolean failOnDynamicFallback;
  private final boolean strictReferences;

  TemplateExplainRequest(
      List<Path> sourceDirectories,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      TypeCheckingMode typeCheckingMode,
      VtlProfile profile,
      String template,
      Integer line,
      Integer column,
      String format,
      Map<TemplateId, TemplateContract> contracts,
      List<TemplateId> globalMacroLibraries,
      TemplateId layoutId,
      ClassLoader classLoader,
      Path outputFile,
      boolean failOnDynamicFallback,
      boolean strictReferences) {
    this.sourceDirectories =
        List.copyOf(
            Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null"));
    this.includePatterns =
        List.copyOf(Objects.requireNonNull(includePatterns, "includePatterns must not be null"));
    this.excludePatterns =
        List.copyOf(Objects.requireNonNull(excludePatterns, "excludePatterns must not be null"));
    this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
    this.profile = profile;
    this.template = template;
    this.line = line;
    this.column = column;
    this.format = format != null && !format.isBlank() ? format.trim() : "text";
    this.contracts = contracts != null ? Map.copyOf(contracts) : Map.of();
    this.globalMacroLibraries =
        globalMacroLibraries != null ? List.copyOf(globalMacroLibraries) : List.of();
    this.layoutId = layoutId;
    this.classLoader = classLoader;
    this.outputFile = outputFile;
    this.failOnDynamicFallback = failOnDynamicFallback;
    this.strictReferences = strictReferences;
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

  public TypeCheckingMode typeCheckingMode() {
    return typeCheckingMode;
  }

  public Optional<VtlProfile> profile() {
    return Optional.ofNullable(profile);
  }

  public Optional<String> template() {
    return Optional.ofNullable(template);
  }

  public OptionalInt line() {
    return line != null ? OptionalInt.of(line) : OptionalInt.empty();
  }

  public OptionalInt column() {
    return column != null ? OptionalInt.of(column) : OptionalInt.empty();
  }

  public String format() {
    return format;
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

  public Optional<Path> outputFile() {
    return Optional.ofNullable(outputFile);
  }

  public boolean failOnDynamicFallback() {
    return failOnDynamicFallback;
  }

  public boolean strictReferences() {
    return strictReferences;
  }

  public Builder toBuilder() {
    Builder b = new Builder();
    b.sourceDirectories.addAll(this.sourceDirectories);
    b.includePatterns.clear();
    b.includePatterns.addAll(this.includePatterns);
    b.excludePatterns.clear();
    b.excludePatterns.addAll(this.excludePatterns);
    b.encoding = this.encoding;
    b.typeCheckingMode = this.typeCheckingMode;
    b.profile = this.profile;
    b.template = this.template;
    b.line = this.line;
    b.column = this.column;
    b.format = this.format;
    b.contracts.putAll(this.contracts);
    b.globalMacroLibraries.addAll(this.globalMacroLibraries);
    b.layoutId = this.layoutId;
    b.classLoader = this.classLoader;
    b.outputFile = this.outputFile;
    b.failOnDynamicFallback = this.failOnDynamicFallback;
    b.strictReferences = this.strictReferences;
    return b;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TemplateExplainRequest that)) {
      return false;
    }
    return failOnDynamicFallback == that.failOnDynamicFallback
        && strictReferences == that.strictReferences
        && Objects.equals(sourceDirectories, that.sourceDirectories)
        && Objects.equals(includePatterns, that.includePatterns)
        && Objects.equals(excludePatterns, that.excludePatterns)
        && Objects.equals(encoding, that.encoding)
        && typeCheckingMode == that.typeCheckingMode
        && profile == that.profile
        && Objects.equals(template, that.template)
        && Objects.equals(line, that.line)
        && Objects.equals(column, that.column)
        && Objects.equals(format, that.format)
        && Objects.equals(contracts, that.contracts)
        && Objects.equals(globalMacroLibraries, that.globalMacroLibraries)
        && Objects.equals(layoutId, that.layoutId)
        && Objects.equals(classLoader, that.classLoader)
        && Objects.equals(outputFile, that.outputFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sourceDirectories,
        includePatterns,
        excludePatterns,
        encoding,
        typeCheckingMode,
        profile,
        template,
        line,
        column,
        format,
        contracts,
        globalMacroLibraries,
        layoutId,
        classLoader,
        outputFile,
        failOnDynamicFallback,
        strictReferences);
  }

  @Override
  public String toString() {
    return "TemplateExplainRequest{"
        + "sourceDirectories="
        + sourceDirectories
        + ", includePatterns="
        + includePatterns
        + ", excludePatterns="
        + excludePatterns
        + ", encoding="
        + encoding
        + ", typeCheckingMode="
        + typeCheckingMode
        + ", profile="
        + profile
        + ", template="
        + template
        + ", line="
        + line
        + ", column="
        + column
        + ", format='"
        + format
        + '\''
        + ", contracts="
        + contracts
        + ", globalMacroLibraries="
        + globalMacroLibraries
        + ", layoutId="
        + layoutId
        + ", classLoader="
        + classLoader
        + ", outputFile="
        + outputFile
        + ", failOnDynamicFallback="
        + failOnDynamicFallback
        + ", strictReferences="
        + strictReferences
        + '}';
  }

  public static final class Builder {

    private final List<Path> sourceDirectories = new ArrayList<>();
    private final List<String> includePatterns = new ArrayList<>(List.of("**/*.vtl", "**/*.vm"));
    private final List<String> excludePatterns = new ArrayList<>();
    private Charset encoding = StandardCharsets.UTF_8;
    private TypeCheckingMode typeCheckingMode = TypeCheckingMode.OFF;
    private VtlProfile profile;
    private String template;
    private Integer line;
    private Integer column;
    private String format = "text";
    private final Map<TemplateId, TemplateContract> contracts = new LinkedHashMap<>();
    private final List<TemplateId> globalMacroLibraries = new ArrayList<>();
    private TemplateId layoutId;
    private ClassLoader classLoader;
    private Path outputFile;
    private boolean failOnDynamicFallback = false;
    private boolean strictReferences = false;

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

    public Builder typeCheckingMode(TypeCheckingMode typeCheckingMode) {
      if (typeCheckingMode != null) {
        this.typeCheckingMode = typeCheckingMode;
      }
      return this;
    }

    public Builder profile(VtlProfile profile) {
      this.profile = profile;
      return this;
    }

    public Builder profile(Optional<VtlProfile> profile) {
      this.profile = profile != null ? profile.orElse(null) : null;
      return this;
    }

    public Builder template(String template) {
      this.template = template != null && !template.isBlank() ? template.trim() : null;
      return this;
    }

    public Builder template(Optional<String> template) {
      this.template = template != null ? template.orElse(null) : null;
      return this;
    }

    public Builder line(int line) {
      this.line = line > 0 ? line : null;
      return this;
    }

    public Builder line(OptionalInt line) {
      this.line = line != null && line.isPresent() ? line.getAsInt() : null;
      return this;
    }

    public Builder column(int column) {
      this.column = column > 0 ? column : null;
      return this;
    }

    public Builder column(OptionalInt column) {
      this.column = column != null && column.isPresent() ? column.getAsInt() : null;
      return this;
    }

    public Builder format(String format) {
      if (format != null && !format.isBlank()) {
        this.format = format.trim();
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
      this.layoutId = layoutId != null ? layoutId.orElse(null) : null;
      return this;
    }

    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public Builder classLoader(Optional<ClassLoader> classLoader) {
      this.classLoader = classLoader != null ? classLoader.orElse(null) : null;
      return this;
    }

    public Builder outputFile(Path outputFile) {
      this.outputFile = outputFile;
      return this;
    }

    public Builder outputFile(Optional<Path> outputFile) {
      this.outputFile = outputFile != null ? outputFile.orElse(null) : null;
      return this;
    }

    public Builder failOnDynamicFallback(boolean failOnDynamicFallback) {
      this.failOnDynamicFallback = failOnDynamicFallback;
      return this;
    }

    public Builder strictReferences(boolean strictReferences) {
      this.strictReferences = strictReferences;
      return this;
    }

    public TemplateExplainRequest build() {
      return new TemplateExplainRequest(
          sourceDirectories,
          includePatterns,
          excludePatterns,
          encoding,
          typeCheckingMode,
          profile,
          template,
          line,
          column,
          format,
          contracts,
          globalMacroLibraries,
          layoutId,
          classLoader,
          outputFile,
          failOnDynamicFallback,
          strictReferences);
    }
  }
}
