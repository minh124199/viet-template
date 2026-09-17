package io.github.minh124199.viettemplate.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;

/** Default implementation of {@link CsrfViewFactory}. */
final class DefaultCsrfViewFactory implements CsrfViewFactory {

  @Override
  public CsrfView create(HttpServletRequest request) {
    if (request == null) {
      return null;
    }

    Object attr = request.getAttribute(CsrfToken.class.getName());
    if (attr == null) {
      attr = request.getAttribute("_csrf");
    }
    if (attr == null) {
      attr = request.getAttribute(DeferredCsrfToken.class.getName());
    }

    if (attr instanceof CsrfToken csrfToken) {
      return toCsrfView(csrfToken);
    }
    if (attr instanceof DeferredCsrfToken deferredCsrfToken) {
      CsrfToken csrfToken = deferredCsrfToken.get();
      return csrfToken != null ? toCsrfView(csrfToken) : null;
    }

    return null;
  }

  private static CsrfView toCsrfView(CsrfToken csrfToken) {
    String token = csrfToken.getToken();
    String parameterName = csrfToken.getParameterName();
    String headerName = csrfToken.getHeaderName();
    if (token == null || parameterName == null || headerName == null) {
      return null;
    }
    return new DefaultCsrfView(token, parameterName, headerName);
  }
}
