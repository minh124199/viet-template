package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.List;

/** Strategy interface defining property, method, and index resolution and assignment semantics. */
public interface ReferenceAccess {

  EvaluationValue getProperty(Object target, String propertyName, SourceSpan span, TemplateId id);

  EvaluationValue invokeMethod(
      Object target,
      String methodName,
      List<EvaluationValue> arguments,
      SourceSpan span,
      TemplateId id);

  EvaluationValue getIndex(Object target, EvaluationValue index, SourceSpan span, TemplateId id);

  void setProperty(
      Object target, String propertyName, EvaluationValue value, SourceSpan span, TemplateId id);

  void setIndex(
      Object target, EvaluationValue index, EvaluationValue value, SourceSpan span, TemplateId id);
}
