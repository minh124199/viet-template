package io.github.minh124199.viettemplate.language.vtl.semantics.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Java interface or record as the strongly-typed data model for a template.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @TemplateModel("orders/list.vm")
 * public interface OrdersListModel {
 *   User user();
 *   List<Order> orders();
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TemplateModel {
  /** Optional relative path or template identifier that this model corresponds to. */
  String value() default "";
}
