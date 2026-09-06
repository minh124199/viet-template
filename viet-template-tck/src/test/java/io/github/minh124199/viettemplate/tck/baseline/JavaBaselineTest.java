package io.github.minh124199.viettemplate.tck.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.DataInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaBaselineTest {

  private static final int JAVA_17_CLASS_FILE_MAJOR_VERSION = 61;

  @Test
  void verifiesRuntimeJvmMeetsBaseline() {
    int featureVersion = Runtime.version().feature();
    assertThat(featureVersion)
        .as("Runtime JVM must be at least Java 17")
        .isGreaterThanOrEqualTo(17);
  }

  @ParameterizedTest
  @ValueSource(
      classes = {
        TemplateId.class,
        StringTemplateOutput.class,
        VtlProfile.class,
        io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter.class
      })
  void verifiesProductionBytecodeTargetIsJava17(Class<?> clazz) throws Exception {
    String classResource = "/" + clazz.getName().replace('.', '/') + ".class";
    try (InputStream in = clazz.getResourceAsStream(classResource)) {
      assertThat(in).as("Class resource %s should be readable", classResource).isNotNull();
      try (DataInputStream data = new DataInputStream(in)) {
        int magic = data.readInt();
        assertThat(magic).as("Magic bytes must be 0xCAFEBABE").isEqualTo(0xCAFEBABE);
        int minor = data.readUnsignedShort();
        int major = data.readUnsignedShort();

        assertThat(major)
            .as(
                "Class file major version for %s must target Java 17 (class version 61)",
                clazz.getSimpleName())
            .isEqualTo(JAVA_17_CLASS_FILE_MAJOR_VERSION);
      }
    }
  }
}
