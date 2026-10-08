package io.github.minh124199.viettemplate.lsp;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Parser transforming Java source files into indexed {@link ParsedJavaSourceFile} instances using
 * the JDK Compiler Tree API.
 *
 * <p>Identifies exact identifier declaration bounds across classes, records, interfaces, enums,
 * record components, methods, and fields without relying on fragile regex heuristics.
 */
final class JavaSourceDeclarationParser {

  private JavaSourceDeclarationParser() {}

  public static ParsedJavaSourceFile parse(Path path) {
    Objects.requireNonNull(path, "path must not be null");
    if (!Files.isRegularFile(path)) {
      return ParsedJavaSourceFile.empty(path, 0L, 0L);
    }

    String content;
    long lastModified;
    long fileSize;
    try {
      content = Files.readString(path, StandardCharsets.UTF_8);
      lastModified = Files.getLastModifiedTime(path).toMillis();
      fileSize = Files.size(path);
    } catch (IOException e) {
      return ParsedJavaSourceFile.empty(path, 0L, 0L);
    }

    return parseSourceText(path, content, lastModified, fileSize);
  }

  static ParsedJavaSourceFile parseSourceText(
      Path path, String content, long lastModified, long fileSize) {
    if (content == null || content.isEmpty()) {
      return ParsedJavaSourceFile.empty(path, lastModified, fileSize);
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      // Graceful fallback when running in a JRE environment missing jdk.compiler
      return ParsedJavaSourceFile.empty(path, lastModified, fileSize);
    }

    int[] lineStarts = computeLineStarts(content);
    Map<String, JavaSourceDeclaration> typeDecls = new LinkedHashMap<>();
    Map<String, List<JavaSourceDeclaration>> memberDecls = new LinkedHashMap<>();

    try {
      URI uri = path != null ? path.toUri() : URI.create("string:///Source.java");
      JavaFileObject fileObject =
          new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
              return content;
            }
          };

      JavacTask task =
          (JavacTask) compiler.getTask(null, null, null, List.of(), null, List.of(fileObject));
      Iterable<? extends CompilationUnitTree> compilationUnits = task.parse();
      Trees trees = Trees.instance(task);
      SourcePositions positions = trees.getSourcePositions();

      for (CompilationUnitTree cu : compilationUnits) {
        String pkgName = cu.getPackage() != null ? cu.getPackage().getPackageName().toString() : "";
        for (Tree typeDecl : cu.getTypeDecls()) {
          if (typeDecl instanceof ClassTree ct) {
            processClassTree(
                cu, positions, ct, pkgName, "", "", lineStarts, content, typeDecls, memberDecls);
          }
        }
      }
    } catch (IOException | RuntimeException ignored) {
      // Graceful degradation on syntax errors, compiler task failures, or unsupported constructs
      return ParsedJavaSourceFile.empty(path, lastModified, fileSize);
    }

    return new ParsedJavaSourceFile(path, lastModified, fileSize, typeDecls, memberDecls);
  }

  private static void processClassTree(
      CompilationUnitTree cu,
      SourcePositions positions,
      ClassTree ct,
      String pkgName,
      String enclosingBinary,
      String enclosingCanonical,
      int[] lineStarts,
      String content,
      Map<String, JavaSourceDeclaration> typeDecls,
      Map<String, List<JavaSourceDeclaration>> memberDecls) {
    String simpleName = ct.getSimpleName().toString();
    if (simpleName.isEmpty()) {
      // Anonymous class
      return;
    }

    String binaryName;
    String canonicalName;
    if (enclosingBinary.isEmpty()) {
      binaryName = pkgName.isEmpty() ? simpleName : pkgName + "." + simpleName;
      canonicalName = binaryName;
    } else {
      binaryName = enclosingBinary + "$" + simpleName;
      canonicalName = enclosingCanonical + "." + simpleName;
    }

    int cStart = (int) positions.getStartPosition(cu, ct);
    int cEnd = (int) positions.getEndPosition(cu, ct);
    int cId = findIdentifier(content, cStart, cEnd, simpleName);
    Range cDeclRange = createRange(lineStarts, content, cStart, cEnd);
    Range cIdRange =
        cId != -1 ? createRange(lineStarts, content, cId, cId + simpleName.length()) : cDeclRange;

    JavaSourceDeclaration typeDeclaration =
        new JavaSourceDeclaration(
            cIdRange, cDeclRange, simpleName, JavaSourceSymbolKey.Kind.TYPE, 0);
    typeDecls.put(binaryName, typeDeclaration);
    typeDecls.put(canonicalName, typeDeclaration);

    List<JavaSourceDeclaration> members = new ArrayList<>();
    for (Tree member : ct.getMembers()) {
      if (member instanceof ClassTree innerClass) {
        processClassTree(
            cu,
            positions,
            innerClass,
            pkgName,
            binaryName,
            canonicalName,
            lineStarts,
            content,
            typeDecls,
            memberDecls);
      } else if (member instanceof VariableTree vt) {
        String vName = vt.getName().toString();
        int vStart = (int) positions.getStartPosition(cu, vt);
        int vEnd = (int) positions.getEndPosition(cu, vt);
        int typeEnd =
            vt.getType() != null ? (int) positions.getEndPosition(cu, vt.getType()) : vStart;
        int vId = findIdentifier(content, Math.max(vStart, typeEnd), vEnd, vName);
        if (vId == -1) {
          vId = findIdentifier(content, vStart, vEnd, vName);
        }

        Range vDeclRange = createRange(lineStarts, content, vStart, vEnd);
        Range vIdRange =
            vId != -1 ? createRange(lineStarts, content, vId, vId + vName.length()) : vDeclRange;

        boolean isRecord = ct.getKind() == Tree.Kind.RECORD;
        boolean isStatic =
            vt.getModifiers() != null && vt.getModifiers().getFlags().contains(Modifier.STATIC);
        JavaSourceSymbolKey.Kind vKind =
            (isRecord && !isStatic)
                ? JavaSourceSymbolKey.Kind.RECORD_COMPONENT
                : JavaSourceSymbolKey.Kind.FIELD;

        JavaSourceDeclaration vDecl =
            new JavaSourceDeclaration(vIdRange, vDeclRange, vName, vKind, 0);
        members.add(vDecl);
      } else if (member instanceof MethodTree mt) {
        String mName = mt.getName().toString();
        if ("<init>".equals(mName) || "<clinit>".equals(mName)) {
          continue;
        }

        int paramCount = mt.getParameters().size();
        int mStart = (int) positions.getStartPosition(cu, mt);
        int mEnd = (int) positions.getEndPosition(cu, mt);
        int retEnd =
            mt.getReturnType() != null
                ? (int) positions.getEndPosition(cu, mt.getReturnType())
                : mStart;
        int mId = findIdentifier(content, Math.max(mStart, retEnd), mEnd, mName);
        if (mId == -1) {
          mId = findIdentifier(content, mStart, mEnd, mName);
        }

        Range mDeclRange = createRange(lineStarts, content, mStart, mEnd);
        Range mIdRange =
            mId != -1 ? createRange(lineStarts, content, mId, mId + mName.length()) : mDeclRange;

        JavaSourceDeclaration mDecl =
            new JavaSourceDeclaration(
                mIdRange, mDeclRange, mName, JavaSourceSymbolKey.Kind.METHOD, paramCount);
        members.add(mDecl);
      }
    }

    memberDecls.put(binaryName, Collections.unmodifiableList(members));
    memberDecls.put(canonicalName, Collections.unmodifiableList(members));
  }

  static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    int len = text.length();
    for (int i = 0; i < len; i++) {
      char c = text.charAt(i);
      if (c == '\r') {
        if (i + 1 < len && text.charAt(i + 1) == '\n') {
          i++;
        }
        starts.add(i + 1);
      } else if (c == '\n') {
        starts.add(i + 1);
      }
    }
    int[] array = new int[starts.size()];
    for (int i = 0; i < starts.size(); i++) {
      array[i] = starts.get(i);
    }
    return array;
  }

  static Position offsetToPosition(int[] lineStarts, String content, int offset) {
    if (offset <= 0) {
      return Position.of(0, 0);
    }
    if (offset >= content.length()) {
      int lastLine = lineStarts.length - 1;
      int charOffset = content.length() - lineStarts[lastLine];
      return Position.of(lastLine, charOffset);
    }
    int idx = Arrays.binarySearch(lineStarts, offset);
    if (idx >= 0) {
      return Position.of(idx, 0);
    }
    int line = Math.max(0, -idx - 2);
    int charOffset = offset - lineStarts[line];
    return Position.of(line, charOffset);
  }

  static Range createRange(int[] lineStarts, String content, int startOffset, int endOffset) {
    Position start = offsetToPosition(lineStarts, content, Math.max(0, startOffset));
    Position end = offsetToPosition(lineStarts, content, Math.max(startOffset, endOffset));
    return Range.of(start, end);
  }

  static int findIdentifier(String text, int searchStart, int searchEnd, String id) {
    if (searchStart < 0 || searchEnd <= searchStart || searchStart >= text.length()) {
      return -1;
    }
    int limit = Math.min(searchEnd, text.length());
    int i = searchStart;
    while (i <= limit - id.length()) {
      // Skip single-line comment
      if (text.startsWith("//", i)) {
        int eol = text.indexOf('\n', i);
        i = (eol == -1) ? limit : eol + 1;
        continue;
      }
      // Skip block comment
      if (text.startsWith("/*", i)) {
        int end = text.indexOf("*/", i + 2);
        i = (end == -1) ? limit : end + 2;
        continue;
      }
      // Skip string literal
      if (text.charAt(i) == '"') {
        int j = i + 1;
        while (j < limit && text.charAt(j) != '"') {
          if (text.charAt(j) == '\\' && j + 1 < limit) {
            j++;
          }
          j++;
        }
        i = Math.min(limit, j + 1);
        continue;
      }
      // Skip char literal
      if (text.charAt(i) == '\'') {
        int j = i + 1;
        while (j < limit && text.charAt(j) != '\'') {
          if (text.charAt(j) == '\\' && j + 1 < limit) {
            j++;
          }
          j++;
        }
        i = Math.min(limit, j + 1);
        continue;
      }

      if (text.startsWith(id, i)) {
        boolean prevOk = (i == searchStart) || !Character.isJavaIdentifierPart(text.charAt(i - 1));
        int nextIdx = i + id.length();
        boolean nextOk =
            (nextIdx >= text.length()) || !Character.isJavaIdentifierPart(text.charAt(nextIdx));
        if (prevOk && nextOk) {
          return i;
        }
      }
      i++;
    }
    return -1;
  }
}
