package io.github.minh124199.viettemplate.runtime;

import java.util.Objects;

/** Factory and accessor for standard {@link Escaper} implementations. */
public final class StandardEscapers {

  private StandardEscapers() {}

  /**
   * Returns the standard HTML text escaper.
   *
   * @return the HTML text escaper
   */
  public static Escaper htmlText() {
    return HtmlTextEscaper.INSTANCE;
  }

  /**
   * Returns the standard HTML quoted attribute escaper.
   *
   * @return the HTML attribute escaper
   */
  public static Escaper htmlAttribute() {
    return HtmlAttributeEscaper.INSTANCE;
  }

  /**
   * Returns the standard RFC 3986 URL component escaper.
   *
   * @return the URL component escaper
   */
  public static Escaper urlComponent() {
    return UrlComponentEscaper.INSTANCE;
  }

  /**
   * Returns the JavaScript string literal escaper.
   *
   * @return the JavaScript string escaper
   */
  public static Escaper jsString() {
    return JsStringEscaper.INSTANCE;
  }

  /**
   * Returns the CSS string literal escaper.
   *
   * @return the CSS string escaper
   */
  public static Escaper cssString() {
    return CssStringEscaper.INSTANCE;
  }

  /**
   * Returns the pass-through unescaped raw escaper.
   *
   * @return the raw escaper
   */
  public static Escaper raw() {
    return RawEscaper.INSTANCE;
  }

  /**
   * Resolves the standard escaper corresponding to an {@link EscapeMode}.
   *
   * @param mode the escape mode
   * @return the matching escaper
   */
  public static Escaper get(EscapeMode mode) {
    Objects.requireNonNull(mode, "mode must not be null");
    return switch (mode) {
      case HTML_TEXT -> htmlText();
      case HTML_ATTRIBUTE_QUOTED -> htmlAttribute();
      case URL_COMPONENT -> urlComponent();
      case JS_STRING -> jsString();
      case CSS_STRING -> cssString();
      case RAW -> raw();
    };
  }
}
