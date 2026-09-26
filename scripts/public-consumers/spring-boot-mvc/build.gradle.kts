plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "1.0.0"

val consumerVersion = providers.gradleProperty("consumerVersion").getOrElse("@CONSUMER_VERSION@")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.github.minh124199:viet-template-spring-boot-starter:$consumerVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Match the published Java 21 baseline even when Gradle runs on newer JDKs.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
