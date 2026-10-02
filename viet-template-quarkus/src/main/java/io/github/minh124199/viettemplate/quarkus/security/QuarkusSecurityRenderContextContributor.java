package io.github.minh124199.viettemplate.quarkus.security;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.SecurityIdentity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link RenderContextContributor} that provides the {@code $security} and {@code $csrf} model
 * bindings for Quarkus applications backed by Quarkus {@link SecurityIdentity} and Quarkus CSRF
 * protection.
 */
public final class QuarkusSecurityRenderContextContributor implements RenderContextContributor {

  /** Default variable name exposed to templates for security state. */
  public static final String DEFAULT_SECURITY_VARIABLE_NAME = "security";

  /** Optional render request attribute key to explicitly provide a {@link SecurityIdentity}. */
  public static final String SECURITY_IDENTITY_ATTRIBUTE =
      "io.quarkus.security.identity.SecurityIdentity";

  /** Default variable name exposed to templates for CSRF state. */
  public static final String DEFAULT_CSRF_VARIABLE_NAME = "csrf";

  /** Optional render request attribute key to explicitly provide a CSRF token. */
  public static final String CSRF_TOKEN_ATTRIBUTE = "io.quarkus.csrf.token";

  private final Supplier<SecurityIdentity> identitySupplier;
  private final QuarkusSecurityViewFactory viewFactory;
  private final Supplier<QuarkusCsrfView> csrfSupplier;
  private final String securityVariableName;
  private final String csrfVariableName;

  /** Constructs a contributor that discovers {@link SecurityIdentity} via Arc CDI container. */
  public QuarkusSecurityRenderContextContributor() {
    this(null, null, null, DEFAULT_SECURITY_VARIABLE_NAME, DEFAULT_CSRF_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor that uses the specified identity supplier and default variable names.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   */
  public QuarkusSecurityRenderContextContributor(Supplier<SecurityIdentity> identitySupplier) {
    this(identitySupplier, null, null, DEFAULT_SECURITY_VARIABLE_NAME, DEFAULT_CSRF_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor with a custom identity supplier and security variable name.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   * @param variableName template variable name for security view (defaults to {@code "security"})
   */
  public QuarkusSecurityRenderContextContributor(
      Supplier<SecurityIdentity> identitySupplier, String variableName) {
    this(identitySupplier, null, null, variableName, DEFAULT_CSRF_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor with custom identity and CSRF suppliers using default variable names.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   * @param csrfSupplier custom CSRF supplier, or {@code null} to use request/Arc resolution
   */
  public QuarkusSecurityRenderContextContributor(
      Supplier<SecurityIdentity> identitySupplier, Supplier<QuarkusCsrfView> csrfSupplier) {
    this(
        identitySupplier,
        null,
        csrfSupplier,
        DEFAULT_SECURITY_VARIABLE_NAME,
        DEFAULT_CSRF_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor with custom identity and CSRF suppliers and variable names.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   * @param csrfSupplier custom CSRF supplier, or {@code null} to use request/Arc resolution
   * @param securityVariableName template variable name for security view (defaults to {@code
   *     "security"})
   * @param csrfVariableName template variable name for CSRF view (defaults to {@code "csrf"})
   */
  public QuarkusSecurityRenderContextContributor(
      Supplier<SecurityIdentity> identitySupplier,
      Supplier<QuarkusCsrfView> csrfSupplier,
      String securityVariableName,
      String csrfVariableName) {
    this(identitySupplier, null, csrfSupplier, securityVariableName, csrfVariableName);
  }

  /**
   * Constructs a contributor with custom suppliers, custom view factory, and variable names.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   * @param viewFactory custom security view factory, or {@code null} to use default or Arc bean
   * @param csrfSupplier custom CSRF supplier, or {@code null} to use request/Arc resolution
   * @param securityVariableName template variable name for security view (defaults to {@code
   *     "security"})
   * @param csrfVariableName template variable name for CSRF view (defaults to {@code "csrf"})
   */
  public QuarkusSecurityRenderContextContributor(
      Supplier<SecurityIdentity> identitySupplier,
      QuarkusSecurityViewFactory viewFactory,
      Supplier<QuarkusCsrfView> csrfSupplier,
      String securityVariableName,
      String csrfVariableName) {
    this.identitySupplier = identitySupplier;
    this.viewFactory = viewFactory;
    this.csrfSupplier = csrfSupplier;
    this.securityVariableName =
        (securityVariableName != null && !securityVariableName.isBlank())
            ? securityVariableName
            : DEFAULT_SECURITY_VARIABLE_NAME;
    this.csrfVariableName =
        (csrfVariableName != null && !csrfVariableName.isBlank())
            ? csrfVariableName
            : DEFAULT_CSRF_VARIABLE_NAME;
  }

  @Override
  public void contribute(ContributorContext context, RenderRequest request) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(request, "request must not be null");

    SecurityIdentity identity = resolveIdentity(request);
    QuarkusSecurityViewFactory factory = resolveViewFactory();
    QuarkusSecurityView securityView =
        identity != null ? factory.create(identity) : QuarkusSecurityView.anonymous();
    if (securityView == null) {
      securityView = QuarkusSecurityView.anonymous();
    }
    context.put(this.securityVariableName, securityView);

    QuarkusCsrfView csrfView = resolveCsrf(request);
    if (csrfView == null) {
      csrfView = QuarkusCsrfView.unavailable();
    }
    context.put(this.csrfVariableName, csrfView);
  }

  /**
   * Returns the variable name under which the security facade is registered.
   *
   * @return security template variable name
   */
  public String getSecurityVariableName() {
    return this.securityVariableName;
  }

  /**
   * Backwards-compatible alias for {@link #getSecurityVariableName()}.
   *
   * @return security template variable name
   */
  public String getVariableName() {
    return this.securityVariableName;
  }

  /**
   * Returns the variable name under which the CSRF facade is registered.
   *
   * @return CSRF template variable name
   */
  public String getCsrfVariableName() {
    return this.csrfVariableName;
  }

  /**
   * Returns the configured or resolved {@link QuarkusSecurityViewFactory}.
   *
   * @return security view factory
   */
  public QuarkusSecurityViewFactory getViewFactory() {
    return resolveViewFactory();
  }

  /**
   * Returns the custom CSRF supplier, if configured.
   *
   * @return custom CSRF supplier, or {@code null}
   */
  public Supplier<QuarkusCsrfView> getCsrfSupplier() {
    return this.csrfSupplier;
  }

  /**
   * Returns the custom identity supplier, if configured.
   *
   * @return custom identity supplier, or {@code null}
   */
  public Supplier<SecurityIdentity> getIdentitySupplier() {
    return this.identitySupplier;
  }

  @SuppressWarnings("removal")
  private QuarkusSecurityViewFactory resolveViewFactory() {
    if (this.viewFactory != null) {
      return this.viewFactory;
    }
    try {
      ArcContainer container = Arc.container();
      if (container != null && container.isRunning()) {
        InstanceHandle<QuarkusSecurityViewFactory> handle =
            container.instance(QuarkusSecurityViewFactory.class);
        if (handle != null && handle.isAvailable()) {
          return handle.get();
        }
      }
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (Throwable ignored) {
    }
    return QuarkusSecurityViewFactory.defaultFactory();
  }

  @SuppressWarnings("removal")
  private SecurityIdentity resolveIdentity(RenderRequest request) {
    // 1. Explicit request attribute takes precedence
    if (request.attributes() != null) {
      Object attr = request.attributes().get(SECURITY_IDENTITY_ATTRIBUTE);
      if (attr instanceof SecurityIdentity si) {
        return si;
      }
      Object shortAttr = request.attributes().get("securityIdentity");
      if (shortAttr instanceof SecurityIdentity si) {
        return si;
      }
    }

    // 2. Custom supplier if provided
    if (this.identitySupplier != null) {
      try {
        return this.identitySupplier.get();
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable ignored) {
        return null;
      }
    }

    // 3. Arc CDI container lookup
    try {
      ArcContainer container = Arc.container();
      if (container != null && container.isRunning()) {
        InstanceHandle<SecurityIdentity> handle = container.instance(SecurityIdentity.class);
        if (handle != null && handle.isAvailable()) {
          return handle.get();
        }
      }
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (Throwable ignored) {
      // Container not running or security identity bean not resolvable
    }

    return null;
  }

  private static final class CsrfProviderAccessor {
    private static final Class<?> PROVIDER_CLASS;
    private static final Method GET_TOKEN_METHOD;
    private static final Method GET_PARAM_METHOD;
    private static final Method GET_HEADER_METHOD;
    private static final boolean AVAILABLE;

    static {
      Class<?> clazz = null;
      Method getToken = null;
      Method getParam = null;
      Method getHeader = null;
      boolean avail = false;
      try {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        clazz =
            Class.forName("io.quarkus.csrf.reactive.runtime.CsrfTokenParameterProvider", false, cl);
        getToken = clazz.getMethod("getToken");
        try {
          getParam = clazz.getMethod("getParameterName");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
          getHeader = clazz.getMethod("getHeaderName");
        } catch (ReflectiveOperationException ignored) {
        }
        avail = true;
      } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException ignored) {
      }
      PROVIDER_CLASS = clazz;
      GET_TOKEN_METHOD = getToken;
      GET_PARAM_METHOD = getParam;
      GET_HEADER_METHOD = getHeader;
      AVAILABLE = avail;
    }
  }

  @SuppressWarnings("removal")
  private QuarkusCsrfView resolveCsrf(RenderRequest request) {
    // 1. Check request attributes
    if (request.attributes() != null) {
      Object attr = request.attributes().get(CSRF_TOKEN_ATTRIBUTE);
      if (attr instanceof QuarkusCsrfView qcv) {
        return qcv;
      }
      if (attr instanceof String s && !s.isBlank()) {
        return QuarkusCsrfView.of(s);
      }

      Object csrfAttr = request.attributes().get("csrf");
      if (csrfAttr instanceof QuarkusCsrfView qcv) {
        return qcv;
      }
      if (csrfAttr instanceof String s && !s.isBlank()) {
        return QuarkusCsrfView.of(s);
      }

      Object tokenAttr = request.attributes().get("csrfToken");
      if (tokenAttr instanceof String s && !s.isBlank()) {
        return QuarkusCsrfView.of(s);
      }
      if (tokenAttr instanceof QuarkusCsrfView qcv) {
        return qcv;
      }

      Object rc = request.attributes().get("io.vertx.ext.web.RoutingContext");
      if (rc == null) {
        rc = request.attributes().get("routingContext");
      }
      if (rc != null) {
        try {
          Method getMethod = rc.getClass().getMethod("get", String.class);
          Object token = getMethod.invoke(rc, "csrf_token");
          if (token instanceof String s && !s.isBlank()) {
            return QuarkusCsrfView.of(s);
          }
          if (token instanceof QuarkusCsrfView qcv) {
            return qcv;
          }
          Object token2 = getMethod.invoke(rc, CSRF_TOKEN_ATTRIBUTE);
          if (token2 instanceof String s && !s.isBlank()) {
            return QuarkusCsrfView.of(s);
          }
          if (token2 instanceof QuarkusCsrfView qcv) {
            return qcv;
          }
        } catch (InvocationTargetException ite) {
          Throwable cause = ite.getCause();
          if (cause instanceof VirtualMachineError vme) {
            throw vme;
          }
          if (cause instanceof ThreadDeath td) {
            throw td;
          }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
      }
    }

    // 2. Custom supplier if provided
    if (this.csrfSupplier != null) {
      try {
        QuarkusCsrfView supplied = this.csrfSupplier.get();
        if (supplied != null) {
          return supplied;
        }
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable ignored) {
      }
    }

    // 3. Arc CDI container lookup for CsrfTokenParameterProvider
    if (CsrfProviderAccessor.AVAILABLE) {
      try {
        ArcContainer container = Arc.container();
        if (container != null && container.isRunning()) {
          @SuppressWarnings({"rawtypes", "unchecked"})
          InstanceHandle<?> handle =
              container.instance((Class) CsrfProviderAccessor.PROVIDER_CLASS);
          if (handle != null && handle.isAvailable()) {
            Object provider = handle.get();
            if (provider != null) {
              String token = (String) CsrfProviderAccessor.GET_TOKEN_METHOD.invoke(provider);
              if (token != null && !token.isBlank()) {
                String paramName = null;
                String headerName = null;
                if (CsrfProviderAccessor.GET_PARAM_METHOD != null) {
                  try {
                    paramName = (String) CsrfProviderAccessor.GET_PARAM_METHOD.invoke(provider);
                  } catch (ReflectiveOperationException ignored) {
                  }
                }
                if (CsrfProviderAccessor.GET_HEADER_METHOD != null) {
                  try {
                    headerName = (String) CsrfProviderAccessor.GET_HEADER_METHOD.invoke(provider);
                  } catch (ReflectiveOperationException ignored) {
                  }
                }
                return QuarkusCsrfView.of(token, paramName, headerName);
              }
            }
          }
        }
      } catch (InvocationTargetException ite) {
        Throwable cause = ite.getCause();
        if (cause instanceof VirtualMachineError vme) {
          throw vme;
        }
        if (cause instanceof ThreadDeath td) {
          throw td;
        }
        // When token is not set for request, getToken() throws IllegalStateException
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable ignored) {
      }
    }

    // 4. Unavailable fallback
    return QuarkusCsrfView.unavailable();
  }
}
