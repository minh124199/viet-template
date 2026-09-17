package io.github.minh124199.test.devtools;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DevToolsTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DevToolsTestAppMockMvcTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void publicEndpointRendersSuccessfully() throws Exception {
    mockMvc
        .perform(get("/public"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Welcome, Alice!")))
        .andExpect(content().string(containsString("Account: AliceAccount (Logins: 10)")))
        .andExpect(content().string(containsString("Profile: Alice Wonderland")))
        .andExpect(content().string(containsString("Theme: dark")))
        .andExpect(content().string(containsString("Status: Veteran")))
        .andExpect(content().string(containsString("<li>java</li>")))
        .andExpect(content().string(containsString("<div id=\"escaped\"><script>alert('x')</script></div>")));
  }

  @Test
  void unauthenticatedDashboardReturnsUnauthorizedOrRedirect() throws Exception {
    mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedDashboardRendersSecurityView() throws Exception {
    mockMvc
        .perform(get("/dashboard").with(httpBasic("user", "password")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("user")))
        .andExpect(content().string(containsString("true")))
        .andExpect(content().string(containsString("false")));
  }

  @Test
  void nonAdminOnAdminEndpointReturnsForbidden() throws Exception {
    mockMvc
        .perform(get("/admin").with(httpBasic("user", "password")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminOnAdminEndpointSucceeds() throws Exception {
    mockMvc
        .perform(get("/admin").with(httpBasic("admin", "admin")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("admin")))
        .andExpect(content().string(containsString("true")));
  }

  @Test
  void restartGenerationEndpointReturnsJsonMetadata() throws Exception {
    mockMvc
        .perform(get("/__test/restart-generation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contextId").isNotEmpty())
        .andExpect(jsonPath("$.engineId").isNotEmpty())
        .andExpect(jsonPath("$.classLoaderId").isNotEmpty())
        .andExpect(jsonPath("$.classLoaderName").isNotEmpty());
  }

  @Test
  void leakCheckEndpointReturnsStatus() throws Exception {
    mockMvc
        .perform(get("/__test/classloader-leak-check"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalTracked").isNumber())
        .andExpect(jsonPath("$.leakFree").isBoolean());
  }
}
