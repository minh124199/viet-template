package io.github.minh124199.test.frontend.e2e;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
@Priority(1)
public class TestIdentityProvider
    implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

  @Override
  public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
    return UsernamePasswordAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      UsernamePasswordAuthenticationRequest request,
      AuthenticationRequestContext context) {
    String username = request.getUsername();
    char[] password = request.getPassword().getPassword();
    String pwd = new String(password);

    if ("alice".equals(username) && "secret".equals(pwd)) {
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(new QuarkusPrincipal("alice"))
                  .setAnonymous(false)
                  .addRoles(Set.of("USER"))
                  .build());
    } else if ("admin".equals(username) && "admin-secret".equals(pwd)) {
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(new QuarkusPrincipal("admin"))
                  .setAnonymous(false)
                  .addRoles(Set.of("ADMIN", "USER"))
                  .build());
    }
    return Uni.createFrom().failure(new AuthenticationFailedException("Invalid credentials"));
  }
}
