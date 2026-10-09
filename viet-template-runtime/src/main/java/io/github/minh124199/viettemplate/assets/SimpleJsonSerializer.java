package io.github.minh124199.viettemplate.assets;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Map;

/**
 * Built-in, lightweight JSON serializer for standalone usage and tests without third-party JSON
 * libraries.
 *
 * <p>Supports {@code null}, {@link Boolean}, {@link Number}, {@link CharSequence}, {@link Map},
 * {@link Iterable}, Java arrays, records, and basic JavaBeans with public getters.
 */
public final class SimpleJsonSerializer implements ClientDataSerializer {

  public static final SimpleJsonSerializer INSTANCE = new SimpleJsonSerializer();

  @Override
  public void serialize(Object value, Appendable target) throws ClientDataSerializationException {
    try {
      writeValue(value, target);
    } catch (IOException e) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002, "JSON serialization I/O failure", e.getMessage(), e);
    }
  }

  private void writeValue(Object value, Appendable out) throws IOException {
    if (value == null) {
      out.append("null");
      return;
    }
    if (value instanceof Boolean b) {
      out.append(b.toString());
      return;
    }
    if (value instanceof Number n) {
      out.append(n.toString());
      return;
    }
    if (value instanceof CharSequence cs) {
      writeJsonString(cs, out);
      return;
    }
    if (value instanceof Map<?, ?> map) {
      writeMap(map, out);
      return;
    }
    if (value instanceof Iterable<?> iter) {
      writeIterable(iter, out);
      return;
    }
    if (value.getClass().isArray()) {
      writeArray(value, out);
      return;
    }
    if (value.getClass().isRecord()) {
      writeRecord(value, out);
      return;
    }
    // Fallback: bean properties
    writeBean(value, out);
  }

  private void writeJsonString(CharSequence cs, Appendable out) throws IOException {
    out.append('"');
    for (int i = 0; i < cs.length(); i++) {
      char c = cs.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private void writeMap(Map<?, ?> map, Appendable out) throws IOException {
    out.append('{');
    boolean first = true;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      String key = entry.getKey() != null ? entry.getKey().toString() : "null";
      writeJsonString(key, out);
      out.append(':');
      writeValue(entry.getValue(), out);
    }
    out.append('}');
  }

  private void writeIterable(Iterable<?> iter, Appendable out) throws IOException {
    out.append('[');
    Iterator<?> it = iter.iterator();
    boolean first = true;
    while (it.hasNext()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeValue(it.next(), out);
    }
    out.append(']');
  }

  private void writeArray(Object array, Appendable out) throws IOException {
    out.append('[');
    int len = java.lang.reflect.Array.getLength(array);
    for (int i = 0; i < len; i++) {
      if (i > 0) {
        out.append(',');
      }
      writeValue(java.lang.reflect.Array.get(array, i), out);
    }
    out.append(']');
  }

  private void writeRecord(Object record, Appendable out) throws IOException {
    out.append('{');
    RecordComponent[] components = record.getClass().getRecordComponents();
    boolean first = true;
    for (RecordComponent comp : components) {
      try {
        Object val = comp.getAccessor().invoke(record);
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJsonString(comp.getName(), out);
        out.append(':');
        writeValue(val, out);
      } catch (ReflectiveOperationException e) {
        throw new IOException("Failed reading record component " + comp.getName(), e);
      }
    }
    out.append('}');
  }

  private void writeBean(Object bean, Appendable out) throws IOException {
    out.append('{');
    boolean first = true;
    for (Method method : bean.getClass().getMethods()) {
      if (method.getParameterCount() != 0) {
        continue;
      }
      String name = method.getName();
      if ("getClass".equals(name)) {
        continue;
      }
      String propName = null;
      if (name.startsWith("get") && name.length() > 3) {
        propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
      } else if (name.startsWith("is") && name.length() > 2) {
        propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
      }
      if (propName != null) {
        try {
          Object val = method.invoke(bean);
          if (!first) {
            out.append(',');
          }
          first = false;
          writeJsonString(propName, out);
          out.append(':');
          writeValue(val, out);
        } catch (ReflectiveOperationException ignored) {
          // Skip unreadable property
        }
      }
    }
    out.append('}');
  }
}
