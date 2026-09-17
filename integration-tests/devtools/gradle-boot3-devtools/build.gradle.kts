plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.github.minh124199.viet-template") version "0.2.2-SNAPSHOT"
}

group = "io.github.minh124199.test"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.2-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-spring-security:0.2.2-SNAPSHOT")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
