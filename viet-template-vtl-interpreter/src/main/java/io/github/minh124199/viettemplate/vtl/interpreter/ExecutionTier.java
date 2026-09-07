package io.github.minh124199.viettemplate.vtl.interpreter;

/**
 * Execution tier used by the execution engine.
 *
 * <ul>
 *   <li>{@code AOT_BYTECODE}: Executes directly via compiled JVM bytecode (Milestone M11 AOT
 *       backend).
 *   <li>{@code IR}: Executes the verified, lowered {@code IrTemplate} (Milestone M7 reference
 *       backend).
 *   <li>{@code AST}: Executes directly on the parsed {@code VtlTemplate} AST (legacy fallback).
 * </ul>
 */
public enum ExecutionTier {
  AOT_BYTECODE,
  IR,
  AST
}
