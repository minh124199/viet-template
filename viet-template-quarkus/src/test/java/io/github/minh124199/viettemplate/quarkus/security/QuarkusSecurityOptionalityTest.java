package io.github.minh124199.viettemplate.quarkus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class QuarkusSecurityOptionalityTest {

  private static class SimpleContributorContext implements ContributorContext {
    private final Map<String, Object> storage = new HashMap<>();

    @Override
    public ContributorContext put(String key, Object value) {
      storage.put(key, value);
      return this;
    }

    @Override
    public ContributorContext putAll(Map<String, ?> entries) {
      if (entries != null) {
        storage.putAll(entries);
      }
      return this;
    }

    @Override
    public boolean contains(String key) {
      return storage.containsKey(key);
    }

    @Override
    public Object get(String key) {
      return storage.get(key);
    }
  }

  @Test
  public void testFatalVirtualMachineErrorInIdentitySupplierPropagates() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            () -> {
              throw new OutOfMemoryError("simulated out of memory error");
            });

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    assertThatThrownBy(() -> contributor.contribute(ctx, req))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("simulated out of memory error");
  }

  @Test
  public void testFatalVirtualMachineErrorInCsrfSupplierPropagates() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            null,
            () -> {
              throw new OutOfMemoryError("simulated csrf out of memory error");
            });

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    assertThatThrownBy(() -> contributor.contribute(ctx, req))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("simulated csrf out of memory error");
  }

  @Test
  public void testNonFatalExceptionInIdentitySupplierFallsBackToAnonymous() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            () -> {
              throw new IllegalStateException("transient identity resolution failure");
            });

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    Object secObj = ctx.get("security");
    assertThat(secObj).isInstanceOf(QuarkusSecurityView.class);
    QuarkusSecurityView view = (QuarkusSecurityView) secObj;
    assertThat(view.isAnonymous()).isTrue();
    assertThat(view.isAuthenticated()).isFalse();
  }

  @Test
  public void testNonFatalExceptionInCsrfSupplierFallsBackToUnavailable() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            null,
            () -> {
              throw new RuntimeException("transient csrf error");
            });

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    Object csrfObj = ctx.get("csrf");
    assertThat(csrfObj).isInstanceOf(QuarkusCsrfView.class);
    QuarkusCsrfView view = (QuarkusCsrfView) csrfObj;
    assertThat(view.isAvailable()).isFalse();
    assertThat(view.token()).isEmpty();
  }

  @Test
  public void testNullIdentitySupplierReturnsAnonymous() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(() -> null);

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    QuarkusSecurityView view = (QuarkusSecurityView) ctx.get("security");
    assertThat(view).isNotNull();
    assertThat(view.isAnonymous()).isTrue();
  }

  @Test
  public void testNullCsrfSupplierReturnsUnavailable() {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(null, () -> null);

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    QuarkusCsrfView view = (QuarkusCsrfView) ctx.get("csrf");
    assertThat(view).isNotNull();
    assertThat(view.isAvailable()).isFalse();
  }

  @Test
  public void testFactoryReturningNullFallsBackToAnonymous() {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("alice", false, java.util.Set.of("USER"));

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            () -> identity, id -> null, null, "security", "csrf");

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    QuarkusSecurityView view = (QuarkusSecurityView) ctx.get("security");
    assertThat(view).isNotNull();
    assertThat(view.isAnonymous()).isTrue();
  }

  @Test
  public void testMissingArcContainerDefaultBehavior() {
    // Arc container is not started in this plain JUnit test
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    SimpleContributorContext ctx = new SimpleContributorContext();
    RenderRequest req = RenderRequest.of(TemplateId.of("dummy.vtl"), RenderContext.empty());

    contributor.contribute(ctx, req);

    QuarkusSecurityView sec = (QuarkusSecurityView) ctx.get("security");
    QuarkusCsrfView csrf = (QuarkusCsrfView) ctx.get("csrf");

    assertThat(sec).isNotNull();
    assertThat(sec.isAnonymous()).isTrue();

    assertThat(csrf).isNotNull();
    assertThat(csrf.isAvailable()).isFalse();
  }
}
