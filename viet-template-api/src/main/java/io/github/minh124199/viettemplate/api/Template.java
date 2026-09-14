package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Public template rendering contract.
 *
 * <p><strong>Thread Safety:</strong> Compiled {@code Template} instances are strictly immutable and
 * thread-safe. A single {@code Template} can be safely executed concurrently by multiple threads
 * using different {@link RenderContext} and {@link TemplateOutput} instances.
 */
public interface Template {

  TemplateDescriptor descriptor();

  void render(RenderContext context, TemplateOutput output) throws IOException;

  /**
   * Convenience method to render this template directly to a {@link String}.
   *
   * @param context evaluation context
   * @return rendered template output string
   * @throws IOException on write failures
   */
  default String render(RenderContext context) throws IOException {
    StringBuilder sb = new StringBuilder();
    render(
        context,
        new TemplateOutput() {
          @Override
          public void write(CharSequence v) {
            if (v != null) {
              sb.append(v);
            }
          }

          @Override
          public void write(CharSequence v, int s, int e) {
            if (v != null) {
              Objects.checkFromToIndex(s, e, v.length());
              sb.append(v, s, e);
            }
          }

          @Override
          public void write(char v) {
            sb.append(v);
          }

          @Override
          public void writeUtf8(byte[] b) {
            sb.append(new String(b, StandardCharsets.UTF_8));
          }

          @Override
          public void writeUtf8(byte[] b, int off, int len) {
            sb.append(new String(b, off, len, StandardCharsets.UTF_8));
          }

          @Override
          public void writeInt(int v) {
            sb.append(v);
          }

          @Override
          public void writeLong(long v) {
            sb.append(v);
          }

          @Override
          public void writeDouble(double v) {
            sb.append(v);
          }

          @Override
          public void writeBoolean(boolean v) {
            sb.append(v);
          }
        });
    return sb.toString();
  }
}
