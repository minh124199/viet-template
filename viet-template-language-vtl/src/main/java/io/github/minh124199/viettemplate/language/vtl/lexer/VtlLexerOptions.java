package io.github.minh124199.viettemplate.language.vtl.lexer;

/** Configuration options for the VTL lexical scanner. */
public record VtlLexerOptions(boolean allowHyphenatedIdentifiers, boolean includeTrivia) {

  public static final VtlLexerOptions DEFAULT = new VtlLexerOptions(false, true);

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private boolean allowHyphenatedIdentifiers = false;
    private boolean includeTrivia = true;

    private Builder() {}

    public Builder allowHyphenatedIdentifiers(boolean allow) {
      this.allowHyphenatedIdentifiers = allow;
      return this;
    }

    public Builder includeTrivia(boolean include) {
      this.includeTrivia = include;
      return this;
    }

    public VtlLexerOptions build() {
      return new VtlLexerOptions(allowHyphenatedIdentifiers, includeTrivia);
    }
  }
}
