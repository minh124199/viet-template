package io.github.minh124199.viettemplate.language.vtl.ir.constant;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Constant pool for a compiled template, storing static text chunks and reusable constants.
 *
 * <p>Deduplicates identical static text constants to minimize memory overhead.
 */
public final class IrConstantPool {

  private final List<IrTextConstant> textConstants = new ArrayList<>();
  private final Map<String, Integer> textToId = new LinkedHashMap<>();

  public IrConstantPool() {}

  public IrConstantPool(List<IrTextConstant> constants) {
    Objects.requireNonNull(constants, "constants must not be null");
    for (IrTextConstant c : constants) {
      add(c);
    }
  }

  /**
   * Registers a static text chunk, deduplicating with existing entries if present.
   *
   * @param text the static text
   * @param span source location of this text occurrence
   * @return unique ID of the constant in this pool
   */
  public int registerText(String text, SourceSpan span) {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(span, "span must not be null");
    Integer existingId = textToId.get(text);
    if (existingId != null) {
      return existingId;
    }
    int id = textConstants.size();
    IrTextConstant constant = IrTextConstant.of(id, text, span);
    textConstants.add(constant);
    textToId.put(text, id);
    return id;
  }

  /**
   * Adds an explicitly created constant to the pool.
   *
   * @param constant the text constant
   */
  public void add(IrTextConstant constant) {
    Objects.requireNonNull(constant, "constant must not be null");
    while (textConstants.size() <= constant.id()) {
      textConstants.add(null);
    }
    textConstants.set(constant.id(), constant);
    textToId.put(constant.text(), constant.id());
  }

  /**
   * Retrieves a text constant by its pool ID.
   *
   * @param id constant ID
   * @return text constant if present
   */
  public Optional<IrTextConstant> getTextConstant(int id) {
    if (id < 0 || id >= textConstants.size()) {
      return Optional.empty();
    }
    return Optional.ofNullable(textConstants.get(id));
  }

  /** Returns an unmodifiable view of all registered text constants. */
  public List<IrTextConstant> allTextConstants() {
    return Collections.unmodifiableList(textConstants);
  }

  /** Returns the total number of text constants in this pool. */
  public int size() {
    return textConstants.size();
  }

  /** Returns true if the pool contains no constants. */
  public boolean isEmpty() {
    return textConstants.isEmpty();
  }
}
