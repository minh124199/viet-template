package io.github.minh124199.viettemplate.spring.client;

import io.github.minh124199.viettemplate.assets.JacksonClientDataSerializer;

/**
 * Spring-specific {@link io.github.minh124199.viettemplate.assets.ClientDataSerializer} adapting a
 * Spring-managed Jackson {@code ObjectMapper} to the Viet Template client data bridge.
 *
 * <p>Supports both Jackson 2.x ({@code com.fasterxml.jackson.databind.ObjectMapper}) and Jackson
 * 3.x ({@code tools.jackson.databind.ObjectMapper}) without hard compile-time binding.
 */
public class SpringJacksonClientDataSerializer extends JacksonClientDataSerializer {

  /**
   * Constructs a serializer adapting the provided Spring Jackson {@code ObjectMapper}.
   *
   * @param objectMapper the Jackson mapper bean
   */
  public SpringJacksonClientDataSerializer(Object objectMapper) {
    super(objectMapper);
  }
}
