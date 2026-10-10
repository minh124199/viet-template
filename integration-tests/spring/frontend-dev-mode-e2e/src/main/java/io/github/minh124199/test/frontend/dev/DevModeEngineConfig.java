package io.github.minh124199.test.frontend.dev;

import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevModeEngineConfig {

  @Bean
  public VietTemplateEngineCustomizer devModeCustomizer() {
    return builder -> {
      Path srcDir = Path.of("src/main/viet-template").toAbsolutePath();
      if (!Files.isDirectory(srcDir)) {
        srcDir = Path.of("integration-tests/spring/frontend-dev-mode-e2e/src/main/viet-template").toAbsolutePath();
      }
      if (Files.isDirectory(srcDir)) {
        builder.repository(FilesystemTemplateRepository.of(srcDir))
            .hotReload(true)
            .watchDebounceMillis(50L);
      }
    };
  }
}
