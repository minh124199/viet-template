package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight, zero-dependency pure Java classfile generator producing JVM Specification SE 17
 * binary classfiles (major 61, minor 0).
 */
public final class ClassFileWriter {

  public static final int ACC_PUBLIC = 0x0001;
  public static final int ACC_PRIVATE = 0x0002;
  public static final int ACC_STATIC = 0x0008;
  public static final int ACC_FINAL = 0x0010;
  public static final int ACC_SUPER = 0x0020;

  private final ConstantPool cp = new ConstantPool();
  private final int thisClassIndex;
  private final int superClassIndex;
  private final List<Integer> interfaceIndices = new ArrayList<>();
  private final List<FieldEntry> fields = new ArrayList<>();
  private final List<MethodEntry> methods = new ArrayList<>();
  private String sourceFileName;

  public ClassFileWriter(String internalClassName, String internalSuperClassName) {
    Objects.requireNonNull(internalClassName, "internalClassName must not be null");
    Objects.requireNonNull(internalSuperClassName, "internalSuperClassName must not be null");
    this.thisClassIndex = cp.addClass(internalClassName);
    this.superClassIndex = cp.addClass(internalSuperClassName);
  }

  public void addInterface(String internalInterfaceName) {
    Objects.requireNonNull(internalInterfaceName, "internalInterfaceName must not be null");
    interfaceIndices.add(cp.addClass(internalInterfaceName));
  }

  public void setSourceFile(String fileName) {
    this.sourceFileName = fileName;
  }

  public void addField(int accessFlags, String name, String descriptor) {
    int nameIndex = cp.addUtf8(name);
    int descIndex = cp.addUtf8(descriptor);
    fields.add(new FieldEntry(accessFlags, nameIndex, descIndex));
  }

  public MethodWriter addMethod(int accessFlags, String name, String descriptor) {
    int nameIndex = cp.addUtf8(name);
    int descIndex = cp.addUtf8(descriptor);
    MethodWriter writer = new MethodWriter(this, accessFlags, nameIndex, descIndex, descriptor);
    methods.add(new MethodEntry(accessFlags, nameIndex, descIndex, writer));
    return writer;
  }

  public byte[] toByteArray() {
    try {
      // 0. Pre-build all method code and resolve all attribute names in the constant pool FIRST
      if (sourceFileName != null) {
        cp.addUtf8("SourceFile");
        cp.addUtf8(sourceFileName);
      }
      int codeAttrIndex = cp.addUtf8("Code");
      List<byte[]> methodCodeBytes = new ArrayList<>(methods.size());
      for (MethodEntry method : methods) {
        methodCodeBytes.add(method.writer.buildCode(cp));
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);

      // 1. Magic
      out.writeInt(0xCAFEBABE);
      // 2. Minor, Major (Java 17 = 61)
      out.writeShort(0);
      out.writeShort(61);

      // 3. Constant Pool (now completely finalized!)
      cp.write(out);

      // 4. Access flags
      out.writeShort(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);

      // 5. This class & Super class
      out.writeShort(thisClassIndex);
      out.writeShort(superClassIndex);

      // 6. Interfaces
      out.writeShort(interfaceIndices.size());
      for (int ifaceIdx : interfaceIndices) {
        out.writeShort(ifaceIdx);
      }

      // 7. Fields
      out.writeShort(fields.size());
      for (FieldEntry field : fields) {
        out.writeShort(field.accessFlags);
        out.writeShort(field.nameIndex);
        out.writeShort(field.descriptorIndex);
        out.writeShort(0); // attributes_count
      }

      // 8. Methods
      out.writeShort(methods.size());
      for (int i = 0; i < methods.size(); i++) {
        MethodEntry method = methods.get(i);
        byte[] codeBytes = methodCodeBytes.get(i);
        out.writeShort(method.accessFlags);
        out.writeShort(method.nameIndex);
        out.writeShort(method.descriptorIndex);

        // Code attribute
        out.writeShort(1); // 1 attribute: Code
        out.writeShort(codeAttrIndex);
        out.writeInt(codeBytes.length);
        out.write(codeBytes);
      }

      // 9. Class attributes (SourceFile)
      if (sourceFileName != null) {
        out.writeShort(1); // 1 attribute: SourceFile
        out.writeShort(cp.addUtf8("SourceFile"));
        out.writeInt(2); // attribute_length
        out.writeShort(cp.addUtf8(sourceFileName));
      } else {
        out.writeShort(0);
      }

      out.flush();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write bytecode", e);
    }
  }

  // --- Constant Pool Implementation ---

  public static final class ConstantPool {
    private final List<CpEntry> entries = new ArrayList<>();
    private final Map<String, Integer> utf8Map = new HashMap<>();
    private final Map<String, Integer> classMap = new HashMap<>();
    private final Map<String, Integer> stringMap = new HashMap<>();
    private final Map<String, Integer> nameAndTypeMap = new HashMap<>();
    private final Map<String, Integer> memberRefMap = new HashMap<>();
    private final Map<Integer, Integer> intMap = new HashMap<>();
    private final Map<Long, Integer> longMap = new HashMap<>();
    private final Map<Double, Integer> doubleMap = new HashMap<>();

    public ConstantPool() {
      // Slot 0 is unused in JVM constant pool
      entries.add(null);
    }

    public int addUtf8(String str) {
      return utf8Map.computeIfAbsent(
          str,
          s -> {
            int idx = entries.size();
            entries.add(new Utf8Entry(s));
            return idx;
          });
    }

    public int addClass(String internalName) {
      return classMap.computeIfAbsent(
          internalName,
          name -> {
            int nameIdx = addUtf8(name);
            int idx = entries.size();
            entries.add(new ClassEntry(nameIdx));
            return idx;
          });
    }

    public int addString(String str) {
      return stringMap.computeIfAbsent(
          str,
          s -> {
            int utf8Idx = addUtf8(s);
            int idx = entries.size();
            entries.add(new StringEntry(utf8Idx));
            return idx;
          });
    }

    public int addInteger(int val) {
      return intMap.computeIfAbsent(
          val,
          v -> {
            int idx = entries.size();
            entries.add(new IntegerEntry(v));
            return idx;
          });
    }

    public int addLong(long val) {
      return longMap.computeIfAbsent(
          val,
          v -> {
            int idx = entries.size();
            entries.add(new LongEntry(v));
            entries.add(null); // Long takes two constant pool slots
            return idx;
          });
    }

    public int addDouble(double val) {
      return doubleMap.computeIfAbsent(
          val,
          v -> {
            int idx = entries.size();
            entries.add(new DoubleEntry(v));
            entries.add(null); // Double takes two constant pool slots
            return idx;
          });
    }

    public int addNameAndType(String name, String descriptor) {
      String key = name + ":" + descriptor;
      return nameAndTypeMap.computeIfAbsent(
          key,
          k -> {
            int nIdx = addUtf8(name);
            int dIdx = addUtf8(descriptor);
            int idx = entries.size();
            entries.add(new NameAndTypeEntry(nIdx, dIdx));
            return idx;
          });
    }

    public int addFieldref(String classInternalName, String name, String descriptor) {
      String key = "F:" + classInternalName + "." + name + ":" + descriptor;
      return memberRefMap.computeIfAbsent(
          key,
          k -> {
            int cIdx = addClass(classInternalName);
            int ntIdx = addNameAndType(name, descriptor);
            int idx = entries.size();
            entries.add(new MemberRefEntry(9, cIdx, ntIdx)); // tag 9 = Fieldref
            return idx;
          });
    }

    public int addMethodref(String classInternalName, String name, String descriptor) {
      String key = "M:" + classInternalName + "." + name + ":" + descriptor;
      return memberRefMap.computeIfAbsent(
          key,
          k -> {
            int cIdx = addClass(classInternalName);
            int ntIdx = addNameAndType(name, descriptor);
            int idx = entries.size();
            entries.add(new MemberRefEntry(10, cIdx, ntIdx)); // tag 10 = Methodref
            return idx;
          });
    }

    public int addInterfaceMethodref(String classInternalName, String name, String descriptor) {
      String key = "I:" + classInternalName + "." + name + ":" + descriptor;
      return memberRefMap.computeIfAbsent(
          key,
          k -> {
            int cIdx = addClass(classInternalName);
            int ntIdx = addNameAndType(name, descriptor);
            int idx = entries.size();
            entries.add(new MemberRefEntry(11, cIdx, ntIdx)); // tag 11 = InterfaceMethodref
            return idx;
          });
    }

    public void write(DataOutputStream out) throws IOException {
      out.writeShort(entries.size());
      for (int i = 1; i < entries.size(); i++) {
        CpEntry entry = entries.get(i);
        if (entry != null) {
          entry.write(out);
        }
      }
    }
  }

  private interface CpEntry {
    void write(DataOutputStream out) throws IOException;
  }

  private record Utf8Entry(String value) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(1); // CONSTANT_Utf8
      out.writeUTF(value);
    }
  }

  private record ClassEntry(int nameIndex) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(7); // CONSTANT_Class
      out.writeShort(nameIndex);
    }
  }

  private record StringEntry(int stringIndex) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(8); // CONSTANT_String
      out.writeShort(stringIndex);
    }
  }

  private record IntegerEntry(int value) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(3); // CONSTANT_Integer
      out.writeInt(value);
    }
  }

  private record LongEntry(long value) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(5); // CONSTANT_Long
      out.writeLong(value);
    }
  }

  private record DoubleEntry(double value) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(6); // CONSTANT_Double
      out.writeDouble(value);
    }
  }

  private record NameAndTypeEntry(int nameIndex, int descriptorIndex) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(12); // CONSTANT_NameAndType
      out.writeShort(nameIndex);
      out.writeShort(descriptorIndex);
    }
  }

  private record MemberRefEntry(int tag, int classIndex, int nameAndTypeIndex) implements CpEntry {
    @Override
    public void write(DataOutputStream out) throws IOException {
      out.writeByte(tag);
      out.writeShort(classIndex);
      out.writeShort(nameAndTypeIndex);
    }
  }

  private record FieldEntry(int accessFlags, int nameIndex, int descriptorIndex) {}

  private record MethodEntry(
      int accessFlags, int nameIndex, int descriptorIndex, MethodWriter writer) {}

  // --- Method / Bytecode Writer ---

  public static final class Label {
    int targetPc = -1;
  }

  private record LineEntry(int pc, int line) {}

  private record BranchPatch(int instructionPc, int branchOffsetPc, Label target) {}

  public sealed interface VerificationType {
    int tag();

    record Simple(int tag) implements VerificationType {}

    record ObjectType(int classIndex) implements VerificationType {
      @Override
      public int tag() {
        return 7;
      }
    }
  }

  public static final class MethodWriter {
    private final ClassFileWriter classFile;
    private final int accessFlags;
    private final int nameIndex;
    private final int descriptorIndex;
    private final ByteArrayOutputStream code = new ByteArrayOutputStream();
    private final List<BranchPatch> branchPatches = new ArrayList<>();
    private final List<LineEntry> lineEntries = new ArrayList<>();
    private final Set<Integer> branchTargetPcs = new HashSet<>();
    private final List<VerificationType> localTypes = new ArrayList<>();
    private int maxStack = 8;
    private int maxLocals = 4;

    public MethodWriter(
        ClassFileWriter classFile,
        int accessFlags,
        int nameIndex,
        int descriptorIndex,
        String descriptor) {
      this.classFile = classFile;
      this.accessFlags = accessFlags;
      this.nameIndex = nameIndex;
      this.descriptorIndex = descriptorIndex;

      initParameters(descriptor);
    }

    private void initParameters(String descriptor) {
      if ((accessFlags & ACC_STATIC) == 0) {
        localTypes.add(new VerificationType.ObjectType(classFile.thisClassIndex));
      }
      int p = 1;
      while (p < descriptor.length() && descriptor.charAt(p) != ')') {
        char c = descriptor.charAt(p);
        if (c == 'I' || c == 'Z' || c == 'B' || c == 'C' || c == 'S') {
          localTypes.add(new VerificationType.Simple(1));
          p++;
        } else if (c == 'F') {
          localTypes.add(new VerificationType.Simple(2));
          p++;
        } else if (c == 'J') {
          localTypes.add(new VerificationType.Simple(4));
          localTypes.add(new VerificationType.Simple(0));
          p++;
        } else if (c == 'D') {
          localTypes.add(new VerificationType.Simple(3));
          localTypes.add(new VerificationType.Simple(0));
          p++;
        } else if (c == 'L') {
          int semi = descriptor.indexOf(';', p);
          String className = descriptor.substring(p + 1, semi);
          localTypes.add(new VerificationType.ObjectType(classFile.cp.addClass(className)));
          p = semi + 1;
        } else if (c == '[') {
          int start = p;
          while (descriptor.charAt(p) == '[') p++;
          if (descriptor.charAt(p) == 'L') {
            p = descriptor.indexOf(';', p) + 1;
          } else {
            p++;
          }
          String arrayDesc = descriptor.substring(start, p);
          localTypes.add(new VerificationType.ObjectType(classFile.cp.addClass(arrayDesc)));
        } else {
          p++;
        }
      }
    }

    public void setLocalType(int slot, VerificationType type) {
      while (localTypes.size() <= slot) {
        localTypes.add(new VerificationType.Simple(0));
      }
      localTypes.set(slot, type);
    }

    public void setMaxStack(int maxStack) {
      this.maxStack = Math.max(this.maxStack, maxStack);
    }

    public void setMaxLocals(int maxLocals) {
      this.maxLocals = Math.max(this.maxLocals, maxLocals);
    }

    public Label newLabel() {
      return new Label();
    }

    public void bindLabel(Label label) {
      Objects.requireNonNull(label, "label must not be null");
      label.targetPc = code.size();
      branchTargetPcs.add(label.targetPc);
    }

    public void addLineNumber(int lineNumber) {
      if (lineNumber > 0) {
        lineEntries.add(new LineEntry(code.size(), lineNumber));
      }
    }

    public int currentPc() {
      return code.size();
    }

    // --- Bytecode Instructions ---

    public void aload(int slot) {
      setMaxLocals(slot + 1);
      if (slot >= 0 && slot <= 3) {
        code.write(0x2A + slot); // aload_0 .. aload_3
      } else {
        code.write(0x19); // aload
        code.write(slot);
      }
    }

    public void astore(int slot) {
      setMaxLocals(slot + 1);
      setLocalType(slot, new VerificationType.ObjectType(classFile.cp.addClass("java/lang/Object")));
      if (slot >= 0 && slot <= 3) {
        code.write(0x4B + slot); // astore_0 .. astore_3
      } else {
        code.write(0x3A); // astore
        code.write(slot);
      }
    }

    public void iload(int slot) {
      setMaxLocals(slot + 1);
      if (slot >= 0 && slot <= 3) {
        code.write(0x1A + slot); // iload_0 .. iload_3
      } else {
        code.write(0x15); // iload
        code.write(slot);
      }
    }

    public void istore(int slot) {
      setMaxLocals(slot + 1);
      setLocalType(slot, new VerificationType.Simple(1));
      if (slot >= 0 && slot <= 3) {
        code.write(0x3B + slot); // istore_0 .. istore_3
      } else {
        code.write(0x36); // istore
        code.write(slot);
      }
    }

    public void lload(int slot) {
      setMaxLocals(slot + 2);
      if (slot >= 0 && slot <= 3) {
        code.write(0x1E + slot); // lload_0 .. lload_3
      } else {
        code.write(0x16); // lload
        code.write(slot);
      }
    }

    public void lstore(int slot) {
      setMaxLocals(slot + 2);
      setLocalType(slot, new VerificationType.Simple(4));
      setLocalType(slot + 1, new VerificationType.Simple(0));
      if (slot >= 0 && slot <= 3) {
        code.write(0x3F + slot); // lstore_0 .. lstore_3
      } else {
        code.write(0x37); // lstore
        code.write(slot);
      }
    }

    public void dload(int slot) {
      setMaxLocals(slot + 2);
      if (slot >= 0 && slot <= 3) {
        code.write(0x26 + slot); // dload_0 .. dload_3
      } else {
        code.write(0x18); // dload
        code.write(slot);
      }
    }

    public void dstore(int slot) {
      setMaxLocals(slot + 2);
      setLocalType(slot, new VerificationType.Simple(3));
      setLocalType(slot + 1, new VerificationType.Simple(0));
      if (slot >= 0 && slot <= 3) {
        code.write(0x47 + slot); // dstore_0 .. dstore_3
      } else {
        code.write(0x39); // dstore
        code.write(slot);
      }
    }

    public void iconst(int val) {
      if (val == -1) {
        code.write(0x02); // iconst_m1
      } else if (val >= 0 && val <= 5) {
        code.write(0x03 + val); // iconst_0 .. iconst_5
      } else if (val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
        code.write(0x10); // bipush
        code.write(val & 0xFF);
      } else if (val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
        code.write(0x11); // sipush
        code.write((val >> 8) & 0xFF);
        code.write(val & 0xFF);
      } else {
        ldcInt(val);
      }
    }

    public void lconst(long val) {
      if (val == 0L) {
        code.write(0x09); // lconst_0
      } else if (val == 1L) {
        code.write(0x0A); // lconst_1
      } else {
        int idx = classFile.cp.addLong(val);
        code.write(0x14); // ldc2_w
        code.write((idx >> 8) & 0xFF);
        code.write(idx & 0xFF);
      }
    }

    public void dconst(double val) {
      if (val == 0.0) {
        code.write(0x0E); // dconst_0
      } else if (val == 1.0) {
        code.write(0x0F); // dconst_1
      } else {
        int idx = classFile.cp.addDouble(val);
        code.write(0x14); // ldc2_w
        code.write((idx >> 8) & 0xFF);
        code.write(idx & 0xFF);
      }
    }

    public void aconst_null() {
      code.write(0x01); // aconst_null
    }

    public void ldc(String str) {
      int idx = classFile.cp.addString(str);
      emitLdc(idx);
    }

    private void ldcInt(int val) {
      int idx = classFile.cp.addInteger(val);
      emitLdc(idx);
    }

    public void ldcClass(String internalName) {
      int idx = classFile.cp.addClass(internalName);
      emitLdc(idx);
    }

    private void emitLdc(int idx) {
      if (idx <= 255) {
        code.write(0x12); // ldc
        code.write(idx);
      } else {
        code.write(0x13); // ldc_w
        code.write((idx >> 8) & 0xFF);
        code.write(idx & 0xFF);
      }
    }

    public void pop() {
      code.write(0x57); // pop
    }

    public void dup() {
      code.write(0x59); // dup
    }

    public void checkcast(String internalName) {
      int idx = classFile.cp.addClass(internalName);
      code.write(0xC0); // checkcast
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void instanceofOp(String internalName) {
      int idx = classFile.cp.addClass(internalName);
      code.write(0xC1); // instanceof
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void invokevirtual(String owner, String name, String desc) {
      int idx = classFile.cp.addMethodref(owner, name, desc);
      code.write(0xB6); // invokevirtual
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void invokespecial(String owner, String name, String desc) {
      int idx = classFile.cp.addMethodref(owner, name, desc);
      code.write(0xB7); // invokespecial
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void invokestatic(String owner, String name, String desc) {
      int idx = classFile.cp.addMethodref(owner, name, desc);
      code.write(0xB8); // invokestatic
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void invokeinterface(String owner, String name, String desc, int argSlots) {
      int idx = classFile.cp.addInterfaceMethodref(owner, name, desc);
      code.write(0xB9); // invokeinterface
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
      code.write(argSlots);
      code.write(0); // 4th byte is always 0
    }

    public void getstatic(String owner, String name, String desc) {
      int idx = classFile.cp.addFieldref(owner, name, desc);
      code.write(0xB2); // getstatic
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void putstatic(String owner, String name, String desc) {
      int idx = classFile.cp.addFieldref(owner, name, desc);
      code.write(0xB3); // putstatic
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void getfield(String owner, String name, String desc) {
      int idx = classFile.cp.addFieldref(owner, name, desc);
      code.write(0xB4); // getfield
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void anewarray(String internalClassName) {
      int idx = classFile.cp.addClass(internalClassName);
      code.write(0xBD); // anewarray
      code.write((idx >> 8) & 0xFF);
      code.write(idx & 0xFF);
    }

    public void aastore() {
      code.write(0x53); // aastore
    }

    public void aaload() {
      code.write(0x32); // aaload
    }

    public void swap() {
      code.write(0x5F); // swap
    }

    public void returnOp() {
      code.write(0xB1); // return
    }

    public void areturn() {
      code.write(0xB0); // areturn
    }

    public void ireturn() {
      code.write(0xAC); // ireturn
    }

    public void ifeq(Label target) {
      emitBranch(0x99, target); // ifeq
    }

    public void ifne(Label target) {
      emitBranch(0x9A, target); // ifne
    }

    public void if_acmpeq(Label target) {
      emitBranch(0xA5, target); // if_acmpeq
    }

    public void if_acmpne(Label target) {
      emitBranch(0xA6, target); // if_acmpne
    }

    public void ifnull(Label target) {
      emitBranch(0xC6, target); // ifnull
    }

    public void ifnonnull(Label target) {
      emitBranch(0xC7, target); // ifnonnull
    }

    public void gotoOp(Label target) {
      emitBranch(0xA7, target); // goto
    }

    private void emitBranch(int opcode, Label target) {
      int instrPc = code.size();
      code.write(opcode);
      int offsetPc = code.size();
      code.write(0); // placeholder
      code.write(0);
      branchPatches.add(new BranchPatch(instrPc, offsetPc, target));
    }

    byte[] buildCode(ConstantPool cp) throws IOException {
      byte[] bytecode = code.toByteArray();

      // 1. Patch branch targets
      Set<Integer> validTargets = new HashSet<>();
      for (BranchPatch patch : branchPatches) {
        if (patch.target.targetPc < 0) {
          throw new IllegalStateException("Unbound branch target label");
        }
        int offset = patch.target.targetPc - patch.instructionPc;
        bytecode[patch.branchOffsetPc] = (byte) ((offset >> 8) & 0xFF);
        bytecode[patch.branchOffsetPc + 1] = (byte) (offset & 0xFF);
        validTargets.add(patch.target.targetPc);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);

      out.writeShort(maxStack);
      out.writeShort(maxLocals);
      out.writeInt(bytecode.length);
      out.write(bytecode);

      // Exception table: 0 entries
      out.writeShort(0);

      // Code attributes: LineNumberTable + StackMapTable
      int attrCount = 0;
      if (!lineEntries.isEmpty()) {
        attrCount++;
      }
      if (!validTargets.isEmpty()) {
        attrCount++;
      }
      out.writeShort(attrCount);

      // LineNumberTable
      if (!lineEntries.isEmpty()) {
        out.writeShort(cp.addUtf8("LineNumberTable"));
        out.writeInt(2 + 4 * lineEntries.size());
        out.writeShort(lineEntries.size());
        for (LineEntry le : lineEntries) {
          out.writeShort(le.pc);
          out.writeShort(le.line);
        }
      }

      // StackMapTable
      if (!validTargets.isEmpty()) {
        List<Integer> sortedTargets = new ArrayList<>(validTargets);
        Collections.sort(sortedTargets);

        ByteArrayOutputStream smtBaos = new ByteArrayOutputStream();
        DataOutputStream smtOut = new DataOutputStream(smtBaos);

        smtOut.writeShort(sortedTargets.size());
        int previousPc = -1;

        for (int targetPc : sortedTargets) {
          int offsetDelta = (previousPc == -1) ? targetPc : (targetPc - previousPc - 1);
          // full_frame: tag 255
          smtOut.writeByte(255);
          smtOut.writeShort(offsetDelta);

          // Find number of active locals
          int numLocals = localTypes.size();
          while (numLocals > 0 && localTypes.get(numLocals - 1).tag() == 0) {
            numLocals--;
          }
          smtOut.writeShort(numLocals);
          for (int i = 0; i < numLocals; i++) {
            VerificationType vt = localTypes.get(i);
            smtOut.writeByte(vt.tag());
            if (vt instanceof VerificationType.ObjectType obj) {
              smtOut.writeShort(obj.classIndex());
            }
          }
          // 0 stack items
          smtOut.writeShort(0);

          previousPc = targetPc;
        }

        smtOut.flush();
        byte[] smtBytes = smtBaos.toByteArray();

        out.writeShort(cp.addUtf8("StackMapTable"));
        out.writeInt(smtBytes.length);
        out.write(smtBytes);
      }

      out.flush();
      return baos.toByteArray();
    }
  }
}
