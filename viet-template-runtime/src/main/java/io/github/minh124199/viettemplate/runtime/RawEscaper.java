package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/** Escaper for raw output mode that writes inputs directly without escaping. */
public final class RawEscaper implements Escaper {

  public static final RawEscaper INSTANCE = new RawEscaper();

  private RawEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.RAW;
  }

  @Override
  public void escape(CharSequence input, TemplateOutput output) throws IOException {
    if (input != null) {
      if (input instanceof SafeContent safe) {
        output.write(safe.content());
      } else {
        output.write(input);
      }
    }
  }
}
