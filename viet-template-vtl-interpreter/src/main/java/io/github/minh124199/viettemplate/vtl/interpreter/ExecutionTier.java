package io.github.minh124199.viettemplate.vtl.interpreter;

/**
 * Execution tier used by the reference interpreter engine.
 *
 * <ul>
 *   <li>{@code IR}: Executes the verified, lowered {@code IrTemplate} (Milestone M7 reference
 *       backend).
 *   <li>{@code AST}: Executes directly on the parsed {@code VtlTemplate} AST (legacy fallback).
 * </ul>
 */
public enum ExecutionTier {
  IR,
  AST
}
