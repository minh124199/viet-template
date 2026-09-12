package io.github.minh124199.viettemplate.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as explicitly callable from sandboxed template code (such as {@code VTL_SAFE}).
 *
 * <p>Under strict safe execution profiles, arbitrary public Java methods cannot be invoked unless
 * they belong to standard approved JDK types (e.g. {@link java.util.Collection#size()}) or are
 * explicitly authorized via this annotation or an explicit allowlist.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface TemplateCallable {}
