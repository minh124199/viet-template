package io.github.minh124199.viettemplate.vtl.interpreter;

import java.util.Arrays;
import java.util.Objects;

/** Slot-backed storage for statically resolved template variable bindings. */
public final class ExecutionFrame {

  private EvaluationValue[] slots;

  public ExecutionFrame(int slotCount) {
    if (slotCount < 0) throw new IllegalArgumentException("slotCount must be >= 0");
    slots = new EvaluationValue[slotCount];
    Arrays.fill(slots, EvaluationValue.undefined());
  }

  public int size() {
    return slots.length;
  }

  /** Returns semantic undefined for every allocated slot until a value is written. */
  public EvaluationValue get(int slot) {
    checkSlot(slot);
    return slots[slot];
  }

  public void set(int slot, EvaluationValue value) {
    ensureCapacity(slot);
    slots[slot] = Objects.requireNonNull(value, "value must not be null");
  }

  public void reset(int slot) {
    ensureCapacity(slot);
    slots[slot] = EvaluationValue.undefined();
  }

  public void reset(int[] slotsToReset) {
    if (slotsToReset == null) return;
    for (int slot : slotsToReset) {
      reset(slot);
    }
  }

  public void seed(int slot, EvaluationValue value) {
    ensureCapacity(slot);
    slots[slot] = Objects.requireNonNull(value, "value must not be null");
  }

  private void ensureCapacity(int slot) {
    if (slot < 0) throw new IndexOutOfBoundsException("slot must be >= 0: " + slot);
    if (slot < slots.length) return;
    int oldSize = slots.length;
    int newSize = Math.max(slot + 1, Math.max(4, oldSize * 2));
    slots = Arrays.copyOf(slots, newSize);
    Arrays.fill(slots, oldSize, newSize, EvaluationValue.undefined());
  }

  private void checkSlot(int slot) {
    if (slot < 0 || slot >= slots.length) {
      throw new IndexOutOfBoundsException("slot " + slot + " outside frame size " + slots.length);
    }
  }
}
