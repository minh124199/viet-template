package io.github.minh124199.test.frontend.e2e;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
@Priority(1)
public class TestTrustedIdentityProvider
    implements IdentityProvider<TrustedAuthenticationRequest> {

  @Override
  public Class<TrustedAuthenticationRequest> getRequestType() {
    return TrustedAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      TrustedAuthenticationRequest request,
      AuthenticationRequestContext context) {
    String username = request.getPrincipal();

    if ("alice".equals(username)) {
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(new QuarkusPrincipal("alice"))
                  .setAnonymous(false)
                  .addRoles(Set.of("USER"))
                  .build());
    } else if ("admin".equals(username)) {
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(new QuarkusPrincipal("admin"))
                  .setAnonymous(false)
                  .addRoles(Set.of("ADMIN", "USER"))
                  .build());
    }
    return Uni.createFrom().failure(new AuthenticationFailedException("Unknown principal"));
  }
}
