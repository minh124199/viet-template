package io.github.minh124199.viettemplate.intellij.runtime;

import com.intellij.openapi.diagnostic.Logger;
import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and validates a Java 21+ runtime for launching the Viet Template Language Server.
 */
public class JavaRuntimeResolver {

  private static final Logger LOG = Logger.getInstance(JavaRuntimeResolver.class);
  private static final int MINIMUM_JAVA_VERSION = 21;
  private static final Pattern VERSION_PATTERN = Pattern.compile("\"?(\\d+)(\\.\\d+)*.*\"?");

  private final VietTemplateSettings settings;

  public JavaRuntimeResolver() {
    this(VietTemplateSettings.getInstance());
  }

  public JavaRuntimeResolver(VietTemplateSettings settings) {
    this.settings = settings;
  }

  /**
   * Resolves the Java executable path, following the precedence:
   * 1. Custom settings javaHome
   * 2. JAVA_HOME environment variable
   * 3. System property java.home
   * 4. System PATH
   *
   * @return the resolved Java executable path
   * @throws IllegalStateException if no valid Java executable could be found
   */
  public Path resolveJavaExecutable() {
    String customJavaHome = (settings != null) ? settings.getJavaHome() : "";
    String envJavaHome = System.getenv("JAVA_HOME");
    String sysJavaHome = System.getProperty("java.home");

    Path home = resolveJavaHome(customJavaHome, envJavaHome, sysJavaHome);
    if (home != null) {
      Path bin = findExecutableInJavaHome(home);
      if (bin != null) {
        return bin;
      }
    }

    // Try PATH lookup
    Path pathExecutable = findExecutableOnPath(System.getenv("PATH"));
    if (pathExecutable != null) {
      return pathExecutable;
    }

    throw new IllegalStateException(
        "Could not find a valid Java executable. Please configure Java Home (JDK 21+) in Viet Template Settings."
    );
  }

  /**
   * Resolves and validates that the Java executable is version 21 or higher.
   *
   * @return validated Java executable path
   * @throws IllegalStateException if Java is not found or is below version 21
   */
  public Path resolveAndValidateJavaExecutable() {
    Path executable = resolveJavaExecutable();
    int version = inspectJavaMajorVersion(executable);
    if (version < MINIMUM_JAVA_VERSION) {
      throw new IllegalStateException(
          "Viet Template Language Server requires Java " + MINIMUM_JAVA_VERSION + " or higher. Detected Java " + version + " at: " + executable
      );
    }
    return executable;
  }

  public static Path resolveJavaHome(String customJavaHome, String envJavaHome, String sysJavaHome) {
    if (customJavaHome != null && !customJavaHome.isBlank()) {
      Path path = Paths.get(customJavaHome.trim());
      if (Files.isDirectory(path)) {
        return path;
      }
    }
    if (envJavaHome != null && !envJavaHome.isBlank()) {
      Path path = Paths.get(envJavaHome.trim());
      if (Files.isDirectory(path)) {
        return path;
      }
    }
    if (sysJavaHome != null && !sysJavaHome.isBlank()) {
      Path path = Paths.get(sysJavaHome.trim());
      if (Files.isDirectory(path)) {
        return path;
      }
    }
    return null;
  }

  public static Path findExecutableInJavaHome(Path javaHome) {
    if (javaHome == null || !Files.isDirectory(javaHome)) return null;

    boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    String exeName = isWindows ? "java.exe" : "java";

    Path bin = javaHome.resolve("bin").resolve(exeName);
    if (Files.isRegularFile(bin) && Files.isExecutable(bin)) {
      return bin.toAbsolutePath().normalize();
    }
    return null;
  }

  public static Path findExecutableOnPath(String pathEnv) {
    if (pathEnv == null || pathEnv.isBlank()) return null;

    boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    String exeName = isWindows ? "java.exe" : "java";

    String[] parts = pathEnv.split(File.pathSeparator);
    for (String part : parts) {
      if (part.isBlank()) continue;
      Path candidate = Paths.get(part.trim()).resolve(exeName);
      if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }
    return null;
  }

  public static int inspectJavaMajorVersion(Path javaExecutable) {
    try {
      Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
          .redirectErrorStream(true)
          .start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Timeout inspecting Java version from: " + javaExecutable);
      }

      return parseMajorVersion(output.toString());
    } catch (Exception e) {
      LOG.warn("Failed to execute java -version for " + javaExecutable, e);
      throw new IllegalStateException("Failed to determine Java version for: " + javaExecutable + ": " + e.getMessage(), e);
    }
  }

  public static int parseMajorVersion(String versionOutput) {
    if (versionOutput == null || versionOutput.isBlank()) {
      return -1;
    }

    for (String line : versionOutput.split("\\r?\\n")) {
      String trimmed = line.trim();
      if (trimmed.contains("version")) {
        int quoteStart = trimmed.indexOf('"');
        if (quoteStart >= 0) {
          int quoteEnd = trimmed.indexOf('"', quoteStart + 1);
          if (quoteEnd > quoteStart) {
            String verStr = trimmed.substring(quoteStart + 1, quoteEnd);
            return extractMajor(verStr);
          }
        }
        // Fallback if no quotes
        Matcher matcher = VERSION_PATTERN.matcher(trimmed);
        if (matcher.find()) {
          return Integer.parseInt(matcher.group(1));
        }
      }
    }

    return -1;
  }

  private static int extractMajor(String verStr) {
    String clean = verStr.trim();
    if (clean.startsWith("1.")) {
      // Legacy 1.8 etc.
      String[] parts = clean.split("\\.");
      if (parts.length > 1) {
        return Integer.parseInt(parts[1]);
      }
    }
    int dotIndex = clean.indexOf('.');
    int dashIndex = clean.indexOf('-');
    int end = clean.length();
    if (dotIndex > 0) end = Math.min(end, dotIndex);
    if (dashIndex > 0) end = Math.min(end, dashIndex);
    return Integer.parseInt(clean.substring(0, end));
  }
}
