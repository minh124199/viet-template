package io.github.minh124199.viettemplate.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.quarkus.VietTemplateProducer;
import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.junit.jupiter.api.Test;

class VietTemplateProcessorTest {

  @Test
  void testFeatureRegistration() {
    VietTemplateProcessor processor = new VietTemplateProcessor();
    FeatureBuildItem item = processor.feature();
    assertThat(item.getName()).isEqualTo("viet-template");
  }

  @Test
  void testAdditionalBeansRegistration() {
    VietTemplateProcessor processor = new VietTemplateProcessor();
    AdditionalBeanBuildItem item = processor.createBeans();
    assertThat(item.getBeanClasses())
        .contains(VietTemplateProducer.class.getName(), VietTemplateRenderer.class.getName());
    assertThat(item.isRemovable()).isFalse();
  }
}
