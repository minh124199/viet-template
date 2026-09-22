package io.github.minh124199.viettemplate.quarkus.security;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link RenderContextContributor} that provides the {@code $security} model binding for Quarkus
 * applications backed by Quarkus {@link SecurityIdentity}.
 */
public final class QuarkusSecurityRenderContextContributor implements RenderContextContributor {

  /** Default variable name exposed to templates. */
  public static final String DEFAULT_SECURITY_VARIABLE_NAME = "security";

  /** Optional render request attribute key to explicitly provide a {@link SecurityIdentity}. */
  public static final String SECURITY_IDENTITY_ATTRIBUTE =
      "io.quarkus.security.identity.SecurityIdentity";

  private final Supplier<SecurityIdentity> identitySupplier;
  private final String variableName;

  /** Constructs a contributor that discovers {@link SecurityIdentity} via Arc CDI container. */
  public QuarkusSecurityRenderContextContributor() {
    this(null, DEFAULT_SECURITY_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor that uses the specified identity supplier and default variable name.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   */
  public QuarkusSecurityRenderContextContributor(Supplier<SecurityIdentity> identitySupplier) {
    this(identitySupplier, DEFAULT_SECURITY_VARIABLE_NAME);
  }

  /**
   * Constructs a contributor with a custom identity supplier and variable name.
   *
   * @param identitySupplier custom identity supplier, or {@code null} to use Arc lookup
   * @param variableName template variable name (defaults to {@code "security"})
   */
  public QuarkusSecurityRenderContextContributor(
      Supplier<SecurityIdentity> identitySupplier, String variableName) {
    this.identitySupplier = identitySupplier;
    this.variableName =
        (variableName != null && !variableName.isBlank())
            ? variableName
            : DEFAULT_SECURITY_VARIABLE_NAME;
  }

  @Override
  public void contribute(ContributorContext context, RenderRequest request) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(request, "request must not be null");

    SecurityIdentity identity = resolveIdentity(request);
    QuarkusSecurityView view =
        identity != null ? QuarkusSecurityView.from(identity) : QuarkusSecurityView.anonymous();
    context.put(this.variableName, view);
  }

  /**
   * Returns the variable name under which the security facade is registered.
   *
   * @return template variable name
   */
  public String getVariableName() {
    return this.variableName;
  }

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
    } catch (Throwable ignored) {
      // Container not running or security identity bean not resolvable
    }

    return null;
  }
}
