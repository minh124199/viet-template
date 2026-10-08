package io.github.minh124199.viettemplate.lsp.models;

public class NavOuter {
  public static class Inner {
    private String label;

    public Inner() {}

    public Inner(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  private Inner inner;

  public NavOuter() {}

  public NavOuter(Inner inner) {
    this.inner = inner;
  }

  public Inner getInner() {
    return inner;
  }
}
