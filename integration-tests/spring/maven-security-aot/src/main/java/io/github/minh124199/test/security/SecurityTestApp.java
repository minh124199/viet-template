package io.github.minh124199.test.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class SecurityTestApp {

  public static void main(String[] args) {
    SpringApplication.run(SecurityTestApp.class, args);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/public", "/error")
                    .permitAll()
                    .requestMatchers("/admin")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers("/user")
                    .hasAuthority("ROLE_USER")
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails alice =
        User.withUsername("alice")
            .password("{noop}password")
            .authorities("ROLE_USER", "ROLE_ADMIN")
            .build();
    UserDetails bob =
        User.withUsername("bob")
            .password("{noop}password")
            .authorities("ROLE_USER")
            .build();
    return new InMemoryUserDetailsManager(alice, bob);
  }
}
