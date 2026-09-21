package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = VietTemplateMultiSuffixIntegrationTest.TestApp.class,
    properties = {
      "viet-template.prefix=views/",
      "viet-template.suffixes[0]=.vtl",
      "viet-template.suffixes[1]=.vm",
      "viet-template.order=1"
    })
class VietTemplateMultiSuffixIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TemplateEngine templateEngine;
  @Autowired private VietTemplateViewResolver viewResolver;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    TestTemplateEngine testEngine = (TestTemplateEngine) templateEngine;
    testEngine.registerTemplate(
        TemplateId.of("views/only-vtl.vtl"), createTemplate("Rendered VTL Content"));
    testEngine.registerTemplate(
        TemplateId.of("views/only-vm.vm"), createTemplate("Rendered VM Content"));
    testEngine.registerTemplate(
        TemplateId.of("views/conflict.vtl"), createTemplate("Precedence VTL Content"));
    testEngine.registerTemplate(
        TemplateId.of("views/conflict.vm"), createTemplate("Precedence VM Content"));
  }

  @Test
  @DisplayName("Resolves view without extension using first suffix (.vtl)")
  void resolvesViewWithFirstSuffix() throws Exception {
    mockMvc
        .perform(get("/test-vtl"))
        .andExpect(status().isOk())
        .andExpect(view().name("only-vtl"))
        .andExpect(content().string(containsString("Rendered VTL Content")));
  }

  @Test
  @DisplayName("Resolves view without extension falling through to second suffix (.vm)")
  void resolvesViewWithSecondSuffix() throws Exception {
    mockMvc
        .perform(get("/test-vm"))
        .andExpect(status().isOk())
        .andExpect(view().name("only-vm"))
        .andExpect(content().string(containsString("Rendered VM Content")));
  }

  @Test
  @DisplayName("Suffix precedence: .vtl wins over .vm when both exist")
  void suffixPrecedenceWhenBothExist() throws Exception {
    mockMvc
        .perform(get("/test-conflict"))
        .andExpect(status().isOk())
        .andExpect(view().name("conflict"))
        .andExpect(content().string(containsString("Precedence VTL Content")));
  }

  @Test
  @DisplayName("Missing view returns null from resolver")
  void missingViewReturnsNull() throws Exception {
    assertThat(viewResolver.resolveViewName("missing", Locale.ROOT)).isNull();
    assertThat(viewResolver.resolveViewName("nonexistent", Locale.ROOT)).isNull();
  }

  private static Template createTemplate(String content) {
    return new Template() {
      @Override
      public TemplateDescriptor descriptor() {
        return null;
      }

      @Override
      public void render(RenderContext context, TemplateOutput output) throws IOException {
        output.write(content);
      }
    };
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @org.springframework.context.annotation.Import(TestApp.MultiSuffixController.class)
  static class TestApp {

    @Controller
    static class MultiSuffixController {

      @GetMapping("/test-vtl")
      String onlyVtl() {
        return "only-vtl";
      }

      @GetMapping("/test-vm")
      String onlyVm() {
        return "only-vm";
      }

      @GetMapping("/test-conflict")
      String conflict() {
        return "conflict";
      }

      @GetMapping("/test-missing")
      String missing() {
        return "missing";
      }
    }
  }
}
