package io.github.minh124199.test.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SecurityTestApp.class)
@AutoConfigureMockMvc
class SpringMavenSecurityMockMvcTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @WithAnonymousUser
  @DisplayName("Anonymous request to public page renders anonymous SecurityView")
  void anonymousPublicPage() throws Exception {
    mockMvc
        .perform(get("/public"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>Public Page</h1>")))
        .andExpect(content().string(containsString("Browsing anonymously")));
  }

  @Test
  @DisplayName("Unauthenticated request to protected endpoint is intercepted by Spring Security")
  void unauthenticatedProtectedEndpoint() throws Exception {
    mockMvc.perform(get("/admin")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "alice",
      authorities = {"ROLE_USER", "ROLE_ADMIN"})
  @DisplayName("Authenticated ADMIN user sees welcome greeting, admin panel, user panel, and CSRF token")
  void authenticatedAdminUser() throws Exception {
    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>Welcome, alice!</h1>")))
        .andExpect(content().string(containsString("Admin Access Granted")))
        .andExpect(content().string(containsString("User Access Granted")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @Test
  @WithMockUser(
      username = "bob",
      authorities = {"ROLE_USER"})
  @DisplayName("Authenticated regular user sees user panel but NOT admin panel")
  void authenticatedRegularUser() throws Exception {
    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>Welcome, bob!</h1>")))
        .andExpect(content().string(not(containsString("Admin Access Granted"))))
        .andExpect(content().string(containsString("User Access Granted")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @Test
  @WithMockUser(
      username = "bob",
      authorities = {"ROLE_USER"})
  @DisplayName("Regular user accessing admin endpoint receives HTTP 403 Forbidden")
  void regularUserAccessingAdminForbidden() throws Exception {
    mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "<script>alert('xss')</script>")
  @DisplayName("Adversarial username in Spring Security is safely processed by SecurityView contract")
  void adversarialUsername() throws Exception {
    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>Welcome, <script>alert('xss')</script>!</h1>")))
        .andExpect(content().string(containsString("User Access Granted")))
        .andExpect(content().string(not(containsString("Admin Access Granted"))));
  }
}
