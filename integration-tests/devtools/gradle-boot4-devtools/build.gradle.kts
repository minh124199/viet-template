plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.github.minh124199.viet-template") version "1.0.0-RC3"
}

group = "io.github.minh124199.test"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("io.github.minh124199:viet-template-spring-boot-starter:1.0.0-RC3")
    implementation("io.github.minh124199:viet-template-spring-security:1.0.0-RC3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

vietTemplate {
    excludes.add("**/dynamic-page.vtl")
}

tasks.named("processResources") {
    dependsOn("compileVietTemplates")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
