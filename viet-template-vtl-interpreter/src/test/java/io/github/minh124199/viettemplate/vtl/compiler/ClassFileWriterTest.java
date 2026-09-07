package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.vtl.compiler.bytecode.ClassFileWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileWriterTest {

  @Test
  @DisplayName("Emits valid class implementing Supplier<String> and executes successfully")
  void testClassFileWriterExecution() throws Exception {
    String internalName = "io/github/minh124199/viettemplate/generated/TestSupplier";
    String fqcn = internalName.replace('/', '.');

    ClassFileWriter writer = new ClassFileWriter(internalName, "java/lang/Object");
    writer.addInterface("java/util/function/Supplier");
    writer.setSourceFile("TestSupplier.java");

    // Static constant field
    writer.addField(
        ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC | ClassFileWriter.ACC_FINAL,
        "MESSAGE",
        "Ljava/lang/String;");

    // <init>()
    ClassFileWriter.MethodWriter init =
        writer.addMethod(ClassFileWriter.ACC_PUBLIC, "<init>", "()V");
    init.aload(0);
    init.invokespecial("java/lang/Object", "<init>", "()V");
    init.returnOp();

    // get()Ljava/lang/Object;
    ClassFileWriter.MethodWriter getMethod =
        writer.addMethod(ClassFileWriter.ACC_PUBLIC, "get", "()Ljava/lang/Object;");
    getMethod.setMaxLocals(2);
    getMethod.setMaxStack(2);
    getMethod.ldc("Hello from ClassFileWriter!");
    getMethod.areturn();

    // <clinit>()
    ClassFileWriter.MethodWriter clinit =
        writer.addMethod(ClassFileWriter.ACC_STATIC, "<clinit>", "()V");
    clinit.ldc("Static Message");
    clinit.putstatic(internalName, "MESSAGE", "Ljava/lang/String;");
    clinit.returnOp();

    byte[] bytes = writer.toByteArray();
    assertThat(bytes).isNotNull();
    // Classfile magic CAFEBABE
    assertThat(bytes[0]).isEqualTo((byte) 0xCA);
    assertThat(bytes[1]).isEqualTo((byte) 0xFE);
    assertThat(bytes[2]).isEqualTo((byte) 0xBA);
    assertThat(bytes[3]).isEqualTo((byte) 0xBE);
    // Major version 61 (Java 17)
    assertThat(bytes[6]).isEqualTo((byte) 0x00);
    assertThat(bytes[7]).isEqualTo((byte) 0x3D);

    // Load and execute
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<?> clazz = loader.defineRawClass(fqcn, bytes);
    assertThat(clazz).isNotNull();

    @SuppressWarnings("unchecked")
    Supplier<String> supplier = (Supplier<String>) clazz.getDeclaredConstructor().newInstance();
    assertThat(supplier.get()).isEqualTo("Hello from ClassFileWriter!");

    // Inspect static field
    Object staticMsg = clazz.getDeclaredField("MESSAGE").get(null);
    assertThat(staticMsg).isEqualTo("Static Message");
  }

  @Test
  @DisplayName("Emits StackMapTable frames accepted by JVM verifier on branching logic")
  void testBranchingWithStackMapTable() throws Exception {
    String internalName = "io/github/minh124199/viettemplate/generated/BranchTest";
    String fqcn = internalName.replace('/', '.');

    ClassFileWriter writer = new ClassFileWriter(internalName, "java/lang/Object");

    // <init>()
    ClassFileWriter.MethodWriter init =
        writer.addMethod(ClassFileWriter.ACC_PUBLIC, "<init>", "()V");
    init.aload(0);
    init.invokespecial("java/lang/Object", "<init>", "()V");
    init.returnOp();

    // testMethod(I)Ljava/lang/Object;
    ClassFileWriter.MethodWriter m =
        writer.addMethod(ClassFileWriter.ACC_PUBLIC, "compute", "(I)Ljava/lang/Object;");
    m.setMaxLocals(4);
    m.setMaxStack(4);

    // Initialize local slots for strict split verifier
    m.aconst_null();
    m.astore(2);
    m.aconst_null();
    m.astore(3);

    ClassFileWriter.Label elseLabel = m.newLabel();
    ClassFileWriter.Label endLabel = m.newLabel();

    m.iload(1); // load int arg
    m.ifeq(elseLabel);

    m.ldc("non-zero");
    m.astore(2);
    m.gotoOp(endLabel);

    m.bindLabel(elseLabel);
    m.ldc("zero");
    m.astore(2);

    m.bindLabel(endLabel);
    m.aload(2);
    m.areturn();

    byte[] bytes = writer.toByteArray();
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<?> clazz = loader.defineRawClass(fqcn, bytes);
    Object instance = clazz.getDeclaredConstructor().newInstance();

    var computeMethod = clazz.getDeclaredMethod("compute", int.class);
    assertThat(computeMethod.invoke(instance, 1)).isEqualTo("non-zero");
    assertThat(computeMethod.invoke(instance, 0)).isEqualTo("zero");
  }

  @Test
  @DisplayName("Validates classfile output via OpenJDK javap disassembler")
  void testValidatesClassfileOutput(@TempDir Path tempDir) throws Exception {
    String internalName = "io/github/minh124199/viettemplate/generated/JavapVerification";
    ClassFileWriter writer = new ClassFileWriter(internalName, "java/lang/Object");
    writer.setSourceFile("JavapVerification.vtl");

    ClassFileWriter.MethodWriter init =
        writer.addMethod(ClassFileWriter.ACC_PUBLIC, "<init>", "()V");
    init.aload(0);
    init.invokespecial("java/lang/Object", "<init>", "()V");
    init.returnOp();

    byte[] bytes = writer.toByteArray();
    Path classFile = tempDir.resolve("JavapVerification.class");
    Files.write(classFile, bytes);

    File javapBin = new File("/home/lynguyen/opt/usr/lib/jvm/java-17-openjdk/bin/javap");
    if (!javapBin.exists()) {
      javapBin = new File(System.getProperty("java.home"), "bin/javap");
    }

    if (javapBin.exists()) {
      ProcessBuilder pb =
          new ProcessBuilder(javapBin.getAbsolutePath(), "-verbose", classFile.toString());
      Process process = pb.start();
      String output = new String(process.getInputStream().readAllBytes());
      int exitCode = process.waitFor();

      assertThat(exitCode).isZero();
      assertThat(output).contains("major version: 61");
      assertThat(output).contains("JavapVerification");
    }
  }
}
