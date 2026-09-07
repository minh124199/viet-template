package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/** Adapts a {@link VtlSecurityPolicy} to the runtime {@link LinkerAccessPolicy} contract. */
final class VtlLinkerAccessPolicyAdapter implements LinkerAccessPolicy {

  private final VtlSecurityPolicy policy;
  private final String policyId;

  VtlLinkerAccessPolicyAdapter(VtlSecurityPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    this.policyId =
        policy == VtlSecurityPolicy.standard()
            ? "vtl-standard"
            : "vtl-"
                + policy.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(policy));
  }

  @Override
  public String policyId() {
    return policyId;
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return policy.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return policy.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return policy.isFieldPermitted(receiverClass, field);
  }
}
