package io.github.minh124199.viettemplate.quarkus.security;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * SPI factory functional interface for creating {@link QuarkusSecurityView} instances from Quarkus
 * {@link SecurityIdentity} snapshots.
 *
 * <p>Applications can register custom factory beans in Arc CDI container to customize security view
 * generation without replacing the contributor pipeline.
 */
@FunctionalInterface
public interface QuarkusSecurityViewFactory {

  /**
   * Creates an immutable {@link QuarkusSecurityView} from the given Quarkus security identity.
   *
   * @param identity the Quarkus security identity, or {@code null}
   * @return immutable security view facade
   */
  QuarkusSecurityView create(SecurityIdentity identity);

  /**
   * Returns the default factory implementation delegating to {@link
   * QuarkusSecurityView#from(SecurityIdentity)}.
   *
   * @return default view factory
   */
  static QuarkusSecurityViewFactory defaultFactory() {
    return QuarkusSecurityView::from;
  }
}
