package io.github.minh124199.viettemplate.language.vtl.ir.plan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Explicit access plan describing how a property or member is accessed on a receiver object.
 *
 * <p>Enables direct invocation in compiled backends without reflective overhead, while supporting
 * dynamic fallback when types are polymorphic or unknown.
 */
public sealed interface AccessPlan {

  /** Directly invokes a record component accessor method. */
  record DirectRecord(Class<?> owner, String componentName, Class<?> returnType, Method accessor)
      implements AccessPlan {
    public DirectRecord {
      Objects.requireNonNull(owner, "owner must not be null");
      Objects.requireNonNull(componentName, "componentName must not be null");
      Objects.requireNonNull(returnType, "returnType must not be null");
      Objects.requireNonNull(accessor, "accessor must not be null");
    }
  }

  /** Directly invokes a JavaBean getter or boolean isX getter method. */
  record DirectGetter(Class<?> owner, String methodName, Class<?> returnType, Method getter)
      implements AccessPlan {
    public DirectGetter {
      Objects.requireNonNull(owner, "owner must not be null");
      Objects.requireNonNull(methodName, "methodName must not be null");
      Objects.requireNonNull(returnType, "returnType must not be null");
      Objects.requireNonNull(getter, "getter must not be null");
    }
  }

  /** Directly accesses a public field on the owner object. */
  record DirectField(Class<?> owner, String fieldName, Class<?> fieldType, Field field)
      implements AccessPlan {
    public DirectField {
      Objects.requireNonNull(owner, "owner must not be null");
      Objects.requireNonNull(fieldName, "fieldName must not be null");
      Objects.requireNonNull(fieldType, "fieldType must not be null");
      Objects.requireNonNull(field, "field must not be null");
    }
  }

  /** Retrieves a value by key lookup on a java.util.Map. */
  record MapLookup(String keyConstant) implements AccessPlan {
    public MapLookup {
      Objects.requireNonNull(keyConstant, "keyConstant must not be null");
    }
  }

  /** Dynamically resolves the property at runtime through an indy / PIC call site. */
  record DynamicCallSite(int callSiteId, String propertyName) implements AccessPlan {
    public DynamicCallSite {
      Objects.requireNonNull(propertyName, "propertyName must not be null");
    }
  }

  /** Invokes a registered extension property method. */
  record ExtensionCall(Class<?> targetClass, String methodName, Method method)
      implements AccessPlan {
    public ExtensionCall {
      Objects.requireNonNull(targetClass, "targetClass must not be null");
      Objects.requireNonNull(methodName, "methodName must not be null");
      Objects.requireNonNull(method, "method must not be null");
    }
  }
}
