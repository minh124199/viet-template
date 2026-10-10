package io.github.minh124199.test.frontend.e2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails alice =
        User.withUsername("alice")
            .password("{noop}secret")
            .roles("USER")
            .build();
    UserDetails admin =
        User.withUsername("admin")
            .password("{noop}admin-secret")
            .roles("ADMIN", "USER")
            .build();
    return new InMemoryUserDetailsManager(alice, admin);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/assets/**", "/health", "/login", "/api/test/reset")
                    .permitAll()
                    .requestMatchers("/secure/admin")
                    .hasRole("ADMIN")
                    .requestMatchers("/secure/**")
                    .hasRole("USER")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .permitAll()
                    .defaultSuccessUrl("/secure/employees/42", true))
        .csrf(
            csrf ->
                csrf.ignoringRequestMatchers("/api/test/reset", "/health"));
    return http.build();
  }
}
