package io.github.minh124199.test.nativeapp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NativeTestAppMockMvcTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void publicEndpointRendersModelAndEscapesHostileInput() throws Exception {
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
  void authenticatedDashboardRendersSecurityFacade() throws Exception {
    mockMvc
        .perform(get("/dashboard").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("admin")))
        .andExpect(content().string(containsString("true")));
  }
}
