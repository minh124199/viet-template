package io.github.minh124199.viettemplate.assets;

/**
 * Strategy interface for serializing Java objects to JSON text for the client data bridge.
 *
 * <p>Implementations should serialize {@code value} into {@code target} as valid JSON text.
 */
@FunctionalInterface
public interface ClientDataSerializer {

  /**
   * Serializes the given value into the target {@link Appendable}.
   *
   * @param value the Java object to serialize
   * @param target the destination appendable
   * @throws ClientDataSerializationException if serialization fails
   */
  void serialize(Object value, Appendable target) throws ClientDataSerializationException;
}
