package io.github.minh124199.viettemplate.runtime.linker;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/** Encapsulates an approved, resolved dynamic access target for a specific receiver class shape. */
public final class AccessLink {

  /** Resolution status of the link. */
  public enum Status {
    OK,
    DENIED,
    MISSING
  }

  private final WeakReference<Class<?>> receiverClassRef;
  private final MethodHandle handle;
  private final Status status;
  private final String memberName;
  private final String denialReason;

  private AccessLink(
      Class<?> receiverClass,
      MethodHandle handle,
      Status status,
      String memberName,
      String denialReason) {
    this.receiverClassRef = new WeakReference<>(receiverClass);
    this.handle = handle;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.memberName = memberName;
    this.denialReason = denialReason;
  }

  /** Creates an approved executable access link. */
  public static AccessLink ok(Class<?> receiverClass, MethodHandle handle, String memberName) {
    Objects.requireNonNull(receiverClass, "receiverClass must not be null");
    Objects.requireNonNull(handle, "handle must not be null");
    return new AccessLink(receiverClass, handle, Status.OK, memberName, null);
  }

  /** Creates a security-denied sentinel link. */
  public static AccessLink denied(Class<?> receiverClass, String memberName, String reason) {
    return new AccessLink(receiverClass, null, Status.DENIED, memberName, reason);
  }

  /** Creates a missing member sentinel link. */
  public static AccessLink missing(Class<?> receiverClass, String memberName) {
    return new AccessLink(receiverClass, null, Status.MISSING, memberName, null);
  }

  /** Returns the receiver class associated with this link, or null if collected. */
  public Class<?> receiverClass() {
    return receiverClassRef.get();
  }

  /** Checks if this link matches the given receiver class and has not been garbage-collected. */
  public boolean matches(Class<?> clazz) {
    return receiverClassRef.get() == clazz;
  }

  public MethodHandle handle() {
    return handle;
  }

  public Status status() {
    return status;
  }

  public String memberName() {
    return memberName;
  }

  public String denialReason() {
    return denialReason;
  }

  public boolean isOk() {
    return status == Status.OK;
  }

  public boolean isDenied() {
    return status == Status.DENIED;
  }

  public boolean isMissing() {
    return status == Status.MISSING;
  }

  public MethodHandle methodHandle() {
    return handle;
  }

  /** Invokes the linked target for a zero-argument operation (e.g. property get). */
  public Object invoke(Object target) throws Throwable {
    checkStatus(target);
    try {
      return handle.invoke(target);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** Convenience invocation for property getters. */
  public Object invokeGet(Object target) throws Throwable {
    return invoke(target);
  }

  /** Invokes the linked target with a single argument (e.g. property set, index get). */
  public Object invoke(Object target, Object arg) throws Throwable {
    checkStatus(target);
    try {
      return handle.invoke(target, arg);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** Convenience invocation for property setters. */
  public Object invokeSet(Object target, Object value) throws Throwable {
    return invoke(target, value);
  }

  /** Convenience invocation for index get. */
  public Object invokeIndexGet(Object target, Object index) throws Throwable {
    return invoke(target, index);
  }

  /** Convenience invocation for index set. */
  public Object invokeIndexSet(Object target, Object index, Object value) throws Throwable {
    return invokeWithArgs(target, new Object[] {index, value});
  }

  /** Invokes the linked target with an array of arguments. */
  public Object invokeWithArgs(Object target, Object[] args) throws Throwable {
    checkStatus(target);
    try {
      if (args == null || args.length == 0) {
        return handle.invoke(target);
      }
      Object[] fullArgs = new Object[args.length + 1];
      fullArgs[0] = target;
      System.arraycopy(args, 0, fullArgs, 1, args.length);
      return handle.invokeWithArguments(fullArgs);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** Convenience invocation for method calls. */
  public Object invokeMethod(Object target, Object... args) throws Throwable {
    return invokeWithArgs(target, args);
  }

  private void checkStatus(Object target) {
    if (status == Status.DENIED) {
      Class<?> clazz = target != null ? target.getClass() : receiverClassRef.get();
      String className = clazz != null ? clazz.getName() : "unknown";
      throw new TemplateSecurityException(
          "Access to "
              + memberName
              + " on "
              + className
              + " is denied by security policy"
              + (denialReason != null ? ": " + denialReason : ""),
          TemplateId.of("dynamic.link"),
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("SECURITY", "VIOLATION"));
    }
  }
}
