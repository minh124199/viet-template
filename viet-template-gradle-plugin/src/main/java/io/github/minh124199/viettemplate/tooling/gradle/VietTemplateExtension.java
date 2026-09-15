package io.github.minh124199.viettemplate.tooling.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Extension for configuring Viet Template Ahead-Of-Time (AOT) compilation in Gradle builds. */
public abstract class VietTemplateExtension {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateExtension(ObjectFactory objects) {
    getSourceDirectory().convention(objects.directoryProperty());
    getOutputDirectory().convention(objects.directoryProperty());
    getResourceOutputDirectory().convention(objects.directoryProperty());
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getPackagePrefix().convention("io.github.minh124199.viettemplate.generated");
    getFailOnWarning().convention(false);
    getIncremental().convention(true);
  }

  public abstract DirectoryProperty getSourceDirectory();

  public abstract DirectoryProperty getOutputDirectory();

  public abstract DirectoryProperty getResourceOutputDirectory();

  public abstract ListProperty<String> getIncludes();

  public abstract ListProperty<String> getExcludes();

  public abstract Property<String> getEncoding();

  public abstract Property<String> getPackagePrefix();

  public abstract Property<Boolean> getFailOnWarning();

  public abstract Property<Boolean> getIncremental();
}
