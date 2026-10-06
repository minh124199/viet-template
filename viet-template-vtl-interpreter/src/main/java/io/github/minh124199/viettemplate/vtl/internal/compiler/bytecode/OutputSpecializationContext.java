package io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode;

/**
 * Contextual metadata required for determining output specialization (e.g. typed String and Integer
 * fast paths) during bytecode template compilation and explanation.
 */
public interface OutputSpecializationContext {

  /** Returns whether strict reference rendering or strict error null handling is enabled. */
  boolean isStrict();

  /** Returns whether the template runs under a security-restricted safe profile. */
  boolean isSafeProfile();

  /** Returns whether the template is typed (has a model schema or template parameters). */
  boolean isTyped();

  /**
   * Returns whether the local variable at the given slot is known not to be a proven integer.
   *
   * @param slot local variable slot
   * @return {@code true} if the slot holds non-integer values or cannot be proven integer
   */
  boolean isNonIntLocal(int slot);
}
