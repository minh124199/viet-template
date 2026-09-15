package io.github.minh124199.test.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SpringTestApp.class)
@AutoConfigureMockMvc
public class SpringGradleMockMvcTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private VietTemplateViewResolver viewResolver;

  @Test
  @DisplayName("MockMvc resolves /hello view, binds model, and renders output")
  void testMockMvcHello() throws Exception {
    mockMvc
        .perform(get("/hello").param("name", "Alice").param("location", "Hanoi"))
        .andExpect(status().isOk())
        .andExpect(view().name("hello"))
        .andExpect(model().attribute("name", "Alice"))
        .andExpect(model().attribute("location", "Hanoi"))
        .andExpect(content().string(containsString("<h1>Hello, Alice!</h1>")))
        .andExpect(content().string(containsString("<p>Welcome to Hanoi.</p>")));
  }

  @Test
  @DisplayName("MockMvc resolves /users view and renders list")
  void testMockMvcUsers() throws Exception {
    mockMvc
        .perform(get("/users"))
        .andExpect(status().isOk())
        .andExpect(view().name("users"))
        .andExpect(model().attributeExists("users"))
        .andExpect(content().string(containsString("<li>Alice - Admin</li>")))
        .andExpect(content().string(containsString("<li>Bob - Engineer</li>")));
  }

  @Test
  @DisplayName("Rejects path traversal view name directly and via MVC request")
  void testPathTraversalRejection() {
    assertThatThrownBy(() -> viewResolver.resolveViewName("../secret", Locale.ROOT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path traversal");

    assertThatThrownBy(() -> mockMvc.perform(get("/malicious-traversal")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Missing view returns null from ViewResolver")
  void testMissingView() throws Exception {
    assertThat(viewResolver.resolveViewName("non-existent-template", Locale.ROOT)).isNull();
  }
}
