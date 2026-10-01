package io.github.minh124199.viettemplate.api;

/**
 * Extension of {@link RenderContext} that supports positional slot-indexed access to template
 * parameters, eliminating string-based lookup overhead during AOT template seeding.
 *
 * <p>When a {@link TemplateContract} defines parameters in a canonical order, implementations of
 * this interface allow the compiled template to extract values by slot index rather than by name,
 * replacing N string comparisons with N direct array accesses per render invocation.
 *
 * <p>Implementations must guarantee that {@code getBySlot(i)} returns the same value as {@code
 * get(parameterNameAtSlotI)} for all valid slot indices.
 *
 * @since M26
 */
public interface SlottedRenderContext extends RenderContext {

  /**
   * Returns the value at the given parameter slot index.
   *
   * @param slot zero-based parameter slot index matching the {@link TemplateContract} parameter
   *     order
   * @return the parameter value, or {@code null} if the slot is unoccupied
   * @throws IndexOutOfBoundsException if slot is negative or {@code >= slotCount()}
   */
  Object getBySlot(int slot);

  /**
   * Returns the number of positional slots available.
   *
   * @return number of slots
   */
  int slotCount();
}
