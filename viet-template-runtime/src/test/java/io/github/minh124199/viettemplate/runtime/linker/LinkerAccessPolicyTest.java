package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LinkerAccessPolicyTest {

  @Test
  void testStandardPolicyAllowsNormalClasses() throws Exception {
    LinkerAccessPolicy policy = LinkerAccessPolicy.standard();

    assertTrue(policy.isClassPermitted(String.class));
    assertTrue(policy.isClassPermitted(Integer.class));
    assertTrue(policy.isClassPermitted(LinkerAccessPolicyTest.class));

    Method lengthMethod = String.class.getMethod("length");
    assertTrue(policy.isMethodPermitted(lengthMethod));
  }

  @Test
  void testStandardPolicyDeniesDangerousClasses() {
    LinkerAccessPolicy policy = LinkerAccessPolicy.standard();

    assertFalse(policy.isClassPermitted(Class.class));
    assertFalse(policy.isClassPermitted(ClassLoader.class));
    assertFalse(policy.isClassPermitted(Runtime.class));
    assertFalse(policy.isClassPermitted(Process.class));
    assertFalse(policy.isClassPermitted(ProcessBuilder.class));
    assertFalse(policy.isClassPermitted(System.class));
    assertFalse(policy.isClassPermitted(Thread.class));
    assertFalse(policy.isClassPermitted(Method.class));
  }

  @Test
  void testStandardPolicyDeniesDangerousMethodsOnAllowedClasses() throws Exception {
    LinkerAccessPolicy policy = LinkerAccessPolicy.standard();

    Method getClassMethod = Object.class.getMethod("getClass");
    assertFalse(policy.isMethodPermitted(getClassMethod));

    Method waitMethod = Object.class.getMethod("wait");
    assertFalse(policy.isMethodPermitted(waitMethod));

    Method notifyMethod = Object.class.getMethod("notify");
    assertFalse(policy.isMethodPermitted(notifyMethod));
  }

  @Test
  void testDenyAllPolicy() throws Exception {
    LinkerAccessPolicy policy = LinkerAccessPolicy.denyAll();

    assertFalse(policy.isClassPermitted(String.class));
    assertFalse(policy.isClassPermitted(Object.class));

    Method lengthMethod = String.class.getMethod("length");
    assertFalse(policy.isMethodPermitted(lengthMethod));

    assertEquals("deny-all", policy.policyId());
  }

  @Test
  void testPolicyIdIsDistinct() {
    LinkerAccessPolicy standard = LinkerAccessPolicy.standard();
    LinkerAccessPolicy denyAll = LinkerAccessPolicy.denyAll();

    assertNotEquals(standard.policyId(), denyAll.policyId());
  }
}
