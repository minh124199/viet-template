package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/** Escapes dynamic content into the output stream according to an {@link EscapeMode}. */
public interface Escaper {

  EscapeMode mode();

  void escape(CharSequence input, TemplateOutput output) throws IOException;
}
