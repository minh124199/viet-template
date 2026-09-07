package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityLinkageTest {

  public static class SafeBean {
    public String getSecret() {
      return "safe-value";
    }
  }

  public static class SensitiveBean {
    public String getSecret() {
      return "sensitive-value";
    }
  }

  private DynamicLinker linker;
  private LinkerAccessPolicy policy;

  @BeforeEach
  void setUp() {
    policy =
        new LinkerAccessPolicy() {
          @Override
          public String policyId() {
            return "custom-test-policy";
          }

          @Override
          public boolean isClassPermitted(Class<?> clazz) {
            return clazz != SensitiveBean.class
                && LinkerAccessPolicy.standard().isClassPermitted(clazz);
          }

          @Override
          public boolean isMethodPermitted(
              Class<?> receiverClass, java.lang.reflect.Method method) {
            if (receiverClass == SensitiveBean.class) {
              return false;
            }
            return LinkerAccessPolicy.standard().isMethodPermitted(receiverClass, method);
          }

          @Override
          public boolean isFieldPermitted(Class<?> receiverClass, java.lang.reflect.Field field) {
            return receiverClass != SensitiveBean.class;
          }
        };
    linker = new DynamicLinker(policy);
  }

  @Test
  void testDeniedClassNeverBecomesLinkableThroughCacheReuse() throws Throwable {
    LinkerStatistics stats = new LinkerStatistics();
    DynamicCallSite callSite =
        new DynamicCallSite(100, MemberKey.propertyGet("secret"), policy, linker, stats);

    SafeBean safe = new SafeBean();
    SensitiveBean sensitive = new SensitiveBean();

    // 1. Safe bean should succeed
    assertEquals("safe-value", callSite.invoke(safe));
    assertEquals(DynamicCallSite.State.MONOMORPHIC, callSite.state());

    // 2. Sensitive bean on same call site must fail with security exception
    assertThrows(TemplateSecurityException.class, () -> callSite.invoke(sensitive));

    // 3. Repeated attempts with sensitive bean must STILL fail (cached denied link)
    assertThrows(TemplateSecurityException.class, () -> callSite.invoke(sensitive));

    // 4. Safe bean must still succeed and not be corrupted
    assertEquals("safe-value", callSite.invoke(safe));

    // 5. Direct access link from linker must be denied
    AccessLink sensitiveLink =
        linker.link(SensitiveBean.class, MemberKey.propertyGet("secret"), policy);
    assertTrue(sensitiveLink.isDenied());
    assertThrows(TemplateSecurityException.class, () -> sensitiveLink.invoke(sensitive));
  }

  @Test
  void testCoreSecurityRestrictionsEnforced() {
    DynamicCallSite getClassSite =
        new DynamicCallSite(
            101, MemberKey.methodCall("getClass", 0), policy, linker, new LinkerStatistics());

    SafeBean safe = new SafeBean();
    assertThrows(TemplateSecurityException.class, () -> getClassSite.invoke(safe));

    DynamicCallSite exitSite =
        new DynamicCallSite(
            102, MemberKey.methodCall("exit", 1), policy, linker, new LinkerStatistics());
    assertThrows(TemplateSecurityException.class, () -> exitSite.invoke(System.class, 0));
  }

  @Test
  void testPolicyIsolationInRegistry() throws Throwable {
    CallSiteRegistry registry = new CallSiteRegistry(100, linker);

    SafeBean safe = new SafeBean();
    MemberKey key = MemberKey.propertyGet("secret");

    // Standard policy permits SafeBean
    DynamicCallSite siteStandard = registry.getOrCreate(200, key, policy);
    assertEquals("safe-value", siteStandard.invoke(safe));

    // Deny-all policy denies SafeBean even for same siteId
    LinkerAccessPolicy denyAll = LinkerAccessPolicy.denyAll();
    DynamicCallSite siteDenyAll = registry.getOrCreate(200, key, denyAll);

    assertNotSame(siteStandard, siteDenyAll);
    assertThrows(TemplateSecurityException.class, () -> siteDenyAll.invoke(safe));
  }
}
