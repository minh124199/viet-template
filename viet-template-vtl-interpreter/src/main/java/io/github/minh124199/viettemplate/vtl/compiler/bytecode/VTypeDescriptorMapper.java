package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;

/** Maps {@link VType} instances to JVM type descriptors and internal names. */
public final class VTypeDescriptorMapper {

  private VTypeDescriptorMapper() {}

  /** Converts a semantic {@link VType} to its standard JVM type descriptor. */
  public static String toDescriptor(VType type) {
    if (type == null
        || type instanceof VType.DynamicType
        || type instanceof VType.ErrorType
        || type instanceof VType.NullType) {
      return "Ljava/lang/Object;";
    }
    if (type instanceof VType.PrimitiveType pt) {
      return toPrimitiveDescriptor(pt.kind());
    }
    if (type instanceof VType.ClassType ct) {
      return "L" + ct.className().replace('.', '/') + ";";
    }
    if (type instanceof VType.ArrayType at) {
      return "[" + toDescriptor(at.componentType());
    }
    if (type instanceof VType.UnionType) {
      return "Ljava/lang/Object;";
    }
    return "Ljava/lang/Object;";
  }

  /** Maps {@link PrimitiveKind} to a JVM single-character primitive descriptor. */
  public static String toPrimitiveDescriptor(PrimitiveKind kind) {
    return switch (kind) {
      case BOOLEAN -> "Z";
      case BYTE -> "B";
      case SHORT -> "S";
      case INT -> "I";
      case LONG -> "J";
      case FLOAT -> "F";
      case DOUBLE -> "D";
      case CHAR -> "C";
    };
  }

  /** Converts a dot-separated Java class name to an internal JVM slash-separated name. */
  public static String toInternalName(String className) {
    return className.replace('.', '/');
  }
}
