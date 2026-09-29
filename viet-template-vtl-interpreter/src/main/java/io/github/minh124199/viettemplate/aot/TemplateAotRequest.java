package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable request configuration for Ahead-Of-Time (AOT) template compilation. */
public final class TemplateAotRequest {

  private final List<Path> sourceDirectories;
  private final Path outputDirectory;
  private final Path resourceOutputDirectory;
  private final List<String> includePatterns;
  private final List<String> excludePatterns;
  private final Charset encoding;
  private final String packagePrefix;
  private final boolean failOnWarning;
  private final boolean incremental;
  private final Path stateFile;
  private final Map<TemplateId, TemplateContract> contracts;
  private final boolean generateTypedFacades;
  private final Path generatedSourcesDirectory;
  private final ClassLoader classLoader;
  private final TypeCheckingMode typeCheckingMode;

  TemplateAotRequest(
      List<Path> sourceDirectories,
      Path outputDirectory,
      Path resourceOutputDirectory,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      String packagePrefix,
      boolean failOnWarning,
      boolean incremental,
      Path stateFile) {
    this(
        sourceDirectories,
        outputDirectory,
        resourceOutputDirectory,
        includePatterns,
        excludePatterns,
        encoding,
        packagePrefix,
        failOnWarning,
        incremental,
        stateFile,
        Map.of(),
        false,
        null,
        null,
        TypeCheckingMode.OFF);
  }

  TemplateAotRequest(
      List<Path> sourceDirectories,
      Path outputDirectory,
      Path resourceOutputDirectory,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      String packagePrefix,
      boolean failOnWarning,
      boolean incremental,
      Path stateFile,
      Map<TemplateId, TemplateContract> contracts,
      boolean generateTypedFacades,
      Path generatedSourcesDirectory,
      ClassLoader classLoader) {
    this(
        sourceDirectories,
        outputDirectory,
        resourceOutputDirectory,
        includePatterns,
        excludePatterns,
        encoding,
        packagePrefix,
        failOnWarning,
        incremental,
        stateFile,
        contracts,
        generateTypedFacades,
        generatedSourcesDirectory,
        classLoader,
        TypeCheckingMode.OFF);
  }

  TemplateAotRequest(
      List<Path> sourceDirectories,
      Path outputDirectory,
      Path resourceOutputDirectory,
      List<String> includePatterns,
      List<String> excludePatterns,
      Charset encoding,
      String packagePrefix,
      boolean failOnWarning,
      boolean incremental,
      Path stateFile,
      Map<TemplateId, TemplateContract> contracts,
      boolean generateTypedFacades,
      Path generatedSourcesDirectory,
      ClassLoader classLoader,
      TypeCheckingMode typeCheckingMode) {
    this.sourceDirectories =
        List.copyOf(
            Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null"));
    this.outputDirectory =
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    this.resourceOutputDirectory =
        Objects.requireNonNull(resourceOutputDirectory, "resourceOutputDirectory must not be null");
    this.includePatterns =
        List.copyOf(Objects.requireNonNull(includePatterns, "includePatterns must not be null"));
    this.excludePatterns =
        List.copyOf(Objects.requireNonNull(excludePatterns, "excludePatterns must not be null"));
    this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    this.packagePrefix = Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
    validatePackagePrefix(packagePrefix.trim());
    this.failOnWarning = failOnWarning;
    this.incremental = incremental;
    this.stateFile = stateFile;
    this.contracts = contracts != null ? Map.copyOf(contracts) : Map.of();
    this.generateTypedFacades = generateTypedFacades;
    this.generatedSourcesDirectory = generatedSourcesDirectory;
    this.classLoader = classLoader;
    this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<Path> sourceDirectories() {
    return sourceDirectories;
  }

  public Path outputDirectory() {
    return outputDirectory;
  }

  public Path resourceOutputDirectory() {
    return resourceOutputDirectory;
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

  public String packagePrefix() {
    return packagePrefix;
  }

  public boolean failOnWarning() {
    return failOnWarning;
  }

  public boolean incremental() {
    return incremental;
  }

  public Optional<Path> stateFile() {
    return Optional.ofNullable(stateFile);
  }

  public Map<TemplateId, TemplateContract> contracts() {
    return contracts;
  }

  public boolean generateTypedFacades() {
    return generateTypedFacades;
  }

  public Optional<Path> generatedSourcesDirectory() {
    return Optional.ofNullable(generatedSourcesDirectory);
  }

  public Optional<ClassLoader> classLoader() {
    return Optional.ofNullable(classLoader);
  }

  public TypeCheckingMode typeCheckingMode() {
    return typeCheckingMode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateAotRequest that)) return false;
    return failOnWarning == that.failOnWarning
        && incremental == that.incremental
        && generateTypedFacades == that.generateTypedFacades
        && typeCheckingMode == that.typeCheckingMode
        && Objects.equals(sourceDirectories, that.sourceDirectories)
        && Objects.equals(outputDirectory, that.outputDirectory)
        && Objects.equals(resourceOutputDirectory, that.resourceOutputDirectory)
        && Objects.equals(includePatterns, that.includePatterns)
        && Objects.equals(excludePatterns, that.excludePatterns)
        && Objects.equals(encoding, that.encoding)
        && Objects.equals(packagePrefix, that.packagePrefix)
        && Objects.equals(stateFile, that.stateFile)
        && Objects.equals(contracts, that.contracts)
        && Objects.equals(generatedSourcesDirectory, that.generatedSourcesDirectory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sourceDirectories,
        outputDirectory,
        resourceOutputDirectory,
        includePatterns,
        excludePatterns,
        encoding,
        packagePrefix,
        failOnWarning,
        incremental,
        stateFile,
        contracts,
        generateTypedFacades,
        generatedSourcesDirectory,
        typeCheckingMode);
  }

  @Override
  public String toString() {
    return "TemplateAotRequest{"
        + "sourceDirectories="
        + sourceDirectories
        + ", outputDirectory="
        + outputDirectory
        + ", resourceOutputDirectory="
        + resourceOutputDirectory
        + ", includePatterns="
        + includePatterns
        + ", contracts="
        + contracts.size()
        + ", generateTypedFacades="
        + generateTypedFacades
        + ", excludePatterns="
        + excludePatterns
        + ", encoding="
        + encoding
        + ", packagePrefix='"
        + packagePrefix
        + '\''
        + ", failOnWarning="
        + failOnWarning
        + ", incremental="
        + incremental
        + ", stateFile="
        + stateFile
        + ", typeCheckingMode="
        + typeCheckingMode
        + '}';
  }

  public static final class Builder {
    private final List<Path> sourceDirectories = new ArrayList<>();
    private Path outputDirectory;
    private Path resourceOutputDirectory;
    private List<String> includePatterns = List.of("**/*.vtl", "**/*.vm");
    private List<String> excludePatterns = List.of();
    private Charset encoding = StandardCharsets.UTF_8;
    private String packagePrefix = "io.github.minh124199.viettemplate.generated";
    private boolean failOnWarning = false;
    private boolean incremental = true;
    private Path stateFile;
    private final Map<TemplateId, TemplateContract> contracts = new LinkedHashMap<>();
    private boolean generateTypedFacades = false;
    private Path generatedSourcesDirectory;
    private ClassLoader classLoader;
    private TypeCheckingMode typeCheckingMode = TypeCheckingMode.OFF;

    private Builder() {}

    public Builder typeCheckingMode(TypeCheckingMode typeCheckingMode) {
      this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
      return this;
    }

    public Builder typeChecking(String typeChecking) {
      this.typeCheckingMode = TypeCheckingMode.parse(typeChecking);
      return this;
    }

    public Builder sourceDirectory(Path sourceDirectory) {
      Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
      this.sourceDirectories.add(sourceDirectory);
      return this;
    }

    public Builder sourceDirectories(List<Path> sourceDirectories) {
      Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null");
      this.sourceDirectories.clear();
      this.sourceDirectories.addAll(sourceDirectories);
      return this;
    }

    public Builder sourceDirectories(Path... sourceDirectories) {
      Objects.requireNonNull(sourceDirectories, "sourceDirectories must not be null");
      this.sourceDirectories.clear();
      this.sourceDirectories.addAll(List.of(sourceDirectories));
      return this;
    }

    public Builder outputDirectory(Path outputDirectory) {
      this.outputDirectory =
          Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
      return this;
    }

    public Builder resourceOutputDirectory(Path resourceOutputDirectory) {
      this.resourceOutputDirectory = resourceOutputDirectory;
      return this;
    }

    public Builder includePatterns(List<String> includePatterns) {
      Objects.requireNonNull(includePatterns, "includePatterns must not be null");
      this.includePatterns = List.copyOf(includePatterns);
      return this;
    }

    public Builder includePatterns(String... includePatterns) {
      Objects.requireNonNull(includePatterns, "includePatterns must not be null");
      this.includePatterns = List.of(includePatterns);
      return this;
    }

    public Builder includePattern(String includePattern) {
      Objects.requireNonNull(includePattern, "includePattern must not be null");
      List<String> list = new ArrayList<>(this.includePatterns);
      list.add(includePattern);
      this.includePatterns = List.copyOf(list);
      return this;
    }

    public Builder excludePatterns(List<String> excludePatterns) {
      Objects.requireNonNull(excludePatterns, "excludePatterns must not be null");
      this.excludePatterns = List.copyOf(excludePatterns);
      return this;
    }

    public Builder excludePatterns(String... excludePatterns) {
      Objects.requireNonNull(excludePatterns, "excludePatterns must not be null");
      this.excludePatterns = List.of(excludePatterns);
      return this;
    }

    public Builder excludePattern(String excludePattern) {
      Objects.requireNonNull(excludePattern, "excludePattern must not be null");
      List<String> list = new ArrayList<>(this.excludePatterns);
      list.add(excludePattern);
      this.excludePatterns = List.copyOf(list);
      return this;
    }

    public Builder encoding(Charset encoding) {
      this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
      return this;
    }

    public Builder packagePrefix(String packagePrefix) {
      this.packagePrefix = Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
      return this;
    }

    public Builder failOnWarning(boolean failOnWarning) {
      this.failOnWarning = failOnWarning;
      return this;
    }

    public Builder incremental(boolean incremental) {
      this.incremental = incremental;
      return this;
    }

    public Builder stateFile(Path stateFile) {
      this.stateFile = stateFile;
      return this;
    }

    public Builder stateFile(Optional<Path> stateFile) {
      this.stateFile = (stateFile != null) ? stateFile.orElse(null) : null;
      return this;
    }

    public Builder contract(TemplateId id, TemplateContract contract) {
      Objects.requireNonNull(id, "id must not be null");
      Objects.requireNonNull(contract, "contract must not be null");
      if (!id.equals(contract.templateId())) {
        throw new IllegalArgumentException(
            "TemplateId mismatch: contract mapping key is '"
                + id.value()
                + "' but contract.templateId is '"
                + contract.templateId().value()
                + "'");
      }
      this.contracts.put(id, contract);
      return this;
    }

    public Builder contracts(Map<TemplateId, TemplateContract> contracts) {
      Objects.requireNonNull(contracts, "contracts must not be null");
      for (Map.Entry<TemplateId, TemplateContract> entry : contracts.entrySet()) {
        contract(entry.getKey(), entry.getValue());
      }
      return this;
    }

    public Builder generateTypedFacades(boolean generateTypedFacades) {
      this.generateTypedFacades = generateTypedFacades;
      return this;
    }

    public Builder generatedSourcesDirectory(Path generatedSourcesDirectory) {
      this.generatedSourcesDirectory = generatedSourcesDirectory;
      return this;
    }

    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public TemplateAotRequest build() {
      Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
      if (sourceDirectories.isEmpty()) {
        throw new IllegalArgumentException("At least one source directory must be specified");
      }
      validatePackagePrefix(packagePrefix.trim());
      Path resOut = (resourceOutputDirectory != null) ? resourceOutputDirectory : outputDirectory;
      return new TemplateAotRequest(
          sourceDirectories,
          outputDirectory,
          resOut,
          includePatterns,
          excludePatterns,
          encoding,
          packagePrefix,
          failOnWarning,
          incremental,
          stateFile,
          contracts,
          generateTypedFacades,
          generatedSourcesDirectory,
          classLoader,
          typeCheckingMode);
    }
  }

  static void validatePackagePrefix(String packagePrefix) {
    if (packagePrefix == null) {
      throw new IllegalArgumentException("packagePrefix must not be null");
    }
    if (packagePrefix.isEmpty()) {
      return;
    }
    if (packagePrefix.isBlank()) {
      throw new IllegalArgumentException("packagePrefix must not be blank");
    }
    if (packagePrefix.contains("..")
        || packagePrefix.contains("/")
        || packagePrefix.contains("\\")) {
      throw new IllegalArgumentException(
          "Invalid packagePrefix contains illegal characters or path traversal: " + packagePrefix);
    }
    for (String segment : packagePrefix.split("\\.", -1)) {
      if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
        throw new IllegalArgumentException("Invalid packagePrefix identifier segment: " + segment);
      }
      for (int i = 1; i < segment.length(); i++) {
        if (!Character.isJavaIdentifierPart(segment.charAt(i))) {
          throw new IllegalArgumentException(
              "Invalid packagePrefix identifier segment: " + segment);
        }
      }
    }
  }
}
