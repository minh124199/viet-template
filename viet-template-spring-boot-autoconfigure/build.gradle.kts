description = "Viet Template Spring Boot Auto-Configuration"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-runtime"))
    api(project(":viet-template-spring"))
    api(libs.spring.boot)
    api(libs.spring.boot.autoconfigure)

    annotationProcessor(libs.spring.boot.configuration.processor)
    compileOnly(libs.spring.boot.configuration.processor)
    compileOnly(libs.jakarta.servlet.api)
    compileOnly(project(":viet-template-spring-security"))
    compileOnly(libs.spring.security.core)
    compileOnly(libs.spring.security.web)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(project(":viet-template-spring-security"))
    testImplementation(libs.spring.security.core)
    testImplementation(libs.spring.security.web)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
