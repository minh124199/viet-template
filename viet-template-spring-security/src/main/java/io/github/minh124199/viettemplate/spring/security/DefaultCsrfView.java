package io.github.minh124199.viettemplate.spring.security;

import io.github.minh124199.viettemplate.api.TemplateData;
import java.util.Objects;

/** Default immutable implementation of {@link CsrfView}. */
@TemplateData
final class DefaultCsrfView implements CsrfView {

  private final String token;
  private final String parameterName;
  private final String headerName;

  DefaultCsrfView(String token, String parameterName, String headerName) {
    this.token = Objects.requireNonNull(token, "token must not be null");
    this.parameterName = Objects.requireNonNull(parameterName, "parameterName must not be null");
    this.headerName = Objects.requireNonNull(headerName, "headerName must not be null");
  }

  @Override
  public String getToken() {
    return this.token;
  }

  @Override
  public String getParameterName() {
    return this.parameterName;
  }

  @Override
  public String getHeaderName() {
    return this.headerName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DefaultCsrfView other)) {
      return false;
    }
    return Objects.equals(this.token, other.token)
        && Objects.equals(this.parameterName, other.parameterName)
        && Objects.equals(this.headerName, other.headerName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.token, this.parameterName, this.headerName);
  }

  @Override
  public String toString() {
    // Redact the actual token value to prevent credential leakage in logs
    return "CsrfView[parameterName="
        + this.parameterName
        + ", headerName="
        + this.headerName
        + ", token=[PROTECTED]]";
  }
}
