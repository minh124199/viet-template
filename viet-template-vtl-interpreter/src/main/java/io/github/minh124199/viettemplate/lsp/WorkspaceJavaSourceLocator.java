package io.github.minh124199.viettemplate.lsp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workspace locator resolving Java source declaration locations across single-module and
 * multi-module Maven and Gradle project structures.
 *
 * <p>Enforces strict path traversal defenses, maintains timestamp-invalidated AST caches, and
 * accurately maps JVM symbol keys to exact source ranges.
 */
final class WorkspaceJavaSourceLocator {

  private static final Set<String> IGNORED_DIRS =
      Set.of(
          ".git",
          ".svn",
          ".hg",
          ".idea",
          ".vscode",
          ".gradle",
          "build",
          "target",
          "node_modules",
          "bin",
          "out",
          ".settings");

  private final Set<Path> sourceRoots = ConcurrentHashMap.newKeySet();
  private final Map<Path, ParsedJavaSourceFile> fileCache = new ConcurrentHashMap<>();
  private volatile Path workspaceRoot = null;

  WorkspaceJavaSourceLocator() {}

  public void setWorkspaceRoot(Path root) {
    if (root == null) {
      return;
    }
    Path norm = root.toAbsolutePath().normalize();
    this.workspaceRoot = norm;
    scanForSourceRoots(norm);
  }

  public Optional<Path> workspaceRoot() {
    return Optional.ofNullable(workspaceRoot);
  }

  public void addSourceRoot(Path sourceRoot) {
    if (sourceRoot == null) {
      return;
    }
    Path norm = sourceRoot.toAbsolutePath().normalize();
    if (Files.isDirectory(norm)) {
      sourceRoots.add(norm);
    }
  }

  public Set<Path> sourceRoots() {
    return Collections.unmodifiableSet(sourceRoots);
  }

  public void onFileChanged(Path path) {
    if (path == null) {
      return;
    }
    fileCache.remove(path.toAbsolutePath().normalize());
  }

  public void onFileDeleted(Path path) {
    if (path == null) {
      return;
    }
    fileCache.remove(path.toAbsolutePath().normalize());
  }

  public void clear() {
    fileCache.clear();
    sourceRoots.clear();
    workspaceRoot = null;
  }

  /** Probes upward from the given template/document path to discover candidate source roots. */
  public void probeSourceRootsFor(Path documentPath) {
    if (documentPath == null) {
      return;
    }
    Path curr = documentPath.toAbsolutePath().normalize();
    if (!Files.isDirectory(curr)) {
      curr = curr.getParent();
    }

    int depth = 0;
    while (curr != null && depth < 12) {
      Path mainJava = curr.resolve("src/main/java");
      if (Files.isDirectory(mainJava)) {
        sourceRoots.add(mainJava.toAbsolutePath().normalize());
      }
      Path testJava = curr.resolve("src/test/java");
      if (Files.isDirectory(testJava)) {
        sourceRoots.add(testJava.toAbsolutePath().normalize());
      }

      // Check if curr is a project root with submodules
      if (workspaceRoot == null
          && (Files.isRegularFile(curr.resolve("pom.xml"))
              || Files.isRegularFile(curr.resolve("settings.gradle"))
              || Files.isRegularFile(curr.resolve("settings.gradle.kts")))) {
        scanForSourceRoots(curr);
      }

      curr = curr.getParent();
      depth++;
    }
  }

  /**
   * Resolves the exact declaration location for the given symbol key if source exists in the
   * workspace.
   */
  public Optional<LocationInfo> locate(JavaSourceSymbolKey key) {
    if (key == null || !isSafeJavaTypeName(key.typeName())) {
      return Optional.empty();
    }

    Optional<Path> sourceFileOpt = findSourceFileForType(key.typeName());
    if (sourceFileOpt.isEmpty()) {
      return Optional.empty();
    }

    Path sourceFile = sourceFileOpt.get();
    ParsedJavaSourceFile parsed = getOrParse(sourceFile);
    String uri = parsed.path().toUri().toString();

    String typeName = key.typeName();
    String altTypeName =
        typeName.contains("$") ? typeName.replace('$', '.') : typeName.replace('.', '$');

    return switch (key.kind()) {
      case TYPE -> locateType(parsed, uri, typeName, altTypeName);
      case RECORD_COMPONENT ->
          locateRecordComponent(parsed, uri, typeName, altTypeName, key.memberName());
      case METHOD ->
          locateMethod(parsed, uri, typeName, altTypeName, key.memberName(), key.parameterCount());
      case FIELD -> locateField(parsed, uri, typeName, altTypeName, key.memberName());
    };
  }

  private Optional<LocationInfo> locateType(
      ParsedJavaSourceFile parsed, String uri, String typeName, String altTypeName) {
    JavaSourceDeclaration decl = parsed.typeDeclarations().get(typeName);
    if (decl == null) {
      decl = parsed.typeDeclarations().get(altTypeName);
    }
    if (decl != null) {
      return Optional.of(LocationInfo.of(uri, decl.identifierRange()));
    }
    return Optional.empty();
  }

  private Optional<LocationInfo> locateRecordComponent(
      ParsedJavaSourceFile parsed,
      String uri,
      String typeName,
      String altTypeName,
      String componentName) {
    List<JavaSourceDeclaration> members = parsed.memberDeclarations().get(typeName);
    if (members == null) {
      members = parsed.memberDeclarations().get(altTypeName);
    }
    if (members == null) {
      return Optional.empty();
    }

    // 1. Explicit accessor method takes precedence: e.g. public String name()
    for (JavaSourceDeclaration m : members) {
      if (m.kind() == JavaSourceSymbolKey.Kind.METHOD
          && m.name().equals(componentName)
          && m.parameterCount() == 0) {
        return Optional.of(LocationInfo.of(uri, m.identifierRange()));
      }
    }

    // 2. Record component in header: e.g. (String name)
    for (JavaSourceDeclaration m : members) {
      if (m.kind() == JavaSourceSymbolKey.Kind.RECORD_COMPONENT && m.name().equals(componentName)) {
        return Optional.of(LocationInfo.of(uri, m.identifierRange()));
      }
    }

    return Optional.empty();
  }

  private Optional<LocationInfo> locateMethod(
      ParsedJavaSourceFile parsed,
      String uri,
      String typeName,
      String altTypeName,
      String methodName,
      int parameterCount) {
    List<JavaSourceDeclaration> members = parsed.memberDeclarations().get(typeName);
    if (members == null) {
      members = parsed.memberDeclarations().get(altTypeName);
    }
    if (members == null) {
      return Optional.empty();
    }

    for (JavaSourceDeclaration m : members) {
      if (m.kind() == JavaSourceSymbolKey.Kind.METHOD
          && m.name().equals(methodName)
          && m.parameterCount() == parameterCount) {
        return Optional.of(LocationInfo.of(uri, m.identifierRange()));
      }
    }

    return Optional.empty();
  }

  private Optional<LocationInfo> locateField(
      ParsedJavaSourceFile parsed,
      String uri,
      String typeName,
      String altTypeName,
      String fieldName) {
    List<JavaSourceDeclaration> members = parsed.memberDeclarations().get(typeName);
    if (members == null) {
      members = parsed.memberDeclarations().get(altTypeName);
    }
    if (members == null) {
      return Optional.empty();
    }

    for (JavaSourceDeclaration m : members) {
      if (m.kind() == JavaSourceSymbolKey.Kind.FIELD && m.name().equals(fieldName)) {
        return Optional.of(LocationInfo.of(uri, m.identifierRange()));
      }
    }

    return Optional.empty();
  }

  Optional<Path> findSourceFileForType(String typeName) {
    if (!isSafeJavaTypeName(typeName)) {
      return Optional.empty();
    }

    // Extract candidate relative paths
    List<String> candidateRels = new ArrayList<>();

    if (typeName.contains("$")) {
      String topFqcn = typeName.substring(0, typeName.indexOf('$'));
      candidateRels.add(topFqcn.replace('.', '/') + ".java");
    } else {
      candidateRels.add(typeName.replace('.', '/') + ".java");
      // Check if nested class using dot notation: e.g. com.example.Outer.Inner ->
      // com/example/Outer.java
      int dot = typeName.lastIndexOf('.');
      while (dot > 0) {
        String prefix = typeName.substring(0, dot);
        candidateRels.add(prefix.replace('.', '/') + ".java");
        dot = prefix.lastIndexOf('.');
      }
    }

    for (Path root : sourceRoots) {
      for (String rel : candidateRels) {
        Path candidate = root.resolve(rel).normalize();
        if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
          return Optional.of(candidate);
        }
      }
    }

    // Fallback: If typeName is a simple unqualified name (e.g. "User")
    if (!typeName.contains(".")) {
      String targetFileName = typeName + ".java";
      for (Path root : sourceRoots) {
        Path direct = root.resolve(targetFileName).normalize();
        if (direct.startsWith(root) && Files.isRegularFile(direct)) {
          return Optional.of(direct);
        }
        Optional<Path> found = findFileByName(root, targetFileName, 6);
        if (found.isPresent()) {
          return found;
        }
      }
    }

    return Optional.empty();
  }

  private Optional<Path> findFileByName(Path dir, String fileName, int maxDepth) {
    if (maxDepth < 0 || !Files.isDirectory(dir)) {
      return Optional.empty();
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        if (Files.isRegularFile(entry) && entry.getFileName().toString().equals(fileName)) {
          return Optional.of(entry.toAbsolutePath().normalize());
        } else if (Files.isDirectory(entry)) {
          String name = entry.getFileName().toString();
          if (!IGNORED_DIRS.contains(name) && !name.startsWith(".")) {
            Optional<Path> sub = findFileByName(entry, fileName, maxDepth - 1);
            if (sub.isPresent()) {
              return sub;
            }
          }
        }
      }
    } catch (IOException ignored) {
    }
    return Optional.empty();
  }

  private ParsedJavaSourceFile getOrParse(Path path) {
    Path norm = path.toAbsolutePath().normalize();
    ParsedJavaSourceFile cached = fileCache.get(norm);
    if (cached != null) {
      try {
        long lastMod = Files.getLastModifiedTime(norm).toMillis();
        long size = Files.size(norm);
        if (cached.lastModified() == lastMod && cached.fileSize() == size) {
          return cached;
        }
      } catch (IOException ignored) {
      }
    }

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(norm);
    fileCache.put(norm, parsed);
    return parsed;
  }

  private void scanForSourceRoots(Path rootDir) {
    if (rootDir == null || !Files.isDirectory(rootDir)) {
      return;
    }
    List<Path> queue = new ArrayList<>();
    queue.add(rootDir);
    int depth = 0;
    while (!queue.isEmpty() && depth < 8) {
      List<Path> nextQueue = new ArrayList<>();
      for (Path dir : queue) {
        Path mainJava = dir.resolve("src/main/java");
        if (Files.isDirectory(mainJava)) {
          sourceRoots.add(mainJava.toAbsolutePath().normalize());
        }
        Path testJava = dir.resolve("src/test/java");
        if (Files.isDirectory(testJava)) {
          sourceRoots.add(testJava.toAbsolutePath().normalize());
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
          for (Path sub : stream) {
            String name = sub.getFileName().toString();
            if (!IGNORED_DIRS.contains(name) && !name.startsWith(".") && !"src".equals(name)) {
              nextQueue.add(sub);
            }
          }
        } catch (IOException ignored) {
        }
      }
      queue = nextQueue;
      depth++;
    }
  }

  static boolean isSafeJavaTypeName(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return false;
    }
    if (typeName.contains("..")
        || typeName.contains("/")
        || typeName.contains("\\")
        || typeName.contains(":")
        || typeName.contains("\0")) {
      return false;
    }
    for (int i = 0; i < typeName.length(); i++) {
      char c = typeName.charAt(i);
      if (!Character.isJavaIdentifierPart(c) && c != '.' && c != '$') {
        return false;
      }
    }
    return true;
  }
}
