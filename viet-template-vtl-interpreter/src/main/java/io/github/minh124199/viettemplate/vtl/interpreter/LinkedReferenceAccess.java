package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.linker.AccessLink;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.InterpreterDiagnosticCodes;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance {@link ReferenceAccess} implementation backed by Milestone M9 {@link
 * DynamicLinker} and inline-cached {@link DynamicCallSite} instances, with transparent fallback to
 * {@link DefaultReferenceAccess}.
 */
final class LinkedReferenceAccess implements ReferenceAccess {

  private final VtlSecurityPolicy securityPolicy;
  private final LinkerAccessPolicy linkerPolicy;
  private final DefaultReferenceAccess fallback;
  private final CallSiteRegistry registry;

  LinkedReferenceAccess(VtlSecurityPolicy securityPolicy) {
    this(
        securityPolicy,
        new CallSiteRegistry(
            4096, new DynamicLinker(new VtlLinkerAccessPolicyAdapter(securityPolicy))));
  }

  LinkedReferenceAccess(VtlSecurityPolicy securityPolicy, CallSiteRegistry registry) {
    this.securityPolicy = Objects.requireNonNull(securityPolicy, "securityPolicy must not be null");
    this.linkerPolicy = new VtlLinkerAccessPolicyAdapter(securityPolicy);
    this.fallback = new DefaultReferenceAccess(securityPolicy);
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  CallSiteRegistry registry() {
    return registry;
  }

  VtlSecurityPolicy securityPolicy() {
    return securityPolicy;
  }

  DefaultReferenceAccess fallback() {
    return fallback;
  }

  @Override
  @SuppressWarnings("removal")
  public EvaluationValue getProperty(
      Object target, String propertyName, SourceSpan span, TemplateId id) {
    if (target == null) {
      return EvaluationValue.definedNull();
    }

    Class<?> clazz = target.getClass();
    if (!securityPolicy.isClassPermitted(clazz)) {
      throw new TemplateSecurityException(
          "Access to class " + clazz.getName() + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (target instanceof Map<?, ?> map && !map.containsKey(propertyName)) {
      return fallback.getProperty(target, propertyName, span, id);
    }

    int siteId = propertyName.hashCode() & 0x7FFFFFFF;
    DynamicCallSite callSite =
        registry.getOrCreate(siteId, MemberKey.propertyGet(propertyName), linkerPolicy);
    AccessLink link = callSite.resolveLink(clazz);

    if (link.isDenied()) {
      throw new TemplateSecurityException(
          "Access to property '"
              + propertyName
              + "' on "
              + clazz.getName()
              + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (link.isOk()) {
      try {
        Object value = link.invokeGet(target);
        return EvaluationValue.of(value);
      } catch (ControlSignal cs) {
        throw cs;
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (TemplateException te) {
        throw te;
      } catch (Throwable t) {
        Throwable cause =
            (t instanceof InvocationTargetException ite) ? ite.getTargetException() : t;
        if (cause instanceof VirtualMachineError || cause instanceof ThreadDeath) {
          throw (Error) cause;
        }
        if (cause instanceof TemplateException te) {
          throw te;
        }
        throw new TemplateRenderException(
            "Property '"
                + propertyName
                + "' evaluation threw an exception: "
                + (cause.getMessage() != null
                    ? cause.getMessage()
                    : cause.getClass().getSimpleName()),
            id,
            span,
            InterpreterDiagnosticCodes.INVALID_METHOD,
            cause);
      }
    }

    return fallback.getProperty(target, propertyName, span, id);
  }

  @Override
  @SuppressWarnings("removal")
  public EvaluationValue invokeMethod(
      Object target,
      String methodName,
      List<EvaluationValue> arguments,
      SourceSpan span,
      TemplateId id) {
    if (target == null) {
      throw new TemplateRenderException(
          "Cannot invoke method '" + methodName + "' on null target",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    Class<?> clazz = target.getClass();
    if (!securityPolicy.isClassPermitted(clazz)) {
      throw new TemplateSecurityException(
          "Access to class " + clazz.getName() + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (arguments.isEmpty()) {
      int siteId = (methodName.hashCode() * 31) & 0x7FFFFFFF;
      DynamicCallSite callSite =
          registry.getOrCreate(siteId, MemberKey.methodCall(methodName, 0), linkerPolicy);
      AccessLink link = callSite.resolveLink(clazz);

      if (link.isDenied()) {
        throw new TemplateSecurityException(
            "Invocation of method "
                + methodName
                + " on "
                + clazz.getName()
                + " is denied by security policy",
            id,
            span,
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }

      if (link.isOk()) {
        try {
          Object result = link.invokeMethod(target);
          return EvaluationValue.of(result);
        } catch (ControlSignal cs) {
          throw cs;
        } catch (VirtualMachineError | ThreadDeath fatal) {
          throw fatal;
        } catch (TemplateException te) {
          throw te;
        } catch (Throwable t) {
          Throwable cause =
              (t instanceof InvocationTargetException ite) ? ite.getTargetException() : t;
          if (cause instanceof VirtualMachineError || cause instanceof ThreadDeath) {
            throw (Error) cause;
          }
          if (cause instanceof TemplateException te) {
            throw te;
          }
          throw new TemplateRenderException(
              "Method '"
                  + methodName
                  + "' threw an exception: "
                  + (cause.getMessage() != null
                      ? cause.getMessage()
                      : cause.getClass().getSimpleName()),
              id,
              span,
              InterpreterDiagnosticCodes.INVALID_METHOD,
              cause);
        }
      }
    }

    return fallback.invokeMethod(target, methodName, arguments, span, id);
  }

  @Override
  public EvaluationValue getIndex(
      Object target, EvaluationValue index, SourceSpan span, TemplateId id) {
    return fallback.getIndex(target, index, span, id);
  }

  @Override
  public void setProperty(
      Object target, String propertyName, EvaluationValue value, SourceSpan span, TemplateId id) {
    fallback.setProperty(target, propertyName, value, span, id);
  }

  @Override
  public void setIndex(
      Object target, EvaluationValue index, EvaluationValue value, SourceSpan span, TemplateId id) {
    fallback.setIndex(target, index, value, span, id);
  }
}
