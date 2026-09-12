package io.github.minh124199.viettemplate.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class or record as an approved presentation data transfer object (DTO) within
 * sandboxed template execution profiles (such as {@code VTL_SAFE}).
 *
 * <p>Annotating a record or class with {@code @TemplateData} explicitly exposes its public
 * properties (record components, standard JavaBean getters, or public fields) to template property
 * navigation. Arbitrary non-getter public methods are not automatically exposed and require {@link
 * TemplateCallable}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface TemplateData {}
