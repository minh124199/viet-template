plugins {
    java
}

group = "io.github.minh124199.test"
version = "1.0.0"

repositories {
    val isolatedRepository = providers.gradleProperty("m18MavenRepository")
        .orElse(providers.environmentVariable("M18_MAVEN_REPOSITORY"))
    if (isolatedRepository.isPresent) {
        maven { url = uri(isolatedRepository.get()) }
    } else {
        mavenLocal()
    }
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val vietTemplateVersion = "0.2.2-SNAPSHOT"

dependencies {
    testImplementation("io.github.minh124199:viet-template-tck:$vietTemplateVersion")
    testImplementation("io.github.minh124199:viet-template-vtl-interpreter:$vietTemplateVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}
