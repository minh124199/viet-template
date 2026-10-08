package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Package-private reflection bridge for MemberResolver, MemberResolution, and MethodResolution to
 * prevent leaking internal language-vtl implementation types across modules.
 */
final class MemberResolutionBridge {
  private static final Method RESOLVE_PROPERTY;
  private static final Method RES_IS_FOUND;
  private static final Method RES_KIND;
  private static final Method RES_TARGET_MEMBER;
  private static final Method METHOD_RES_IS_RESOLVED;
  private static final Method METHOD_RES_TARGET_METHOD;

  static {
    Method rp = null;
    Method rif = null;
    Method rk = null;
    Method rtm = null;
    Method mrir = null;
    Method mrtm = null;
    try {
      Class<?> resolverClass =
          Class.forName(
              "io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolver");
      rp =
          resolverClass.getMethod(
              "resolveProperty", VType.class, String.class, MemberAccessPolicy.class);

      Class<?> resolutionClass =
          Class.forName(
              "io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolution");
      rif = resolutionClass.getMethod("isFound");
      rk = resolutionClass.getMethod("kind");
      rtm = resolutionClass.getMethod("targetMember");

      Class<?> methodResClass =
          Class.forName(
              "io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MethodResolution");
      mrir = methodResClass.getMethod("isResolved");
      mrtm = methodResClass.getMethod("targetMethod");
    } catch (ReflectiveOperationException ignored) {
    }
    RESOLVE_PROPERTY = rp;
    RES_IS_FOUND = rif;
    RES_KIND = rk;
    RES_TARGET_MEMBER = rtm;
    METHOD_RES_IS_RESOLVED = mrir;
    METHOD_RES_TARGET_METHOD = mrtm;
  }

  record MemberResolutionView(boolean isFound, String kindName, Optional<Member> targetMember) {}

  static Optional<MemberResolutionView> inspectMemberResolution(Object resolution) {
    if (resolution == null
        || RES_IS_FOUND == null
        || RES_KIND == null
        || RES_TARGET_MEMBER == null) {
      return Optional.empty();
    }
    try {
      boolean isFound = (Boolean) RES_IS_FOUND.invoke(resolution);
      Object kindObj = RES_KIND.invoke(resolution);
      String kindName = kindObj != null ? kindObj.toString() : "DYNAMIC";
      @SuppressWarnings("unchecked")
      Optional<Member> targetMember = (Optional<Member>) RES_TARGET_MEMBER.invoke(resolution);
      return Optional.of(
          new MemberResolutionView(
              isFound, kindName, targetMember != null ? targetMember : Optional.empty()));
    } catch (ReflectiveOperationException ignored) {
      return Optional.empty();
    }
  }

  static Optional<MemberResolutionView> resolveProperty(
      Class<?> clazz, String propertyName, MemberAccessPolicy policy) {
    if (clazz == null || RESOLVE_PROPERTY == null) {
      return Optional.empty();
    }
    try {
      Object res = RESOLVE_PROPERTY.invoke(null, VType.ClassType.of(clazz), propertyName, policy);
      return inspectMemberResolution(res);
    } catch (ReflectiveOperationException ignored) {
      return Optional.empty();
    }
  }

  record MethodResolutionView(boolean isResolved, Optional<Method> targetMethod) {}

  static Optional<MethodResolutionView> inspectMethodResolution(Object resolution) {
    if (resolution == null || METHOD_RES_IS_RESOLVED == null || METHOD_RES_TARGET_METHOD == null) {
      return Optional.empty();
    }
    try {
      boolean isResolved = (Boolean) METHOD_RES_IS_RESOLVED.invoke(resolution);
      @SuppressWarnings("unchecked")
      Optional<Method> targetMethod =
          (Optional<Method>) METHOD_RES_TARGET_METHOD.invoke(resolution);
      return Optional.of(
          new MethodResolutionView(
              isResolved, targetMethod != null ? targetMethod : Optional.empty()));
    } catch (ReflectiveOperationException ignored) {
      return Optional.empty();
    }
  }

  private MemberResolutionBridge() {}
}
