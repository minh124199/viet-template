package io.github.minh124199.test.quarkus;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
@Priority(1)
public class TestHttpAuthenticationMechanism implements HttpAuthenticationMechanism {

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String user = context.request().getHeader("X-Test-User");
    if (user == null || user.isBlank()) {
      return Uni.createFrom().nullItem();
    }
    if ("admin".equals(user)) {
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(new QuarkusPrincipal("admin"))
                  .setAnonymous(false)
                  .addRoles(Set.of("ADMIN", "USER"))
                  .build());
    }
    return Uni.createFrom()
        .item(
            QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(user))
                .setAnonymous(false)
                .addRoles(Set.of("USER"))
                .build());
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom().item(new ChallengeData(401));
  }
}
