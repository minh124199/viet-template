package io.github.minh124199.viettemplate.assets;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Strategy implementation of {@link ClientDataSerializer} adapting any Jackson {@code ObjectMapper}
 * (including Jackson 2.x {@code com.fasterxml.jackson.databind.ObjectMapper} and Jackson 3.x {@code
 * tools.jackson.databind.ObjectMapper}) to serialize Java values into valid script-safe JSON text.
 */
public class JacksonClientDataSerializer implements ClientDataSerializer {

  private final Object mapper;
  private final Method writeValueMethod;

  /**
   * Constructs a serializer adapting the given Jackson object mapper instance.
   *
   * @param mapper the Jackson object mapper (must declare {@code writeValue(Writer, Object)})
   * @throws NullPointerException if {@code mapper} is null
   * @throws IllegalArgumentException if {@code mapper} does not declare a public {@code
   *     writeValue(Writer, Object)} method
   */
  public JacksonClientDataSerializer(Object mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.writeValueMethod = findWriteValueMethod(mapper.getClass());
  }

  @Override
  public void serialize(Object value, Appendable target) throws ClientDataSerializationException {
    Objects.requireNonNull(target, "target Appendable must not be null");
    try {
      Writer writer = (target instanceof Writer w) ? w : new AppendableWriter(target);
      writeValueMethod.invoke(mapper, writer, value);
      writer.flush();
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          "payload",
          "Jackson client data serialization failed: " + cause.getMessage(),
          cause);
    } catch (IllegalAccessException | IOException e) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          "payload",
          "Jackson client data serialization failed: " + e.getMessage(),
          e);
    }
  }

  private static Method findWriteValueMethod(Class<?> mapperClass) {
    try {
      return mapperClass.getMethod("writeValue", Writer.class, Object.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Provided mapper of class "
              + mapperClass.getName()
              + " does not declare a public writeValue(Writer, Object) method.");
    }
  }

  private static final class AppendableWriter extends Writer {
    private final Appendable appendable;

    AppendableWriter(Appendable appendable) {
      this.appendable = appendable;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      for (int i = off; i < off + len; i++) {
        appendable.append(cbuf[i]);
      }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
      appendable.append(str, off, off + len);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
