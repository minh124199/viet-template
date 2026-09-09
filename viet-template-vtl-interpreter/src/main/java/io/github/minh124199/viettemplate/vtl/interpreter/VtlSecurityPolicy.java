package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Security policy governing reflection, property access, and method invocations in the interpreter.
 */
public interface VtlSecurityPolicy {

  boolean isClassPermitted(Class<?> clazz);

  boolean isMethodPermitted(Class<?> receiverClass, Method method);

  boolean isFieldPermitted(Class<?> receiverClass, Field field);

  default String policyFingerprint() {
    return "standard";
  }

  default io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy
      toLinkerAccessPolicy() {
    return io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy.standard();
  }

  static VtlSecurityPolicy standard() {
    return StandardSecurityPolicy.INSTANCE;
  }

  static VtlSecurityPolicy of(MemberAccessPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new MemberAccessPolicyVtlAdapter(policy);
  }
}

final class StandardSecurityPolicy implements VtlSecurityPolicy {
  static final StandardSecurityPolicy INSTANCE = new StandardSecurityPolicy();
  private final MemberAccessPolicy delegate = MemberAccessPolicy.standard();

  private StandardSecurityPolicy() {}

  @Override
  public String policyFingerprint() {
    return delegate.policyFingerprint();
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return delegate.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return delegate.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return delegate.isFieldPermitted(receiverClass, field);
  }

  @Override
  public io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy
      toLinkerAccessPolicy() {
    return io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy.of(delegate);
  }
}

final class MemberAccessPolicyVtlAdapter implements VtlSecurityPolicy {
  private final MemberAccessPolicy policy;

  MemberAccessPolicyVtlAdapter(MemberAccessPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
  }

  @Override
  public String policyFingerprint() {
    return policy.policyFingerprint();
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

  @Override
  public io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy
      toLinkerAccessPolicy() {
    return io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy.of(policy);
  }
}
