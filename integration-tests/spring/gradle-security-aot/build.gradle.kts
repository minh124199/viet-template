plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.github.minh124199.viet-template") version "0.2.3-SNAPSHOT"
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
    implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.3-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-spring-security:0.2.3-SNAPSHOT")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("processResources") {
    dependsOn("compileVietTemplates")
}

tasks.bootJar {
    archiveFileName.set("spring-gradle-security-aot-1.0.0.jar")
}
